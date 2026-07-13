# Guia Técnico Avançado — Signal+

> Documentação didática do projeto Android **Signal+**: um medidor de sinal de rádio móvel que lê dados brutos do modem, converte parâmetros 3GPP e os apresenta em uma interface Compose moderna.

---

## Sumário

1. [Visão Geral do Sistema](#1-visão-geral-do-sistema)
2. [Arquitetura do Projeto](#2-arquitetura-do-projeto)
3. [Estrutura de Pastas e Responsabilidades](#3-estrutura-de-pastas-e-responsabilidades)
4. [Análise Detalhada do Código](#4-análise-detalhada-do-código)
   - 4.1 [Camada de Dados — `data/`](#41-camada-de-dados--data)
   - 4.2 [Camada de ViewModel — `ui/SignalViewModel`](#42-camada-de-viewmodel--uisignalviewmodel)
   - 4.3 [Camada de UI — `ui/screens/` e `ui/components/`](#43-camada-de-ui--uiscreens-e-uicomponents)
   - 4.4 [Tema — `ui/theme/`](#44-tema--uitheme)
5. [Fluxo de Execução Completo](#5-fluxo-de-execução-completo)
6. [Tecnologias e Dependências](#6-tecnologias-e-dependências)
7. [Decisões de Design](#7-decisões-de-design)
8. [Boas Práticas e Possíveis Melhorias](#8-boas-práticas-e-possíveis-melhorias)
9. [Como Recriar o Projeto do Zero](#9-como-recriar-o-projeto-do-zero)

---

## 1. Visão Geral do Sistema

### O que o app faz

Signal+ lê em tempo real os dados do modem de rádio do dispositivo Android e apresenta ao usuário:

- **Potência do sinal** da célula à qual está conectado, em dBm, com gauge animado.
- **Identificadores de rádio**: TAC/LAC, eNB/gNB, Cell ID, PCI/PSC/BSIC, ARFCN e derivados.
- **Frequências calculadas**: frequência de downlink (FDL) e uplink (FUL) em MHz/GHz, derivadas do ARFCN via tabelas 3GPP.
- **Largura de banda do canal** (quando disponível via API Android 9+).
- **Métricas de qualidade** específicas por geração: RSRP/RSRQ/CQI (LTE), SS-RSRP/SS-RSRQ/SS-SINR (5G NR), ASU (2G/3G).
- **Histórico de 90 leituras** com gráfico de linha, estatísticas de sessão (média, mínimo, máximo, desvio padrão) e log de leituras recentes.
- **Células vizinhas** detectadas pelo hardware, com nível relativo.

### O problema que resolve

O Android expõe dados brutos de modem via `TelephonyManager`, mas de forma fragmentada:
cada geração de rede (2G/3G/4G/5G) tem suas próprias classes, campos com nomes diferentes
e comportamentos distintos entre fabricantes e versões de API. O app encapsula toda essa
complexidade numa interface coesa, convertendo valores técnicos (ARFCN → frequência em MHz,
TA → distância em metros) em informações compreensíveis.

### Interação entre os componentes

```
┌──────────────────────────────────────────────────────────────────┐
│  Android OS                                                      │
│  TelephonyManager.getAllCellInfo()  (chamada no Dispatchers.IO)  │
└───────────────────────────┬──────────────────────────────────────┘
                            │ List<CellInfo>
                            ▼
┌──────────────────────────────────────────────────────────────────┐
│  SignalRepository                                                │
│  ├─ parseCellInfo()   classifica o tipo concreto de CellInfo     │
│  ├─ parseNr / parseLte / parseWcdma / parseGsm / parseCdma      │
│  │   extrai campos da API, trata nulos e valores sentinela       │
│  ├─ FrequencyUtils    ARFCN → FDL / FUL / Banda (math 3GPP)     │
│  └─ readPrimaryBwKhz  reflections → PhysicalChannelConfig       │
│                            │ List<CellSignalData>               │
└───────────────────────────┬──────────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────────┐
│  SignalViewModel                                                 │
│  ├─ polling loop (1 s via viewModelScope + delay)               │
│  ├─ _uiState: MutableStateFlow<SignalUiState>                   │
│  ├─ history: SignalHistory (buffer circular de SignalPoints)     │
│  └─ _currentTab: MutableStateFlow<AppTab>                       │
│                            │ StateFlow                          │
└───────────────────────────┬──────────────────────────────────────┘
                            │ collectAsStateWithLifecycle()
                            ▼
┌──────────────────────────────────────────────────────────────────┐
│  Jetpack Compose UI                                              │
│  MainScreen → AppTopBar + AppBottomBar + conteúdo por tab       │
│  ├─ SignalTabContent  → SignalGauge + CellIdentifiersCard +      │
│  │                       MetricCards + CellRows                 │
│  ├─ HistoryScreen     → SignalChart + estatísticas + log        │
│  └─ InfoScreen        → tabelas de referência técnica           │
└──────────────────────────────────────────────────────────────────┘
```

---

## 2. Arquitetura do Projeto

### Padrão utilizado: MVVM com UDF (Unidirectional Data Flow)

O projeto adota o padrão **MVVM** (Model-View-ViewModel) com **fluxo de dados unidirecional**:
os dados sempre fluem numa única direção — da camada de hardware para a UI — sem que a UI
possa modificar o estado diretamente.

#### Como funciona o MVVM neste contexto

| Camada | Responsabilidade | No projeto |
|--------|-----------------|------------|
| **Model** | Dados e lógica de negócio | `SignalRepository`, `SignaData.kt`, `FrequencyUtils.kt`, `SignalHistory` |
| **ViewModel** | Estado reativo, orquestração | `SignalViewModel` |
| **View** | Exibição, eventos do usuário | Todas as `@Composable` functions |

A **View** nunca chama o repositório diretamente. Ela apenas:
1. Observa o `StateFlow` do ViewModel.
2. Despacha *intenções* (ex.: `vm.startPolling()`, `vm.selectTab(tab)`).

O **ViewModel** nunca importa classes Compose. Ele processa dados e publica estado imutável.

O **Model** não sabe nada sobre a UI — pode ser testado em JVM puro.

#### Por que MVVM faz sentido aqui?

**Sobrevivência a rotações de tela:** O ViewModel é gerenciado pelo ciclo de vida do Android
e sobrevive a mudanças de configuração (rotação, mudança de idioma). O loop de polling não é
reiniciado ao girar o telefone.

**Separação de preocupações:** A lógica de conversão ARFCN→MHz não sabe que existe uma tela,
e a tela não sabe como o ARFCN é convertido.

**Testabilidade:** `FrequencyUtils.kt` e `SignaData.kt` não têm nenhuma dependência Android —
podem ser testados com JUnit puro sem emulador.

#### Alternativas consideradas

- **MVI (Model-View-Intent):** Mais estrito no fluxo de eventos (toda ação é um `Intent`,
  todo estado é imutável e gerado por `reduce()`). Seria adequado se houvesse formulários
  complexos ou histórico de navegação, mas para leitura passiva de sensores o overhead não
  se justifica.

- **MVP:** Mais antigo, acoplado a interface `View`, não se integra bem com Compose (que é
  declarativo e não tem referência ao "presenter").

- **Nenhuma arquitetura (God Activity):** Simples de começar, impossível de manter. Mistura
  lógica de sensor, formatação, estado de UI e ciclo de vida numa classe só.

---

## 3. Estrutura de Pastas e Responsabilidades

```
app/src/main/java/com/example/ls/
│
├── MainActivity.kt              ← porta de entrada; configura Compose e edge-to-edge
│
├── data/                        ← Camada de Modelo (zero dependência de UI)
│   ├── SignaData.kt             ← contratos de dados: enums, data classes, UiState, helpers
│   ├── SignalHistory.kt         ← buffer circular reativo de leituras históricas
│   ├── SignalRepositry.kt       ← acesso ao hardware via TelephonyManager
│   └── FrequencyUtils.kt       ← matemática de conversão ARFCN ↔ frequência (3GPP)
│
└── ui/
    ├── SignalViewModel.kt       ← orquestrador: polling, estado, histórico, abas
    │
    ├── screens/
    │   ├── MainScreen.kt        ← scaffold principal: permissões, navegação por abas
    │   ├── HistoryScreen.kt     ← aba de histórico: gráfico + estatísticas + log
    │   └── InfoScreen.kt        ← aba informativa: glossário, limiares, explicações
    │
    ├── components/
    │   ├── Components.kt        ← primitivos reutilizáveis (badges, cards, barras)
    │   ├── SignalGauge.kt       ← gauge de arco semicircular desenhado em Canvas
    │   ├── SignalChart.kt       ← gráfico de linha temporal desenhado em Canvas
    │   └── CellIdentifiersCard.kt ← grade de identificadores de rádio por célula
    │
    └── theme/
        ├── Color.kt             ← paleta semântica (backgrounds, bordas, texto, acentos)
        ├── Theme.kt             ← MaterialTheme escuro com tipografia customizada
        └── Type.kt              ← hierarquia tipográfica (não usada diretamente; bundled)
```

### Por que essa separação?

**`data/` sem imports de UI:** Nenhum arquivo em `data/` importa `androidx.compose.*`. Isso
garante que a lógica de negócio pode compilar e ser testada sem o compilador Compose.

**`components/` vs `screens/`:** Composables em `components/` são *burros* — recebem apenas
dados primitivos como parâmetros, não acessam o ViewModel. Composables em `screens/` são
*inteligentes* — têm acesso ao ViewModel e compõem os componentes menores.

**Um único `MainActivity`:** O app tem uma única `Activity`. A navegação entre abas é feita
pelo ViewModel + `AnimatedContent` no Compose. Isso é a abordagem recomendada para apps
Compose modernos (Single Activity Architecture).

---

## 4. Análise Detalhada do Código

### 4.1 Camada de Dados — `data/`

---

#### `SignaData.kt` — Contratos de dados

Este arquivo define os "contratos" do sistema: os tipos que fluem de uma camada para outra.
Não tem lógica de I/O, não tem lógica de UI — apenas estruturas de dados e duas funções puras
de cálculo.

##### `NetworkType` (enum)

```kotlin
enum class NetworkType(
    val label: String,
    val generation: String,
    val badgeColor: Color,
) {
    GSM_2G ("GSM",   "2G", Net2G),
    CDMA   ("CDMA",  "2G", Net2G),
    WCDMA  ("WCDMA", "3G", Net3G),
    HSPA   ("HSPA+", "3G", Net3G),
    LTE    ("LTE",   "4G", Net4G),
    NR_5G  ("NR",    "5G", Net5G),
    UNKNOWN("?",     "?",  QualityNone),
}
```

**Decisão de design:** Cada entrada do enum carrega suas próprias informações de
apresentação (`label`, `badgeColor`). Isso elimina switches/whens na UI — o componente
`NetworkBadge` simplesmente acessa `type.badgeColor` e `type.label` diretamente.

*Trade-off:* O enum importa `androidx.compose.ui.graphics.Color`, criando um acoplamento
leve da camada de dados à UI. Alternativa seria mover as cores para o ViewModel ou para um
mapper separado — mas dado que as cores são constantes semânticas imutáveis, o acoplamento
é aceitável.

##### `SignalQuality` (enum)

Mapeia o nível de qualidade do sinal (EXCELLENT a NO_SIGNAL) com `label`, `color` e `bars`.
A função `signalQuality(dbm, type)` converte um valor dBm para um `SignalQuality` com
thresholds diferentes por geração:

```kotlin
NetworkType.NR_5G -> when {
    dbm >= -80  -> SignalQuality.EXCELLENT
    dbm >= -90  -> SignalQuality.GOOD
    ...
}
```

**Por que os thresholds variam por geração?** Porque cada tecnologia opera em faixas de
frequência e com modulações diferentes. O sinal 5G NR em mmWave (>24 GHz) tem atenuação
altíssima — um -90 dBm em 5G pode ser excelente, enquanto em 2G GSM seria péssimo.
Os valores seguem ITU-T E.802 e 3GPP TS 36.214.

##### `signalProgress(dbm, type): Float`

Normaliza o dBm para o intervalo [0.0, 1.0] para animar o gauge:

```kotlin
val (min, max) = when (type) {
    NetworkType.NR_5G -> -130f to -44f
    NetworkType.LTE   -> -130f to -44f
    ...
}
return ((dbm - min) / (max - min)).coerceIn(0f, 1f)
```

**Analogia:** É como converter uma temperatura em Celsius para uma escala de 0 a 100%.
`coerceIn` garante que nunca sai do intervalo mesmo com valores de hardware inválidos.

##### `CellNetworkInfo` (data class)

Contém os identificadores de rádio de uma célula. **Todos os campos são nullable** porque o
hardware pode não expor certos valores — fabricantes diferentes implementam drivers diferentes,
e versões antigas do Android não expõem alguns campos.

Campos como `tacLabel`, `nodeIdLabel`, `arfcnLabel` e `pciLabel` são strings configuráveis
porque cada geração usa nomenclatura diferente para o mesmo conceito:
- Área de rastreamento: **TAC** (LTE/5G) vs **LAC** (2G/3G)
- Identificador do nó: **eNB** (LTE) vs **gNB** (5G) vs **CI** (GSM)
- Identificador de célula física: **PCI** (LTE/5G) vs **PSC** (WCDMA) vs **BSIC** (GSM)

Ao configurar os labels no `SignalRepository` ao construir o objeto, a UI não precisa
saber qual tecnologia está sendo exibida para mostrar o rótulo correto.

##### `SignalUiState` (sealed class)

```kotlin
sealed class SignalUiState {
    object Loading            : SignalUiState()
    object PermissionRequired : SignalUiState()
    data class Success(val primary: CellSignalData?, val allCells: List<CellSignalData>, val updatedAt: Long)
    data class Error(val message: String)
}
```

**Por que sealed class?** Porque o estado da tela é exclusivo — ela só pode estar num estado
de cada vez, e o compilador Kotlin força o tratamento de todos os casos no `when`:

```kotlin
when (val s = state) {
    is SignalUiState.Loading          -> LoadingScreen()
    is SignalUiState.PermissionRequired -> PermissionScreen { ... }
    is SignalUiState.Error            -> ErrorScreen(s.message)
    is SignalUiState.Success          -> SignalTabContent(s)
}
```

Se alguém adicionar um novo estado (ex.: `BluetoothRequired`), o compilador vai reclamar
em todo `when` que não tratar o novo caso — isso é *safety by construction*.

---

#### `SignalHistory.kt` — Buffer circular reativo

```kotlin
class SignalHistory(val capacity: Int = 60) {
    private val _points = MutableStateFlow<List<SignalPoint>>(emptyList())
    val points: StateFlow<List<SignalPoint>> = _points.asStateFlow()

    fun add(point: SignalPoint) {
        val current = _points.value
        _points.value = if (current.size >= capacity) {
            current.drop(1) + point     // remove o mais antigo, adiciona o novo
        } else {
            current + point
        }
    }
    ...
}
```

**Por que `StateFlow<List<SignalPoint>>` em vez de `ArrayDeque`?**

Um `ArrayDeque` simples seria mais eficiente em memória, mas não notificaria a UI de mudanças.
Ao usar `StateFlow`, qualquer `@Composable` que observe `history.points` será recomposto
automaticamente quando um novo ponto for adicionado — sem nenhum código extra.

**A imutabilidade aqui é essencial:** `_points.value = current.drop(1) + point` cria uma
nova lista a cada atualização. Isso parece ineficiente, mas garante que o StateFlow dispara
a notificação de mudança (ele compara por referência — se a referência for a mesma, não
dispara). Com 90 pontos de Int + Long + enum, o custo é negligenciável.

##### Funções de estatística

```kotlin
fun range(): Pair<Int, Int>   // min e max dBm para escalar o eixo Y do gráfico
fun average(n: Int = 10): Double?  // média dos últimos N pontos
fun stdDev(n: Int = 10): Double    // desvio padrão dos últimos N pontos
```

`stdDev` usa a fórmula de variância populacional (divide por N, não N-1):

```kotlin
val variance = pts.sumOf { (it.dbm - avg) * (it.dbm - avg) } / pts.size
return Math.sqrt(variance)
```

Isso é adequado aqui porque não estamos fazendo inferência estatística — apenas medindo a
dispersão dos últimos N pontos observados.

---

#### `SignalRepositry.kt` — Acesso ao hardware

Esta é a classe mais complexa do projeto. Ela navega nas APIs fragmentadas do Android para
extrair dados coerentes de todas as gerações de rede.

##### `readCells(): List<CellSignalData>`

```kotlin
suspend fun readCells(): List<CellSignalData> = withContext(Dispatchers.IO) {
    val operator   = telephony.networkOperatorName.takeIf { it.isNotBlank() } ?: "Desconhecido"
    val primaryBwKhz = readPrimaryBwKhz()
    val rawList    = telephony.allCellInfo ?: return@withContext emptyList()

    rawList
        .mapNotNull { parseCellInfo(it, operator, primaryBwKhz) }
        .sortedByDescending { it.dbm }
        .sortedByDescending { it.isRegistered }
}
```

**`withContext(Dispatchers.IO)`:** `getAllCellInfo()` é uma chamada ao driver do modem —
potencialmente bloqueante. Ao executar em `Dispatchers.IO`, não travamos a thread principal
(UI thread). O `suspend fun` sinaliza ao chamador que esta função pode ser suspensa e retomada.

**Ordenação dupla:** `sortedByDescending { it.dbm }` ordena por intensidade, depois
`sortedByDescending { it.isRegistered }` eleva a célula registrada ao topo. A ordenação
é estável em Kotlin, então a célula registrada fica no topo e, entre as não-registradas,
a de maior sinal vem primeiro.

##### `readPrimaryBwKhz()` — Reflexão para largura de banda

```kotlin
private fun readPrimaryBwKhz(): Int? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
    return try {
        val method  = telephony.javaClass.getMethod("getPhysicalChannelConfigs")
        val configs = method.invoke(telephony) as? List<*>
        val config  = configs?.firstOrNull() ?: return null
        val bwMethod = config.javaClass.getMethod("getCellBandwidthDownlinkKhz")
        (bwMethod.invoke(config) as? Int)?.takeIf { it > 0 }
    } catch (_: Exception) { null }
}
```

**Por que reflexão?** `PhysicalChannelConfig.getCellBandwidthDownlinkKhz()` em si sempre
foi um getter público (não é `@SystemApi`/oculto) — não há mudança de visibilidade a
contornar aí. O obstáculo real é outro: `TelephonyManager.getPhysicalChannelConfigs()`
(API 31+) e o `PhysicalChannelConfigListener` exigem a permissão `READ_PRECISE_PHONE_STATE`,
que é de assinatura/sistema — apps de terceiros como este (que só declaram
`READ_PHONE_STATE`) não conseguem obtê-la. Reflexão **não contorna** essa checagem de
permissão, que é feita no lado do sistema; na prática, a chamada tende a cair no `catch`
por `SecurityException` mesmo via reflexão. O ganho real da reflexão aqui é apenas evitar
depender de uma API pública recente (API 31+) no código-fonte, permitindo compilar contra
SDKs mais antigos — não é uma forma de obter dados que o sistema de outra forma negaria.

**`try/catch` silencioso:** Qualquer exceção (`NoSuchMethodException`, `SecurityException`,
`ClassCastException`) resulta em `null` — o app degrada graciosamente, mostrando "N/D" para
BW em vez de travar.

##### `parseCellInfo()` — Dispatch por tipo concreto

```kotlin
private fun parseCellInfo(info: CellInfo, op: String, bwKhz: Int?): CellSignalData? = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && info is CellInfoNr -> parseNr(info, op, bwKhz)
    info is CellInfoLte   -> parseLte(info, op, bwKhz)
    info is CellInfoWcdma -> parseWcdma(info, op)
    info is CellInfoGsm   -> parseGsm(info, op)
    info is CellInfoCdma  -> parseCdma(info, op)
    else                  -> null
}
```

`CellInfoNr` (5G) só existe a partir do Android 10 (API 29 = Q). A verificação de versão
**antes** do `is CellInfoNr` é obrigatória para evitar `ClassNotFoundException` em devices
com Android 9 ou anterior.

##### `parseNr()` — Exemplo de parse detalhado (5G NR)

```kotlin
val nci   = safeL { cid.nci.takeIf { it != Long.MAX_VALUE } }
val tac   = safeI { cid.tac.takeIf { it != Int.MAX_VALUE } }
val gnbId = nci?.let { it ushr 14 }      // gNB ID = NCI >> 14
val freq  = arfcn?.let { nrArfcnToMhz(it) }
```

**Valores sentinela:** O Android usa `Int.MAX_VALUE` e `Long.MAX_VALUE` para indicar
"campo não disponível". Se usássemos esses valores diretamente, exibiríamos `2147483647`
como TAC — sem sentido. A função helper `safeI` encapsula essa limpeza:

```kotlin
private fun safeI(block: () -> Int?): Int? = try {
    val v = block()
    if (v != null && v != Int.MAX_VALUE && v != Int.MIN_VALUE) v else null
} catch (_: Exception) { null }
```

**gNB ID do NCI:** O NCI (NR Cell Identity) é um número de 36 bits. Os 22 bits menos
significativos identificam a célula dentro do nó; os 14 bits mais significativos identificam
o gNB. `nci ushr 14` (unsigned shift right) extrai esses bits superiores.

Para LTE, a lógica é análoga: `CI shr 8` extrai o eNB ID (28-bit CI, onde os 8 bits
menos significativos identificam o setor, e os 20 bits restantes o eNB).

---

#### `FrequencyUtils.kt` — Matemática de conversão 3GPP

##### LTE EARFCN → Frequência

A especificação 3GPP TS 36.101 §5.7.3 define para cada banda LTE:
- `fDlLow`: frequência de downlink inicial em MHz
- `nDlLow`, `nDlHigh`: intervalo de EARFCNs de downlink
- `fUlLow`: frequência de uplink inicial (nula em TDD e SDL)

A fórmula é: `FDL = fDlLow + 0.1 × (EARFCN − nDlLow)`

```kotlin
fun earfcnToInfo(earfcn: Int): Triple<Int, Double, Double?>? {
    val b   = LTE_BANDS.firstOrNull { earfcn in it.nDlLow..it.nDlHigh } ?: return null
    val off = earfcn - b.nDlLow
    val fdl = b.fDlLow + 0.1 * off
    val ful = when {
        b.isSdl -> null          // SDL = Supplemental Downlink, sem uplink dedicado
        b.isTdd -> fdl           // TDD = Time Division Duplex, DL e UL compartilham frequência
        else    -> b.fUlLow + 0.1 * off
    }
    return Triple(b.band, fdl, ful)
}
```

**Exemplo prático:** EARFCN 1300 (Banda 3, 1800 MHz):
- `nDlLow = 1200`, `fDlLow = 1805.0`
- `off = 1300 − 1200 = 100`
- `fdl = 1805.0 + 0.1 × 100 = 1815.0 MHz`
- `ful = 1710.0 + 0.1 × 100 = 1720.0 MHz`

##### 5G NR ARFCN → Frequência

A 3GPP TS 38.104 §5.4.2.1 divide o espectro 5G NR em três regiões (FR1 sub-6GHz e FR2 mmWave):

```kotlin
fun nrArfcnToMhz(arfcn: Int): Double? = when {
    arfcn <= 599999   -> arfcn * 0.005                          // Δf = 5 kHz → ≤ 3 GHz
    arfcn <= 2016666  -> 3000.0   + (arfcn - 600000)  * 0.015  // Δf = 15 kHz → 3–24 GHz
    arfcn <= 3279165  -> 24250.08 + (arfcn - 2016667) * 0.060  // Δf = 60 kHz → >24 GHz (mmWave)
    else              -> null
}
```

A granularidade aumenta com a frequência porque o espectro mmWave tem canais mais largos.

##### GSM ARFCN → Frequência

GSM é a geração mais antiga e tem múltiplos planos de numeração ARFCN que se sobrepõem:

```kotlin
fun gsmArfcnToInfo(arfcn: Int): Pair<Double, Double>? = when (arfcn) {
    in 1..124    -> (935.2 + 0.2 * (arfcn - 1)).let { Pair(it, it - 45.0) }    // GSM 900
    0            -> Pair(935.0, 890.0)                                           // GSM 900 especial
    in 128..251  -> (869.2 + 0.2 * (arfcn - 128)).let { Pair(it, it - 45.0) }  // GSM 850
    in 512..885  -> (1805.2 + 0.2 * (arfcn - 512)).let { Pair(it, it - 95.0) } // DCS 1800
    in 975..1023 -> (935.0 + 0.2 * (arfcn - 1024)).let { Pair(it, it - 45.0) } // GSM 900 ext.
    else         -> null
}
```

O duplex no GSM é FDD (Frequency Division Duplex): UL = DL − 45 MHz (GSM 900) ou UL = DL − 95 MHz (DCS 1800).

##### Timing Advance → Distância

```kotlin
fun taToDistanceMeters(ta: Int): Double? =
    if (ta > 0 && ta != Int.MAX_VALUE) ta * 78.125 else null
```

O Timing Advance (TA) no LTE é a antecipação de transmissão para compensar o tempo de
propagação do sinal. 1 TA ≈ 78,125 m de distância one-way entre o device e a ERB. Portanto,
TA 10 indica que o dispositivo está a ~781 m da torre.

---

### 4.2 Camada de ViewModel — `ui/SignalViewModel`

```kotlin
class SignalViewModel(app: Application) : AndroidViewModel(app) {
    companion object {
        const val POLL_INTERVAL_MS = 1_000L
        const val HISTORY_CAPACITY = 90
    }

    private val repository = SignalRepository(app)

    private val _uiState   = MutableStateFlow<SignalUiState>(SignalUiState.Loading)
    val uiState: StateFlow<SignalUiState> = _uiState.asStateFlow()

    val history = SignalHistory(capacity = HISTORY_CAPACITY)

    private val _currentTab = MutableStateFlow(AppTab.SIGNAL)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    private var pollingJob: Job? = null
    ...
}
```

##### Por que `AndroidViewModel` em vez de `ViewModel`?

`AndroidViewModel` recebe o `Application` no construtor, o que permite ao `SignalRepository`
acessar o `Context` (necessário para `getSystemService(TELEPHONY_SERVICE)`). É a única
alternativa correta à injeção de dependência quando não há um DI framework (Hilt/Koin).

##### Loop de polling

```kotlin
fun startPolling() {
    if (pollingJob?.isActive == true) return   // guarda contra chamadas duplas
    pollingJob = viewModelScope.launch {
        while (isActive) {
            refreshOnce()
            delay(POLL_INTERVAL_MS)
        }
    }
}
```

**`viewModelScope`:** A coroutine é cancelada automaticamente quando o ViewModel é destruído
(`onCleared()`). Não é preciso gerenciar o lifecycle manualmente.

**`isActive` no `while`:** Ao cancelar a coroutine (ex.: via `stopPolling()`), a próxima
verificação de `isActive` retorna `false` e o loop termina. O `delay()` também é cooperativo
com cancelamento — se cancelado durante o delay, lança `CancellationException`.

**Guarda contra chamadas duplas:** `if (pollingJob?.isActive == true) return` evita criar
dois loops em paralelo se `startPolling` for chamado duas vezes (ex.: rotação rápida).

##### `refreshOnce()`

```kotlin
private suspend fun refreshOnce() {
    try {
        val cells   = repository.readCells()
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
```

**Seleção da célula primária:** Primeiro tenta a célula `isRegistered` (conectada). Se não
houver nenhuma registrada (pode acontecer em transição de rede), usa a primeira da lista —
que foi ordenada por `isRegistered` e `dbm` no repositório.

**Filtro de valores inválidos no histórico:** `Int.MIN_VALUE` é o valor sentinela do Android
para "sem leitura"; `0` não é um dBm válido. Adicionar esses valores ao histórico
distorceria o gráfico e as estatísticas.

**`_uiState.value =` (não `emit`):** `MutableStateFlow` tem uma propriedade `value` que
pode ser atribuída de qualquer contexto (não só de uma coroutine). Internamente é thread-safe.

---

### 4.3 Camada de UI — `ui/screens/` e `ui/components/`

#### `MainScreen.kt` — Orquestrador de permissões e navegação

##### Gerenciamento de permissões

```kotlin
val launcher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
) { results ->
    if (results.values.any { it }) vm.startPolling() else vm.onPermissionDenied()
}

LaunchedEffect(Unit) {
    if (hasPerms()) vm.startPolling() else launcher.launch(perms)
}
```

**`rememberLauncherForActivityResult`:** Registra um "Activity Result Callback" de forma
declarativa no Compose. A restrição de registrar o launcher antes do estado `STARTED`
vem da própria biblioteca AndroidX Activity/Fragment (`ActivityResultRegistry`), não é
uma exigência do Android 12 como versão de plataforma — `rememberLauncherForActivityResult`
garante isso ao registrar o launcher na composição, antes de qualquer `onStart()`.

**`LaunchedEffect(Unit)`:** Roda uma única vez quando o composable entra em composição
(analogia: `componentDidMount` no React). O parâmetro `Unit` é a *key* — se ela não mudar,
o efeito não roda novamente.

**`DisposableEffect(Unit)`:** Roda o cleanup (`vm.stopPolling()`) quando o composable sai
de composição. Garante que o polling para quando a tela é destruída.

##### Navegação por abas com animação direcional

```kotlin
AnimatedContent(
    targetState = currentTab,
    transitionSpec = {
        val d = if (targetState.ordinal > initialState.ordinal) 1 else -1
        slideInHorizontally { d * it } + fadeIn() togetherWith
            slideOutHorizontally { -d * it } + fadeOut()
    },
    label = "tabs",
) { tab -> ... }
```

A direção do slide é calculada comparando os `ordinal` das abas (`SIGNAL=0`, `HISTORY=1`,
`INFO=2`). Ir de SIGNAL para INFO desliza para a esquerda; voltar desliza para a direita.
Isso cria uma sensação de profundidade espacial na navegação.

##### `LiveDot` — Indicador de atualização pulsante

```kotlin
val pulse by rememberInfiniteTransition(label = "dot").animateFloat(
    initialValue = 0.35f,
    targetValue  = 1.0f,
    animationSpec = infiniteRepeatable(
        animation   = tween(1_100, easing = EaseInOutSine),
        repeatMode  = RepeatMode.Reverse,
    ),
    label = "dot_a",
)
```

Anima o `alpha` do ponto verde entre 35% e 100% com easing sinusoidal — suave nas bordas,
mais rápido no meio — criando um efeito de "respiração". `RepeatMode.Reverse` faz ir e
voltar sem salto abrupto.

---

#### `SignalGauge.kt` — Gauge em Canvas

O gauge é desenhado em múltiplas camadas via a API `Canvas` do Compose, do fundo para a frente:

```
Camada 1: Trilha de fundo (arco escuro, 250°, largura 14dp)
Camada 2: Glow pulsante (arco da cor do sinal, largura 2.8× a normal, alpha animado)
Camada 3: Arco principal (sólido, preenchido proporcionalmente ao sinal)
Camada 4: Brilho interno (arco menor, alpha 55%, simula luz refletida)
Camada 5: Ponto luminoso na ponta do arco (círculo branco com halo)
Camada 6: Ticks de escala (5 marcações radiais em 0%, 25%, 50%, 75%, 100%)
```

##### Geometria do arco

```kotlin
val startAngle  = 145f   // começa no canto inferior esquerdo
val totalSweep  = 250f   // varre 250°, deixando 110° de abertura embaixo
val filledSweep = totalSweep * animProgress
```

Os ângulos no Canvas do Android são medidos em sentido horário a partir do eixo X positivo
(direita). `145°` posiciona o início do arco no canto inferior esquerdo.

##### Ponto luminoso na ponta

```kotlin
val tipAngle = Math.toRadians((startAngle + filledSweep).toDouble())
val tipR     = outerR - trackW / 2f
val tip      = Offset(cx + tipR * cos(tipAngle).toFloat(), cy + tipR * sin(tipAngle).toFloat())
drawCircle(animColor, trackW * 0.55f, tip)
drawCircle(Color.White.copy(alpha = 0.6f), trackW * 0.22f, tip)
```

A posição da ponta é calculada com trigonometria: `(cx + r·cos(θ), cy + r·sin(θ))`.
Um círculo colorido maior dá o brilho; um círculo branco menor no centro cria a ilusão
de reflexo de luz.

---

#### `SignalChart.kt` — Gráfico de linha em Canvas

##### Curva Bézier cúbica

Em vez de segmentos de linha retos (que criam um visual quebrado), o gráfico usa splines
Bézier cúbicas:

```kotlin
else {
    val px  = xOf(ri - 1); val py = yOf(visible[idx - 1].dbm)
    val cp  = (x - px) * 0.4f    // ponto de controle = 40% da distância horizontal
    cubicTo(px + cp, py, x - cp, y, x, y)
}
```

`cubicTo(x1, y1, x2, y2, x, y)` define:
- `(x1, y1)` = ponto de controle saindo do ponto anterior (tangente de saída)
- `(x2, y2)` = ponto de controle chegando no ponto atual (tangente de chegada)
- `(x, y)` = ponto destino

Com o fator 0.4, os pontos de controle ficam a 40% do caminho horizontal — curva suave
sem overshooting (não sobe acima dos pontos).

##### Área preenchida com gradiente

```kotlin
drawPath(Path().apply {
    addPath(linePath)
    lineTo(xOf(off + visible.size - 1), padT + chartH)   // desce até a base direita
    lineTo(xOf(off), padT + chartH)                       // vai para a base esquerda
    close()
}, Brush.verticalGradient(
    listOf(AccentBlue.copy(alpha = 0.25f), Color.Transparent),
    padT, padT + chartH
))
```

O gradiente vai de `AccentBlue@25%` no topo para `Transparent` na base — cria profundidade
visual sem peso excessivo.

---

#### `CellIdentifiersCard.kt` — Grade de identificadores

O card organiza os identificadores em 4 grupos temáticos de 3 colunas:

```
┌─────────────────────────────────────────────────────────────┐
│  IDENTIFICADORES DE CÉLULA                                  │
├──────────────────────────────────────────────────────────── │
│  Identidade de área                                         │
│  [ TAC/LAC ]    [ eNB/gNB/CI ]   [ Cell ID ]               │
├──────────────────────────────────────────────────────────── │
│  Canal de rádio                                             │
│  [ PCI/PSC ]    [ EARFCN ]        [ Banda ]                 │
├──────────────────────────────────────────────────────────── │
│  Frequência / largura de banda                              │
│  [ FDL ]        [ FUL ]           [ BW ]                    │
├──────────────────────────────────────────────────────────── │
│  Timing Advance (só LTE ou quando disponível)               │
│  [ TA ]         [ Distância ]     [      ]                  │
└─────────────────────────────────────────────────────────────┘
```

Cada `IdItem` usa `AnimatedContent` para animar o valor quando ele muda:

```kotlin
AnimatedContent(
    targetState    = display,
    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) },
    label          = "id_$label",
) { v -> Text(v, ...) }
```

O fade rápido (220ms entrada, 140ms saída) torna as atualizações perceptíveis sem serem
distrativas.

---

#### `Components.kt` — Primitivos reutilizáveis

##### `MetricCard` com animação

```kotlin
AnimatedContent(value, transitionSpec = {
    slideInVertically { it / 2 } + fadeIn() togetherWith
        slideOutVertically { -it / 2 } + fadeOut()
}, label = "mc_$label") { v ->
    Text(v, ...)
}
```

O valor "entra subindo" e o anterior "sai subindo" — criando a ilusão de que o número
novo empurrou o antigo para cima. A entrada vem de baixo (`it / 2` = metade da altura)
para um efeito mais sutil.

##### `SignalBars`

```kotlin
repeat(barCount) { idx ->
    Box(modifier = Modifier
        .width(barWidth)
        .height(maxHeight * ((idx + 1f) / barCount))   // altura proporcional ao índice
        .background(if (idx < level) color else color.copy(alpha = 0.18f)))
}
```

Barras de altura crescente, as preenchidas com cor sólida e as vazias com 18% de opacidade.
Alinhadas na base via `verticalAlignment = Alignment.Bottom`.

---

### 4.4 Tema — `ui/theme/`

#### `Color.kt` — Paleta semântica

A paleta é organizada por função, não por nome de cor:

```kotlin
// Backgrounds (do mais escuro ao mais claro)
val BgDeep    = Color(0xFF060810)   // fundo principal
val BgMid     = Color(0xFF0B0F1C)   // TopBar, BottomBar
val BgCard    = Color(0xFF141E35)   // cards
val BgCardAlt = Color(0xFF19243E)   // fundo de células alternativas

// Qualidade (semáforo visual)
val QualityExcel    = Color(0xFF22D36A)   // verde
val QualityGood     = Color(0xFF7ED348)   // verde-amarelo
val QualityFair     = Color(0xFFF5C020)   // amarelo
val QualityPoor     = Color(0xFFF97020)   // laranja
val QualityVeryPoor = Color(0xFFEF4545)   // vermelho
```

**Por que semântico?** Se amanhã a paleta mudar de azul escuro para cinza escuro, basta
alterar `BgDeep` em um lugar. Se usássemos `val Blue900 = Color(0xFF060810)`, cada uso
precisaria ser rastreado manualmente.

#### `Theme.kt` — Dark Color Scheme

```kotlin
private val DarkColorScheme = darkColorScheme(
    primary           = AccentBlue,
    background        = BgDeep,
    surface           = BgSurface,
    surfaceVariant    = BgCard,
    outline           = BorderDefault,
    outlineVariant    = BorderSubtle,
    error             = QualityVeryPoor,
    ...
)
```

Mapear as cores semânticas da paleta às roles do Material3 permite que componentes padrão
(`Button`, `TopAppBar`) herdem as cores corretas sem precisar especificá-las em cada uso.

---

## 5. Fluxo de Execução Completo

### Inicialização (primeira abertura)

```
1. Android cria MainActivity
   └─ enableEdgeToEdge()               → remove barras de sistema do layout
   └─ setContent { SignalPlusTheme }  → inicializa Compose runtime

2. Compose compõe MainScreen()
   └─ viewModel() cria SignalViewModel (singleton no escopo da Activity)
   └─ _uiState = Loading
   └─ UI exibe LoadingScreen (spinner)

3. LaunchedEffect(Unit) verifica permissões
   ├─ SEM permissão → launcher.launch(perms) → sistema exibe diálogo de permissão
   │   ├─ NEGADO → vm.onPermissionDenied() → _uiState = PermissionRequired
   │   └─ CONCEDIDO → vm.startPolling()
   └─ COM permissão → vm.startPolling()
```

### Loop de polling (estado normal)

```
A cada 1 segundo:
1. refreshOnce() é chamado pela coroutine no viewModelScope

2. withContext(Dispatchers.IO):
   a. telephony.networkOperatorName → "Vivo"
   b. readPrimaryBwKhz() via reflexão → 20000 (20 MHz) ou null
   c. telephony.allCellInfo → List<CellInfo> com N células

3. Para cada CellInfo em allCells:
   a. parseCellInfo() identifica o tipo concreto
   b. parseNr/parseLte/parseWcdma/parseGsm/parseCdma extrai campos
   c. safeI/safeL filtra valores sentinela
   d. FrequencyUtils converte ARFCN → FDL/FUL/Banda
   e. Retorna CellSignalData

4. Lista ordenada: isRegistered > dbm

5. De volta no Main dispatcher:
   a. history.add(SignalPoint) se dbm válido
   b. _uiState.value = Success(primary, allCells)

6. Compose detecta mudança no StateFlow
   └─ MainScreen recompõe
       └─ SignalTabContent recompõe
           ├─ SignalGauge: animateFloatAsState anima para novo progresso (900ms)
           ├─ CellIdentifiersCard: AnimatedContent faz fade nos valores novos
           └─ MetricCards: slideInVertically nos valores novos
```

### Troca de aba

```
Usuário toca em "Histórico"
→ vm.selectTab(AppTab.HISTORY)
→ _currentTab.value = HISTORY
→ currentTab StateFlow emite nova valor
→ MainScreen recompõe
→ AnimatedContent detecta mudança de SIGNAL → HISTORY
→ slideOutHorizontally (SIGNAL sai para a esquerda)
→ slideInHorizontally (HISTORY entra da direita)
→ HistoryScreen começa a compor
→ history.points.collectAsState() → UI tem acesso aos 90 pontos acumulados
→ SignalChart desenha o gráfico com os pontos disponíveis
```

---

## 6. Tecnologias e Dependências

### Android SDK / TelephonyManager

**O que é:** API do sistema Android para acesso a dados de rádio celular.

**Principais classes usadas:**
- `TelephonyManager.getAllCellInfo()` → lista de `CellInfo` de todas as células visíveis
- `CellInfoNr`, `CellInfoLte`, `CellInfoWcdma`, `CellInfoGsm`, `CellInfoCdma` → subclasses concretas
- `CellSignalStrengthLte.rsrp`, `rsrq`, `rssnr`, `cqi` → métricas LTE
- `CellSignalStrengthNr.ssRsrp`, `ssRsrq`, `ssSinr` → métricas 5G NR

**Por que não usar outras bibliotecas?** Não existe alternativa — só o TelephonyManager tem
acesso ao driver do modem. Bibliotecas de terceiros (Signal/Network libraries) usam o mesmo
TelephonyManager internamente.

### Jetpack Compose + Material3

**O que é:** UI toolkit declarativo da Google para Android. Em vez de manipular views
imperativas (como no XML), você descreve como a UI deve parecer dado um estado.

**Por que Compose?** Para este app, Compose tem vantagens concretas:
- **Animações:** `animateFloatAsState`, `AnimatedContent`, `rememberInfiniteTransition` são
  triviais no Compose e dolorosas com View system.
- **StateFlow → UI:** `collectAsStateWithLifecycle()` converte um Flow em estado Compose
  com cancelamento automático quando o componente sai de composição.
- **Canvas:** A API de Canvas no Compose é mais ergonômica e integrada ao sistema de layout.

### Kotlin Coroutines + StateFlow

**O que são:** Coroutines são uma abstração de concorrência cooperativa. StateFlow é um
stream de dados reativo e thread-safe.

**Por que StateFlow e não LiveData?** `StateFlow` é uma API Kotlin pura (sem dependência
do Android runtime), tem suporte nativo a coroutines, e tem um valor inicial obrigatório
(sem o estado `null` problemático do LiveData). `collectAsStateWithLifecycle()` integra
StateFlow ao ciclo de vida da UI diretamente.

**Por que não RxJava?** Kotlin Coroutines + Flow resolve os mesmos problemas com sintaxe
mais simples e sem a sobrecarga de operadores reativos complexos para um caso de uso de
polling simples.

### Gradle Version Catalog (`libs.versions.toml`)

O projeto usa o **Version Catalog** do Gradle — um único arquivo que centraliza todas as
versões de dependências:

```toml
[versions]
kotlin = "2.4.0"
compose-bom = "2026.06.00"

[libraries]
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
```

Isso evita inconsistências entre módulos e facilita atualizações — basta mudar a versão em
um lugar.

### R8 / ProGuard (release)

Na build de release, R8 faz três otimizações:
1. **Minificação de código:** remove classes e métodos não usados.
2. **Ofuscação:** renomeia classes e métodos para nomes curtos (dificulta engenharia reversa).
3. **Otimização de bytecode:** inline de métodos, remoção de código morto.

As regras em `proguard-rules.pro` mantêm os data classes (`com.example.ls.data.**`),
as APIs de TelephonyManager (necessárias para a reflexão) e os metadados Kotlin (necessários
para `data class` com named parameters).

---

## 7. Decisões de Design

### Polling em vez de escuta por eventos

O TelephonyManager tem `PhoneStateListener`/`TelephonyCallback` para receber eventos quando
o sinal muda. Por que então fazer polling de 1 em 1 segundo?

**Razão:** `PhoneStateListener` notifica mudanças de estado de chamada e tipo de rede, mas
**não** atualiza as métricas de sinal em tempo real com frequência adequada para diagnóstico.
`getAllCellInfo()` retorna um snapshot do estado atual do modem a cada chamada. Para
visualização ao vivo, polling com intervalo fixo é mais previsível e simples.

### Imutabilidade dos modelos de dados

Todos os modelos em `data/` são `data class` ou `object` imutáveis. Isso tem duas
consequências práticas:

1. **StateFlow funciona corretamente:** StateFlow compara por igualdade estrutural. Se
   `CellSignalData` fosse mutável e você modificasse um campo, o StateFlow não notificaria
   a UI (mesma referência, mesma aparência). Com data class, cada atualização é um objeto
   novo.

2. **Thread-safety gratuita:** Objetos imutáveis podem ser lidos de qualquer thread sem
   sincronização. Como `readCells()` roda em `Dispatchers.IO` e a UI está na Main thread,
   isso elimina race conditions.

### Nullable fields vs. tipos sealed para campos de hardware

Os campos de `CellNetworkInfo` são `Int?` e `Double?` em vez de um sealed class
`HardwareField<T>`. Isso é uma escolha deliberada de simplicidade — o `IdItem` já trata
o caso `null` como "N/D", e criar uma hierarquia de tipos para representar "não disponível"
seria over-engineering para esse contexto.

### Reflexão para `getPhysicalChannelConfigs`

Usar reflexão quebra a type-safety em tempo de compilação — se a API mudar, saberemos
apenas em runtime. O `try/catch` silencioso é o mecanismo de proteção: qualquer mudança
de API resulta em `null` (campo não disponível), sem crash.

O bloqueio real, porém, não é de visibilidade de API: `getPhysicalChannelConfigs()`
(público desde a API 31) exige a permissão de assinatura/sistema
`READ_PRECISE_PHONE_STATE`, que este app não declara (só tem `READ_PHONE_STATE`). Reflexão
não contorna checagem de permissão — ela é feita pelo sistema, não pelo compilador — então
a chamada tende a lançar `SecurityException` e cair no `catch` de qualquer forma. O valor
prático da reflexão aqui é permitir compilar contra `compileSdk` mais antigos sem depender
da API 31 diretamente no código-fonte, não obter dados que o sistema negaria de outra forma.

### Separação entre `bwKhz` da célula registrada vs. outras

```kotlin
bwKhz = if (info.isRegistered) bwKhz else null,
```

A largura de banda obtida via `PhysicalChannelConfig` é do canal **ativo** — da conexão
atual. Não faz sentido atribuir esse valor a células vizinhas (não conectadas). Por isso
`bwKhz` só é preenchido para a célula registrada.

---

## 8. Boas Práticas e Possíveis Melhorias

### Boas práticas aplicadas

**Defensive null handling:** `safeI` e `safeL` encapsulam toda limpeza de valores sentinela
em um ponto centralizado. Se o Android mudar os valores sentinela num SDK futuro, há um
único lugar para corrigir.

**`asStateFlow()`:** Expor `StateFlow` público em vez de `MutableStateFlow` previne que
código externo modifique o estado diretamente — apenas o ViewModel pode publicar novos valores.

**`collectAsStateWithLifecycle()`:** Cancela a coleta do Flow quando a UI entra em background
(onStop), poupando recursos. Usar `collectAsState()` sem o `WithLifecycle` coletaria mesmo
com a tela invisível.

**Composables sem estado (stateless):** Componentes como `SignalGauge`, `MetricCard` e
`CellIdentifiersCard` recebem apenas dados como parâmetros — sem acessar ViewModel ou
contexto. Isso os torna testáveis e previsíveis.

### Possíveis melhorias

**Injeção de dependência:** `SignalViewModel` instancia `SignalRepository` diretamente.
Em um projeto maior, usar Hilt permitiria trocar o repositório por um fake em testes, sem
alterar o ViewModel.

**Testes unitários para `FrequencyUtils`:** As funções de conversão ARFCN são lógica pura
sem dependências — candidatas perfeitas para testes paramétricos. Ex.:
```kotlin
@Test fun `EARFCN 1300 deve retornar banda 3 e FDL 1815 MHz`() {
    val result = earfcnToInfo(1300)
    assertEquals(3, result?.first)
    assertEquals(1815.0, result?.second, 0.001)
}
```

**Persistência do histórico:** O `SignalHistory` é mantido em memória. Ao fechar o app,
o histórico é perdido. Uma melhoria seria serializar os `SignalPoint` em um banco Room ou
DataStore.

**Configuração do intervalo de polling:** O intervalo de 1 segundo é fixo em código. Uma
preferência de usuário (1s / 2s / 5s) reduziria o consumo de bateria para usos prolongados.

**Tratamento de `SecurityException` com feedback:** Atualmente, `SecurityException` no
repositório retorna lista vazia silenciosamente. Seria mais informativo propagá-la até o
`SignalUiState.PermissionRequired`.

---

## 9. Como Recriar o Projeto do Zero

Esta seção guia como reproduzir o app partindo do zero, ensinando os conceitos junto com os passos.

### Passo 1 — Criar o projeto no Android Studio

Novo projeto → **Empty Activity** → Linguagem: Kotlin, Min SDK: 26, Build configuration: Kotlin DSL.

O template gera `MainActivity` com `setContent {}` e as dependências básicas do Compose.

### Passo 2 — Definir os modelos de dados

Antes de escrever qualquer linha de UI, defina os contratos:
- **O que a UI precisa exibir?** → `CellSignalData` com `dbm`, `networkType`, `operator`, `extraMetrics`, `netInfo`
- **Em quantos estados a tela pode estar?** → 4 (Loading, PermissionRequired, Success, Error) → `SignalUiState`
- **Que tipos de rede existem?** → `NetworkType` enum

*Princípio: defina as interfaces antes das implementações.*

### Passo 3 — Implementar `FrequencyUtils`

Funções puras sem dependências. Pesquise a 3GPP TS 36.101 §5.7.3 (LTE), 3GPP TS 38.104 §5.4.2.1 (NR)
e 3GPP TS 45.005 §2 (GSM). Copie as tabelas de banda e implemente as fórmulas matemáticas.

Teste estas funções imediatamente com casos conhecidos antes de continuar.

### Passo 4 — Implementar `SignalRepository`

```kotlin
class SignalRepository(private val context: Context) {
    suspend fun readCells(): List<CellSignalData> = withContext(Dispatchers.IO) {
        // 1. telephony.allCellInfo
        // 2. mapNotNull { parseCellInfo(it) }
        // 3. sort
    }
}
```

Implemente um `parseXxx` por tecnologia. Use `safeI`/`safeL` para blindar valores sentinela
desde o início — não tente tratar nulos na UI.

### Passo 5 — Implementar o ViewModel

```kotlin
class SignalViewModel(app: Application) : AndroidViewModel(app) {
    private val _uiState = MutableStateFlow<SignalUiState>(SignalUiState.Loading)
    val uiState = _uiState.asStateFlow()

    fun startPolling() { /* viewModelScope.launch { while(isActive) { ... } } */ }
}
```

**Conceito chave:** O ViewModel é o único ponto de mutação do estado. A UI apenas observa.

### Passo 6 — Construir a UI com Compose

Ordem sugerida:
1. `MainScreen`: scaffold com `TopAppBar` + `NavigationBar` + tratamento de estado
2. `LoadingScreen`, `PermissionScreen`, `ErrorScreen`: estados simples sem dados
3. `SignalTabContent`: `LazyColumn` com cards simples primeiro, sem animação
4. Adicionar `SignalGauge` com Canvas básico (só o arco, sem glow e animações)
5. Adicionar animações progressivamente

**Regra:** Faça funcionar primeiro, depois faça bonito.

### Passo 7 — Adicionar permissões e o loop de lifecycle

```kotlin
LaunchedEffect(Unit) {
    if (hasPerms()) vm.startPolling() else launcher.launch(perms)
}
DisposableEffect(Unit) { onDispose { vm.stopPolling() } }
```

Teste no dispositivo físico — emuladores retornam dados simulados que mascaram problemas reais.

### Passo 8 — Polir animações e tema

Crie `Color.kt` com paleta semântica. Configure `darkColorScheme` em `Theme.kt`.
Adicione animações progressivas:
1. `animateFloatAsState` no gauge
2. `animateColorAsState` para transições de qualidade
3. `AnimatedContent` nos valores das métricas
4. `AnimatedContent` na navegação entre abas

### Conceitos chave para internalizar

| Conceito | Onde ver no projeto |
|----------|-------------------|
| StateFlow reativo | `SignalViewModel._uiState`, `SignalHistory._points` |
| Coroutine + Dispatchers | `SignalRepository.readCells()` |
| Sealed class como estado | `SignalUiState` e o `when` em `MainScreen` |
| Imutabilidade | Todos os `data class` em `data/` |
| Canvas API | `SignalGauge.kt`, `SignalChart.kt` |
| AnimatedContent | `MetricCard`, `IdItem`, navegação entre abas |
| LaunchedEffect vs DisposableEffect | Gerenciamento de permissões em `MainScreen` |
| Valores sentinela do Android | `safeI`, `safeL`, filtros em `refreshOnce()` |
| Reflexão para APIs restritas | `readPrimaryBwKhz()` |
| Version Catalog | `gradle/libs.versions.toml` |

---

*Documentação gerada em 2026-04-20. Versão do app: 1.0.*
