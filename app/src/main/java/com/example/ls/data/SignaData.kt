package com.example.ls.data

import androidx.compose.ui.graphics.Color
import com.example.ls.ui.theme.*

// ── Enums ──────────────────────────────────────────────────────────────────

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

enum class SignalQuality(
    val label: String,
    val color: Color,
    val bars: Int,
) {
    EXCELLENT("Excelente",   QualityExcel,    5),
    GOOD     ("Bom",         QualityGood,     4),
    FAIR     ("Regular",     QualityFair,     3),
    POOR     ("Fraco",       QualityPoor,     2),
    VERY_POOR("Muito Fraco", QualityVeryPoor, 1),
    NO_SIGNAL("Sem Sinal",   QualityNone,     0),
}

// ── Identificadores de rádio ───────────────────────────────────────────────

/**
 * Parâmetros de identidade de rádio de uma célula.
 *
 * Todos os campos são nullable — o hardware pode não expor certos
 * valores dependendo do fabricante, versão de Android ou tipo de rede.
 *
 * @param tac            TAC (LTE/5G) ou LAC (2G/3G). Identifica a área de rastreamento.
 * @param enbId          eNB ID (LTE = CI >> 8) ou gNB ID (5G = NCI >> 14).
 * @param cellId         Cell Identity completo: CI (LTE 28-bit), NCI (5G 36-bit), CID (2G/3G).
 * @param pci            PCI 0–503 (LTE), 0–1007 (5G NR), PSC 0–511 (WCDMA), BSIC 0–63 (GSM).
 * @param arfcn          EARFCN (LTE), NRARFCN (5G), UARFCN (3G), ARFCN (2G).
 * @param fdlMhz         Frequência de downlink calculada (MHz) a partir do ARFCN.
 * @param fulMhz         Frequência de uplink (MHz). Null em TDD/SDL (DL=UL ou somente DL).
 * @param band           Número da faixa de rádio (ex: B28 = 700 MHz no Brasil).
 * @param bwKhz          Largura de banda do canal em kHz. Requer API 28+.
 * @param timingAdvance  TA em unidades (LTE). 1 TA ≈ 78 m de distância à ERB.
 * @param tacLabel       "TAC" para LTE/5G, "LAC" para 2G/3G.
 * @param nodeIdLabel    "eNB" (LTE), "gNB" (5G), "CI" (GSM), "CID" (WCDMA).
 * @param arfcnLabel     "EARFCN" (LTE), "NRARFCN" (5G), "UARFCN" (3G), "ARFCN" (2G).
 * @param pciLabel       "PCI" (LTE/5G), "PSC" (WCDMA), "BSIC" (GSM), "SID" (CDMA).
 */
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
    // labels adaptados por tecnologia
    val tacLabel      : String  = "TAC",
    val nodeIdLabel   : String  = "eNB",
    val arfcnLabel    : String  = "EARFCN",
    val pciLabel      : String  = "PCI",
)

// ── Dados de célula ────────────────────────────────────────────────────────

/**
 * Todos os dados de uma célula/antena detectada pelo hardware.
 *
 * @param networkType    Tipo de rede (2G/3G/4G/5G).
 * @param dbm            Potência em dBm. Mais próximo de 0 = mais forte.
 * @param asuLevel       Unidade relativa do hardware (Arbitrary Strength Unit).
 * @param level          Barras 0–4 conforme o Android SDK.
 * @param isRegistered   true = célula à qual o aparelho está conectado.
 * @param operator       Nome da operadora ("Vivo", "Claro", etc.).
 * @param extraMetrics   Métricas de qualidade: RSRP, RSRQ, CQI, SS-SINR...
 * @param netInfo        Identificadores: TAC, eNB, Cell ID, PCI, ARFCN, FDL, FUL, BW, TA.
 */
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

// ── UI State ───────────────────────────────────────────────────────────────

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

// ── Helpers ────────────────────────────────────────────────────────────────

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