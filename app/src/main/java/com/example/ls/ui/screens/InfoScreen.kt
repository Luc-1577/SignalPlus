package com.example.ls.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ls.data.*
import com.example.ls.ui.components.NetworkBadge
import com.example.ls.ui.theme.*

@Composable
fun InfoScreen(modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { InfoHeader() }
        item { InfoCard("Referência de qualidade") { QualityTable() } }
        item { InfoCard("Tecnologias de rede") { NetworkTable() } }
        item { InfoCard("Glossário de métricas") { MetricsTable() } }
        item { InfoCard("Como a leitura funciona") { HowItWorks() } }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun InfoHeader() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BgCard).border(1.dp, BorderDefault, RoundedCornerShape(16.dp)).padding(20.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(AccentBlue.copy(alpha = 0.15f)).border(1.dp, AccentBlue.copy(alpha = 0.3f), RoundedCornerShape(14.dp))) {
            Icon(Icons.Default.SignalCellularAlt, null, tint = AccentBlue, modifier = Modifier.size(28.dp))
        }
        Column {
            Text("Signal Meter", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text("v1.0 — medição precisa de sinal", fontSize = 12.sp, color = TextMuted)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NetworkBadge(NetworkType.GSM_2G); NetworkBadge(NetworkType.WCDMA)
                NetworkBadge(NetworkType.LTE);    NetworkBadge(NetworkType.NR_5G)
            }
        }
    }
}

@Composable
private fun QualityTable() {
    listOf(
        Triple(SignalQuality.EXCELLENT, "≥ −80 dBm (LTE/5G)",   "Excelente — streaming 4K, chamadas perfeitas"),
        Triple(SignalQuality.GOOD,      "−81 a −90 dBm",         "Bom — navegação rápida, sem travamentos"),
        Triple(SignalQuality.FAIR,      "−91 a −100 dBm",        "Regular — pode haver lentidão em dados"),
        Triple(SignalQuality.POOR,      "−101 a −110 dBm",       "Fraco — chamadas podem cair"),
        Triple(SignalQuality.VERY_POOR, "< −110 dBm",            "Muito fraco — quase sem cobertura"),
    ).forEachIndexed { idx, (q, range, desc) ->
        if (idx > 0) Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.padding(top = 5.dp).size(10.dp).clip(RoundedCornerShape(50)).background(q.color))
            Column { Text(range, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = q.color); Text(desc, fontSize = 12.sp, color = TextMuted, lineHeight = 17.sp) }
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(AccentBlue.copy(alpha = 0.07f)).border(1.dp, AccentBlue.copy(alpha = 0.2f), RoundedCornerShape(8.dp)).padding(10.dp)) {
        Icon(Icons.Default.Info, null, tint = AccentBlue, modifier = Modifier.size(14.dp).padding(top = 1.dp))
        Text("Limiares seguem ITU-T E.802 e 3GPP TS 36.214.", fontSize = 11.sp, color = TextSecondary, lineHeight = 16.sp)
    }
}

@Composable
private fun NetworkTable() {
    listOf(
        Triple(NetworkType.GSM_2G, "GSM / GPRS / EDGE",  "Voz e SMS. Dados lentos (até 384 kbps). Usado como fallback."),
        Triple(NetworkType.WCDMA,  "UMTS / HSPA / WCDMA", "3G clássico e HSPA+. Dados de 1–42 Mbps. Cobertura ampla no Brasil."),
        Triple(NetworkType.LTE,    "LTE / LTE-A",         "4G. Dados de 50–300 Mbps. Métricas: RSRP, RSRQ, RSSNR."),
        Triple(NetworkType.NR_5G,  "NR (New Radio)",      "5G. Até 20 Gbps. Métricas: SS-RSRP, SS-RSRQ, SS-SINR."),
    ).forEachIndexed { idx, (type, name, desc) ->
        if (idx > 0) Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NetworkBadge(type)
            Column { Text(name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary); Text(desc, fontSize = 12.sp, color = TextMuted, lineHeight = 17.sp) }
        }
    }
}

@Composable
private fun MetricsTable() {
    listOf(
        "dBm" to "Potência em decibéis-miliwatt. Quanto mais próximo de 0, mais forte.",
        "ASU" to "Arbitrary Strength Unit. Unidade relativa do hardware. Escala varia por tecnologia.",
        "RSRP" to "(4G) Reference Signal Received Power. Faixa: −140 a −44 dBm.",
        "RSRQ" to "(4G) Reference Signal Received Quality. Considera interferência. Faixa: −19.5 a −3 dB.",
        "RSSNR" to "(4G) Signal-to-Noise Ratio. Quanto maior, melhor.",
        "CQI" to "(4G) Channel Quality Indicator. Escala 0–15.",
        "SS-RSRP" to "(5G) Synchronization Signal RSRP. Equivalente ao RSRP no 5G NR.",
        "SS-SINR" to "(5G) Signal-to-Interference+Noise Ratio. Quanto maior, mais limpo o sinal.",
    ).forEachIndexed { idx, (key, desc) ->
        if (idx > 0) HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(key, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentCyan, modifier = Modifier.width(70.dp))
            Text(desc, fontSize = 12.sp, color = TextMuted, lineHeight = 17.sp, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun HowItWorks() {
    listOf(
        Icons.Default.PhoneAndroid to "Usa TelephonyManager.getAllCellInfo() para ler os dados brutos do modem.",
        Icons.Default.CellTower    to "Cada CellInfo é uma antena detectada — não só a conectada, mas as vizinhas também.",
        Icons.Default.Refresh      to "Atualizado a cada 2 segundos. O Android pode limitar a frequência em alguns aparelhos.",
        Icons.Default.Lock         to "A partir do Android 9 (API 28), exige permissão de localização — só para o sinal, sem rastreamento.",
        Icons.Default.BarChart     to "Os dBm vêm diretamente do hardware via driver — são medições reais, não estimativas.",
    ).forEachIndexed { idx, (icon, text) ->
        if (idx > 0) Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = AccentBlue, modifier = Modifier.size(18.dp).padding(top = 1.dp))
            Text(text, fontSize = 13.sp, color = TextSecondary, lineHeight = 19.sp)
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(AccentBlue.copy(alpha = 0.07f)).border(1.dp, AccentBlue.copy(alpha = 0.2f), RoundedCornerShape(8.dp)).padding(10.dp)) {
        Icon(Icons.Default.Info, null, tint = AccentBlue, modifier = Modifier.size(14.dp).padding(top = 1.dp))
        Text("Emuladores retornam dados simulados. Teste sempre em dispositivo físico.", fontSize = 11.sp, color = TextSecondary, lineHeight = 16.sp)
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BgCard).border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)).padding(20.dp)) {
        Text(title.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(16.dp))
        content()
    }
}