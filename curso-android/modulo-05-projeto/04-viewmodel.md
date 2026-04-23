# Módulo 05 · Aula 04 — ViewModel: Orquestrando Polling, Estado e Histórico

> **Objetivo:** Implementar o `SignalViewModel` — o coração que conecta hardware, estado e UI.

---

## O que o ViewModel precisa fazer

```
Responsabilidades do SignalViewModel:
1. Gerenciar o loop de polling (iniciar, pausar, parar)
2. Publicar o estado atual da UI via StateFlow
3. Alimentar o histórico de leituras
4. Controlar qual aba está ativa
5. Lidar com negação de permissão
```

---

## Implementação completa

Crie `app/src/main/java/com/example/ls/ui/SignalViewModel.kt`:

```kotlin
package com.example.ls.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ls.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// Enum das abas — definido aqui perto do ViewModel por ser estado de navegação
enum class AppTab { SIGNAL, HISTORY, INFO }

class SignalViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        const val POLL_INTERVAL_MS = 1_000L    // 1 segundo entre leituras
        const val HISTORY_CAPACITY = 90        // 90 segundos de histórico
    }

    // ════════════════════════════════════════════════════════
    // DEPENDÊNCIAS
    // ════════════════════════════════════════════════════════

    // AndroidViewModel nos dá acesso ao Application (necessário para Context)
    private val repository = SignalRepository(app)

    // ════════════════════════════════════════════════════════
    // ESTADO PÚBLICO (somente-leitura para a UI)
    // ════════════════════════════════════════════════════════

    private val _uiState = MutableStateFlow<SignalUiState>(SignalUiState.Loading)
    val uiState: StateFlow<SignalUiState> = _uiState.asStateFlow()

    // SignalHistory é exposto diretamente — ele gerencia seu próprio StateFlow
    val history = SignalHistory(capacity = HISTORY_CAPACITY)

    private val _currentTab = MutableStateFlow(AppTab.SIGNAL)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    // ════════════════════════════════════════════════════════
    // CONTROLE DO POLLING
    // ════════════════════════════════════════════════════════

    private var pollingJob: Job? = null

    fun startPolling() {
        // Guarda idempotente: não inicia um segundo loop se já está rodando
        if (pollingJob?.isActive == true) return

        pollingJob = viewModelScope.launch {
            while (isActive) {          // continua enquanto não for cancelado
                refreshOnce()
                delay(POLL_INTERVAL_MS) // pausa sem bloquear
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    // ════════════════════════════════════════════════════════
    // AÇÕES DA UI
    // ════════════════════════════════════════════════════════

    fun onPermissionDenied() {
        stopPolling()
        _uiState.value = SignalUiState.PermissionRequired
    }

    fun selectTab(tab: AppTab) {
        _currentTab.value = tab
    }

    fun clearHistory() {
        history.clear()
    }

    // ════════════════════════════════════════════════════════
    // LÓGICA INTERNA
    // ════════════════════════════════════════════════════════

    private suspend fun refreshOnce() {
        try {
            val cells = repository.readCells()

            // A célula primária é a registrada; se nenhuma, a primeira disponível
            val primary = cells.firstOrNull { it.isRegistered } ?: cells.firstOrNull()

            // Adiciona ao histórico apenas se o dBm é um valor real
            primary?.let { cell ->
                if (cell.dbm != Int.MIN_VALUE && cell.dbm != 0) {
                    history.add(
                        SignalPoint(
                            timestampMs = System.currentTimeMillis(),
                            dbm         = cell.dbm,
                            networkType = cell.networkType,
                        )
                    )
                }
            }

            _uiState.value = SignalUiState.Success(
                primary  = primary,
                allCells = cells,
            )

        } catch (e: Exception) {
            _uiState.value = SignalUiState.Error(
                message = e.message ?: "Erro desconhecido ao ler sinal"
            )
        }
    }

    // ════════════════════════════════════════════════════════
    // LIFECYCLE
    // ════════════════════════════════════════════════════════

    override fun onCleared() {
        super.onCleared()
        stopPolling()   // garante que o polling para quando o ViewModel é destruído
    }
}
```

---

## Entendendo cada decisão

### Por que `AndroidViewModel` e não `ViewModel`?

```kotlin
// ViewModel — sem acesso ao Context
class Simples : ViewModel() { ... }

// AndroidViewModel — tem `app: Application`
class ComContexto(app: Application) : AndroidViewModel(app) {
    // `app` é um Application — sobrevive ao ciclo de vida, não causa memory leak
    val context: Context = app.applicationContext
}
```

`ViewModel` puro não pode ter referência a `Activity` ou `Fragment` (memory leak!).
`AndroidViewModel` recebe `Application` — seguro porque Application vive enquanto o app vive.

### Por que `Job?` para o pollingJob?

```kotlin
private var pollingJob: Job? = null
```

Nullable porque:
1. Começa como `null` — polling não iniciado ainda
2. Após `stopPolling()`, volta para `null` — indica parado explicitamente

A guarda `if (pollingJob?.isActive == true) return` usa null-safety: se `pollingJob`
for null, `?.isActive` retorna null, `null == true` é false, então entra no if e cria.

### Por que `while (isActive)` e não `while (true)`?

```kotlin
// ❌ Ignora cancelamento — a coroutine continua mesmo após stopPolling()
while (true) {
    refreshOnce()
    delay(1000)
}

// ✅ Coopera com cancelamento
while (isActive) {      // isActive = false quando a coroutine é cancelada
    refreshOnce()
    delay(1000)         // delay() também verifica cancelamento internamente
}
```

Quando `stopPolling()` cancela o job, o `isActive` retorna false na próxima iteração.
Mas na prática, o `delay(1000)` já lança `CancellationException` quando cancelado.

### Por que o try/catch em `refreshOnce` e não em `readCells`?

O repositório tem seu próprio try/catch para erros esperados (`SecurityException`).
O ViewModel tem um try/catch mais amplo para erros inesperados — garante que mesmo
uma exception não tratada no repositório não mata o loop de polling:

```kotlin
private suspend fun refreshOnce() {
    try {
        val cells = repository.readCells()
        // ... processa ...
        _uiState.value = SignalUiState.Success(...)
    } catch (e: Exception) {
        // Ao invés de deixar a coroutine morrer, publica o estado de erro
        _uiState.value = SignalUiState.Error(e.message ?: "...")
        // O loop continua! Na próxima iteração, tenta novamente.
    }
}
```

---

## O ciclo de vida do ViewModel no projeto

```
Usuário abre o app
    └─ Activity.onCreate() → MainScreen() → viewModel() cria SignalViewModel
       └─ _uiState = Loading

LaunchedEffect(Unit) → vm.startPolling()
    └─ pollingJob = viewModelScope.launch { while(isActive) { ... } }

A cada 1 segundo:
    └─ refreshOnce()
       ├─ repository.readCells() (IO thread)
       ├─ history.add(SignalPoint) se válido
       └─ _uiState.value = Success(cells)
          └─ Compose recompõe automaticamente

Usuário gira o celular:
    ├─ Activity é destruída e recriada
    ├─ ViewModel SOBREVIVE (ViewModelStore preserva)
    ├─ Polling continua sem interrupção
    └─ Nova Activity obtém o MESMO ViewModel via viewModel()

Usuário pressiona voltar:
    ├─ Activity é concluída
    ├─ ViewModel.onCleared() é chamado
    ├─ stopPolling() cancela o pollingJob
    └─ viewModelScope é cancelado → todas as coroutines filhas morrem
```

---

## Testando o ViewModel

```kotlin
// SignalViewModelTest.kt
@OptIn(ExperimentalCoroutinesApi::class)
class SignalViewModelTest {

    // Substitui o Dispatcher.Main por um dispatcher controlável em testes
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeRepository = FakeSignalRepository()
    private lateinit var viewModel: SignalViewModel

    @Before
    fun setUp() {
        viewModel = SignalViewModel(
            app        = ApplicationProvider.getApplicationContext(),
            // Para testar, injetamos o repositório fake
            // (o projeto atual não tem injeção — seria uma melhoria)
        )
    }

    @Test
    fun `deve publicar Success após startPolling com dados válidos`() = runTest {
        // Dado: repositório retorna uma célula
        fakeRepository.setCells(listOf(
            CellSignalData(NetworkType.LTE, -87, 43, 3, true, "Vivo")
        ))

        // Quando: polling inicia
        viewModel.startPolling()
        advanceTimeBy(1_500)   // avança o tempo virtual 1.5 segundos

        // Então: estado é Success
        val state = viewModel.uiState.value
        assertIs<SignalUiState.Success>(state)
        assertEquals(-87, state.primary?.dbm)
    }

    @Test
    fun `deve publicar PermissionRequired após onPermissionDenied`() {
        viewModel.onPermissionDenied()
        assertIs<SignalUiState.PermissionRequired>(viewModel.uiState.value)
    }
}
```

---

## 📌 Resumo

```kotlin
class SignalViewModel(app: Application) : AndroidViewModel(app) {

    // Repositório
    private val repository = SignalRepository(app)

    // Estado
    private val _uiState = MutableStateFlow<SignalUiState>(SignalUiState.Loading)
    val uiState = _uiState.asStateFlow()

    // Polling
    private var pollingJob: Job? = null

    fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (isActive) { refreshOnce(); delay(POLL_INTERVAL_MS) }
        }
    }

    fun stopPolling() { pollingJob?.cancel(); pollingJob = null }

    // Cleanup automático quando a tela fecha
    override fun onCleared() { super.onCleared(); stopPolling() }
}
```

**Próxima aula →** [Telas e Permissões](05-telas.md) — MainScreen com controle de permissão e navegação por abas.
