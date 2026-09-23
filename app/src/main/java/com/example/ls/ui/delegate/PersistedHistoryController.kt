package com.example.ls.ui.delegate

import android.app.Application
import android.content.Intent
import com.example.ls.data.CellSignalData
import com.example.ls.data.SettingsRepository
import com.example.ls.data.SignalReadingRepository
import com.example.ls.data.db.SignalReadingEntity
import com.example.ls.util.CsvExportUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Histórico persistido (Room) + export CSV + retenção — extraído de
 * SignalViewModel (que acumulava isso junto com polling e triangulação de
 * torres; ver o design que motivou este split). Não é um ViewModel: é uma
 * classe comum, dirigida pelo [scope] (viewModelScope) que o ViewModel dono
 * repassa, e alimentada pelo hook [onReading] a cada leitura válida do
 * SignalPollingController — não tem loop próprio.
 */
class PersistedHistoryController(
    private val app: Application,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
) {
    companion object {
        /** Roda a poda de retenção no máximo 1x por hora, checada oportunistamente
         *  a cada gravação — não a cada tick de poll, que aconteceria até 5x/s no
         *  piso de 200ms e faria um DELETE desnecessário na maioria das vezes
         *  (nada novo cruzou o corte de retenção entre um tick e o próximo). */
        const val RETENTION_PRUNE_INTERVAL_MS = 60 * 60_000L

        private const val MS_PER_DAY = 24 * 60 * 60_000L
    }

    private val readingRepository = SignalReadingRepository(app)

    /** Quantos dias de histórico manter (0 = para sempre, sem poda automática) —
     *  ver SettingsRepository.RETENTION_KEEP_FOREVER/DEFAULT_HISTORY_RETENTION_DAYS. */
    val historyRetentionDays: StateFlow<Int> = settingsRepository.historyRetentionDays.stateIn(
        scope, SharingStarted.Eagerly, SettingsRepository.DEFAULT_HISTORY_RETENTION_DAYS,
    )

    fun setHistoryRetentionDays(days: Int) {
        scope.launch { settingsRepository.setHistoryRetentionDays(days) }
        // Aplica na hora, não só no próximo tick de poll — sem isso, trocar de
        // "Para sempre" pra "7 dias" só teria efeito visível depois da próxima
        // gravação (até pollIntervalMs de distância, geralmente imperceptível,
        // mas por que esperar se a poda em si já é barata e idempotente).
        lastPrunedAtMs = 0L
    }

    /** Throttle em memória da última poda — não persistido, reinicia a cada
     *  processo (ver RETENTION_PRUNE_INTERVAL_MS). */
    private var lastPrunedAtMs = 0L

    /** Apaga o histórico persistido (Room) — irreversível, distinto do "Limpar"
     *  da janela em RAM (que fica com SignalPollingController.history). */
    fun clearPersistedHistory() {
        scope.launch {
            try {
                readingRepository.clearAll()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
    }

    /** Erro da última tentativa de [exportReadingsAsCsv] — antes disso uma falha
     *  (disco cheio, storage revogado durante a escrita) era engolida em
     *  silêncio e o botão "Exportar" da aba Histórico simplesmente não fazia
     *  nada, sem indicar ao usuário por quê. Mesmo padrão de shareError já
     *  usado em ExportManagerViewModel para a mesma classe de falha. */
    private val _exportError = MutableStateFlow<String?>(null)
    val exportError: StateFlow<String?> = _exportError.asStateFlow()

    fun dismissExportError() {
        _exportError.value = null
    }

    /** Lê o histórico completo persistido (Room, não só a janela em RAM do
     *  SignalPollingController), monta o CSV e devolve o Intent pronto via
     *  [onReady] — tudo em [scope] (viewModelScope), não no escopo de
     *  composição de quem chama. HistoryScreen faz parte do AnimatedContent de
     *  MainScreen: trocar de aba durante um export longo descompõe a tela
     *  antiga e cancelaria um `rememberCoroutineScope()` local no meio da
     *  leitura do Room ou da escrita do CSV, abortando a exportação sem
     *  nenhum aviso. Não recebe Context de UI: a leitura do Room + escrita do
     *  CSV só precisam de [app] (File I/O/FileProvider não exigem Context de
     *  Activity) — só o `startActivity` final (que quem chama faz com
     *  [onReady]) precisa de um Context de Activity, e esse já fica na UI. */
    fun exportReadingsAsCsv(onReady: (Intent) -> Unit) {
        scope.launch {
            try {
                // getAll() e buildShareIntent() já despacham para Dispatchers.IO
                // internamente — não precisa de um withContext(IO) por fora aqui.
                val intent = CsvExportUtil.buildShareIntent(app, readingRepository.getAll())
                _exportError.value = null
                onReady(intent)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _exportError.value = "Não foi possível exportar o histórico."
            }
        }
    }

    /** Hook chamado pelo SignalPollingController a cada leitura válida — trilha
     *  paralela e permanente (Room), roda em sua própria corrotina para nunca
     *  atrasar o ciclo de polling nem o _uiState com latência de disco. O
     *  SignalReadingRepository serializa essa gravação com clearAll()/getAll()
     *  via Mutex, então não há corrida com "Apagar histórico" nem com
     *  "Exportar". Nota: não grava qual SIM originou a leitura — se o usuário
     *  trocar de chip, o histórico não distingue as leituras por SIM. */
    fun onReading(cell: CellSignalData, timestampMs: Long) {
        scope.launch {
            try {
                readingRepository.record(
                    SignalReadingEntity(
                        timestampMs = timestampMs,
                        dbm         = cell.dbm,
                        networkType = cell.networkType.name,
                        operator    = cell.operator,
                    )
                )
                maybePruneOldReadings(timestampMs)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
    }

    /** Apaga leituras mais velhas que o período de retenção configurado — sem
     *  isso, signal_readings cresce ~1 linha por tick de poll pra sempre (até
     *  5/s no piso de 200ms): em ~1 mês de uso contínuo já passa de 2,5 milhões
     *  de linhas e ~100MB de CSV, arriscando OutOfMemoryError no export (que
     *  carrega a tabela inteira em memória de uma vez via
     *  [SignalReadingRepository.getAll]). Chamada oportunistamente a cada
     *  gravação, mas só executa de fato no máximo 1x por hora. */
    private suspend fun maybePruneOldReadings(nowMs: Long) {
        if (nowMs - lastPrunedAtMs < RETENTION_PRUNE_INTERVAL_MS) return
        val retentionDays = settingsRepository.historyRetentionDays.first()
        if (retentionDays <= SettingsRepository.RETENTION_KEEP_FOREVER) return
        lastPrunedAtMs = nowMs
        try {
            readingRepository.pruneOlderThan(nowMs - retentionDays * MS_PER_DAY)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}
    }
}
