# Módulo 03 · Aula 02 — Dispatchers: Onde Cada Código Deve Rodar

> **Pré-requisito:** Aula 01 — Coroutines
> **Objetivo:** Entender os Dispatchers e nunca mais ter ANR por código na thread errada.

---

## Threads no Android

O Android tem um modelo de threading específico:

```
┌─────────────────────────────────────────────────────────────┐
│  Main Thread (UI Thread)                                    │
│  • Desenha a tela (60fps = 16ms por frame!)                 │
│  • Processa toques do usuário                               │
│  • Atualiza Views e Composables                             │
│  • NUNCA deve fazer I/O ou processamento pesado             │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  Background Threads                                         │
│  • Acesso a banco de dados                                  │
│  • Requisições de rede                                      │
│  • Leitura de arquivos                                      │
│  • Processamento pesado (cálculos, parsing)                 │
│  • NUNCA deve atualizar a UI diretamente                    │
└─────────────────────────────────────────────────────────────┘
```

---

## O que são Dispatchers?

Dispatchers determinam **em qual thread ou pool de threads** uma coroutine roda.

```kotlin
Dispatchers.Main    // UI thread — para atualizar a interface
Dispatchers.IO      // pool de threads para I/O (rede, disco, banco)
Dispatchers.Default // pool de threads para CPU (cálculos, parsing)
```

---

## 1. `Dispatchers.Main` — a thread da UI

```kotlin
// viewModelScope usa Main por padrão
viewModelScope.launch {
    // Aqui estamos na Main thread

    _uiState.value = SignalUiState.Loading   // OK — Main thread
    textView.text = "Atualizando..."         // OK — Main thread
}
```

**Quando usar:** Para atualizar `StateFlow`, `LiveData`, ou qualquer coisa que afeta a UI.

**Nunca faça na Main:**
- `Thread.sleep()`
- Acesso à rede ou banco de dados
- Leitura de arquivos grandes
- Processamento de listas extensas

---

## 2. `Dispatchers.IO` — para operações de entrada/saída

```kotlin
viewModelScope.launch {
    // Main thread aqui

    val cells = withContext(Dispatchers.IO) {
        // IO thread aqui — pode bloquear sem problema
        telephony.allCellInfo   // chamada ao driver do modem
    }

    // Volta para Main thread automaticamente
    _uiState.value = SignalUiState.Success(cells)   // Main thread
}
```

**Quando usar:**
- Chamadas de rede (Retrofit, OkHttp)
- Banco de dados (Room)
- Leitura/escrita de arquivos
- Chamadas ao sistema como `TelephonyManager.allCellInfo`
- SharedPreferences

**Por que IO tem uma pool grande?** Operações de I/O ficam **bloqueadas esperando**
(disco girando, pacote de rede chegando). Uma pool grande garante que outras operações
de I/O não ficam esperando uma liberar a thread.

Típicamente: `min(64, nCpus + 1)` threads.

---

## 3. `Dispatchers.Default` — para processamento de CPU

```kotlin
viewModelScope.launch {
    val resultado = withContext(Dispatchers.Default) {
        // CPU thread — para processamento pesado
        processarImagemGrande(bitmap)        // CPU intensivo
        calcularFFT(sinalDeAudio)            // CPU intensivo
        jsonGigante.parseToList()            // parsing de JSON enorme
    }

    _resultado.value = resultado
}
```

**Quando usar:**
- Processamento de imagens
- Cálculos matemáticos complexos
- Parsing de grandes volumes de dados
- Algoritmos de ordenação em listas grandes

**Por que Default != IO?** Para CPU você quer **exatamente N threads** (N = núcleos do processador).
Mais threads que núcleos só adicionam overhead de context-switching sem ganho.
Para IO, você quer muitas threads porque elas ficam ociosas esperando.

---

## 4. Padrão correto no repositório

```kotlin
class SignalRepository(private val context: Context) {

    // A função suspend é chamada de qualquer contexto
    // Internamente, garante que roda no dispatcher correto
    suspend fun readCells(): List<CellSignalData> = withContext(Dispatchers.IO) {
        // Todo o bloco roda em IO thread
        val operator = telephony.networkOperatorName
        val bwKhz    = readPrimaryBwKhz()          // reflexão → pode bloquear
        val rawList  = telephony.allCellInfo        // chamada ao driver

        rawList
            ?.mapNotNull { parseCellInfo(it, operator, bwKhz) }
            ?: emptyList()
    }
    // Retorna para o Dispatcher do chamador (Main, no caso do ViewModel)
}
```

**Padrão recomendado:** O repositório define seu próprio Dispatcher com `withContext`.
O chamador (ViewModel) não precisa saber em qual thread o repositório opera.

---

## 5. Visualizando o fluxo completo

```
viewModelScope.launch {           ← Main thread
    _uiState.value = Loading      ← Main thread (atualiza UI)

    withContext(Dispatchers.IO) { ← MUDA para IO thread
        telephony.allCellInfo     ← IO thread (pode bloquear)
        parseCellInfo(...)        ← IO thread
    }                             ← VOLTA para Main thread

    _uiState.value = Success(...) ← Main thread (atualiza UI)
}
```

O `withContext` faz o "switch" de thread e, quando o bloco termina, **retorna ao contexto anterior**.
Não é preciso fazer switch manual de volta.

---

## 6. Erros comuns

### Erro 1: Fazer I/O na Main thread

```kotlin
// ❌ ERRADO — trava a UI
viewModelScope.launch {
    // Sem withContext(IO) — roda na Main thread!
    val cells = telephony.allCellInfo   // pode levar 200ms → UI congela!
}

// ✅ CORRETO
viewModelScope.launch {
    val cells = withContext(Dispatchers.IO) {
        telephony.allCellInfo
    }
}
```

### Erro 2: Atualizar UI fora da Main thread

```kotlin
// ❌ ERRADO — crash: "Only the original thread that created a view hierarchy can touch its views"
Thread {
    val dados = buscarDados()
    textView.text = dados   // CRASH!
}.start()

// ✅ CORRETO — com coroutines, o switch é automático
viewModelScope.launch {
    val dados = withContext(Dispatchers.IO) { buscarDados() }
    textView.text = dados   // Main thread — OK
}
```

### Erro 3: Esquecer `withContext` dentro de `suspend fun`

```kotlin
// ❌ PERIGOSO — não garante a thread correta
suspend fun lerArquivo(): String {
    return File("grande.json").readText()   // qual thread? depende do chamador!
}

// ✅ CORRETO — define o dispatcher internamente
suspend fun lerArquivo(): String = withContext(Dispatchers.IO) {
    File("grande.json").readText()
}
```

---

## 7. `Dispatchers.Main.immediate`

```kotlin
// Dispatchers.Main: sempre posta na fila da Main thread (assíncrono)
// Dispatchers.Main.immediate: se já está na Main, executa AGORA (síncrono)

viewModelScope.launch(Dispatchers.Main.immediate) {
    // Útil para animações que precisam de timing preciso
}
```

Para a maioria dos casos, `Dispatchers.Main` é suficiente.

---

## 8. Testando com Dispatchers

Em testes unitários, não queremos dependências de threads reais:

```kotlin
// Produção:
class SignalViewModel(app: Application) : AndroidViewModel(app) {
    private val ioDispatcher = Dispatchers.IO   // hardcoded
}

// Melhor — Dispatcher injetável (testável):
class SignalViewModel(
    app: Application,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AndroidViewModel(app) {
    suspend fun refresh() = withContext(ioDispatcher) {
        repository.readCells()
    }
}

// No teste:
@Test
fun `deve publicar Success após readCells`() = runTest {
    val vm = SignalViewModel(app, Dispatchers.Unconfined)   // roda imediatamente
    vm.refresh()
    assertEquals(SignalUiState.Success::class, vm.uiState.value::class)
}
```

---

## 🧪 Desafios

### Desafio 1
Dado o código abaixo, identifique qual Dispatcher deve ser usado em cada bloco:

```kotlin
fun carregarPerfil(userId: String) {
    // ??? thread
    val json = httpClient.get("users/$userId")      // (A) qual thread?
    val usuario = gson.fromJson(json, User::class)  // (B) qual thread?
    nomeText.text = usuario.nome                    // (C) qual thread?
}
```

### Desafio 2
Escreva uma `suspend fun processarListaGrande(lista: List<Int>): List<Int>` que:
1. Filtra apenas os números pares (processamento CPU)
2. Mapeia para `it * it` (CPU)
3. Ordena em ordem crescente (CPU)
Use o Dispatcher correto.

### Desafio 3
Explique por que este código pode causar problemas e como corrigi-lo:

```kotlin
class MeuViewModel : ViewModel() {
    fun iniciar() {
        viewModelScope.launch(Dispatchers.IO) {
            val dados = buscarDados()
            _state.value = State.Success(dados)   // ← problema aqui?
        }
    }
}
```

*(Dica: `_state` é um `MutableStateFlow` — `StateFlow.value` pode ser escrito de qualquer
thread sem crash, diferente de `MutableLiveData.value` que exige Main thread)*

---

## 📌 Resumo

```
Dispatchers.Main    → UI: atualizar tela, emitir StateFlow
Dispatchers.IO      → I/O: rede, banco, arquivo, hardware
Dispatchers.Default → CPU: cálculos, parsing, algoritmos

withContext(X) { }  → muda de dispatcher e volta ao original
viewModelScope      → usa Main por padrão
```

**A regra de ouro:**
> Deixe o repositório definir o dispatcher com `withContext(Dispatchers.IO)`.
> O ViewModel apenas chama `suspend fun` sem se preocupar com threads.

**Próxima aula →** [StateFlow](03-stateflow.md) — o canal de comunicação reativo entre ViewModel e UI.
