# Módulo 02 · Aula 02 — Modelos de Dados: data class, sealed class, enum

> **Pré-requisito:** Aula 01 — MVVM
> **Objetivo:** Modelar dados corretamente para que o compilador te ajude a não cometer erros.

---

## Por que modelar dados bem é fundamental?

Os modelos de dados são o "vocabulário" do seu app. Se o vocabulário for impreciso,
o código fica ambíguo, difícil de ler e cheio de bugs silenciosos.

**Pergunta:** Como você representaria "qualidade de sinal"?

```kotlin
// Opção A — como Int
val qualidade: Int = 3   // 3 de quê? 1-5? 0-4? 0-3?

// Opção B — como String
val qualidade: String = "Bom"   // pode ser "bom", "BOM", "good", "boa"...

// Opção C — como enum (correto!)
val qualidade: SignalQuality = SignalQuality.GOOD
// O compilador garante que só pode ser um dos valores válidos
```

A **Opção C** é superior porque:
- O compilador rejeita valores inválidos
- IDE completa automaticamente os valores possíveis
- Um `when(qualidade)` sem `else` avisa se um caso não for tratado

---

## 1. `data class` — o modelo de dados perfeito

Uma `data class` é uma classe cujo propósito é **carregar dados**. O compilador gera
automaticamente `equals`, `hashCode`, `toString` e `copy`.

### Anatomia de uma data class

```kotlin
data class CellSignalData(
    // val = imutável (preferido)
    val networkType  : NetworkType,
    val dbm          : Int,
    val asuLevel     : Int,
    val level        : Int,
    val isRegistered : Boolean,
    val operator     : String,

    // Campos com valor padrão — opcionais na construção
    val extraMetrics : Map<String, String> = emptyMap(),
    val netInfo      : CellNetworkInfo     = CellNetworkInfo(),
)
```

### Como usar

```kotlin
// Criação
val cell = CellSignalData(
    networkType  = NetworkType.LTE,
    dbm          = -87,
    asuLevel     = 43,
    level        = 3,
    isRegistered = true,
    operator     = "Vivo",
)

// copy — cria nova instância com campo alterado
val cellMaisFraco = cell.copy(dbm = -105)

// Desestruturação
val (tipo, dbm, asu, level, registrado) = cell

// toString automático
println(cell)
// CellSignalData(networkType=LTE, dbm=-87, asuLevel=43, ...)

// equals por valor
val cell2 = CellSignalData(NetworkType.LTE, -87, 43, 3, true, "Vivo")
println(cell == cell2)   // true (mesmos valores)
```

### Por que imutabilidade?

```kotlin
// ❌ Data class mutável — perigosa
data class MutavelData(var dbm: Int)

val d = MutavelData(-87)
processarEmThread1(d)
processarEmThread2(d)   // ambas leem e escrevem dbm → race condition!

// ✅ Data class imutável — segura
data class ImutavelData(val dbm: Int)

val d = ImutavelData(-87)
// Threads podem ler simultaneamente sem problema
// Para "mudar": d.copy(dbm = -95) → cria nova instância
```

**Regra:** `data class` com todos os campos `val`. Nunca `var`.

---

## 2. `enum class` — conjunto de valores fixos

Use quando há um número **fixo e conhecido** de possibilidades.

### Enum simples

```kotlin
enum class AppTab { SIGNAL, HISTORY, INFO }

val aba = AppTab.SIGNAL
println(aba.name)    // "SIGNAL"
println(aba.ordinal) // 0
```

### Enum com propriedades (o mais útil!)

```kotlin
enum class NetworkType(
    val label      : String,
    val generation : String,
    val badgeColor : Color,
) {
    GSM_2G ("GSM",   "2G", Color(0xFF64748B)),
    CDMA   ("CDMA",  "2G", Color(0xFF64748B)),
    WCDMA  ("WCDMA", "3G", Color(0xFF8B5CF6)),
    HSPA   ("HSPA+", "3G", Color(0xFF8B5CF6)),
    LTE    ("LTE",   "4G", Color(0xFF3B82F6)),
    NR_5G  ("NR",    "5G", Color(0xFF10B981)),
    UNKNOWN("?",     "?",  Color(0xFF3A4A65)),
}
```

**Por que colocar dados no enum?**

Porque elimina `when` repetitivos na UI:

```kotlin
// ❌ Sem dados no enum — when em todo lugar
val cor = when (networkType) {
    NetworkType.GSM_2G -> Color(0xFF64748B)
    NetworkType.LTE    -> Color(0xFF3B82F6)
    NetworkType.NR_5G  -> Color(0xFF10B981)
    else               -> Color.Gray
}

// ✅ Com dados no enum — acesso direto
val cor = networkType.badgeColor   // pronto!
```

### Iterando sobre todos os valores

```kotlin
// Todos os valores de um enum:
NetworkType.values().forEach { tipo ->
    println("${tipo.label} (${tipo.generation})")
}
// GSM (2G), CDMA (2G), WCDMA (3G), ...
```

---

## 3. `sealed class` — o enum poderoso

`sealed class` é como um enum, mas cada "valor" pode ter **dados diferentes**.

### O problema que sealed class resolve

Para representar o estado de uma tela, você precisaria:

```kotlin
// ❌ Abordagem sem sealed class — campos opcionais e confusos
data class UiState(
    val isLoading           : Boolean = false,
    val isPermissionNeeded  : Boolean = false,
    val data                : List<Cell>? = null,   // null quando loading
    val errorMessage        : String? = null,        // null quando não é erro
)

// É Loading? isLoading = true, data = null, error = null?
// É sucesso? isLoading = false, data = [...], error = null?
// É erro?    isLoading = false, data = null, error = "..."?
```

Isso cria estados "impossíveis": `isLoading = true` e `data != null` ao mesmo tempo.

```kotlin
// ✅ Abordagem com sealed class — estados exclusivos e precisos
sealed class SignalUiState {
    object Loading            : SignalUiState()
    object PermissionRequired : SignalUiState()
    data class Success(
        val primary  : CellSignalData?,
        val allCells : List<CellSignalData>,
        val updatedAt: Long = System.currentTimeMillis(),
    ) : SignalUiState()
    data class Error(val message: String) : SignalUiState()
}
```

Agora é **impossível** ter `Loading` com dados ao mesmo tempo.

### Usando com `when` — o compilador exige todos os casos

```kotlin
when (val state = uiState) {
    is SignalUiState.Loading          -> LoadingScreen()
    is SignalUiState.PermissionRequired -> PermissionScreen()
    is SignalUiState.Error            -> ErrorScreen(state.message)
    is SignalUiState.Success          -> SuccessScreen(state.primary, state.allCells)
    // Se adicionar um novo estado, o compilador AVISA aqui!
}
```

**Sem o `else`:** Se você adicionar `data class Timeout : SignalUiState()`, o compilador
vai reclamar em **todos** os `when` que não tratam `Timeout`. Isso é segurança em tempo
de compilação — você nunca "esquece" de tratar um caso.

### `object` vs. `data class` em sealed class

```kotlin
sealed class SignalUiState {
    // object = singleton, sem dados
    object Loading : SignalUiState()          // não há "dados de loading"

    // data class = instâncias diferentes com dados
    data class Error(val message: String)     // cada erro tem uma mensagem
    data class Success(val cells: List<Cell>) // cada sucesso tem dados
}
```

Use `object` quando o estado não carrega informação adicional.
Use `data class` quando o estado precisa de dados associados.

---

## 4. Hierarquia de tipos do projeto

```
SignalUiState (sealed)
├── Loading (object)
├── PermissionRequired (object)
├── Success (data class)
│   ├── primary: CellSignalData?
│   └── allCells: List<CellSignalData>
└── Error (data class)
    └── message: String

CellSignalData (data class)
├── networkType: NetworkType (enum)
├── dbm: Int
├── extraMetrics: Map<String, String>
└── netInfo: CellNetworkInfo (data class)
    ├── tac: Int?
    ├── fdlMhz: Double?
    └── ...

NetworkType (enum)
├── GSM_2G
├── WCDMA
├── LTE
└── NR_5G

SignalQuality (enum)
├── EXCELLENT
├── GOOD
├── FAIR
├── POOR
├── VERY_POOR
└── NO_SIGNAL
```

---

## 5. Funções puras em modelos

`SignaData.kt` também contém funções puras — sem side effects, sem dependências externas:

```kotlin
// Entrada: dbm + tipo → Saída: qualidade
// Mesmos inputs → sempre mesmos outputs
fun signalQuality(dbm: Int, type: NetworkType): SignalQuality { ... }

// Entrada: dbm + tipo → Saída: Float 0-1
fun signalProgress(dbm: Int, type: NetworkType): Float { ... }
```

**Por que funções puras são valiosas?**

1. **Testáveis:** `assertEquals(GOOD, signalQuality(-88, LTE))` — sem mock, sem Android.
2. **Previsíveis:** Nunca têm efeitos colaterais surpresa.
3. **Cacheáveis:** Mesmo input, mesmo output — podem ser memoizadas.

---

## 6. Padrão: nullable fields com defaults

```kotlin
data class CellNetworkInfo(
    val tac    : Int?    = null,   // default null
    val enbId  : Long?   = null,
    val fdlMhz : Double? = null,
    // Labels com defaults inteligentes:
    val tacLabel    : String = "TAC",
    val nodeIdLabel : String = "eNB",
)
```

**Por que não usar um sealed class para cada campo?**

Porque seria over-engineering. `Int?` já comunica "pode não estar disponível". A UI
já trata isso com `info.tac?.toString() ?: "N/D"`. Criar `sealed class FieldValue<T>`
para isso seria complexidade sem benefício real neste contexto.

**Mas em uma API pública ou biblioteca** — aí sim, um sealed class `FieldResult<T>`
seria mais expressivo.

---

## 🧪 Desafios

### Desafio 1 — enum com comportamento
Crie um `enum class Operadora` com valores `VIVO`, `CLARO`, `TIM`, `OI`, `DESCONHECIDA`.
Adicione:
- `val nomeCompleto: String` para o nome oficial
- `fun corBrand(): Color` que retorna uma cor associada a cada operadora

### Desafio 2 — sealed class para resultado de rede
Crie `sealed class NetworkResult<T>`:
- `data class Success<T>(val data: T)`
- `data class Error<T>(val message: String, val code: Int? = null)`
- `class Loading<T>` (sem dados)

Escreva uma função `processar(result: NetworkResult<List<CellSignalData>>)` que usa `when`.

### Desafio 3 — modeling
Modele o histórico de localização de um app de rastreamento:
- Uma sessão de rastreamento tem ID, hora de início, hora de fim (opcional) e lista de pontos
- Cada ponto tem latitude, longitude, altitude (opcional) e timestamp
- O estado pode ser: "Inativo", "Gravando", "Pausado", "Concluído com dados"

Use as estruturas corretas: `data class`, `sealed class`, `enum class`.

---

## 📌 Resumo

```kotlin
// Use data class para:
// → Entidades e modelos de dados (CellSignalData, SignalPoint)
data class MinhaEntidade(val campo1: Tipo1, val campo2: Tipo2)

// Use enum class para:
// → Conjuntos FIXOS de valores (NetworkType, SignalQuality, AppTab)
enum class MeuEnum(val propriedade: Tipo) { VALOR_A("a"), VALOR_B("b") }

// Use sealed class para:
// → Estados exclusivos com dados diferentes (UiState, NetworkResult)
sealed class MeuEstado {
    object Vazio : MeuEstado()
    data class ComDados(val info: String) : MeuEstado()
    data class ComErro(val erro: Exception) : MeuEstado()
}
```

**Próximo Módulo →** [Coroutines](../modulo-03-coroutines/01-coroutines.md) — como fazer operações lentas sem travar a tela.
