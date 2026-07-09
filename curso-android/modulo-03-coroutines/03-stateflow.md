# Módulo 03 · Aula 03 — StateFlow: O Canal Reativo entre ViewModel e UI

> **Pré-requisito:** Aulas 01 e 02 — Coroutines e Dispatchers
> **Objetivo:** Dominar StateFlow para criar UIs que reagem automaticamente a mudanças de dados.

---

## O problema: como a UI sabe que os dados mudaram?

Sem nenhum mecanismo reativo, você teria que ficar perguntando:

```kotlin
// ❌ Polling manual — ineficiente e frágil
while (true) {
    Thread.sleep(100)
    val novoEstado = viewModel.getState()
    if (novoEstado != estadoAnterior) {
        atualizarUI(novoEstado)
        estadoAnterior = novoEstado
    }
}
```

A solução moderna é o **padrão Observer**: a UI se registra para ser notificada quando os
dados mudam, em vez de ficar perguntando.

```
ViewModel → "meus dados mudaram!" → StateFlow emite novo valor
                                              ↓
                              Compose recebe a notificação
                                              ↓
                              UI é recomposta automaticamente
```

---

## 1. Flow — a base de tudo

Um `Flow<T>` é uma sequência assíncrona de valores. Pense nele como um rio:
- O produtor coloca valores no rio
- O consumidor pega valores conforme chegam

```kotlin
// Criando um Flow simples
val numeros: Flow<Int> = flow {
    emit(1)
    delay(1000)
    emit(2)
    delay(1000)
    emit(3)
}

// Consumindo o Flow
viewModelScope.launch {
    numeros.collect { numero ->
        println(numero)   // 1 ... (após 1s) ... 2 ... (após 1s) ... 3
    }
}
```

---

## 2. StateFlow — o Flow para estado de UI

`StateFlow<T>` é um Flow especial otimizado para representar **estado**:

| Flow | StateFlow |
|------|-----------|
| Sem valor inicial obrigatório | **Sempre tem um valor atual** |
| Pode emitir valores duplicados | **Não emite se o valor não mudou** |
| Cold (inicia quando alguém coleta) | **Hot (sempre ativo)** |
| Não há `.value` | **Tem `.value` para leitura síncrona** |

```kotlin
// Criando
val _estado = MutableStateFlow<SignalUiState>(SignalUiState.Loading)
//                                            ↑ valor inicial obrigatório!

// Lendo o valor atual de forma síncrona (qualquer thread)
println(_estado.value)   // SignalUiState.Loading

// Atualizando (qualquer thread)
_estado.value = SignalUiState.Success(cells)

// Coletando como Flow
_estado.collect { estado ->
    println("Novo estado: $estado")
}
```

---

## 3. O padrão `_private` + `public`

```kotlin
class SignalViewModel : ViewModel() {

    // Privado e mutável — só o ViewModel pode alterar
    private val _uiState = MutableStateFlow<SignalUiState>(SignalUiState.Loading)

    // Público e imutável — a UI só pode ler
    val uiState: StateFlow<SignalUiState> = _uiState.asStateFlow()
}
```

**Por que esse padrão?**

```kotlin
// Se `uiState` fosse MutableStateFlow, qualquer um poderia fazer:
vm.uiState.value = SignalUiState.Error("Hackei!")   // ← indesejável!

// Com StateFlow (imutável exposto), isso não compila:
vm.uiState.value = ...   // ← ERRO DE COMPILAÇÃO
```

A UI pode apenas **observar**, nunca **modificar** o estado diretamente.

---

## 4. Coletando StateFlow na UI com Compose

### `collectAsStateWithLifecycle` — a forma correta

```kotlin
@Composable
fun MainScreen(vm: SignalViewModel = viewModel()) {

    // Converte StateFlow em State<T> do Compose
    // "WithLifecycle" = para de coletar quando o app vai para background
    val state by vm.uiState.collectAsStateWithLifecycle()

    // Toda vez que `state` mudar, este composable recompõe
    when (state) {
        is SignalUiState.Loading  -> LoadingScreen()
        is SignalUiState.Success  -> ContentScreen(state)
        else                      -> ...
    }
}
```

**Por que `WithLifecycle`?** Se você usar `collectAsState()` (sem Lifecycle), a coleta
continua mesmo com o app em background (tela desligada). Isso consome bateria
desnecessariamente. `WithLifecycle` para de coletar no `onStop` e retoma no `onStart`.

---

## 5. `SignalHistory` — StateFlow em ação

O histórico do projeto usa StateFlow de forma elegante:

```kotlin
class SignalHistory(val capacity: Int = 60) {

    // Lista imutável dentro de um StateFlow
    private val _points = MutableStateFlow<List<SignalPoint>>(emptyList())
    val points: StateFlow<List<SignalPoint>> = _points.asStateFlow()

    fun add(point: SignalPoint) {
        val current = _points.value
        _points.value = if (current.size >= capacity) {
            current.drop(1) + point   // buffer circular
        } else {
            current + point
        }
    }
}
```

Na `HistoryScreen`:

```kotlin
@Composable
fun HistoryScreen(history: SignalHistory) {
    // Coleta os pontos do histórico (WithLifecycle, como na seção anterior)
    val points by history.points.collectAsStateWithLifecycle()

    // Recompõe automaticamente quando um novo ponto é adicionado
    SignalChart(points = points)
}
```

---

## 6. Operadores de Flow

### `map` — transforme cada emissão

```kotlin
val dbmFlow: StateFlow<Int> = ...

// Cria um novo Flow que emite apenas o texto formatado
val textoFlow = dbmFlow.map { dbm -> "$dbm dBm" }
```

### `filter` — filtre emissões

```kotlin
val apenasFortes = dbmFlow.filter { dbm -> dbm >= -90 }
```

### `distinctUntilChanged` — evita duplicatas consecutivas

```kotlin
// Se você emitir -87, -87, -88...
// distinctUntilChanged emite: -87, -88 (ignora o segundo -87)
val semDuplicatas = dbmFlow.distinctUntilChanged()
```

StateFlow já aplica isso por padrão!

### `combine` — combina dois Flows

```kotlin
val nomeFlow: StateFlow<String> = ...
val dbmFlow:  StateFlow<Int> = ...

// Combina: sempre que qualquer um emitir, combina os valores atuais
val combinado: Flow<String> = combine(nomeFlow, dbmFlow) { nome, dbm ->
    "$nome: $dbm dBm"
}
```

---

## 7. `stateIn` — convertendo Flow em StateFlow

Às vezes você tem um `Flow` e precisa de um `StateFlow`:

```kotlin
class ViewModel : ViewModel() {

    // Flow frio — inicia quando alguém coleta
    private val _rawFlow: Flow<List<Cell>> = repository.observeCells()

    // Convertido para StateFlow "quente"
    val cells: StateFlow<List<Cell>> = _rawFlow
        .stateIn(
            scope         = viewModelScope,
            started       = SharingStarted.WhileSubscribed(5000),
            initialValue  = emptyList(),
        )
}
```

`SharingStarted.WhileSubscribed(5000)`:
- Inicia a coleta quando alguém assiste (`WhileSubscribed`)
- Mantém o Flow ativo por 5 segundos após o último observador sair
  (útil para sobreviver a rotações de tela sem reiniciar a coleta)

---

## 8. SharedFlow vs. StateFlow

| | StateFlow | SharedFlow |
|--|-----------|------------|
| Valor inicial | Obrigatório | Opcional |
| Último valor | Armazena | Não armazena (por padrão) |
| Replays | 1 (sempre atual) | Configurável (0 a N) |
| Uso típico | Estado de UI | Eventos únicos (navegação, toast) |

```kotlin
// StateFlow — para estado
val uiState = MutableStateFlow(UiState.Loading)

// SharedFlow — para eventos únicos
private val _eventos = MutableSharedFlow<Evento>()
val eventos = _eventos.asSharedFlow()

fun navegarParaDetalhes(id: String) {
    viewModelScope.launch {
        _eventos.emit(Evento.NavegacaoDetalhes(id))
    }
}
```

**Por que eventos devem usar SharedFlow?** Se o usuário gira o celular enquanto um
`StateFlow` emite `Evento.NavegacaoDetalhes`, o novo observador vai recoletar o evento
e navegar de novo — não é o que queremos. `SharedFlow` sem replay não reenvia eventos passados.

---

## 9. Ciclo de vida completo no projeto

```
MainActivity.onCreate()
    └─ setContent { MainScreen() }

MainScreen compõe pela primeira vez
    └─ val vm = viewModel()              ← obtém/cria SignalViewModel
    └─ val state by vm.uiState.collectAsStateWithLifecycle()
       ↑ começa a coletar uiState (valor inicial = Loading)

LaunchedEffect(Unit) { vm.startPolling() }
    └─ pollingJob = viewModelScope.launch {
           while (isActive) {
               refreshOnce()             ← chama repositório
               delay(1000)
           }
       }

refreshOnce() emite novo estado
    └─ _uiState.value = Success(cells)
       ↑ StateFlow emite novo valor

collectAsStateWithLifecycle() recebe o novo valor
    └─ state agora = Success(cells)

Compose recompõe MainScreen
    └─ when(state) { is Success -> SignalTabContent(state) }

Usuário coloca app em background (onStop)
    └─ collectAsStateWithLifecycle() PARA de coletar
    └─ pollingJob CONTINUA (ViewModel ainda vive)

Usuário volta ao app (onStart)
    └─ collectAsStateWithLifecycle() RETOMA coleta
    └─ recebe o estado mais recente imediatamente (StateFlow tem valor atual)
```

---

## 🧪 Desafios

### Desafio 1 — StateFlow básico
Crie um `CounterViewModel` com:
- `count: StateFlow<Int>` iniciando em 0
- `increment()` que adiciona 1
- `decrement()` que subtrai 1
- `reset()` que volta para 0

### Desafio 2 — Combinação de Flows
Dado:
- `val networkType: StateFlow<NetworkType>`
- `val dbm: StateFlow<Int>`

Crie um Flow derivado `qualidade: Flow<SignalQuality>` que recalcula a qualidade
sempre que qualquer um dos dois mudar.

### Desafio 3 — Buffer circular
Reimplemente `SignalHistory` mas com `ArrayDeque` internamente (mais eficiente).
Ainda exporte `points: StateFlow<List<SignalPoint>>` para compatibilidade com a UI.
Garanta que o `add()` seja thread-safe.

---

## 📌 Resumo

```
Flow<T>          → sequência assíncrona de valores
StateFlow<T>     → Flow com valor atual, hot, sem duplicatas consecutivas
MutableStateFlow → StateFlow que pode ser modificado
asStateFlow()    → converte para StateFlow somente-leitura

_estado.value = X                           → atualiza o estado
vm.estado.collectAsStateWithLifecycle()     → UI observa com lifecycle
```

**A regra do padrão:**
```kotlin
// No ViewModel (padrão obrigatório):
private val _x = MutableStateFlow(valorInicial)
val x: StateFlow<T> = _x.asStateFlow()

// Na UI (Compose):
val x by vm.x.collectAsStateWithLifecycle()
```

**Próximo Módulo →** [Jetpack Compose Básico](../modulo-04-compose/01-compose-basico.md) — a nova forma de construir UIs Android.
