package com.example.ls.ui.delegate

import android.app.Application
import com.example.ls.data.AUTOMATIC_SUBSCRIPTION_ID
import com.example.ls.data.CellSignalData
import com.example.ls.data.SettingsRepository
import com.example.ls.data.SignalHistory
import com.example.ls.data.SignalPoint
import com.example.ls.data.SignalRepository
import com.example.ls.data.SignalUiState
import com.example.ls.data.SimOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Leitura de sinal via TelephonyManager + o loop de polling — o "tronco" que
 * PersistedHistoryController e TowerTriangulationController se conectam via
 * [onReading], chamado uma vez por tick com a célula primária já validada
 * (nem histórico persistido nem triangulação têm loop próprio, ambos
 * "pegam carona" neste). Extraído de SignalViewModel; não é um ViewModel, é
 * dirigido pelo [scope] (viewModelScope) que o ViewModel dono repassa.
 */
class SignalPollingController(
    app: Application,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
    private val onReading: suspend (CellSignalData, Long) -> Unit,
) {
    companion object {
        const val HISTORY_CAPACITY = 90

        /** Throttle da releitura de [_availableSims] dentro do loop de poll — não
         *  a cada tick, que aconteceria até 5x/s no piso de 200ms por nada, já que
         *  a lista de SIMs quase nunca muda tick a tick. Existe pra cobrir o caso
         *  de o usuário trocar perfil eSIM/bandeja dupla-SIM com o app aberto e em
         *  primeiro plano continuamente — sem um ciclo background/foreground que
         *  reinicie o polling (e já recarregue a lista), ela ficaria obsoleta pelo
         *  resto da sessão. */
        const val SIM_REFRESH_INTERVAL_MS = 60_000L
    }

    private val repository = SignalRepository(app)

    private val _uiState = MutableStateFlow<SignalUiState>(SignalUiState.Loading)
    val uiState: StateFlow<SignalUiState> = _uiState.asStateFlow()

    val history = SignalHistory(capacity = HISTORY_CAPACITY)

    val pollIntervalMs: StateFlow<Long> = settingsRepository.pollIntervalMs.stateIn(
        scope, SharingStarted.Eagerly, SettingsRepository.DEFAULT_POLL_INTERVAL_MS,
    )

    fun setPollIntervalMs(ms: Long) {
        scope.launch { settingsRepository.setPollIntervalMs(ms) }
    }

    val selectedSubscriptionId: StateFlow<Int> = settingsRepository.selectedSubscriptionId.stateIn(
        scope, SharingStarted.Eagerly, AUTOMATIC_SUBSCRIPTION_ID,
    )

    fun setSelectedSubscriptionId(id: Int) {
        scope.launch { settingsRepository.setSelectedSubscriptionId(id) }
    }

    private val _availableSims = MutableStateFlow<List<SimOption>>(emptyList())
    val availableSims: StateFlow<List<SimOption>> = _availableSims.asStateFlow()

    private var lastSimRefreshMs = 0L

    /** Reflete se o loop de poll está de fato rodando — não apenas "permissão
     *  concedida em algum momento". Usado por TowerTriangulationController pra
     *  fechar o gate do GPS quando o polling para por revogação de permissão em
     *  sessão (onPermissionDenied()): sem esse sinal, o `combine()` de lá só
     *  conhecia toggle/aba/foreground, nenhum dos quais muda nesse cenário, e o
     *  GPS continuava pedindo updates de localização indefinidamente sem
     *  nenhuma leitura de sinal pra consumi-los. */
    private val _isPolling = MutableStateFlow(false)
    val isPolling: StateFlow<Boolean> = _isPolling.asStateFlow()

    private var pollingJob: Job? = null

    fun startPolling() {
        if (pollingJob?.isActive == true) return
        _isPolling.value = true
        pollingJob = scope.launch {
            _availableSims.value = repository.listActiveSims()
            lastSimRefreshMs = System.currentTimeMillis()
            resetSelectionIfSimGone()
            while (isActive) {
                refreshOnce()
                maybeRefreshAvailableSims()
                delay(settingsRepository.pollIntervalMs.first())
            }
        }
    }

    fun stopPolling() { pollingJob?.cancel(); pollingJob = null; _isPolling.value = false }

    fun onPermissionDenied() {
        stopPolling()
        _uiState.value = SignalUiState.PermissionRequired
    }

    private suspend fun maybeRefreshAvailableSims() {
        val now = System.currentTimeMillis()
        if (now - lastSimRefreshMs < SIM_REFRESH_INTERVAL_MS) return
        lastSimRefreshMs = now
        _availableSims.value = repository.listActiveSims()
        resetSelectionIfSimGone()
    }

    /** Se o SIM escolhido manualmente não existe mais na lista de SIMs ativos
     *  (removido/desativado), volta a seleção persistida pra "Automático" —
     *  sem isso, SignalRepository.telephonyFor() continuaria recebendo o
     *  mesmo subscriptionId morto pra sempre: SettingsScreen só destaca
     *  "Automático" cosmeticamente quando o SIM selecionado some da lista,
     *  nunca persiste essa mudança de volta, então readCells() ficaria preso
     *  num SIM que não existe mais até o app reiniciar. */
    private suspend fun resetSelectionIfSimGone() {
        val selected = settingsRepository.selectedSubscriptionId.first()
        if (selected != AUTOMATIC_SUBSCRIPTION_ID && _availableSims.value.none { it.subscriptionId == selected }) {
            settingsRepository.setSelectedSubscriptionId(AUTOMATIC_SUBSCRIPTION_ID)
        }
    }

    fun clearHistory() { history.clear() }

    private suspend fun refreshOnce() {
        try {
            // Lê o valor persistido diretamente da fonte (não o snapshot
            // stateIn'd/Eagerly em selectedSubscriptionId.value) — esse snapshot
            // começa no valor-semente (AUTOMATIC_SUBSCRIPTION_ID) e só é
            // atualizado de forma assíncrona quando o DataStore emite pela
            // primeira vez; sem isso, o primeiríssimo tick após o processo
            // reiniciar podia consultar o SIM errado mesmo com uma escolha já
            // persistida. Depois do primeiro load, esta chamada só devolve o
            // valor já em cache do DataStore, sem custo extra de disco.
            val subscriptionId = settingsRepository.selectedSubscriptionId.first()
            val cells = repository.readCells(subscriptionId)
            val primary = cells.firstOrNull { it.isRegistered } ?: cells.firstOrNull()
            if (primary != null && primary.dbm != Int.MIN_VALUE && primary.dbm != Int.MAX_VALUE && primary.dbm != 0) {
                val timestampMs = System.currentTimeMillis()
                history.add(SignalPoint(timestampMs, primary.dbm, primary.networkType))
                onReading(primary, timestampMs)
            }
            _uiState.value = SignalUiState.Success(primary = primary, allCells = cells)
        } catch (e: CancellationException) {
            // refreshOnce() roda na própria corrotina-tronco do laço de poll
            // (não num launch filho) — capturar isso junto com Exception
            // silenciaria pollingJob.cancel() bem no instante em que a tela
            // deveria estar sendo desmontada, quebrando o cancelamento
            // cooperativo do laço. Tem que propagar.
            throw e
        } catch (e: Exception) {
            _uiState.value = SignalUiState.Error("Não foi possível ler o sinal. Tente novamente.")
        }
    }
}
