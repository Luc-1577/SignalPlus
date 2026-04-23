# Módulo 05 · Aula 02 — Camada de Dados: Modelos, Histórico e Matemática 3GPP

> **Objetivo:** Implementar toda a camada de dados sem nenhuma dependência de Android ou UI.

---

## Princípio desta camada

A camada de dados deve ser **completamente independente do Android**:

```kotlin
// ✅ OK na camada de dados
import kotlin.math.*
import kotlinx.coroutines.flow.*

// ❌ Proibido na camada de dados
import android.content.Context
import androidx.compose.ui.*    // Exceto Color — aceitável para enums com cor
```

Isso garante que você pode testar toda a lógica de negócio sem um emulador Android.

---

## Arquivo 1: `SignaData.kt`

Crie `app/src/main/java/com/example/ls/data/SignaData.kt`:

```kotlin
package com.example.ls.data

import androidx.compose.ui.graphics.Color
import com.example.ls.ui.theme.*

// ════════════════════════════════════════════════════════
// ENUMS
// ════════════════════════════════════════════════════════

enum class NetworkType(
    val label      : String,
    val generation : String,
    val badgeColor : Color,
) {
    GSM_2G ("GSM",   "2G", Net2G),
    CDMA   ("CDMA",  "2G", Net2G),
    WCDMA  ("WCDMA", "3G", Net3G),
    HSPA   ("HSPA+", "3G", Net3G),
    LTE    ("LTE",   "4G", Net4G),
    NR_5G  ("NR",    "5G", Net5G),
    UNKNOWN("?",     "?",  QualityNone),
}

enum class SignalQuality(
    val label : String,
    val color : Color,
    val bars  : Int,
) {
    EXCELLENT("Excelente",   QualityExcel,    5),
    GOOD     ("Bom",         QualityGood,     4),
    FAIR     ("Regular",     QualityFair,     3),
    POOR     ("Fraco",       QualityPoor,     2),
    VERY_POOR("Muito Fraco", QualityVeryPoor, 1),
    NO_SIGNAL("Sem Sinal",   QualityNone,     0),
}

// ════════════════════════════════════════════════════════
// MODELOS DE DADOS
// ════════════════════════════════════════════════════════

data class CellNetworkInfo(
    val tac           : Int?    = null,
    val enbId         : Long?   = null,
    val cellId        : Long?   = null,
    val pci           : Int?    = null,
    val arfcn         : Int?    = null,
    val fdlMhz        : Double? = null,
    val fulMhz        : Double? = null,
    val band          : Int?    = null,
    val bwKhz         : Int?    = null,
    val timingAdvance : Int?    = null,
    // Labels adaptativos por tecnologia:
    val tacLabel      : String  = "TAC",
    val nodeIdLabel   : String  = "eNB",
    val arfcnLabel    : String  = "EARFCN",
    val pciLabel      : String  = "PCI",
)

data class CellSignalData(
    val networkType  : NetworkType,
    val dbm          : Int,
    val asuLevel     : Int,
    val level        : Int,
    val isRegistered : Boolean,
    val operator     : String,
    val extraMetrics : Map<String, String> = emptyMap(),
    val netInfo      : CellNetworkInfo     = CellNetworkInfo(),
)

// ════════════════════════════════════════════════════════
// ESTADO DA UI
// ════════════════════════════════════════════════════════

sealed class SignalUiState {
    object Loading            : SignalUiState()
    object PermissionRequired : SignalUiState()
    data class Success(
        val primary   : CellSignalData?,
        val allCells  : List<CellSignalData>,
        val updatedAt : Long = System.currentTimeMillis(),
    ) : SignalUiState()
    data class Error(val message: String) : SignalUiState()
}

// ════════════════════════════════════════════════════════
// FUNÇÕES PURAS (sem side effects)
// ════════════════════════════════════════════════════════

fun signalQuality(dbm: Int, type: NetworkType): SignalQuality {
    if (dbm == Int.MIN_VALUE || dbm == Int.MAX_VALUE || dbm == 0)
        return SignalQuality.NO_SIGNAL
    return when (type) {
        NetworkType.NR_5G -> when {
            dbm >= -80  -> SignalQuality.EXCELLENT
            dbm >= -90  -> SignalQuality.GOOD
            dbm >= -100 -> SignalQuality.FAIR
            dbm >= -110 -> SignalQuality.POOR
            else        -> SignalQuality.VERY_POOR
        }
        NetworkType.LTE -> when {
            dbm >= -80  -> SignalQuality.EXCELLENT
            dbm >= -90  -> SignalQuality.GOOD
            dbm >= -100 -> SignalQuality.FAIR
            dbm >= -110 -> SignalQuality.POOR
            else        -> SignalQuality.VERY_POOR
        }
        NetworkType.WCDMA, NetworkType.HSPA -> when {
            dbm >= -75  -> SignalQuality.EXCELLENT
            dbm >= -85  -> SignalQuality.GOOD
            dbm >= -95  -> SignalQuality.FAIR
            dbm >= -105 -> SignalQuality.POOR
            else        -> SignalQuality.VERY_POOR
        }
        else -> when {
            dbm >= -70  -> SignalQuality.EXCELLENT
            dbm >= -80  -> SignalQuality.GOOD
            dbm >= -90  -> SignalQuality.FAIR
            dbm >= -100 -> SignalQuality.POOR
            else        -> SignalQuality.VERY_POOR
        }
    }
}

fun signalProgress(dbm: Int, type: NetworkType): Float {
    if (dbm == Int.MIN_VALUE || dbm == Int.MAX_VALUE || dbm == 0) return 0f
    val (min, max) = when (type) {
        NetworkType.NR_5G                   -> -130f to -44f
        NetworkType.LTE                     -> -130f to -44f
        NetworkType.WCDMA, NetworkType.HSPA -> -120f to -25f
        else                                -> -110f to -51f
    }
    return ((dbm - min) / (max - min)).coerceIn(0f, 1f)
}
```

---

## Arquivo 2: `FrequencyUtils.kt`

Crie `app/src/main/java/com/example/ls/data/FrequencyUtils.kt`:

```kotlin
package com.example.ls.data

// ════════════════════════════════════════════════════════
// TABELA DE BANDAS LTE (3GPP TS 36.101 §5.7.3)
// ════════════════════════════════════════════════════════

private data class LteBand(
    val band   : Int,
    val fDlLow : Double,   // frequência DL inicial em MHz
    val fUlLow : Double,   // frequência UL inicial em MHz (0 se SDL/TDD)
    val nDlLow : Int,      // EARFCN DL mínimo
    val nDlHigh: Int,      // EARFCN DL máximo
    val isTdd  : Boolean = false,  // Time Division Duplex (DL = UL)
    val isSdl  : Boolean = false,  // Supplemental Downlink (sem UL dedicado)
)

private val LTE_BANDS = listOf(
    LteBand( 1, 2110.0, 1920.0,  0,     599),
    LteBand( 2, 1930.0, 1850.0,  600,   1199),
    LteBand( 3, 1805.0, 1710.0,  1200,  1949),   // Band 3 — 1800 MHz (muito usado no Brasil)
    LteBand( 4, 2110.0, 1710.0,  1950,  2399),
    LteBand( 5,  869.0,  824.0,  2400,  2649),
    LteBand( 7, 2620.0, 2500.0,  2750,  3449),   // Band 7 — 2600 MHz
    LteBand( 8,  925.0,  880.0,  3450,  3799),
    LteBand(28,  758.0,  703.0,  9210,  9659),   // Band 28 — 700 MHz (principal no Brasil!)
    LteBand(41, 2496.0, 2496.0, 39650, 41589, isTdd = true),  // Band 41 — 2500 MHz TDD
    // ... outras bandas
)

// ════════════════════════════════════════════════════════
// CONVERSÕES DE FREQUÊNCIA
// ════════════════════════════════════════════════════════

/**
 * Converte EARFCN (LTE) para frequências.
 * Retorna Triple(banda, FDL_MHz, FUL_MHz?) — FUL é null em SDL; igual a FDL em TDD.
 */
fun earfcnToInfo(earfcn: Int): Triple<Int, Double, Double?>? {
    val b   = LTE_BANDS.firstOrNull { earfcn in it.nDlLow..it.nDlHigh } ?: return null
    val off = earfcn - b.nDlLow
    val fdl = b.fDlLow + 0.1 * off          // cada EARFCN = 100 kHz de separação
    val ful = when {
        b.isSdl -> null
        b.isTdd -> fdl
        else    -> b.fUlLow + 0.1 * off
    }
    return Triple(b.band, fdl, ful)
}

/**
 * Converte NRARFCN (5G NR) para frequência em MHz.
 * (3GPP TS 38.104 §5.4.2.1)
 */
fun nrArfcnToMhz(arfcn: Int): Double? = when {
    arfcn < 0        -> null
    arfcn <= 599999  -> arfcn * 0.005                             // ΔF = 5 kHz
    arfcn <= 2016666 -> 3000.0   + (arfcn - 600000) * 0.015      // ΔF = 15 kHz
    arfcn <= 3279165 -> 24250.08 + (arfcn - 2016667) * 0.060     // ΔF = 60 kHz (mmWave)
    else             -> null
}

/**
 * Converte UARFCN (WCDMA/3G) para FDL em MHz.
 * Fórmula simples: FDL = UARFCN × 0.2
 */
fun uarfcnToMhz(uarfcn: Int): Double? =
    if (uarfcn > 0) uarfcn * 0.2 else null

/**
 * Converte ARFCN (GSM/2G) para (FDL, FUL) em MHz.
 * (3GPP TS 45.005 §2)
 */
fun gsmArfcnToInfo(arfcn: Int): Pair<Double, Double>? = when (arfcn) {
    in 1..124    -> (935.2 + 0.2 * (arfcn - 1)).let { Pair(it, it - 45.0) }
    0            -> Pair(935.0, 890.0)
    in 128..251  -> (869.2 + 0.2 * (arfcn - 128)).let { Pair(it, it - 45.0) }
    in 512..885  -> (1805.2 + 0.2 * (arfcn - 512)).let { Pair(it, it - 95.0) }
    in 975..1023 -> (935.2 + 0.2 * (arfcn - 1024)).let { Pair(it, it - 45.0) }
    else         -> null
}

/**
 * Calcula a distância aproximada à ERB via Timing Advance do LTE.
 * 1 TA ≈ 78.125 m one-way.
 */
fun taToDistanceMeters(ta: Int): Double? =
    if (ta > 0 && ta != Int.MAX_VALUE) ta * 78.125 else null

// ════════════════════════════════════════════════════════
// FORMATADORES
// ════════════════════════════════════════════════════════

fun formatMhz(mhz: Double): String =
    if (mhz >= 1000.0) "${"%.3f".format(mhz / 1000.0)} GHz"
    else               "${"%.1f".format(mhz)} MHz"

fun formatBwKhz(khz: Int): String =
    if (khz >= 1000) "${"%.0f".format(khz / 1000.0)} MHz" else "$khz kHz"

fun formatDistance(meters: Double): String =
    if (meters < 1000.0) "~${meters.toInt()} m"
    else "~${"%.1f".format(meters / 1000.0)} km"
```

---

## Arquivo 3: `SignalHistory.kt`

Crie `app/src/main/java/com/example/ls/data/SignalHistory.kt`:

```kotlin
package com.example.ls.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

data class SignalPoint(
    val timestampMs : Long,
    val dbm         : Int,
    val networkType : NetworkType,
)

class SignalHistory(val capacity: Int = 90) {

    private val _points = MutableStateFlow<List<SignalPoint>>(emptyList())
    val points: StateFlow<List<SignalPoint>> = _points.asStateFlow()

    fun add(point: SignalPoint) {
        val current = _points.value
        _points.value = if (current.size >= capacity) {
            current.drop(1) + point   // remove o mais antigo, adiciona o novo
        } else {
            current + point
        }
    }

    fun clear() {
        _points.value = emptyList()
    }

    /**
     * Retorna (min, max) ajustado para escalar o eixo Y do gráfico.
     * Se vazia, retorna faixa padrão (-120, -40).
     */
    fun range(): Pair<Int, Int> {
        val pts = _points.value
        if (pts.isEmpty()) return -120 to -40
        val min = pts.minOf { it.dbm }.coerceAtMost(-40)
        val max = pts.maxOf { it.dbm }.coerceAtLeast(min + 10)
        return min - 5 to max + 5    // margem de 5 dBm em cada extremidade
    }

    /**
     * Média dos últimos N pontos. Null se não há pontos.
     */
    fun average(n: Int = 10): Double? {
        val pts = _points.value.takeLast(n)
        return if (pts.isEmpty()) null else pts.map { it.dbm }.average()
    }

    /**
     * Desvio padrão dos últimos N pontos.
     * Indica estabilidade do sinal (baixo = estável, alto = instável).
     */
    fun stdDev(n: Int = 10): Double {
        val pts = _points.value.takeLast(n)
        if (pts.size < 2) return 0.0
        val avg = pts.map { it.dbm }.average()
        val variance = pts.sumOf { (it.dbm - avg) * (it.dbm - avg) } / pts.size
        return sqrt(variance)
    }
}
```

---

## Testando a camada de dados

Esses arquivos podem ser testados sem Android! Crie em
`app/src/test/java/com/example/ls/`:

```kotlin
// FrequencyUtilsTest.kt
class FrequencyUtilsTest {

    @Test
    fun `EARFCN 1300 deve retornar Banda 3 e FDL 1815 MHz`() {
        val result = earfcnToInfo(1300)
        assertNotNull(result)
        assertEquals(3, result!!.first)
        assertEquals(1815.0, result.second, 0.001)
        assertEquals(1720.0, result.third!!, 0.001)
    }

    @Test
    fun `EARFCN fora de qualquer banda deve retornar null`() {
        assertNull(earfcnToInfo(99999))
    }

    @Test
    fun `NRARFCN 627264 deve retornar frequencia proxima de 3500 MHz`() {
        val freq = nrArfcnToMhz(627264)
        assertNotNull(freq)
        assertEquals(3508.92, freq!!, 1.0)
    }

    @Test
    fun `TA 10 deve ser aproximadamente 781m`() {
        val dist = taToDistanceMeters(10)
        assertNotNull(dist)
        assertEquals(781.25, dist!!, 0.001)
    }
}

// SignalHistoryTest.kt
class SignalHistoryTest {

    @Test
    fun `deve remover o mais antigo quando capacity for atingida`() {
        val history = SignalHistory(capacity = 3)
        val p1 = SignalPoint(1L, -87, NetworkType.LTE)
        val p2 = SignalPoint(2L, -90, NetworkType.LTE)
        val p3 = SignalPoint(3L, -95, NetworkType.LTE)
        val p4 = SignalPoint(4L, -80, NetworkType.LTE)

        history.add(p1); history.add(p2); history.add(p3); history.add(p4)

        val pontos = history.points.value
        assertEquals(3, pontos.size)
        assertEquals(p2, pontos[0])   // p1 foi removido
        assertEquals(p4, pontos[2])
    }

    @Test
    fun `average deve calcular media dos ultimos N pontos`() {
        val history = SignalHistory()
        history.add(SignalPoint(1L, -80, NetworkType.LTE))
        history.add(SignalPoint(2L, -90, NetworkType.LTE))
        history.add(SignalPoint(3L, -100, NetworkType.LTE))

        val avg = history.average(3)
        assertNotNull(avg)
        assertEquals(-90.0, avg!!, 0.001)
    }
}
```

---

## 📌 Resumo

```
SignaData.kt        → enums, data classes, sealed class, funções puras
FrequencyUtils.kt   → matemática 3GPP: ARFCN → MHz
SignalHistory.kt    → buffer circular reativo (StateFlow<List<SignalPoint>>)

Princípio desta camada:
→ Zero imports de android.* ou androidx.compose.*
→ Toda lógica é testável com JUnit puro
→ Funções puras: mesmo input → mesmo output, sem side effects
```

**Próxima aula →** [Repositório](03-repositorio.md) — acessando o hardware via TelephonyManager.
