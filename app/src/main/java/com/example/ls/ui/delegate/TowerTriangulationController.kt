package com.example.ls.ui.delegate

import android.app.Application
import android.location.Location
import com.example.ls.data.CellSignalData
import com.example.ls.data.LocationTracker
import com.example.ls.data.SettingsRepository
import com.example.ls.data.TowerDisplayEntry
import com.example.ls.data.TowerPositionRepository
import com.example.ls.ui.AppTab
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Auto-triangulação de posição de torres (opt-in) — GPS de baixa frequência +
 * acúmulo do centroide por torre. Extraído de SignalViewModel; não é um
 * ViewModel, é dirigido pelo [scope] (viewModelScope) que o ViewModel dono
 * repassa. Diferente de PersistedHistoryController, este PRECISA saber a aba
 * atual e se o app está em primeiro plano — [currentTab]/[appInForeground]
 * continuam de propriedade do ViewModel (só esta classe os lê, não justificam
 * nenhum "estado de sessão" compartilhado à parte) — e é alimentado pelo
 * mesmo hook [onReading] que PersistedHistoryController, chamado pelo
 * SignalPollingController a cada tick com leitura válida; não tem loop próprio.
 */
class TowerTriangulationController(
    app: Application,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
    currentTab: StateFlow<AppTab>,
    appInForeground: StateFlow<Boolean>,
    isPolling: StateFlow<Boolean>,
) {
    companion object {
        /** Limita com que frequência uma MESMA torre funde uma amostra nova na
         *  triangulação — sem isso, um aparelho parado com poll de 1s infla o
         *  sampleCount com amostras correlacionadas do mesmo ponto, o que
         *  destrava "posição conhecida" (ver TowerPositionMath.classify) sem
         *  nenhum espalhamento espacial real por trás. */
        const val TOWER_FOLD_THROTTLE_MS = 7_000L

        /** Poda [lastTowerFoldMs] de entradas velhas demais pra ainda throttlar
         *  qualquer coisa — sem isso, uma sessão longa passando por muitas torres
         *  diferentes (ex.: uma viagem) faria esse mapa em RAM crescer sem limite
         *  pelo resto da vida do processo. */
        const val TOWER_FOLD_PRUNE_AGE_MS = TOWER_FOLD_THROTTLE_MS * 20

        /** LocationTracker cai para NETWORK_PROVIDER quando o GPS não está
         *  disponível/travado, e esse provider pode ter erro de 100m a alguns
         *  km — bem acima do MIN_SPREAD_METERS (40m) que TowerPositionMath.classify
         *  usa pra promover uma torre a "posição conhecida". Sem esse filtro, o
         *  próprio ruído de uma localização imprecisa (não movimento real do
         *  usuário) seria suficiente pra destravar uma posição inventada.
         *
         *  Precisa ficar BEM abaixo de MIN_SPREAD_METERS, não só abaixo — duas
         *  amostras cada uma com um círculo de erro de X metros podem, no pior
         *  caso, discordar em até ~2X metros de ruído puro. Um piso de 50m (valor
         *  usado numa primeira versão desse filtro) na verdade não impedia nada:
         *  ficava ACIMA do próprio limiar de 40m que deveria proteger. 20m deixa
         *  fixos de GPS típicos (5-20m) passarem normalmente, e barra a maior
         *  parte do ruído do NETWORK_PROVIDER antes dele se disfarçar de
         *  espalhamento real. */
        const val MAX_ACCEPTABLE_LOCATION_ACCURACY_M = 20f
    }

    private val towerPositionRepository = TowerPositionRepository(app)
    private val locationTracker = LocationTracker(app)

    /** Opt-in, desligado por padrão (ver SettingsRepository). Liga a localização
     *  de baixa frequência (LocationTracker) e o acúmulo do centroide por torre. */
    val towerTriangulationEnabled: StateFlow<Boolean> = settingsRepository.towerTriangulationEnabled.stateIn(
        scope, SharingStarted.Eagerly, false,
    )

    fun setTowerTriangulationEnabled(enabled: Boolean) {
        scope.launch { settingsRepository.setTowerTriangulationEnabled(enabled) }
    }

    /** Leitura fresca da fonte (não o snapshot Eagerly de [towerTriangulationEnabled])
     *  — usada só pelo check de permissão revogada em MainScreen.kt no cold-start,
     *  onde ON_START pode disparar antes da primeira emissão do DataStore, fazendo
     *  o snapshot ainda mostrar o valor-semente `false` mesmo com `true` persistido. */
    suspend fun isTowerTriangulationEnabledPersisted(): Boolean =
        settingsRepository.towerTriangulationEnabled.first()

    /** Erro da última tentativa de [clearTowerEstimates] — antes disso a falha
     *  (ex.: disco cheio no DELETE) era engolida em silêncio, deixando o usuário
     *  achar que apagou as estimativas quando na verdade nada mudou. Mesmo
     *  padrão de shareError/renameError/deleteError já usado em
     *  ExportManagerViewModel. */
    private val _clearEstimatesError = MutableStateFlow<String?>(null)
    val clearEstimatesError: StateFlow<String?> = _clearEstimatesError.asStateFlow()

    fun dismissClearEstimatesError() {
        _clearEstimatesError.value = null
    }

    /** Ver [clearTowerEstimates] — guarda contra a corrida entre limpar e um
     *  fold concorrente já em andamento. */
    private var towerEstimatesEpoch = 0

    /** Throttle em memória por torre — não persistido, só um limitador de taxa
     *  (ver TOWER_FOLD_THROTTLE_MS). */
    private val lastTowerFoldMs = mutableMapOf<String, Long>()

    /** Localização atual do aparelho (mesma instância que alimenta o fold de
     *  amostras) — TowersScreen usa isso como centro do escopo de radar, pra
     *  calcular distância/rumo até cada torre já mapeada. Não dispara nenhum
     *  fix novo por si só: só reflete o que [locationTracker] já está rastreando. */
    val currentLocation: StateFlow<Location?> = locationTracker.lastLocation

    private val _towerEstimatesDetailed = MutableStateFlow<List<TowerDisplayEntry>>(emptyList())
    val towerEstimatesDetailed: StateFlow<List<TowerDisplayEntry>> = _towerEstimatesDetailed.asStateFlow()

    /** Releitura sob demanda (não contínua): TowersScreen chama isso ao entrar
     *  na aba — um full table scan a cada tick de poll seria desperdício pra
     *  uma tela que não fica aberta o tempo todo. */
    fun refreshTowerEstimatesDetailed() {
        scope.launch {
            try {
                _towerEstimatesDetailed.value = towerPositionRepository.getAllEstimatesDetailed()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
    }

    init {
        // LocationTracker só fica ativo com o toggle ligado, com o app em
        // primeiro plano, com o polling de fato rodando, E na aba Sinal (onde as
        // amostras são fundidas) OU na aba Torres (onde a posição atual é o
        // centro do radar) — mais restrito que só "permissão concedida em algum
        // momento", de propósito: sem o termo isPolling, uma revogação de
        // permissão em sessão (READ_PHONE_STATE revogado com o app já aberto)
        // parava o polling via onPermissionDenied() mas não mudava
        // tab/foreground/toggle, deixando o GPS pedindo updates de localização
        // indefinidamente sem nenhuma leitura de sinal pra consumi-los.
        scope.launch {
            combine(
                towerTriangulationEnabled, currentTab, appInForeground, isPolling,
            ) { enabled, tab, foreground, polling ->
                enabled && foreground && polling && (tab == AppTab.SIGNAL || tab == AppTab.TOWERS)
            }
                .distinctUntilChanged()
                .collect { shouldTrack -> if (shouldTrack) locationTracker.start() else locationTracker.stop() }
        }
    }

    /** Apaga as estimativas de posição acumuladas — irreversível, distinto do
     *  "Apagar histórico salvo" (tabelas independentes). */
    fun clearTowerEstimates() {
        // Incrementado ANTES de qualquer suspensão (chamado da Main thread pela
        // UI, então isso roda atomicamente em relação a qualquer outra
        // corrotina lançada por esta classe). O epoch é passado para
        // towerPositionRepository — o repositório checa o epoch DENTRO da
        // mesma seção crítica que faz o upsert, então um fold cujo epoch já
        // ficou pra trás é descartado antes de tocar o banco (ver
        // TowerPositionRepository.foldSample), não impedindo só um fold em
        // voo de ressuscitar a linha no Room depois do DELETE.
        towerEstimatesEpoch++
        val epoch = towerEstimatesEpoch
        scope.launch {
            try {
                towerPositionRepository.clearAll(epoch)
                // Sem isso, uma torre fundida pouco antes do apagar ficaria
                // "throttled" por até TOWER_FOLD_THROTTLE_MS mesmo depois do banco
                // já estar vazio, atrasando a primeira amostra fresca pós-limpeza.
                lastTowerFoldMs.clear()
                _clearEstimatesError.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _clearEstimatesError.value = "Não foi possível apagar as estimativas de torre."
            }
        }
    }

    /** Hook chamado pelo SignalPollingController a cada leitura válida — opt-in,
     *  desacoplado do CSV/export, respeitando o throttle por torre. Lê a fonte
     *  direto (não towerTriangulationEnabled.value, o snapshot Eagerly) pelo
     *  mesmo motivo do subscriptionId em SignalPollingController: evita pular
     *  o fold no primeiro tick pós-restart só porque o DataStore ainda não emitiu. */
    suspend fun onReading(cell: CellSignalData, timestampMs: Long) {
        // try/catch aqui, não deixado propagar: onReading() é chamado direto
        // (não via scope.launch) de dentro do try/catch único de
        // SignalPollingController.refreshOnce() — ou seja, roda na própria
        // corrotina-tronco do laço de poll. Sem isso, uma falha transitória de
        // leitura do DataStore (ex.: concorrendo com setTowerTriangulationEnabled())
        // vira SignalUiState.Error pra aba inteira, escondendo uma leitura de
        // sinal que já tinha sido gravada com sucesso naquele mesmo tick, por
        // causa de uma feature opt-in sem relação nenhuma com o problema.
        // CancellationException tem que propagar mesmo assim — por rodar na
        // corrotina-tronco, engolir um cancelamento aqui (ex.: pollingJob.cancel()
        // durante a suspensão do .first() abaixo) quebraria o cancelamento
        // cooperativo do laço de poll inteiro, o mesmo risco de refreshOnce().
        try {
            if (settingsRepository.towerTriangulationEnabled.first()) maybeFoldTowerSample(cell, timestampMs)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}
    }

    private fun maybeFoldTowerSample(cell: CellSignalData, timestampMs: Long) {
        val cellId = cell.netInfo.cellId ?: return
        // isFresh(): revalida a idade a cada leitura, não só na primeira vez que
        // o valor chegou — se o provider parar de entregar updates (desligado,
        // throttling de OEM), esse guard é o que impede o app de continuar
        // fundindo indefinidamente a última posição conhecida como se fosse atual.
        // hasAccuracy() && accuracy <= piso — não "!hasAccuracy() || ...": um fix
        // SEM accuracy reportada tinha que ser aceito por esse filtro (o
        // inverso do que ele existe pra fazer), já que "não sei a precisão"
        // deve ser tratado como "não confio", não como "deixa passar".
        val loc = locationTracker.lastLocation.value
            ?.takeIf { locationTracker.isFresh(it) }
            ?.takeIf { it.hasAccuracy() && it.accuracy <= MAX_ACCEPTABLE_LOCATION_ACCURACY_M }
            ?: return
        // Inclui operadora + TAC/LAC na chave: em GSM o PCI vem do BSIC (só 6
        // bits) e o cellId do CID (único só dentro da própria LAC) — sem o
        // TAC/LAC, duas torres físicas diferentes podem colidir na mesma chave.
        // A operadora entra pelo mesmo motivo em roaming/dual-SIM: TAC/CID/PCI
        // não são globalmente únicos, só dentro da rede de uma operadora — sem
        // isso, duas torres de operadoras DIFERENTES que coincidem nesses
        // valores se fundiriam no mesmo centroide inventado.
        //
        // operator.replace(':', '_'): operator é texto arbitrário vindo da rede
        // (TelephonyManager.networkOperatorName) e ':' é o separador da chave —
        // sem escapar, um nome de operadora contendo ':' podia deslocar os
        // limites dos campos e colidir com a chave de uma torre totalmente
        // diferente (mesma classe de risco do CSV formula-injection já
        // mitigado em CsvFormatter: texto vindo da rede usado sem sanitização
        // numa estrutura interna).
        val towerKey = "${cell.operator.replace(':', '_')}:${cell.networkType.name}:${cell.netInfo.tac ?: -1}:$cellId:${cell.netInfo.pci ?: -1}"
        val lastFold = lastTowerFoldMs[towerKey] ?: 0L
        if (timestampMs - lastFold < TOWER_FOLD_THROTTLE_MS) return
        lastTowerFoldMs[towerKey] = timestampMs
        // Poda entradas velhas pra não deixar o mapa crescer sem limite ao longo
        // de sessões longas com muitas torres diferentes (ver TOWER_FOLD_PRUNE_AGE_MS).
        lastTowerFoldMs.entries.removeAll { timestampMs - it.value > TOWER_FOLD_PRUNE_AGE_MS }

        // Ver clearTowerEstimates(): captura o epoch ANTES de suspender, pra
        // detectar se um "Apagar estimativas" aconteceu enquanto este fold
        // estava em voo e, nesse caso, descartar o resultado em vez de
        // republicar uma torre que acabou de ser apagada.
        val epoch = towerEstimatesEpoch
        scope.launch {
            try {
                // Retorno null: um "Apagar estimativas" já rodou com epoch mais
                // recente que este fold (ver TowerPositionRepository.foldSample)
                // — a gravação em si já foi descartada lá dentro. TowersScreen lê
                // o resultado sob demanda via refreshTowerEstimatesDetailed(), não
                // há StateFlow local pra atualizar aqui.
                towerPositionRepository.foldSample(
                    epoch = epoch,
                    towerKey = towerKey,
                    networkType = cell.networkType.name,
                    cellId = cellId,
                    pci = cell.netInfo.pci,
                    tac = cell.netInfo.tac,
                    lat = loc.latitude,
                    lon = loc.longitude,
                    dbm = cell.dbm,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
    }

    /** Chamado por ViewModel.onCleared() (não uma corrotina, roda com
     *  viewModelScope já cancelado — ver LocationTracker.stopBlocking()). */
    fun stopBlocking() {
        locationTracker.stopBlocking()
    }
}
