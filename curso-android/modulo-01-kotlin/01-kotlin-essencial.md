# Módulo 01 · Aula 01 — Kotlin Essencial

> **Pré-requisito:** Ter o Android Studio instalado e saber o que é uma variável.
> **Objetivo:** Dominar a sintaxe do Kotlin que você vai usar todos os dias.

---

## Por que Kotlin?

Antes de qualquer coisa: **por que Kotlin e não Java?**

Java foi a linguagem oficial do Android por anos. Kotlin foi criado pela JetBrains em 2011
e tornou-se co-oficial no Android em 2017. Hoje, todo novo projeto Android usa Kotlin.

| Java | Kotlin |
|------|--------|
| `String nome = null;` pode explodir em runtime | Null-safety em tempo de compilação |
| Muito boilerplate (getters, setters, toString) | `data class` faz tudo em 1 linha |
| Sem suporte nativo a coroutines | Coroutines são cidadãs de primeira classe |
| Lambdas verbosos | Lambdas limpos e concisos |

Kotlin compila para o mesmo bytecode Java — roda na mesma JVM, usa as mesmas bibliotecas.
É Java, mas muito mais agradável de escrever.

---

## 1. Variáveis: `val` e `var`

```kotlin
// val = value = imutável (como final no Java)
val nome = "Android"       // tipo inferido: String
val versao: Int = 35       // tipo explícito

// var = variable = mutável
var contador = 0
contador = contador + 1    // OK
contador += 1              // também OK

// val NÃO pode ser reatribuído
val pi = 3.14159
// pi = 3.0  ← ERRO DE COMPILAÇÃO!
```

### 📌 Regra de ouro
**Use `val` sempre. Troque para `var` apenas quando realmente precisar mudar o valor.**

Por que? Imutabilidade previne uma categoria enorme de bugs onde algo muda o valor
num lugar inesperado. Veja isso no projeto:

```kotlin
// Em SignaData.kt — todos os campos são val
data class CellSignalData(
    val networkType  : NetworkType,   // nunca muda depois de criado
    val dbm          : Int,
    val isRegistered : Boolean,
    ...
)
```

---

## 2. Tipos básicos

```kotlin
val inteiro   : Int     = 42
val longo     : Long    = 9_000_000_000L   // L no final = Long; _ separa milhar
val decimal   : Double  = 3.14
val flutuante : Float   = 3.14f            // f no final = Float
val texto     : String  = "Olá"
val booleano  : Boolean = true
val caractere : Char    = 'A'
```

### String Templates — interporlação de variáveis

```kotlin
val dbm = -87
val tipo = "LTE"

// Java: "Sinal: " + dbm + " dBm (" + tipo + ")"
// Kotlin:
val mensagem = "Sinal: $dbm dBm ($tipo)"

// Para expressões mais complexas, use ${ }
val info = "Potência: ${dbm + 10} dBm relativo"
val maiusculo = "${tipo.uppercase()} ativo"
```

---

## 3. Funções

### Função simples

```kotlin
fun somar(a: Int, b: Int): Int {
    return a + b
}

// Chamada:
val resultado = somar(3, 5)   // resultado = 8
```

### Função de expressão única (para funções simples)

```kotlin
// Equivalente ao código acima, mas mais conciso:
fun somar(a: Int, b: Int): Int = a + b
```

### Parâmetros com valores padrão

```kotlin
fun saudar(nome: String, saudacao: String = "Olá") {
    println("$saudacao, $nome!")
}

saudar("Maria")              // → "Olá, Maria!"
saudar("João", "Oi")         // → "Oi, João!"
saudar(nome = "Ana")         // → "Olá, Ana!" (named argument)
```

Isso você vai ver muito no Compose:

```kotlin
// Em Components.kt — SignalBars tem valores padrão
fun SignalBars(
    level    : Int,
    color    : Color,
    modifier : Modifier = Modifier,  // valor padrão
    barCount : Int      = 5,         // valor padrão
    barWidth : Dp       = 5.dp,      // valor padrão
    maxHeight: Dp       = 20.dp,     // valor padrão
)
```

Ao chamar: `SignalBars(level = 3, color = Verde)` — só precisa dos obrigatórios.

### Funções de extensão — superpoderes

Você pode adicionar funções a qualquer classe **sem herdar dela**:

```kotlin
// Adiciona uma função à classe Int
fun Int.toDbmString(): String = "$this dBm"

val sinal = -87
println(sinal.toDbmString())   // → "-87 dBm"
```

No projeto, `FrequencyUtils.kt` usa isso implicitamente:

```kotlin
fun formatMhz(mhz: Double): String =
    if (mhz >= 1000.0) "${"%.3f".format(mhz / 1000.0)} GHz"
    else "${"%.1f".format(mhz)} MHz"
```

---

## 4. Classes

### Classe básica

```kotlin
class Pessoa(val nome: String, var idade: Int) {

    fun apresentar() {
        println("Sou $nome, tenho $idade anos.")
    }

    fun fazAniversario() {
        idade++   // var pode ser alterado
    }
}

val p = Pessoa("Lucas", 25)
p.apresentar()      // → "Sou Lucas, tenho 25 anos."
p.fazAniversario()
println(p.idade)    // → 26
```

### `data class` — a estrela do Kotlin

```kotlin
data class Ponto(val x: Int, val y: Int)
```

Uma `data class` gera automaticamente:
- `toString()` → `Ponto(x=3, y=5)`
- `equals()` → compara por valor (não referência)
- `hashCode()` → consistente com equals
- `copy()` → cria cópia com campos alterados

```kotlin
val p1 = Ponto(3, 5)
val p2 = Ponto(3, 5)
val p3 = p1.copy(y = 10)   // Ponto(x=3, y=10)

println(p1 == p2)    // true (por valor!)
println(p1 === p2)   // false (referências diferentes)
println(p3)          // Ponto(x=3, y=10)
```

No projeto, toda entidade de dado é uma `data class`:

```kotlin
data class SignalPoint(
    val timestampMs : Long,
    val dbm         : Int,
    val networkType : NetworkType,
)
```

### `object` — Singleton

```kotlin
object Configuracao {
    const val POLL_INTERVAL_MS = 1_000L
    const val VERSION = "1.0"
}

println(Configuracao.VERSION)  // → "1.0"
```

### `companion object` — "métodos estáticos" do Kotlin

```kotlin
class SignalViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        const val POLL_INTERVAL_MS = 1_000L   // acessível como SignalViewModel.POLL_INTERVAL_MS
        const val HISTORY_CAPACITY = 90
    }
}
```

### `enum class` — conjunto de valores fixos

```kotlin
enum class Direcao { NORTE, SUL, LESTE, OESTE }

val d = Direcao.NORTE
println(d.name)    // → "NORTE"
println(d.ordinal) // → 0 (índice)

// Enums podem ter propriedades:
enum class NetworkType(val label: String, val generation: String) {
    GSM_2G ("GSM",   "2G"),
    LTE    ("LTE",   "4G"),
    NR_5G  ("NR",    "5G"),
}

println(NetworkType.LTE.label)       // → "LTE"
println(NetworkType.NR_5G.generation) // → "5G"
```

---

## 5. Controle de fluxo

### `if` como expressão (retorna valor!)

```kotlin
// Java: String resultado = condicao ? "sim" : "nao";
// Kotlin:
val resultado = if (condicao) "sim" else "nao"

val maximo = if (a > b) a else b
```

### `when` — o `switch` poderoso do Kotlin

```kotlin
val tipo: NetworkType = NetworkType.LTE

val cor = when (tipo) {
    NetworkType.GSM_2G         -> "Cinza"
    NetworkType.WCDMA          -> "Roxo"
    NetworkType.LTE            -> "Azul"
    NetworkType.NR_5G          -> "Verde"
    else                       -> "Desconhecido"
}
```

`when` com ranges (muito útil para dBm):

```kotlin
val qualidade = when {
    dbm >= -80  -> "Excelente"
    dbm >= -90  -> "Bom"
    dbm >= -100 -> "Regular"
    dbm >= -110 -> "Fraco"
    else        -> "Muito Fraco"
}
```

### `for` e iteração

```kotlin
val lista = listOf(1, 2, 3, 4, 5)

for (item in lista) {
    println(item)
}

for (i in 1..10) {      // range inclusivo: 1, 2, ..., 10
    println(i)
}

for (i in 0 until 10) { // range exclusivo: 0, 1, ..., 9
    println(i)
}

lista.forEach { item ->
    println(item)
}
```

---

## 6. Lambdas e Higher-Order Functions

Um lambda é uma função sem nome que você pode passar como parâmetro.

```kotlin
// Sintaxe completa:
val dobrar: (Int) -> Int = { numero: Int -> numero * 2 }

// Inferência de tipo:
val dobrar = { numero: Int -> numero * 2 }

// Se só há um parâmetro, use `it`:
val dobrar: (Int) -> Int = { it * 2 }

println(dobrar(5))   // → 10
```

### Funções que recebem lambdas (Higher-Order Functions)

```kotlin
fun executar(acao: () -> Unit) {
    println("Antes")
    acao()
    println("Depois")
}

executar { println("Executando!") }
// → Antes
// → Executando!
// → Depois
```

Isso é **exatamente** como o Compose funciona:

```kotlin
@Composable
fun Button(
    onClick: () -> Unit,   // lambda!
    content: @Composable () -> Unit
) { ... }

// Uso:
Button(onClick = { vm.startPolling() }) {
    Text("Iniciar")
}
```

### Trailing lambda — sintaxe especial

Quando o último parâmetro é um lambda, você pode colocá-lo fora dos parênteses:

```kotlin
// Equivalentes:
Button(onClick = { ... }, content = { Text("OK") })
Button(onClick = { ... }) { Text("OK") }   // mais limpo!
```

---

## 7. Interfaces e herança

```kotlin
interface Mensuravel {
    fun medir(): Double
    fun unidade(): String = "unidade"   // método com implementação padrão
}

class SinalLTE : Mensuravel {
    override fun medir(): Double = -87.5
    override fun unidade(): String = "dBm"
}

val s: Mensuravel = SinalLTE()
println("${s.medir()} ${s.unidade()}")   // → "-87.5 dBm"
```

---

## 🧪 Desafios

### Desafio 1 — Básico
Escreva uma função `classificarSinal(dbm: Int): String` que retorne "Excelente", "Bom",
"Regular", "Fraco" ou "Sem sinal" baseado nos thresholds do projeto.

### Desafio 2 — Intermediário
Crie uma `data class Medicao(val dbm: Int, val tipo: String, val horario: Long)`.
Instancie três medições e use `copy()` para criar uma quarta com dbm = -95.

### Desafio 3 — Avançado
Escreva uma função de extensão `Int.isValidDbm(): Boolean` que retorne `true` se o valor
estiver no intervalo realista de sinal celular (-140 a -44 dBm).

---

## 📌 Resumo da aula

| Conceito | Quando usar |
|----------|-------------|
| `val` | Sempre que possível |
| `var` | Só quando o valor precisa mudar |
| `data class` | Para entidades/modelos de dados |
| `enum class` | Para conjuntos fixos de valores |
| `when` | Em vez de if/else encadeado |
| Lambda `{ }` | Para callbacks e Higher-Order Functions |
| Extensões | Para adicionar comportamento a classes existentes |

**Próxima aula →** [Null Safety](02-null-safety.md) — o sistema que elimina NullPointerException.
