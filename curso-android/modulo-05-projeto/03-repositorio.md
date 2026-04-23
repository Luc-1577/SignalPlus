# Módulo 05 · Aula 03 — Repositório: Acessando o Hardware do Modem

> **Objetivo:** Implementar `SignalRepository` — a ponte entre o hardware Android e os seus modelos de dados.

---

## O que o Repositório faz

O `SignalRepository` encapsula **toda** a complexidade de acessar o `TelephonyManager`:

```
TelephonyManager                    SignalRepository
    │                                     │
    │  allCellInfo → List<CellInfo>        │
    │  (API fragmentada por geração)       │
    │                                     │
    ├─ CellInfoNr (5G)   ──────────────► parseNr()    ──►
    ├─ CellInfoLte (4G)  ──────────────► parseLte()   ──►  List<CellSignalData>
    ├─ CellInfoWcdma (3G) ─────────────► parseWcdma() ──►  (modelo limpo e uniforme)
    ├─ CellInfoGsm (2G)  ──────────────► parseGsm()   ──►
    └─ CellInfoCdma (2G) ──────────────► parseCdma()  ──►
```

---

## A estrutura completa

Crie `app/src/main/java/com/example/ls/data/SignalRepositry.kt`:

```kotlin
package com.example.ls.data

import android.content.Context
import android.os.Build
import android.telephony.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SignalRepository(private val context: Context) {

    // lazy: o TelephonyManager é criado apenas quando `telephony` é acessado pela 1ª vez
    private val telephony: TelephonyManager by lazy {
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }

    // ════════════════════════════════════════════════════════
    // MÉTODO PÚBLICO
    // ════════════════════════════════════════════════════════

    suspend fun readCells(): List<CellSignalData> = withContext(Dispatchers.IO) {
        try {
            val operator     = telephony.networkOperatorName
                .takeIf { it.isNotBlank() } ?: "Desconhecido"
            val primaryBwKhz = readPrimaryBwKhz()
            val rawList      = telephony.allCellInfo ?: return@withContext emptyList()

            rawList
                .mapNotNull { parseCellInfo(it, operator, primaryBwKhz) }
                .sortedByDescending { it.dbm }
                .sortedByDescending { it.isRegistered }

        } catch (_: SecurityException) { emptyList() }
        catch (_: Exception)           { emptyList() }
    }

    // ════════════════════════════════════════════════════════
    // LARGURA DE BANDA VIA REFLEXÃO
    // ════════════════════════════════════════════════════════

    private fun readPrimaryBwKhz(): Int? {
        // API getPhysicalChannelConfigs disponível a partir do Android 9 (API 28)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return try {
            val method  = telephony.javaClass.getMethod("getPhysicalChannelConfigs")
            val configs = method.invoke(telephony) as? List<*> ?: return null
            val config  = configs.firstOrNull() ?: return null
            val bwMethod = config.javaClass.getMethod("getCellBandwidthDownlinkKhz")
            (bwMethod.invoke(config) as? Int)?.takeIf { it > 0 }
        } catch (_: Exception) { null }   // qualquer falha → BW indisponível
    }

    // ════════════════════════════════════════════════════════
    // DISPATCH POR TIPO DE CÉLULA
    // ════════════════════════════════════════════════════════

    private fun parseCellInfo(info: CellInfo, op: String, bwKhz: Int?): CellSignalData? = when {
        // 5G NR — só disponível no Android 10+
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && info is CellInfoNr
            -> parseNr(info, op, bwKhz)
        info is CellInfoLte   -> parseLte(info, op, bwKhz)
        info is CellInfoWcdma -> parseWcdma(info, op)
        info is CellInfoGsm   -> parseGsm(info, op)
        info is CellInfoCdma  -> parseCdma(info, op)
        else                  -> null
    }

    // ════════════════════════════════════════════════════════
    // PARSE 5G NR
    // ════════════════════════════════════════════════════════

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun parseNr(info: CellInfoNr, op: String, bwKhz: Int?): CellSignalData? {
        val ss  = info.cellSignalStrength as? CellSignalStrengthNr ?: return null
        val cid = info.cellIdentity as? CellIdentityNr             ?: return null

        val nci   = safeL { cid.nci.takeIf    { it != Long.MAX_VALUE } }
        val tac   = safeI { cid.tac.takeIf    { it != Int.MAX_VALUE  } }
        val pci   = safeI { cid.pci.takeIf    { it != Int.MAX_VALUE  } }
        val arfcn = safeI { cid.nrarfcn.takeIf { it > 0 } }

        // gNB ID: os 14 bits menos significativos do NCI identificam a célula
        // Os bits restantes (acima de bit 14) identificam o gNB
        val gnbId = nci?.let { it ushr 14 }
        val freq  = arfcn?.let { nrArfcnToMhz(it) }

        var band: Int? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            band = cid.bands.firstOrNull()?.takeIf { it > 0 }
        }

        val extras = mutableMapOf<String, String>()
        safeI { ss.ssRsrp  }?.let { extras["SS-RSRP"]  = "$it dBm" }
        safeI { ss.ssRsrq  }?.let { extras["SS-RSRQ"]  = "$it dB"  }
        safeI { ss.ssSinr  }?.let { extras["SS-SINR"]  = "$it dB"  }
        safeI { ss.csiRsrp }?.let { extras["CSI-RSRP"] = "$it dBm" }
        safeI { ss.csiRsrq }?.let { extras["CSI-RSRQ"] = "$it dB"  }

        return CellSignalData(
            networkType  = NetworkType.NR_5G,
            dbm          = ss.dbm,
            asuLevel     = ss.asuLevel,
            level        = ss.level,
            isRegistered = info.isRegistered,
            operator     = op,
            extraMetrics = extras,
            netInfo = CellNetworkInfo(
                tac         = tac,
                enbId       = gnbId,
                cellId      = nci,
                pci         = pci,
                arfcn       = arfcn,
                fdlMhz      = freq,
                fulMhz      = freq,   // NR TDD: DL = UL
                band        = band,
                bwKhz       = if (info.isRegistered) bwKhz else null,
                tacLabel    = "TAC",
                nodeIdLabel = "gNB",
                arfcnLabel  = "NRARFCN",
                pciLabel    = "PCI",
            ),
        )
    }

    // ════════════════════════════════════════════════════════
    // PARSE LTE
    // ════════════════════════════════════════════════════════

    private fun parseLte(info: CellInfoLte, op: String, bwKhz: Int?): CellSignalData? {
        val ss  = info.cellSignalStrength
        val cid = info.cellIdentity

        val ci     = safeI { cid.ci.takeIf     { it != Int.MAX_VALUE } }
        val tac    = safeI { cid.tac.takeIf    { it != Int.MAX_VALUE } }
        val pci    = safeI { cid.pci.takeIf    { it != Int.MAX_VALUE } }
        val earfcn = safeI { cid.earfcn.takeIf { it > 0 } }

        // eNB ID: Cell Identity LTE tem 28 bits; os 8 menos significativos = setor
        val enbId    = ci?.let { (it.toLong() shr 8) }
        val freqInfo = earfcn?.let { earfcnToInfo(it) }

        var band: Int? = freqInfo?.first
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            band = cid.bands.firstOrNull()?.takeIf { it > 0 } ?: band
        }

        val ta = safeI { ss.timingAdvance.takeIf { it >= 0 } }

        val extras = mutableMapOf<String, String>()
        extras["RSRP"] = "${ss.dbm} dBm"   // LTE: dbm = RSRP
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            safeI { ss.rsrq  }?.let { extras["RSRQ"]  = "$it dB" }
            safeI { ss.rssnr }?.let { extras["RSSNR"] = "$it dB" }
            safeI { ss.cqi   }?.let { extras["CQI"]   = "$it"    }
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

    // ════════════════════════════════════════════════════════
    // PARSE WCDMA (3G)
    // ════════════════════════════════════════════════════════

    private fun parseWcdma(info: CellInfoWcdma, op: String): CellSignalData? {
        val ss  = info.cellSignalStrength
        val cid = info.cellIdentity

        val lac    = safeI { cid.lac.takeIf    { it != Int.MAX_VALUE } }
        val ci     = safeI { cid.cid.takeIf    { it != Int.MAX_VALUE } }
        val psc    = safeI { cid.psc.takeIf    { it != Int.MAX_VALUE } }
        val uarfcn = safeI { cid.uarfcn.takeIf { it > 0 } }

        val fdl = uarfcn?.let { uarfcnToMhz(it) }
        // WCDMA: duplex FDD, separação fixa de 190 MHz (downlink = uplink + 190)
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
                tac         = lac,
                enbId       = ci?.toLong(),
                cellId      = ci?.toLong(),
                pci         = psc,
                arfcn       = uarfcn,
                fdlMhz      = fdl,
                fulMhz      = ful,
                bwKhz       = 5000,     // WCDMA usa sempre 5 MHz de largura de banda
                tacLabel    = "LAC",
                nodeIdLabel = "CID",
                arfcnLabel  = "UARFCN",
                pciLabel    = "PSC",
            ),
        )
    }

    // ════════════════════════════════════════════════════════
    // PARSE GSM (2G)
    // ════════════════════════════════════════════════════════

    private fun parseGsm(info: CellInfoGsm, op: String): CellSignalData? {
        val ss  = info.cellSignalStrength
        val cid = info.cellIdentity

        val lac   = safeI { cid.lac.takeIf   { it != Int.MAX_VALUE } }
        val ci    = safeI { cid.cid.takeIf   { it != Int.MAX_VALUE } }
        val bsic  = safeI { cid.bsic.takeIf  { it != Int.MAX_VALUE } }
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
                tac         = lac,
                enbId       = ci?.toLong(),
                cellId      = ci?.toLong(),
                pci         = bsic,
                arfcn       = arfcn,
                fdlMhz      = freqPair?.first,
                fulMhz      = freqPair?.second,
                bwKhz       = 200,     // GSM usa canais de 200 kHz
                tacLabel    = "LAC",
                nodeIdLabel = "CI",
                arfcnLabel  = "ARFCN",
                pciLabel    = "BSIC",
            ),
        )
    }

    // ════════════════════════════════════════════════════════
    // HELPERS PARA VALORES SENTINELA
    // ════════════════════════════════════════════════════════

    /**
     * Executa `block` com proteção tripla:
     * 1. try/catch para qualquer exceção do hardware
     * 2. filtra Int.MAX_VALUE (sentinela "não disponível" do Android)
     * 3. filtra Int.MIN_VALUE (sentinela "erro" do Android)
     *
     * O lambda (`block: () -> Int?`) é essencial: se passássemos o valor direto,
     * a exceção aconteceria ANTES de entrar no try/catch.
     */
    private fun safeI(block: () -> Int?): Int? = try {
        val v = block()
        if (v != null && v != Int.MAX_VALUE && v != Int.MIN_VALUE) v else null
    } catch (_: Exception) { null }

    private fun safeL(block: () -> Long?): Long? = try {
        val v = block()
        if (v != null && v != Long.MAX_VALUE && v != Long.MIN_VALUE) v else null
    } catch (_: Exception) { null }
}
```

---

## Entendendo os campos da API Android

### `CellInfo.isRegistered`

```
false = célula vizinha detectada mas não conectada
true  = célula à qual o aparelho está registrado (conectado)
```

Geralmente só há 1 `isRegistered = true` por vez, mas pode haver mais em
ambientes de dual SIM ou durante handover.

### Por que `Int.MAX_VALUE` como sentinela?

O TelephonyManager usa `Integer.MAX_VALUE` (2.147.483.647) para indicar "valor não
disponível". Sem o filtro `safeI`, você veria `TAC: 2147483647` na tela — sem sentido.

### `getAllCellInfo()` vs. `listen(LISTEN_CELL_INFO)`

```kotlin
// getAllCellInfo() — snapshot síncrono atual
val cells = telephony.allCellInfo   // pega o que o modem tem agora

// listen() — notificações quando o sinal muda (menos previsível em frequência)
telephony.listen(phoneStateListener, PhoneStateListener.LISTEN_CELL_INFO_CHANGED)
```

O projeto usa `getAllCellInfo()` em polling porque é mais confiável para diagnóstico
— você sabe exatamente quando a leitura ocorreu.

---

## O padrão `by lazy` para o TelephonyManager

```kotlin
private val telephony: TelephonyManager by lazy {
    context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
}
```

`by lazy` cria o valor **na primeira vez que é acessado** e reutiliza nas seguintes.

Por que não criar no construtor? Porque `lazy` garante que o `Context` já está
totalmente inicializado quando `getSystemService` é chamado.

---

## 📌 Resumo

```
SignalRepository
├── readCells(): suspend fun no Dispatchers.IO
│   ├── networkOperatorName → nome da operadora
│   ├── readPrimaryBwKhz() → reflexão para BW
│   └── allCellInfo → mapNotNull { parseCellInfo }
│
├── parseCellInfo() → dispatch por is CellInfoXxx
│   ├── parseNr()    → 5G: NCI, TAC, PCI, NRARFCN, SS-RSRP/Q/SINR
│   ├── parseLte()   → 4G: CI, TAC, PCI, EARFCN, RSRP/Q/SNR/CQI, TA
│   ├── parseWcdma() → 3G: LAC, CID, PSC, UARFCN
│   ├── parseGsm()   → 2G: LAC, CI, BSIC, ARFCN
│   └── parseCdma()  → CDMA: NID, BSID, SID
│
└── safeI/safeL → filtra sentinelas + exceções
```

**Próxima aula →** [ViewModel](04-viewmodel.md) — conectando repositório, histórico e UI com polling.
