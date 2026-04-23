# Módulo 03 · Aula 01 — Coroutines: Código Assíncrono sem Sofrimento

> **Pré-requisito:** Módulo 02 completo
> **Objetivo:** Entender coroutines para nunca mais travar a UI com operações lentas.

---

## O problema: a UI thread

O Android tem uma regra fundamental: **toda operação de UI acontece na UI thread (Main thread)**.

Se você bloquear essa thread, o app "trava" — a tela congela, e o Android exibe
o infame "App não está respondendo":

```kotlin
// ❌ NUNCA faça isso!
class MainActivity : AppCompatActivity() {
    override fun onCreate(...) {
        // Isso TRAVA a UI thread por 2 segundos
        Thread.sleep(2000)

        // Isso também trava (acesso à rede ou arquivo)
        val dados = acessarBancoDeDados()       // pode levar 500ms
        val cells = telephony.allCellInfo       // pode levar 200ms
    }
}
```

### A solução tradicional — callbacks (o inferno)

```kotlin
// ❌ Código assíncrono com callbacks
acessarAPI(
    onSuccess = { dados ->
        processarDados(
            dados = dados,
            onSuccess = { resultado ->
                salvarNoBanco(
                    resultado,
                    onSuccess = {
                        atualizarUI(resultado)
                    },
                    onError = { erro -> mostrarErro(erro) }
                )
            },
            onError = { erro -> mostrarErro(erro) }
        )
    },
    onError = { erro -> mostrarErro(erro) }
)
```

Isso é o **"Callback Hell"** — código difícil de ler, de tratar erros e de depurar.

### A solução moderna — Coroutines

```kotlin
// ✅ O mesmo fluxo com coroutines — sequencial e legível
viewModelScope.launch {
    try {
        val dados = acessarAPI()           // suspende aqui, não bloqueia
        val resultado = processarDados(dados)
        salvarNoBanco(resultado)
        atualizarUI(resultado)
    } catch (e: Exception) {
        mostrarErro(e)
    }
}
```

**Parece síncrono, mas não é.** Isso é a magia das coroutines.

---

## 1. O que é uma coroutine?

Uma coroutine é uma **função que pode ser pausada e retomada** sem bloquear a thread.

**Analogia:** Imagine que você está lendo um livro (tarefa na thread). Em vez de ficar
parado olhando para a página esperando o mensageiro chegar (bloquear a thread), você
dobra a página (salva o estado), faz outras coisas, e quando o mensageiro chega você
volta exatamente onde estava.

```
Thread sem coroutine:
──────────────────────[esperando rede 500ms]────────────────▶
                       ↑ thread BLOQUEADA

Thread com coroutine:
──[iniciou]──[SUSPENDEU]──[outras tarefas]──[RETOMOU]──────▶
                       ↑                   ↑
                  parou aqui          resultado chegou
                  thread LIVRE        continua daqui
```

---

## 2. `suspend fun` — funções que podem pausar

A palavra-chave `suspend` marca uma função que **pode** ser pausada:

```kotlin
// Função normal — executa e retorna imediatamente
fun somar(a: Int, b: Int): Int = a + b

// Função suspend — pode pausar a coroutine enquanto espera
suspend fun buscarDados(): List<Cell> {
    delay(1000)           // pausa por 1 segundo SEM bloquear a thread
    return readFromHardware()
}
```

**Regras:**
- `suspend fun` só pode ser chamada de outra `suspend fun` ou de uma coroutine
- `delay()` é uma `suspend fun` — substitui `Thread.sleep()` sem bloquear

---

## 3. Iniciando uma coroutine: `launch` e `async`

### `launch` — fire and forget

```kotlin
// Inicia a coroutine e não espera o resultado
val job = scope.launch {
    val dados = buscarDados()   // suspend — espera sem bloquear
    processarDados(dados)
}

// Continua aqui imediatamente, sem esperar a coroutine terminar
```

### `async` — quando você precisa do resultado

```kotlin
val deferred = scope.async {
    calcularAlgumCoisa()   // retorna um valor
}

// Mais tarde, quando precisar do resultado:
val resultado = deferred.await()   // suspend — espera o resultado
```

### No projeto

```kotlin
fun startPolling() {
    pollingJob = viewModelScope.launch {  // launch porque não precisamos do retorno
        while (isActive) {
            refreshOnce()                 // suspend fun
            delay(POLL_INTERVAL_MS)       // suspend — pausa 1 segundo
        }
    }
}
```

---

## 4. Escopos de coroutine — onde a coroutine vive

Uma coroutine precisa de um **escopo** — ele define o lifetime da coroutine.

### `viewModelScope` — o mais importante no Android

```kotlin
class MeuViewModel : ViewModel() {

    fun carregarDados() {
        viewModelScope.launch {
            // Quando o ViewModel é destruído,
            // TODAS as coroutines neste escopo são canceladas automaticamente
            val dados = repositorio.buscar()
            _uiState.value = UiState.Success(dados)
        }
    }
}
```

**Por que é tão importante?** Sem escopo correto, uma coroutine pode continuar rodando
mesmo após a tela ser fechada, causando:
- Memory leaks (referência a ViewModel destruído)
- Crashes (tentando atualizar UI inexistente)
- Comportamentos imprevisíveis

### `lifecycleScope` — em Activities/Fragments

```kotlin
class MinhaActivity : AppCompatActivity() {
    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            // Cancelado quando a Activity vai para background (onStop)
        }
    }
}
```

### `coroutineScope` — dentro de suspend fun

```kotlin
suspend fun carregarTudo(): Resultado = coroutineScope {
    // Lança duas coroutines em paralelo
    val a = async { carregarPartA() }
    val b = async { carregarPartB() }
    // Espera as duas terminarem
    combinar(a.await(), b.await())
}
```

---

## 5. `Job` — controle de uma coroutine

```kotlin
val job: Job = scope.launch {
    while (isActive) {
        delay(1000)
        println("executando...")
    }
}

// Depois de 5 segundos:
job.cancel()          // cancela a coroutine
job.join()            // espera a coroutine terminar
println(job.isActive) // false
```

No projeto, o polling usa exatamente isso:

```kotlin
private var pollingJob: Job? = null

fun startPolling() {
    if (pollingJob?.isActive == true) return   // evita duplo polling
    pollingJob = viewModelScope.launch { ... }
}

fun stopPolling() {
    pollingJob?.cancel()
    pollingJob = null
}
```

### Cancelamento cooperativo

Coroutines **precisam cooperar** para serem canceladas. O cancelamento não é forçado:

```kotlin
// ❌ Esta coroutine ignora cancelamento
scope.launch {
    while (true) {          // não verifica isActive
        processarAlgo()
    }
}

// ✅ Esta coroutine coopera com cancelamento
scope.launch {
    while (isActive) {      // verifica se ainda está ativa
        processarAlgo()
    }
}
```

Funções `suspend` da stdlib (`delay`, `yield`, operações de IO) verificam cancelamento
automaticamente. Se você chamar `delay(1000)` numa coroutine cancelada, ela lança
`CancellationException` e para.

---

## 6. Tratamento de erros

### Try/catch numa coroutine

```kotlin
viewModelScope.launch {
    try {
        val cells = repository.readCells()
        _uiState.value = SignalUiState.Success(cells)
    } catch (e: SecurityException) {
        _uiState.value = SignalUiState.PermissionRequired
    } catch (e: Exception) {
        _uiState.value = SignalUiState.Error(e.message ?: "Erro desconhecido")
    }
}
```

### `CoroutineExceptionHandler`

Para capturar erros não tratados num escopo:

```kotlin
val handler = CoroutineExceptionHandler { _, exception ->
    Log.e("App", "Erro não tratado: $exception")
}

scope.launch(handler) {
    // se lançar exceção não tratada, vai para o handler
}
```

---

## 7. Parallelismo com `async`

```kotlin
// Sequencial: 2 segundos total (1s + 1s)
suspend fun sequencial(): Pair<String, String> {
    val a = buscarDadosA()   // espera 1s
    val b = buscarDadosB()   // espera 1s
    return Pair(a, b)
}

// Paralelo: 1 segundo total (ambos em paralelo)
suspend fun paralelo(): Pair<String, String> = coroutineScope {
    val a = async { buscarDadosA() }   // inicia imediatamente
    val b = async { buscarDadosB() }   // inicia imediatamente
    Pair(a.await(), b.await())         // espera ambos
}
```

---

## 8. `withContext` — mudando de thread

```kotlin
suspend fun lerArquivo(): String = withContext(Dispatchers.IO) {
    // Este bloco roda na thread pool de IO
    File("dados.txt").readText()
    // Ao terminar, retorna para o dispatcher original
}

// Na chamada:
viewModelScope.launch {
    // Main thread aqui
    val conteudo = lerArquivo()   // muda para IO, volta para Main
    textView.text = conteudo      // Main thread aqui de novo
}
```

No projeto:

```kotlin
suspend fun readCells(): List<CellSignalData> = withContext(Dispatchers.IO) {
    // Tudo dentro deste bloco roda em IO thread
    telephony.allCellInfo?.mapNotNull { parseCellInfo(it) } ?: emptyList()
    // Ao retornar, volta para o caller (Main thread no ViewModel)
}
```

---

## 🧪 Desafios

### Desafio 1 — Básico
Escreva uma `suspend fun` que:
1. Espera 500ms
2. Retorna o timestamp atual em milissegundos

### Desafio 2 — Job control
Implemente uma classe `Timer` que:
- Tem `start()` que lança uma coroutine que conta segundos
- Tem `stop()` que cancela a contagem
- Expõe `segundos: StateFlow<Int>`

### Desafio 3 — Parallelismo
Você tem duas fontes de dados lentas: `buscarCelulasLocal()` (200ms) e `buscarCelulasAPI()` (800ms).
Escreva código que busque as duas em paralelo e combine os resultados, mas se uma falhar,
use apenas o resultado da outra.

---

## 📌 Resumo

```
Coroutine    = função que pode ser pausada/retomada
suspend fun  = função que pode pausar uma coroutine
launch { }   = inicia coroutine sem retorno
async { }    = inicia coroutine com retorno (deferred.await())
delay(ms)    = pausa sem bloquear a thread
withContext  = muda de Dispatcher dentro da coroutine
viewModelScope = escopo do ViewModel (cancelado com o VM)
isActive     = verifica se a coroutine deve continuar
```

**Próxima aula →** [Dispatchers](02-dispatchers.md) — Main, IO, Default: qual usar quando.
