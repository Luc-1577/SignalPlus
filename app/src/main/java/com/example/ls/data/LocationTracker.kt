package com.example.ls.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Localização de baixa frequência para alimentar a triangulação de torres —
 * NUNCA um fix ativo por ciclo de poll (isso pesaria a bateria como um app de
 * navegação turn-by-turn). Registra um único [LocationListener] a cada
 * ~[MIN_TIME_MS]/[MIN_DISTANCE_M] de movimento e deixa o valor em cache;
 * quem consome (SignalViewModel) só lê [lastLocation], nunca dispara um fix
 * novo — o desacoplamento entre "cadência de GPS" e "cadência de poll" é o
 * que mantém isso leve.
 *
 * Usa android.location.LocationManager puro — o projeto não depende do Play
 * Services (sem FusedLocationProviderClient), então isso não adiciona
 * nenhuma dependência nova.
 */
class LocationTracker(private val context: Context) {

    private companion object {
        const val MIN_TIME_MS = 30_000L
        const val MIN_DISTANCE_M = 10f

        /** Usado tanto na leitura inicial em cache (getLastKnownLocation() pode
         *  devolver um fix de horas/dias atrás — ex.: localização desligada,
         *  depois o usuário viaja e liga a triangulação numa cidade nova antes
         *  do primeiro update do listener chegar) quanto em toda leitura
         *  subsequente de [lastLocation] (ver [isFresh]): se o provider parar de
         *  entregar updates silenciosamente (desligado, throttling de OEM) o
         *  valor guardado fica cada vez mais velho sem nenhum outro sinal disso —
         *  sem revalidar a idade a cada uso, esse fix obsoleto continuaria sendo
         *  fundido no centroide da torre como se fosse atual, contaminando a
         *  estimativa permanentemente. */
        const val MAX_CACHED_LOCATION_AGE_MS = 5 * 60_000L
    }

    private val locationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    private val _lastLocation = MutableStateFlow<Location?>(null)
    val lastLocation: StateFlow<Location?> = _lastLocation.asStateFlow()

    // @Volatile: escrito a partir de Dispatchers.IO (start()/stop()) e de
    // qualquer thread que chame stopBlocking() (normalmente Main, via
    // onCleared()), lido dentro do LocationListener (entregue na Main looper) —
    // sem isso, a JVM não garante que a escrita feita numa thread fique visível
    // pra leitura feita na outra.
    @Volatile
    private var listener: LocationListener? = null

    // Fecha a corrida entre start() e um stop()/stopBlocking() concorrente:
    // sem isso, se stop() rodar enquanto start() ainda está registrando (entre
    // o check `listener != null` do topo e a atribuição `listener = newListener`
    // no fim), start() pode terminar DEPOIS e ressuscitar um registro de GPS
    // vivo que ninguém mais vai chamar removeUpdates() nele — um vazamento de
    // localização/bateria que sobrevive ao próprio ViewModel. Incrementado por
    // start() (ao começar) E por stopInternal() (sempre, mesmo se listener já
    // for null) — @Volatile pelo mesmo motivo de [listener].
    @Volatile
    private var generation = 0

    /** Suspend, não `fun` puro: [android.location.LocationManager] fala com o
     *  system server via Binder — CLAUDE.md exige que todo I/O de hardware rode
     *  em [Dispatchers.IO], e o chamador (SignalViewModel) já invoca isso de
     *  dentro de uma corrotina. */
    suspend fun start() = withContext(Dispatchers.IO) {
        if (listener != null) return@withContext
        val hasFineLocationPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        // GPS_PROVIDER só entra na lista se ACCESS_FINE_LOCATION foi de fato
        // concedida — pedir updates nele sem essa permissão lança
        // SecurityException; antes disso a escolha do provider nem checava
        // permissão nenhuma, e um usuário que só concede localização
        // aproximada (Android 12+ deixa escolher isso mesmo quando o app pede
        // as duas juntas) travava a triangulação pra sempre sem nenhum
        // fallback. NETWORK_PROVIDER entra sempre que habilitado — como alvo
        // principal (permissão aproximada) ou como fallback abaixo, se o
        // registro em GPS falhar por qualquer outro motivo.
        val candidateProviders = buildList {
            if (hasFineLocationPermission && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                add(LocationManager.GPS_PROVIDER)
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                add(LocationManager.NETWORK_PROVIDER)
            }
        }
        if (candidateProviders.isEmpty()) return@withContext
        val myGeneration = ++generation

        // Leitura em cache, custo zero — dá uma posição utilizável desde o
        // primeiro tick, sem esperar o primeiro update do listener abaixo.
        // Tenta cada provider candidato em ordem (não só o primeiro): um GPS
        // sem fix cacheado (comum em uso indoor) não pode mascarar uma posição
        // de rede recente já disponível no NETWORK_PROVIDER. Descarta o fix se
        // ele for velho demais (ver MAX_CACHED_LOCATION_AGE_MS).
        val cachedLocation = candidateProviders.firstNotNullOfOrNull { provider ->
            try {
                locationManager.getLastKnownLocation(provider)?.takeIf { it.isFreshEnough() }
            } catch (_: SecurityException) {
                null
            } catch (_: Exception) {
                // Alguns OEMs lançam outras RuntimeExceptions aqui (não só
                // SecurityException) — sem esse catch, uma delas propagaria sem
                // handler (viewModelScope.launch não tem CoroutineExceptionHandler)
                // e derrubaria o app inteiro por causa de uma feature opt-in.
                null
            }
        }
        // Mesmo guard de geração do registro do listener logo abaixo: sem isso,
        // um stop() concorrente enquanto o Binder call acima estava em voo já
        // tinha zerado _lastLocation, e essa atribuição incondicional
        // ressuscitaria o fix em cache (agora obsoleto) fora da janela de
        // tracking pretendida.
        if (generation == myGeneration) _lastLocation.value = cachedLocation

        // A checagem `listener === newListener` descarta callbacks que cheguem
        // DEPOIS do próprio stop() já ter rodado: removeUpdates() não garante
        // cancelar uma mensagem que já estava enfileirada na Main looper (TOCTOU) —
        // sem esse guard, um fix atrasado ressuscitaria _lastLocation fora da
        // janela de tracking pretendida, podendo ainda ser fundido numa torre.
        lateinit var newListener: LocationListener
        newListener = LocationListener { location -> if (listener === newListener) _lastLocation.value = location }

        // Tenta cada provider candidato em ordem até um registrar com sucesso —
        // sem isso, uma falha no registro em GPS (ex.: permissão revogada entre
        // a checagem acima e esta chamada) deixava a triangulação sem nenhum
        // fix, mesmo com NETWORK_PROVIDER disponível como alternativa.
        for (provider in candidateProviders) {
            try {
                locationManager.requestLocationUpdates(
                    provider, MIN_TIME_MS, MIN_DISTANCE_M, newListener, Looper.getMainLooper(),
                )
                // Só marca como "ativo" depois do registro ter sucesso — se ficasse
                // marcado antes e a chamada lançasse (ex.: sem permissão ainda),
                // o guard `listener != null` do topo desta função travaria qualquer
                // nova tentativa de start() pra sempre, mesmo após a permissão ser
                // concedida depois. Mas só mantém o registro se nenhum stop() rodou
                // enquanto requestLocationUpdates() estava em voo (generation não
                // mudou) — se mudou, desfaz o registro imediatamente em vez de
                // deixar um listener vivo que ninguém mais vai remover.
                if (generation == myGeneration) {
                    listener = newListener
                } else {
                    try { locationManager.removeUpdates(newListener) } catch (_: Exception) {}
                }
                return@withContext
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            }
        }
    }

    /** Caminho normal (chamado de dentro da corrotina que observa o toggle/aba/
     *  foreground) — IO pelo mesmo motivo de [start]: removeUpdates() também
     *  fala com o system server via Binder. */
    suspend fun stop() = withContext(Dispatchers.IO) { stopInternal() }

    /** Variante síncrona só para ViewModel.onCleared(), que não é uma corrotina
     *  e roda com viewModelScope já cancelado — não dá pra agendar trabalho
     *  suspenso ali. removeUpdates() é uma única chamada rara e barata, então o
     *  custo de rodar no thread de chamada nesse caminho específico é aceitável. */
    fun stopBlocking() = stopInternal()

    private fun stopInternal() {
        // Sempre incrementa, mesmo se listener já for null — é isso que avisa
        // um start() concorrente ainda em voo (ver o comentário de [generation])
        // que ele não deve manter o registro que está prestes a terminar.
        generation++
        listener?.let { try { locationManager.removeUpdates(it) } catch (_: Exception) {} }
        listener = null
        // Sem isso, quem consome lastLocation continuaria fundindo a última
        // posição em cache (agora obsoleta) mesmo depois do tracking parar.
        _lastLocation.value = null
    }

    /** Público de propósito: quem consome [lastLocation] (SignalViewModel, antes
     *  de fundir uma amostra) deve revalidar a idade a cada leitura, não só
     *  confiar que o valor já foi checado uma vez no momento em que chegou —
     *  ver o comentário de [MAX_CACHED_LOCATION_AGE_MS]. */
    fun isFresh(location: Location): Boolean = location.isFreshEnough()

    /** elapsedRealtime (monotônico, imune a ajuste de relógio/fuso) em vez de
     *  Location.getTime() (wall-clock) — mede idade real desde a captura do fix. */
    private fun Location.isFreshEnough(): Boolean {
        val ageMs = (SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos) / 1_000_000
        return ageMs in 0..MAX_CACHED_LOCATION_AGE_MS
    }
}
