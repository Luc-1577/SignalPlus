# Módulo 04 · Aula 02 — Estado no Compose: remember, StateFlow e Efeitos

> **Objetivo:** Dominar gerenciamento de estado no Compose — desde variáveis locais até StateFlow do ViewModel.

---

## Os três tipos de estado em Compose

```
1. Estado local simples:   remember { mutableStateOf(...) }
   → Vive dentro do composable, morre quando ele sai de composição
   → Exemplo: "está expandido?" em um card expansível

2. Estado do ViewModel:    StateFlow → collectAsStateWithLifecycle()
   → Vive no ViewModel, sobrevive a rotações
   → Exemplo: estado da tela, dados carregados

3. Estado de configuração: rememberSaveable { mutableStateOf(...) }
   → Sobrevive a rotações E ao processo sendo morto
   → Exemplo: texto digitado num campo que não deve sumir ao girar
```

---

## 1. `remember` — memória local de um composable

```kotlin
@Composable
fun CardExpansivel(titulo: String, conteudo: String) {

    // remember persiste o estado entre recomposições
    var expandido by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .clickable { expandido = !expandido }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(titulo, fontWeight = FontWeight.Bold)
            Icon(
                if (expandido) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                null
            )
        }
        if (expandido) {
            Text(conteudo, color = Color.Gray)
        }
    }
}
```

**O que `remember` faz exatamente?**

```kotlin
// SEM remember — recreado a cada recomposição
var state = mutableStateOf(false)   // sempre false!

// COM remember — persiste entre recomposições
var state by remember { mutableStateOf(false) }   // mantém o valor!
```

`remember` armazena o valor na "memória" do composable. Quando o composable é
recomposto (re-executado), `remember` retorna o mesmo objeto — não cria um novo.

---

## 2. `by` vs. sem `by`

```kotlin
// Com by (delegação de propriedade) — mais ergonômico
var count by remember { mutableStateOf(0) }
count++          // acessa diretamente o valor
println(count)   // lê o valor

// Sem by — mais explícito
val countState = remember { mutableStateOf(0) }
countState.value++           // precisa de .value
println(countState.value)    // precisa de .value
```

Use `by` sempre — é mais limpo. A única razão para não usar `by` é quando você precisa
passar o `State<T>` (o objeto inteiro) como parâmetro.

---

## 3. `remember` com chave — invalidação automática

```kotlin
@Composable
fun FormatadorData(timestampMs: Long) {

    // Se timestampMs mudar, remember recalcula
    val dataFormatada = remember(timestampMs) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            .format(Date(timestampMs))
    }

    Text(dataFormatada)
}
```

Sem a chave `(timestampMs)`, o formato seria calculado **apenas uma vez** e nunca
atualizaria mesmo que `timestampMs` mudasse.

No projeto, `LiveDot` usa exatamente isso:

```kotlin
@Composable
private fun LiveDot(updatedAt: Long) {
    val fmt     = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val timeStr = remember(updatedAt) { fmt.format(Date(updatedAt)) }
    //                     ↑
    //          atualiza quando updatedAt muda
    ...
}
```

---

## 4. Conectando ViewModel ao Compose

```kotlin
@Composable
fun MainScreen(vm: SignalViewModel = viewModel()) {

    // collectAsStateWithLifecycle converte StateFlow → State do Compose
    val state   by vm.uiState.collectAsStateWithLifecycle()
    val tabAtual by vm.currentTab.collectAsStateWithLifecycle()

    // `state` e `tabAtual` são `State<T>` do Compose
    // Quando o StateFlow emite novo valor, o Compose recompõe este composable
    when (state) {
        is SignalUiState.Loading  -> LoadingScreen()
        is SignalUiState.Success  -> ContentScreen(state, tabAtual, vm)
        ...
    }
}
```

**`viewModel()` — obtendo o ViewModel**

```kotlin
// viewModel() busca o ViewModel do ViewModelStore da Activity/Fragment
// Se já existe um, retorna o existente (não cria novo)
// Isso garante que rotações de tela usam o mesmo ViewModel
val vm: SignalViewModel = viewModel()

// Versão tipada mais explícita (equivalente):
val vm = viewModel<SignalViewModel>()
```

---

## 5. Efeitos no Compose — LaunchedEffect e DisposableEffect

Composables são funções puras de UI — eles não devem ter side effects.
Para side effects controlados, Compose tem funções de efeito.

### `LaunchedEffect` — código que roda na composição

```kotlin
@Composable
fun MainScreen(vm: SignalViewModel = viewModel()) {

    // LaunchedEffect(Unit) roda UMA VEZ quando o composable entra na composição
    LaunchedEffect(Unit) {
        if (temPermissao()) {
            vm.startPolling()   // side effect: inicia o polling
        } else {
            launcher.launch(permissoes)
        }
    }

    // LaunchedEffect(chave) roda toda vez que a chave muda
    LaunchedEffect(userId) {
        vm.carregarPerfil(userId)   // recarrega quando userId muda
    }
}
```

**`LaunchedEffect` cria uma coroutine** dentro do escopo do composable:

```kotlin
LaunchedEffect(Unit) {
    // Aqui você pode usar suspend functions!
    delay(2000)
    vm.mostrarDica()
}
```

### `DisposableEffect` — cleanup quando o composable sai

```kotlin
@Composable
fun MainScreen(vm: SignalViewModel = viewModel()) {

    DisposableEffect(Unit) {
        // Código de setup (roda quando compõe)
        val listener = criarListener()
        registrar(listener)

        onDispose {
            // Código de cleanup (roda quando descompõe)
            vm.stopPolling()
            desregistrar(listener)
        }
    }
}
```

No projeto:

```kotlin
// Inicia polling ao entrar na tela
LaunchedEffect(Unit) {
    if (hasPerms()) vm.startPolling() else launcher.launch(perms)
}

// Para polling ao sair da tela
DisposableEffect(Unit) {
    onDispose { vm.stopPolling() }
}
```

### `SideEffect` — sincronizando com sistemas externos

```kotlin
@Composable
fun AtualizarStatusBar(cor: Color) {
    // SideEffect roda APÓS cada recomposição bem-sucedida
    SideEffect {
        // Atualiza a cor da status bar (sistema externo ao Compose)
        systemUiController.setStatusBarColor(cor)
    }
}
```

---

## 6. `rememberSaveable` — estado que sobrevive ao processo

```kotlin
@Composable
fun CampoDeBusca() {
    // remember: perde ao girar o celular
    var texto by remember { mutableStateOf("") }

    // rememberSaveable: sobrevive à rotação E ao processo sendo morto
    var texto by rememberSaveable { mutableStateOf("") }

    TextField(
        value         = texto,
        onValueChange = { texto = it },
        placeholder   = { Text("Buscar...") },
    )
}
```

**Quando usar `rememberSaveable`?** Para texto digitado pelo usuário, posição de scroll,
seleções — qualquer coisa que seria frustrante perder ao girar o celular.

---

## 7. Derivando estado local

```kotlin
@Composable
fun SignalTabContent(state: SignalUiState.Success) {
    val primary = state.primary

    // Estado derivado: calculado a partir de outro State
    // O Compose rastreia que `quality` depende de `state.primary`
    val quality = remember(primary) {
        if (primary != null) signalQuality(primary.dbm, primary.networkType)
        else SignalQuality.NO_SIGNAL
    }

    SignalGauge(cell = primary)
    QualityBadge(quality = quality)
}
```

---

## 8. Anti-patterns a evitar

### Anti-pattern 1: State em variáveis globais/singletons

```kotlin
// ❌ State global — não é rastreado pelo Compose
var dbmAtual = -87

@Composable
fun DbmText() {
    Text("$dbmAtual dBm")   // nunca recompõe quando dbmAtual muda!
}

// ✅ StateFlow no ViewModel — rastreado e reativo
@Composable
fun DbmText(vm: ViewModel = viewModel()) {
    val dbm by vm.dbm.collectAsStateWithLifecycle()
    Text("$dbm dBm")   // recompõe quando dbm muda!
}
```

### Anti-pattern 2: Chamar suspend fun diretamente em composable

```kotlin
// ❌ ERRADO — composables não podem chamar suspend functions diretamente
@Composable
fun Errado() {
    val dados = repositorio.buscarDados()   // ERRO DE COMPILAÇÃO
}

// ✅ CORRETO — use LaunchedEffect ou StateFlow
@Composable
fun Correto(vm: ViewModel = viewModel()) {
    LaunchedEffect(Unit) {
        vm.carregarDados()   // dentro de LaunchedEffect, pode usar suspend
    }
    val dados by vm.dados.collectAsStateWithLifecycle()
    ...
}
```

### Anti-pattern 3: State que deveria estar no ViewModel

```kotlin
// ❌ Estado importante dentro do composable — perdido na rotação!
@Composable
fun TelaDeDados() {
    var dados by remember { mutableStateOf<List<Cell>>(emptyList()) }
    LaunchedEffect(Unit) {
        dados = repositorio.buscar()   // recomeça ao girar!
    }
}

// ✅ Estado no ViewModel — sobrevive à rotação
class DadosViewModel : ViewModel() {
    private val _dados = MutableStateFlow<List<Cell>>(emptyList())
    val dados = _dados.asStateFlow()

    init { viewModelScope.launch { _dados.value = repositorio.buscar() } }
}
```

---

## 🧪 Desafios

### Desafio 1 — remember com chave
Crie um composable `HorarioFormatado(timestamp: Long)` que exibe a hora formatada.
Use `remember(timestamp)` para não recalcular se o timestamp não mudou.

### Desafio 2 — LaunchedEffect
Crie um composable que exibe uma mensagem de "Bem-vindo!" por 3 segundos e depois
desaparece automaticamente. Use `LaunchedEffect` e `AnimatedVisibility`.

### Desafio 3 — Conectando ao ViewModel
Implemente `CounterScreen` que conecta ao `CounterViewModel` (do desafio do módulo 03).
A tela deve ter:
- Display do contador atual
- Botões +, - e Reset
- Indicador de "valor alto" quando count > 10

---

## 📌 Resumo

```
remember { }                              → persiste State entre recomposições
remember(chave) { }                       → reinicia quando a chave muda
rememberSaveable { }                      → sobrevive a rotações
mutableStateOf(valor)                     → State que dispara recomposição
collectAsStateWithLifecycle()             → StateFlow → State do Compose
LaunchedEffect(chave) { }                 → efeito assíncrono (com suspend)
DisposableEffect(chave) { onDispose { } } → efeito com cleanup
viewModel()                               → obtém ViewModel do escopo atual
```

**Próxima aula →** [Layouts no Compose](03-layouts.md) — Column, Row, Box, e o sistema de constraints.
