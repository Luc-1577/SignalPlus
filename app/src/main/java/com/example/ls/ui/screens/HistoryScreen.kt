package com.example.ls.ui.screens

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ls.data.SignalHistory
import com.example.ls.data.SignalPoint
import com.example.ls.data.db.SignalReadingEntity
import com.example.ls.data.signalQuality
import com.example.ls.ui.components.*
import com.example.ls.ui.theme.*
import com.example.ls.util.CsvExportUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(
    history: SignalHistory,
    onClear: () -> Unit,
    onExport: suspend () -> List<SignalReadingEntity>,
    pollIntervalMs: Long,
    modifier: Modifier = Modifier,
) {
    val points by history.points.collectAsStateWithLifecycle()
    val (minDbm, maxDbm) = history.range()
    val avg    = history.average()
    val stdDev = history.stdDev()
    val minPt  = points.minByOrNull { it.dbm }
    val maxPt  = points.maxByOrNull { it.dbm }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ShowChart, null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                        Text("Histórico de Sinal", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    Row {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    val readings = onExport()
                                    // toCsv()/writeText() são CPU/I/O de disco — não têm
                                    // ponto de suspensão próprio, então sem isso rodariam
                                    // na thread principal (herdada do rememberCoroutineScope)
                                    // e travariam a UI proporcionalmente ao tamanho do
                                    // histórico, que agora não tem limite no Room.
                                    val intent = withContext(Dispatchers.IO) {
                                        CsvExportUtil.buildShareIntent(context, readings)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Exportar histórico"))
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = AccentBlue),
                        ) {
                            Icon(Icons.Default.FileDownload, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Exportar", fontSize = 13.sp)
                        }
                        if (points.isNotEmpty()) {
                            TextButton(onClick = onClear, colors = ButtonDefaults.textButtonColors(contentColor = QualityVeryPoor)) {
                                Icon(Icons.Default.DeleteOutline, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Limpar", fontSize = 13.sp)
                            }
                        }
                    }
                }
                if (points.isNotEmpty()) {
                    Text(
                        "\"Limpar\" só afeta esta tela — o histórico completo continua salvo para exportação.",
                        fontSize = 10.sp,
                        color = TextDim,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        item {
            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BgCard).border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)).padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("dBm × tempo", fontSize = 11.sp, color = TextMuted, letterSpacing = 0.5.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { Box(Modifier.width(16.dp).height(2.dp).background(AccentBlue)); Text("sinal", fontSize = 10.sp, color = TextMuted) }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { Box(Modifier.width(16.dp).height(2.dp).background(AccentCyan.copy(alpha = 0.5f))); Text("média", fontSize = 10.sp, color = TextMuted) }
                    }
                }
                Row(verticalAlignment = Alignment.Top) {
                    ChartYLabels(minDbm, maxDbm)
                    Spacer(Modifier.width(4.dp))
                    SignalChart(points, minDbm, maxDbm, Modifier.weight(1f))
                }
                Text(
                    "${points.size} leituras nos últimos ${formatIntervalLabel(points.size * pollIntervalMs)}",
                    fontSize = 10.sp,
                    color = TextDim,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        if (points.size >= 2) {
            item {
                Column {
                    SectionHeader("Estatísticas da sessão")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        @Composable
                        fun SC(label: String, value: String, accent: Color, mod: Modifier) = Column(modifier = mod.clip(RoundedCornerShape(12.dp)).background(BgCard).border(1.dp, BorderSubtle, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
                            Text(label.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 0.8.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = accent)
                        }
                        SC("Média", avg?.let { "%.0f dBm".format(it) } ?: "—", AccentCyan, Modifier.weight(1f))
                        SC("Mínimo", minPt?.let { "${it.dbm} dBm" } ?: "—", QualityVeryPoor, Modifier.weight(1f))
                        SC("Máximo", maxPt?.let { "${it.dbm} dBm" } ?: "—", QualityExcel, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        val stdVal = stdDev
                        val stab = when { stdVal < 2.0 -> "Estável"; stdVal < 5.0 -> "Variável"; stdVal < 10.0 -> "Instável"; else -> "Crítico" }
                        val stabColor = when { stdVal < 2.0 -> QualityExcel; stdVal < 5.0 -> QualityGood; stdVal < 10.0 -> QualityPoor; else -> QualityVeryPoor }
                        Column(modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(BgCard).border(1.dp, BorderSubtle, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) { Text("DESVIO PADRÃO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 0.8.sp); Spacer(Modifier.height(6.dp)); Text("±%.1f dB".format(stdVal), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AccentPurple) }
                        Column(modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(BgCard).border(1.dp, BorderSubtle, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) { Text("ESTABILIDADE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 0.8.sp); Spacer(Modifier.height(6.dp)); Text(stab, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = stabColor) }
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        if (points.isNotEmpty()) {
            item { SectionHeader("Últimas leituras") }
            val reversed = points.reversed().take(40)
            items(reversed, key = { it.timestampMs }) { pt ->
                val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
                val time = remember(pt.timestampMs) { fmt.format(Date(pt.timestampMs)) }
                val q = signalQuality(pt.dbm, pt.networkType)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp)).background(BgCard).border(1.dp, BorderSubtle, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(q.color))
                    Text(time, fontSize = 12.sp, color = TextMuted, modifier = Modifier.width(60.dp))
                    NetworkBadge(pt.networkType)
                    Spacer(Modifier.weight(1f))
                    Text("${pt.dbm} dBm", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = q.color, textAlign = TextAlign.End)
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}
