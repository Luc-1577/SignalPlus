package com.example.ls.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ls.data.SettingsRepository
import com.example.ls.data.SignalHistory
import com.example.ls.data.SignalPoint
import com.example.ls.data.SignalReadingRepository
import com.example.ls.data.SignalRepository
import com.example.ls.data.SignalUiState
import com.example.ls.data.db.SignalReadingEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AppTab { SIGNAL, HISTORY, INFO, SETTINGS }

class SignalViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        const val HISTORY_CAPACITY = 90
    }

    private val repository = SignalRepository(app)
    private val settingsRepository = SettingsRepository(app)
    private val readingRepository = SignalReadingRepository(app)

    private val _uiState = MutableStateFlow<SignalUiState>(SignalUiState.Loading)
    val uiState: StateFlow<SignalUiState> = _uiState.asStateFlow()

    val history = SignalHistory(capacity = HISTORY_CAPACITY)

    private val _currentTab = MutableStateFlow(AppTab.SIGNAL)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    val pollIntervalMs: StateFlow<Long> = settingsRepository.pollIntervalMs.stateIn(
        viewModelScope, SharingStarted.Eagerly, SettingsRepository.DEFAULT_POLL_INTERVAL_MS,
    )

    fun setPollIntervalMs(ms: Long) {
        viewModelScope.launch { settingsRepository.setPollIntervalMs(ms) }
    }

    private var pollingJob: Job? = null

    fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (isActive) {
                refreshOnce()
                delay(pollIntervalMs.value)
            }
        }
    }

    fun stopPolling() { pollingJob?.cancel(); pollingJob = null }

    fun onPermissionDenied() {
        stopPolling()
        _uiState.value = SignalUiState.PermissionRequired
    }

    fun selectTab(tab: AppTab) { _currentTab.value = tab }
    fun clearHistory() { history.clear() }

    /** Apaga o histórico persistido (Room) — irreversível, distinto de [clearHistory]. */
    fun clearPersistedHistory() {
        viewModelScope.launch { try { readingRepository.clearAll() } catch (_: Exception) {} }
    }

    /** Histórico completo persistido (Room), não só a janela de [HISTORY_CAPACITY] em RAM. */
    suspend fun getAllReadings(): List<SignalReadingEntity> = readingRepository.getAll()

    private suspend fun refreshOnce() {
        try {
            val cells = repository.readCells()
            val primary = cells.firstOrNull { it.isRegistered } ?: cells.firstOrNull()
            primary?.let { cell ->
                if (cell.dbm != Int.MIN_VALUE && cell.dbm != Int.MAX_VALUE && cell.dbm != 0) {
                    val timestampMs = System.currentTimeMillis()
                    history.add(SignalPoint(timestampMs, cell.dbm, cell.networkType))
                    // Trilha paralela e permanente (Room) — roda em sua própria
                    // corrotina para nunca atrasar o ciclo de polling nem o
                    // _uiState com latência de disco. O SignalReadingRepository
                    // serializa essa gravação com clearAll()/getAll() via Mutex,
                    // então não há corrida com "Apagar histórico" nem com "Exportar".
                    viewModelScope.launch {
                        try {
                            readingRepository.record(
                                SignalReadingEntity(
                                    timestampMs = timestampMs,
                                    dbm         = cell.dbm,
                                    networkType = cell.networkType.name,
                                    operator    = cell.operator,
                                )
                            )
                        } catch (_: Exception) {}
                    }
                }
            }
            _uiState.value = SignalUiState.Success(primary = primary, allCells = cells)
        } catch (e: Exception) {
            _uiState.value = SignalUiState.Error(e.message ?: "Erro ao ler sinal")
        }
    }

    override fun onCleared() { super.onCleared(); stopPolling() }
}