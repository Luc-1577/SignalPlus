package com.example.ls.ui

import android.app.Application
import android.content.Intent
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ls.data.SettingsRepository
import com.example.ls.data.SignalHistory
import com.example.ls.data.SignalUiState
import com.example.ls.data.SimOption
import com.example.ls.data.TowerDisplayEntry
import com.example.ls.ui.delegate.PersistedHistoryController
import com.example.ls.ui.delegate.SignalPollingController
import com.example.ls.ui.delegate.TowerTriangulationController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppTab { SIGNAL, HISTORY, TOWERS, INFO, SETTINGS }

/**
 * Raiz de composição — não implementa polling, histórico persistido ou
 * triangulação de torres diretamente (ver o pacote ui.delegate), só os une e
 * repassa a mesma superfície pública de sempre, pra MainScreen.kt não
 * precisar mudar nada. Um "SignalViewModel god object" (polling + Room +
 * export + torre, tudo numa classe só) foi deliberadamente reorganizado
 * assim em vez de virar múltiplos ViewModels: a triangulação de torres não
 * tem loop próprio (pega carona no tick de polling via [onReading]) e seu
 * ciclo de vida depende de [currentTab]/[_appInForeground], que só ela lê —
 * dividir isso em ViewModels separados exigiria uma dependência oculta entre
 * eles ou uma ponte por Flow arriscando reabrir a corrida que o epoch-guard
 * de TowerTriangulationController fecha. Classes delegate comuns (dirigidas
 * por [viewModelScope], não ViewModels próprios) resolvem o acoplamento sem
 * esse custo.
 */
class SignalViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsRepository = SettingsRepository(app)

    private val _currentTab = MutableStateFlow(AppTab.SIGNAL)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()
    fun selectTab(tab: AppTab) { _currentTab.value = tab }

    /** Espelha o ciclo de vida do app (ver MainScreen.kt LifecycleStartEffect) —
     *  sem isso, sair pro Home com o toggle ligado e a aba Sinal ainda selecionada
     *  deixaria o LocationTracker rodando indefinidamente em segundo plano, já
     *  que nem a troca de aba nem o toggle mudam nesse cenário. */
    private val _appInForeground = MutableStateFlow(true)
    fun setAppInForeground(inForeground: Boolean) { _appInForeground.value = inForeground }

    private val persistedHistory = PersistedHistoryController(app, settingsRepository, viewModelScope)

    // Construído ANTES de towerTriangulation, de propósito: esta precisa ler
    // polling.isPolling (ver o comentário lá) para fechar o gate do GPS quando
    // o polling para por revogação de permissão em sessão. A referência a
    // towerTriangulation dentro do hook abaixo (declarada só depois) continua
    // válida — é uma forward-reference dentro de uma lambda, resolvida apenas
    // quando o hook é de fato invocado (bem depois da construção completa do
    // ViewModel), o mesmo princípio já usado para currentTab/_appInForeground
    // repassados por referência às classes delegate.
    // Tipo explícito, não inferido: sem isso o compilador cai numa dependência
    // circular de inferência de tipos com towerTriangulation (declarada logo
    // abaixo, cujo construtor lê polling.isPolling, enquanto o hook aqui
    // referencia towerTriangulation.onReading) — anotar o tipo quebra o ciclo,
    // já que a resolução de membro passa a usar o tipo declarado, não o
    // resultado (ainda pendente) de inferir o tipo do inicializador.
    private val polling: SignalPollingController = SignalPollingController(app, settingsRepository, viewModelScope) { cell, timestampMs ->
        persistedHistory.onReading(cell, timestampMs)
        towerTriangulation.onReading(cell, timestampMs)
    }

    private val towerTriangulation = TowerTriangulationController(
        app, settingsRepository, viewModelScope, currentTab, _appInForeground, polling.isPolling,
    )

    // ── Sinal / polling (SignalPollingController) ───────────────────────────

    val uiState: StateFlow<SignalUiState> = polling.uiState
    val history: SignalHistory = polling.history
    val pollIntervalMs: StateFlow<Long> = polling.pollIntervalMs
    fun setPollIntervalMs(ms: Long) = polling.setPollIntervalMs(ms)
    val selectedSubscriptionId: StateFlow<Int> = polling.selectedSubscriptionId
    fun setSelectedSubscriptionId(id: Int) = polling.setSelectedSubscriptionId(id)
    val availableSims: StateFlow<List<SimOption>> = polling.availableSims
    fun startPolling() = polling.startPolling()
    fun stopPolling() = polling.stopPolling()
    fun onPermissionDenied() = polling.onPermissionDenied()
    fun clearHistory() = polling.clearHistory()

    // ── Histórico persistido / CSV / retenção (PersistedHistoryController) ──

    val historyRetentionDays: StateFlow<Int> = persistedHistory.historyRetentionDays
    fun setHistoryRetentionDays(days: Int) = persistedHistory.setHistoryRetentionDays(days)
    fun clearPersistedHistory() = persistedHistory.clearPersistedHistory()
    fun exportReadingsAsCsv(onReady: (Intent) -> Unit) =
        persistedHistory.exportReadingsAsCsv(onReady)
    val exportError: StateFlow<String?> = persistedHistory.exportError
    fun dismissExportError() = persistedHistory.dismissExportError()

    // ── Triangulação de torres (TowerTriangulationController) ──────────────

    val towerTriangulationEnabled: StateFlow<Boolean> = towerTriangulation.towerTriangulationEnabled
    fun setTowerTriangulationEnabled(enabled: Boolean) = towerTriangulation.setTowerTriangulationEnabled(enabled)
    suspend fun isTowerTriangulationEnabledPersisted(): Boolean = towerTriangulation.isTowerTriangulationEnabledPersisted()
    fun clearTowerEstimates() = towerTriangulation.clearTowerEstimates()
    val clearEstimatesError: StateFlow<String?> = towerTriangulation.clearEstimatesError
    fun dismissClearEstimatesError() = towerTriangulation.dismissClearEstimatesError()
    val currentLocation: StateFlow<Location?> = towerTriangulation.currentLocation
    val towerEstimatesDetailed: StateFlow<List<TowerDisplayEntry>> = towerTriangulation.towerEstimatesDetailed
    fun refreshTowerEstimatesDetailed() = towerTriangulation.refreshTowerEstimatesDetailed()

    override fun onCleared() {
        super.onCleared()
        polling.stopPolling()
        towerTriangulation.stopBlocking()
    }
}
