package com.example.ls.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ls.data.SettingsRepository
import com.example.ls.data.SignalHistory
import com.example.ls.data.SignalPoint
import com.example.ls.data.SignalRepository
import com.example.ls.data.SignalUiState
import com.example.ls.data.db.AppDatabase
import com.example.ls.data.db.SignalReadingEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
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
    private val readingDao = AppDatabase.getDatabase(app).signalReadingDao()

    // Fila serializada para as escritas do Room: refreshOnce() apenas enfileira
    // (nunca suspende nem falha — capacidade ilimitada), e uma única corrotina
    // consome e grava uma leitura por vez. Isso evita empilhar uma corrotina de
    // escrita concorrente por tick de polling caso o disco fique lento.
    private val pendingInserts = Channel<SignalReadingEntity>(capacity = Channel.UNLIMITED)

    init {
        viewModelScope.launch {
            for (reading in pendingInserts) {
                try { readingDao.insert(reading) } catch (_: Exception) {}
            }
        }
    }

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
        viewModelScope.launch { try { readingDao.clearAll() } catch (_: Exception) {} }
    }

    /** Histórico completo persistido (Room), não só a janela de [HISTORY_CAPACITY] em RAM. */
    suspend fun getAllReadings(): List<SignalReadingEntity> = readingDao.getAll()

    private suspend fun refreshOnce() {
        try {
            val cells = repository.readCells()
            val primary = cells.firstOrNull { it.isRegistered } ?: cells.firstOrNull()
            primary?.let { cell ->
                if (cell.dbm != Int.MIN_VALUE && cell.dbm != Int.MAX_VALUE && cell.dbm != 0) {
                    val timestampMs = System.currentTimeMillis()
                    history.add(SignalPoint(timestampMs, cell.dbm, cell.networkType))
                    // Trilha paralela e permanente (Room) — apenas enfileira (nunca
                    // suspende) para nunca atrasar o ciclo de polling nem o _uiState
                    // com latência de disco; a escrita real é serializada por
                    // pendingInserts (ver acima).
                    pendingInserts.trySend(
                        SignalReadingEntity(
                            timestampMs = timestampMs,
                            dbm         = cell.dbm,
                            networkType = cell.networkType.name,
                            operator    = cell.operator,
                        )
                    )
                }
            }
            _uiState.value = SignalUiState.Success(primary = primary, allCells = cells)
        } catch (e: Exception) {
            _uiState.value = SignalUiState.Error(e.message ?: "Erro ao ler sinal")
        }
    }

    override fun onCleared() { super.onCleared(); stopPolling() }
}