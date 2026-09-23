package com.example.ls.data

import java.util.Locale

// ── LTE Band Table (3GPP TS 36.101 §5.7.3) ────────────────────────────────

private data class LteBand(
    val band   : Int,
    val fDlLow : Double,
    val fUlLow : Double,
    val nDlLow : Int,
    val nDlHigh: Int,
    val isTdd  : Boolean = false,
    val isSdl  : Boolean = false,
)

private val LTE_BANDS: List<LteBand> = listOf(
    LteBand( 1, 2110.0, 1920.0,  0,     599),
    LteBand( 2, 1930.0, 1850.0,  600,   1199),
    LteBand( 3, 1805.0, 1710.0,  1200,  1949),
    LteBand( 4, 2110.0, 1710.0,  1950,  2399),
    LteBand( 5,  869.0,  824.0,  2400,  2649),
    LteBand( 7, 2620.0, 2500.0,  2750,  3449),
    LteBand( 8,  925.0,  880.0,  3450,  3799),
    LteBand(12,  729.0,  699.0,  5010,  5179),
    LteBand(13,  746.0,  777.0,  5180,  5279),
    LteBand(17,  734.0,  704.0,  5730,  5849),
    LteBand(20,  791.0,  832.0,  6150,  6449),
    LteBand(28,  758.0,  703.0,  9210,  9659),   // B28 — 700 MHz (principal no Brasil)
    LteBand(29,  717.0,    0.0,  9660,  9769, isSdl = true),
    LteBand(32, 1452.0,    0.0,  9920, 10359, isSdl = true),
    LteBand(38, 2570.0, 2570.0, 37750, 38249, isTdd = true),
    LteBand(40, 2300.0, 2300.0, 38650, 39649, isTdd = true),
    LteBand(41, 2496.0, 2496.0, 39650, 41589, isTdd = true),   // B41 — 2500 MHz TDD (Brasil)
    LteBand(42, 3400.0, 3400.0, 41590, 43589, isTdd = true),
    LteBand(43, 3600.0, 3600.0, 43590, 45589, isTdd = true),
    LteBand(48, 3550.0, 3550.0, 55240, 56739, isTdd = true),
    LteBand(65, 2110.0, 1920.0, 65536, 66435),
    LteBand(66, 2110.0, 1710.0, 66436, 67335),
    LteBand(67,  738.0,    0.0, 67336, 67535, isSdl = true),
    LteBand(68,  753.0,  698.0, 67536, 67835),
    LteBand(71,  617.0,  663.0, 68586, 68935),
)

/** EARFCN DL → Triple(banda, FDL_MHz, FUL_MHz?). FUL = null em SDL; TDD: FDL = FUL. */
fun earfcnToInfo(earfcn: Int): Triple<Int, Double, Double?>? {
    val b = LTE_BANDS.firstOrNull { earfcn in it.nDlLow..it.nDlHigh } ?: return null
    val off = earfcn - b.nDlLow
    val fdl = b.fDlLow + 0.1 * off
    val ful = when {
        b.isSdl -> null
        b.isTdd -> fdl
        else    -> b.fUlLow + 0.1 * off
    }
    return Triple(b.band, fdl, ful)
}

/** NRARFCN → frequência central em MHz (3GPP TS 38.104 §5.4.2.1). */
fun nrArfcnToMhz(arfcn: Int): Double? = when {
    arfcn < 0         -> null
    arfcn <= 599999   -> arfcn * 0.005
    arfcn <= 2016666  -> 3000.0   + (arfcn - 600000)  * 0.015
    arfcn <= 3279165  -> 24250.08 + (arfcn - 2016667) * 0.060
    else              -> null
}

/** UARFCN → FDL em MHz (FDL = UARFCN × 0.2, válido pra qualquer banda WCDMA). */
fun uarfcnToMhz(uarfcn: Int): Double? =
    if (uarfcn > 0) uarfcn * 0.2 else null

// ── WCDMA Band Table (3GPP TS 25.101 Table 5.2, faixa downlink Nu) ────────
// O espaçamento duplex (FDL-FUL) varia por banda — NÃO é um valor fixo global
// (era o bug: hardcoded 190MHz, correto só pra Band I). Nu_low/Nu_high aqui
// são o range de UARFCN de downlink que identifica a banda.

private data class WcdmaBand(val band: Int, val nuLow: Int, val nuHigh: Int, val duplexMhz: Double)

private val WCDMA_BANDS: List<WcdmaBand> = listOf(
    WcdmaBand(1, 10562, 10838, 190.0),  // 2100
    WcdmaBand(2,  9662,  9938,  80.0),  // 1900 PCS
    WcdmaBand(3,  1162,  1513,  95.0),  // 1800
    WcdmaBand(4,  1537,  1738, 400.0),  // AWS 1700/2100
    WcdmaBand(5,  4357,  4458,  45.0),  // 850 — comum no Brasil fora dos grandes centros
    WcdmaBand(6,  4387,  4413,  45.0),  // 800 (Japão)
    WcdmaBand(8,  2937,  3088,  45.0),  // 900
    WcdmaBand(9,  9237,  9387,  95.0),  // 1700 (Japão)
)

/** UARFCN → Triple(banda, FDL_MHz, FUL_MHz). Null se a banda não for
 *  reconhecida — antes disso, FUL sempre assumia o espaçamento duplex da
 *  Band I (190MHz), errado pra Band V/850MHz (comum no Brasil) e outras;
 *  falhar fechado (null) é melhor que mostrar um FUL tecnicamente incorreto
 *  como se fosse um dado preciso. */
fun uarfcnToWcdmaInfo(uarfcn: Int): Triple<Int, Double, Double>? {
    val fdl = uarfcnToMhz(uarfcn) ?: return null
    val b = WCDMA_BANDS.firstOrNull { uarfcn in it.nuLow..it.nuHigh } ?: return null
    return Triple(b.band, fdl, fdl - b.duplexMhz)
}

/** GSM ARFCN → (FDL_MHz, FUL_MHz) por banda (3GPP TS 45.005 §2). */
fun gsmArfcnToInfo(arfcn: Int): Pair<Double, Double>? = when (arfcn) {
    in 1..124    -> (935.2 + 0.2 * (arfcn - 1)).let { Pair(it, it - 45.0) }
    0            -> Pair(935.0, 890.0)
    in 128..251  -> (869.2 + 0.2 * (arfcn - 128)).let { Pair(it, it - 45.0) }
    in 512..885  -> (1805.2 + 0.2 * (arfcn - 512)).let { Pair(it, it - 95.0) }
    in 975..1023 -> (935.0 + 0.2 * (arfcn - 1024)).let { Pair(it, it - 45.0) }
    else         -> null
}

/** 1 TA ≈ 78.125 m (LTE one-way). Null se inválido. */
fun taToDistanceMeters(ta: Int): Double? =
    if (ta > 0 && ta != Int.MAX_VALUE) ta * 78.125 else null

fun formatMhz(mhz: Double): String =
    if (mhz >= 1000.0) "${"%.3f".format(Locale.US, mhz / 1000.0)} GHz"
    else "${"%.1f".format(Locale.US, mhz)} MHz"

fun formatBwKhz(khz: Int): String =
    if (khz >= 1000) "${"%.0f".format(Locale.US, khz / 1000.0)} MHz" else "$khz kHz"

fun formatDistance(meters: Double): String =
    if (meters < 1000.0) "~${meters.toInt()} m" else "~${"%.1f".format(Locale.US, meters / 1000.0)} km"