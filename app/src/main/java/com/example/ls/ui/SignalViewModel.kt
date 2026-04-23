package com.example.ls.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ls.data.SignalHistory
import com.example.ls.data.SignalPoint
import com.example.ls.data.SignalRepository
import com.example.ls.data.SignalUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AppTab { SIGNAL, HISTORY, INFO }

class SignalViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        const val POLL_INTERVAL_MS = 1_000L
        const val HISTORY_CAPACITY = 90
    }

    private val repository = SignalRepository(app)

    private val _uiState = MutableStateFlow<SignalUiState>(SignalUiState.Loading)
    val uiState: StateFlow<SignalUiState> = _uiState.asStateFlow()

    val history = SignalHistory(capacity = HISTORY_CAPACITY)

    private val _currentTab = MutableStateFlow(AppTab.SIGNAL)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    private var pollingJob: Job? = null

    fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (isActive) {
                refreshOnce()
                delay(POLL_INTERVAL_MS)
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

    private suspend fun refreshOnce() {
        try {
            val cells = repository.readCells()
            val primary = cells.firstOrNull { it.isRegistered } ?: cells.firstOrNull()
            primary?.let { cell ->
                if (cell.dbm != Int.MIN_VALUE && cell.dbm != 0) {
                    history.add(SignalPoint(System.currentTimeMillis(), cell.dbm, cell.networkType))
                }
            }
            _uiState.value = SignalUiState.Success(primary = primary, allCells = cells)
        } catch (e: Exception) {
            _uiState.value = SignalUiState.Error(e.message ?: "Erro ao ler sinal")
        }
    }

    override fun onCleared() { super.onCleared(); stopPolling() }
}