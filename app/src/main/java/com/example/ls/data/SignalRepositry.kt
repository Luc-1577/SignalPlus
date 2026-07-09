package com.example.ls.data

import android.content.Context
import android.os.Build
import android.telephony.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Lê dados de célula via TelephonyManager e produz [CellSignalData] completos,
 * incluindo todos os identificadores: TAC/LAC, eNB/gNB, Cell ID, PCI/PSC/BSIC,
 * ARFCN/EARFCN/NRARFCN/UARFCN, FDL, FUL, Banda, BW e Timing Advance.
 */
class SignalRepository(private val context: Context) {

    private val telephony: TelephonyManager by lazy {
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }

    suspend fun readCells(): List<CellSignalData> = withContext(Dispatchers.IO) {
        try {
            val operator = telephony.networkOperatorName
                .takeIf { it.isNotBlank() } ?: "Desconhecido"

            val primaryBwKhz = readPrimaryBwKhz()

            fetchCellInfo()
                .mapNotNull { parseCellInfo(it, operator, primaryBwKhz) }
                .sortedByDescending { it.dbm }
                .sortedByDescending { it.isRegistered }
        } catch (_: SecurityException) { emptyList() }
        catch (_: Exception)         { emptyList() }
    }

    /**
     * Em apps com targetSdk ≥ Q, [TelephonyManager.getAllCellInfo] não força mais um
     * refresh do modem — só retorna o último resultado em cache. A partir da API 29,
     * usamos [TelephonyManager.requestCellInfoUpdate] para obter uma leitura fresca.
     */
    private suspend fun fetchCellInfo(): List<CellInfo> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return telephony.allCellInfo ?: emptyList()
        }
        return try {
            suspendCancellableCoroutine { cont ->
                telephony.requestCellInfoUpdate(
                    context.mainExecutor,
                    object : TelephonyManager.CellInfoCallback() {
                        override fun onCellInfo(cellInfo: List<CellInfo>) {
                            if (cont.isActive) cont.resume(cellInfo)
                        }

                        override fun onError(errorCode: Int, detail: Throwable?) {
                            if (cont.isActive) cont.resume(telephony.allCellInfo ?: emptyList())
                        }
                    },
                )
            }
        } catch (_: Exception) {
            telephony.allCellInfo ?: emptyList()
        }
    }

    private fun readPrimaryBwKhz(): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return try {
            // getPhysicalChannelConfigs can be tricky due to visibility in different SDKs.
            // Using reflection to ensure access to it.
            val method = telephony.javaClass.getMethod("getPhysicalChannelConfigs")
            val configs = method.invoke(telephony) as? List<*>
            if (!configs.isNullOrEmpty()) {
                val config = configs[0] ?: return null
                // PhysicalChannelConfig.getCellBandwidthDownlinkKhz()
                val bwMethod = config.javaClass.getMethod("getCellBandwidthDownlinkKhz")
                val bw = bwMethod.invoke(config) as? Int ?: 0
                if (bw > 0) bw else null
            } else null
        } catch (_: Exception) { null }
    }

    private fun parseCellInfo(info: CellInfo, op: String, bwKhz: Int?): CellSignalData? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && info is CellInfoNr
            -> parseNr(info, op, bwKhz)
        info is CellInfoLte   -> parseLte(info, op, bwKhz)
        info is CellInfoWcdma -> parseWcdma(info, op)
        info is CellInfoGsm   -> parseGsm(info, op)
        info is CellInfoCdma  -> parseCdma(info, op)
        else                  -> null
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun parseNr(info: CellInfoNr, op: String, bwKhz: Int?): CellSignalData? {
        val ss  = info.cellSignalStrength as? CellSignalStrengthNr ?: return null
        val cid = info.cellIdentity as? CellIdentityNr ?: return null

        val nci   = safeL { cid.nci.takeIf { it != Long.MAX_VALUE } }
        val tac   = safeI { cid.tac.takeIf { it != Int.MAX_VALUE } }
        val pci   = safeI { cid.pci.takeIf { it != Int.MAX_VALUE } }
        val arfcn = safeI { cid.nrarfcn.takeIf { it > 0 } }

        val gnbId = nci?.let { it ushr 14 }
        val freq  = arfcn?.let { nrArfcnToMhz(it) }

        var band: Int? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val bands = cid.bands
            if (bands.isNotEmpty()) {
                val b = bands[0]
                if (b > 0) band = b
            }
        }

        val extras = mutableMapOf<String, String>()
        safeI { ss.ssRsrp }?.let { extras["SS-RSRP"] = "$it dBm" }
        safeI { ss.ssRsrq }?.let { extras["SS-RSRQ"] = "$it dB" }
        safeI { ss.ssSinr }?.let { extras["SS-SINR"] = "$it dB" }
        safeI { ss.csiRsrp }?.let { extras["CSI-RSRP"] = "$it dBm" }
        safeI { ss.csiRsrq }?.let { extras["CSI-RSRQ"] = "$it dB" }

        return CellSignalData(
            networkType  = NetworkType.NR_5G,
            dbm          = ss.dbm,
            asuLevel     = ss.asuLevel,
            level        = ss.level,
            isRegistered = info.isRegistered,
            operator     = op,
            extraMetrics = extras,
            netInfo = CellNetworkInfo(
                tac           = tac,
                enbId         = gnbId,
                cellId        = nci,
                pci           = pci,
                arfcn         = arfcn,
                fdlMhz        = freq,
                fulMhz        = freq,
                band          = band,
                bwKhz         = if (info.isRegistered) bwKhz else null,
                timingAdvance = null,
                tacLabel      = "TAC",
                nodeIdLabel   = "gNB",
                arfcnLabel    = "NRARFCN",
                pciLabel      = "PCI",
            ),
        )
    }

    private fun parseLte(info: CellInfoLte, op: String, bwKhz: Int?): CellSignalData? {
        val ss  = info.cellSignalStrength
        val cid = info.cellIdentity

        val ci     = safeI { cid.ci.takeIf { it != Int.MAX_VALUE } }
        val tac    = safeI { cid.tac.takeIf { it != Int.MAX_VALUE } }
        val pci    = safeI { cid.pci.takeIf { it != Int.MAX_VALUE } }
        val earfcn = safeI { cid.earfcn.takeIf { it > 0 } }

        val enbId = ci?.let { (it.toLong() shr 8) }
        val freqInfo = earfcn?.let { earfcnToInfo(it) }

        var band: Int? = freqInfo?.first
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bands = cid.bands
            if (bands.isNotEmpty()) {
                val b = bands[0]
                if (b > 0) band = b
            }
        }

        val ta = safeI { ss.timingAdvance.takeIf { it >= 0 } }

        val extras = mutableMapOf<String, String>()
        extras["RSRP"] = "${ss.dbm} dBm"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            safeI { ss.rsrq }?.let { extras["RSRQ"] = "$it dB" }
            safeI { ss.rssnr }?.let { extras["RSSNR"] = "$it dB" }
            safeI { ss.cqi }?.let { extras["CQI"] = "$it" }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            safeI { ss.cqiTableIndex }?.let { extras["CQI Tabela"] = "$it" }
        }

        return CellSignalData(
            networkType  = NetworkType.LTE,
            dbm          = ss.dbm,
            asuLevel     = ss.asuLevel,
            level        = ss.level,
            isRegistered = info.isRegistered,
            operator     = op,
            extraMetrics = extras,
            netInfo = CellNetworkInfo(
                tac           = tac,
                enbId         = enbId,
                cellId        = ci?.toLong(),
                pci           = pci,
                arfcn         = earfcn,
                fdlMhz        = freqInfo?.second,
                fulMhz        = freqInfo?.third,
                band          = band,
                bwKhz         = if (info.isRegistered) bwKhz else null,
                timingAdvance = ta,
                tacLabel      = "TAC",
                nodeIdLabel   = "eNB",
                arfcnLabel    = "EARFCN",
                pciLabel      = "PCI",
            ),
        )
    }

    private fun parseWcdma(info: CellInfoWcdma, op: String): CellSignalData? {
        val ss  = info.cellSignalStrength
        val cid = info.cellIdentity

        val lac    = safeI { cid.lac.takeIf { it != Int.MAX_VALUE } }
        val ci     = safeI { cid.cid.takeIf { it != Int.MAX_VALUE } }
        val psc    = safeI { cid.psc.takeIf { it != Int.MAX_VALUE } }
        val uarfcn = safeI { cid.uarfcn.takeIf { it > 0 } }

        val fdl = uarfcn?.let { uarfcnToMhz(it) }
        val ful = fdl?.let { it - 190.0 }

        return CellSignalData(
            networkType  = NetworkType.WCDMA,
            dbm          = ss.dbm,
            asuLevel     = ss.asuLevel,
            level        = ss.level,
            isRegistered = info.isRegistered,
            operator     = op,
            extraMetrics = mapOf("ASU" to "${ss.asuLevel}"),
            netInfo = CellNetworkInfo(
                tac           = lac,
                enbId         = ci?.toLong(),
                cellId        = ci?.toLong(),
                pci           = psc,
                arfcn         = uarfcn,
                fdlMhz        = fdl,
                fulMhz        = ful,
                band          = null,
                bwKhz         = 5000,
                timingAdvance = null,
                tacLabel      = "LAC",
                nodeIdLabel   = "CID",
                arfcnLabel    = "UARFCN",
                pciLabel      = "PSC",
            ),
        )
    }

    private fun parseGsm(info: CellInfoGsm, op: String): CellSignalData? {
        val ss  = info.cellSignalStrength
        val cid = info.cellIdentity

        val lac   = safeI { cid.lac.takeIf { it != Int.MAX_VALUE } }
        val ci    = safeI { cid.cid.takeIf { it != Int.MAX_VALUE } }
        val bsic  = safeI { cid.bsic.takeIf { it != Int.MAX_VALUE } }
        val arfcn = safeI { cid.arfcn.takeIf { it >= 0 } }

        val freqPair = arfcn?.let { gsmArfcnToInfo(it) }

        return CellSignalData(
            networkType  = NetworkType.GSM_2G,
            dbm          = ss.dbm,
            asuLevel     = ss.asuLevel,
            level        = ss.level,
            isRegistered = info.isRegistered,
            operator     = op,
            extraMetrics = mapOf("ASU" to "${ss.asuLevel}"),
            netInfo = CellNetworkInfo(
                tac           = lac,
                enbId         = ci?.toLong(),
                cellId        = ci?.toLong(),
                pci           = bsic,
                arfcn         = arfcn,
                fdlMhz        = freqPair?.first,
                fulMhz        = freqPair?.second,
                band          = null,
                bwKhz         = 200,
                timingAdvance = null,
                tacLabel      = "LAC",
                nodeIdLabel   = "CI",
                arfcnLabel    = "ARFCN",
                pciLabel      = "BSIC",
            ),
        )
    }

    private fun parseCdma(info: CellInfoCdma, op: String): CellSignalData? {
        val ss  = info.cellSignalStrength
        val cid = info.cellIdentity

        return CellSignalData(
            networkType  = NetworkType.CDMA,
            dbm          = ss.dbm,
            asuLevel     = ss.asuLevel,
            level        = ss.level,
            isRegistered = info.isRegistered,
            operator     = op,
            extraMetrics = mapOf("EVDO dBm" to "${ss.evdoDbm}", "ASU" to "${ss.asuLevel}"),
            netInfo = CellNetworkInfo(
                tac         = safeI { cid.networkId.takeIf { it != Int.MAX_VALUE } },
                enbId       = safeI { cid.basestationId.takeIf { it != Int.MAX_VALUE } }?.toLong(),
                cellId      = safeI { cid.basestationId.takeIf { it != Int.MAX_VALUE } }?.toLong(),
                pci         = safeI { cid.systemId.takeIf { it != Int.MAX_VALUE } },
                tacLabel    = "NID",
                nodeIdLabel = "BSID",
                arfcnLabel  = "—",
                pciLabel    = "SID",
            ),
        )
    }

    private fun safeI(block: () -> Int?): Int? = try {
        val v = block()
        if (v != null && v != Int.MAX_VALUE && v != Int.MIN_VALUE) v else null
    } catch (_: Exception) { null }

    private fun safeL(block: () -> Long?): Long? = try {
        val v = block()
        if (v != null && v != Long.MAX_VALUE && v != Long.MIN_VALUE) v else null
    } catch (_: Exception) { null }
}
