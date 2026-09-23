package com.example.ls.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** dataStore.data pode lançar IOException se o arquivo de preferências não
 *  puder ser lido (corrompido, alguns cenários de primeiro acesso) — padrão
 *  documentado pela própria AndroidX: cai para preferências vazias (== todos
 *  os valores default abaixo) em vez de propagar sem handler para quem
 *  consome via stateIn(..., Eagerly, ...) nos delegates. */
private fun DataStore<Preferences>.dataOrEmpty(): Flow<Preferences> = data.catch { e ->
    if (e is IOException) emit(emptyPreferences()) else throw e
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.dataStore)

    companion object {
        val POLL_INTERVAL_KEY = longPreferencesKey("poll_interval_ms")
        const val DEFAULT_POLL_INTERVAL_MS = 1_000L

        /** Piso de segurança contra 0/negativo (loop sem delay, batendo no rádio
         *  sem parar). Reforçado AQUI, na camada de persistência — não só no
         *  botão "Aplicar" de SettingsScreen — porque qualquer outro caminho
         *  (uma UI futura, um teste, um valor de DataStore corrompido/editado à
         *  mão) que chame setPollIntervalMs() diretamente, ou que já tenha um
         *  valor inválido persistido de antes desse fix, precisa do mesmo piso. */
        const val MIN_POLL_INTERVAL_MS = 200L

        val SELECTED_SUBSCRIPTION_ID_KEY = intPreferencesKey("selected_subscription_id")
        val TOWER_TRIANGULATION_ENABLED_KEY = booleanPreferencesKey("tower_triangulation_enabled")

        val HISTORY_RETENTION_DAYS_KEY = intPreferencesKey("history_retention_days")

        /** 0 = "para sempre" (sem poda automática) — sentinela, não um período
         *  real, então não precisa de validação de piso como MIN_POLL_INTERVAL_MS. */
        const val RETENTION_KEEP_FOREVER = 0

        /** Padrão: 30 dias. Sem uma política de retenção, signal_readings cresce
         *  ~1 linha por tick de poll pra sempre (até 5/s no piso de 200ms) — em
         *  ~1 mês de uso contínuo isso já passa de 2,5 milhões de linhas e ~100MB
         *  de CSV, arriscando OutOfMemoryError no export (que carrega tudo em
         *  memória de uma vez). 30 dias é um teto que ainda cobre uso normal de
         *  diagnóstico sem deixar a tabela crescer sem limite; o usuário pode
         *  trocar pra "Para sempre" em Configurações se quiser. */
        const val DEFAULT_HISTORY_RETENTION_DAYS = 30
    }

    val pollIntervalMs: Flow<Long> = dataStore.dataOrEmpty().map { prefs ->
        (prefs[POLL_INTERVAL_KEY] ?: DEFAULT_POLL_INTERVAL_MS).coerceAtLeast(MIN_POLL_INTERVAL_MS)
    }

    suspend fun setPollIntervalMs(value: Long) {
        dataStore.edit { prefs -> prefs[POLL_INTERVAL_KEY] = value.coerceAtLeast(MIN_POLL_INTERVAL_MS) }
    }

    val selectedSubscriptionId: Flow<Int> = dataStore.dataOrEmpty().map { prefs ->
        prefs[SELECTED_SUBSCRIPTION_ID_KEY] ?: AUTOMATIC_SUBSCRIPTION_ID
    }

    suspend fun setSelectedSubscriptionId(value: Int) {
        dataStore.edit { prefs -> prefs[SELECTED_SUBSCRIPTION_ID_KEY] = value }
    }

    /** Opt-in, desligado por padrão: liga a estimativa de posição das torres
     *  (localização de baixa frequência + acúmulo do centroide). */
    val towerTriangulationEnabled: Flow<Boolean> = dataStore.dataOrEmpty().map { prefs ->
        prefs[TOWER_TRIANGULATION_ENABLED_KEY] ?: false
    }

    suspend fun setTowerTriangulationEnabled(value: Boolean) {
        dataStore.edit { prefs -> prefs[TOWER_TRIANGULATION_ENABLED_KEY] = value }
    }

    /** Quantos dias de histórico manter (ver [RETENTION_KEEP_FOREVER] e
     *  [DEFAULT_HISTORY_RETENTION_DAYS]) — a poda de verdade roda em
     *  SignalViewModel via SignalReadingRepository.pruneOlderThan(). */
    val historyRetentionDays: Flow<Int> = dataStore.dataOrEmpty().map { prefs ->
        prefs[HISTORY_RETENTION_DAYS_KEY] ?: DEFAULT_HISTORY_RETENTION_DAYS
    }

    suspend fun setHistoryRetentionDays(days: Int) {
        dataStore.edit { prefs -> prefs[HISTORY_RETENTION_DAYS_KEY] = days.coerceAtLeast(RETENTION_KEEP_FOREVER) }
    }
}
