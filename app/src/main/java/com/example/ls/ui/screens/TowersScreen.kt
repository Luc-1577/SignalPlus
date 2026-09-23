package com.example.ls.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ExploreOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.example.ls.data.TowerDisplayEntry
import com.example.ls.data.TowerPositionMath
import com.example.ls.ui.components.SectionHeader
import com.example.ls.ui.theme.*
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Uma torre já com distância/rumo calculados a partir da posição atual —
 *  puramente de exibição, não persistido em nenhum lugar. */
private data class RadarPoint(val entry: TowerDisplayEntry, val distanceM: Double, val bearingDeg: Double)

@Composable
fun TowersScreen(
    towerTriangulationEnabled: Boolean,
    currentLocation: Location?,
    towers: List<TowerDisplayEntry>,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // Recarrega a lista sempre que a tela entra em composição — ela some da
    // composição (AnimatedContent troca de branch) quando o usuário sai da
    // aba, então isso dispara de novo a cada visita, mantendo os dados
    // razoavelmente frescos sem precisar de um StateFlow contínuo/polling
    // próprio (a tela não fica aberta o tempo todo).
    LaunchedEffect(Unit) { onRefresh() }

    val radarPoints = remember(currentLocation, towers) {
        val loc = currentLocation ?: return@remember emptyList()
        towers.map { t ->
            RadarPoint(
                entry = t,
                distanceM = TowerPositionMath.haversineMeters(loc.latitude, loc.longitude, t.lat, t.lon),
                bearingDeg = TowerPositionMath.bearingDegrees(loc.latitude, loc.longitude, t.lat, t.lon),
            )
        }.sortedBy { it.distanceM }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Torres", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.weight(1f))
                if (towers.isNotEmpty()) {
                    Text(
                        "${towers.count { it.isKnown }} conhecidas · ${towers.size} no total",
                        fontSize = 11.sp,
                        color = TextDim,
                    )
                }
            }
        }

        when {
            !towerTriangulationEnabled -> item {
                EmptyState(
                    title = "Triangulação desligada",
                    message = "Ligue \"Estimar posição das torres\" em Configurações para começar a mapear as torres detectadas.",
                )
            }
            currentLocation == null -> item {
                EmptyState(
                    title = "Aguardando localização",
                    message = "A estimativa está ligada, mas o app ainda não obteve sua localização. Isso pode levar alguns segundos.",
                )
            }
            towers.isEmpty() -> item {
                EmptyState(
                    title = "Nenhuma torre mapeada ainda",
                    message = "Continue usando o app normalmente — cada torre aparece aqui depois de ser detectada em pontos diferentes.",
                )
            }
            else -> {
                item { RadarScope(points = radarPoints) }
                item { SectionHeader("Torres mapeadas", modifier = Modifier.padding(top = 4.dp)) }
                items(radarPoints, key = { it.entry.towerKey }) { point ->
                    TowerRow(
                        point = point,
                        onOpenInMaps = {
                            openInMaps(context, point.entry.lat, point.entry.lon, towerDisplayName(point.entry))
                        },
                    )
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun EmptyState(title: String, message: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = 60.dp, bottom = 20.dp),
    ) {
        Icon(Icons.Default.ExploreOff, null, tint = TextDim, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            fontSize = 12.5.sp,
            color = TextMuted,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 28.dp),
        )
    }
}

/** Escopo de alcance/rumo (linguagem visual de radar RF) centrado na posição
 *  atual — em vez de um mapa de ruas, que exigiria tiles/API externa que o
 *  projeto deliberadamente não usa em nenhum outro lugar. Anéis de distância
 *  dão a escala; cada torre vira um ponto colorido por confiança
 *  ([TowerDisplayEntry.isKnown]) e dimensionado por [TowerDisplayEntry.sampleCount]. */
@Composable
private fun RadarScope(points: List<RadarPoint>, modifier: Modifier = Modifier) {
    val pulseTransition = rememberInfiniteTransition(label = "radar_pulse")
    val pulse by pulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "radar_pulse_value",
    )
    val youPulse by pulseTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "you_pulse_value",
    )

    // maxDistM é o valor do anel MAIS EXTERNO, não um raio independente com
    // margem à parte — garante por construção que o 3º anel (ringStep * 3)
    // sempre bate exatamente em maxRadiusPx, nunca ultrapassando os limites
    // visíveis do Canvas. Escolher a margem via um fator solto (ex.: *1.18)
    // e computar os anéis a partir dele separadamente permitia o 3º anel
    // (e seu rótulo) estourar pra fora da área desenhada quando a distância
    // máxima observada não caía num múltiplo "redondo" do passo escolhido.
    val maxObservedDistM = points.maxOfOrNull { it.distanceM } ?: 0.0
    val maxDistM = niceOuterScale(maxObservedDistM)
    val ringStep = maxDistM / 3.0
    val rings = listOf(ringStep, ringStep * 2, ringStep * 3)

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(BgDeep)
            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)),
    ) {
        val sizeDp = maxWidth
        val centerDp = sizeDp / 2
        val maxRadiusDp = sizeDp * 0.40f

        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerPx = Offset(centerDp.toPx(), centerDp.toPx())
            val maxRadiusPx = maxRadiusDp.toPx()

            rings.forEach { ringM ->
                val r = (ringM / maxDistM).toFloat() * maxRadiusPx
                drawCircle(color = BorderSubtle, radius = r, center = centerPx, style = Stroke(width = 1.dp.toPx()))
            }
            drawLine(BorderSubtle, Offset(centerPx.x - maxRadiusPx - 10.dp.toPx(), centerPx.y), Offset(centerPx.x + maxRadiusPx + 10.dp.toPx(), centerPx.y), strokeWidth = 1.dp.toPx())
            drawLine(BorderSubtle, Offset(centerPx.x, centerPx.y - maxRadiusPx - 10.dp.toPx()), Offset(centerPx.x, centerPx.y + maxRadiusPx + 10.dp.toPx()), strokeWidth = 1.dp.toPx())

            // Você, no centro
            drawCircle(color = AccentCyan, radius = 5.dp.toPx(), center = centerPx)
            drawCircle(
                color = AccentCyan,
                radius = 5.dp.toPx() + 3.dp.toPx() + youPulse * 4.dp.toPx(),
                center = centerPx,
                alpha = 0.35f * (1f - youPulse),
                style = Stroke(width = 2.dp.toPx()),
            )

            points.forEach { p ->
                val rPx = ((p.distanceM / maxDistM).toFloat().coerceAtMost(1f)) * maxRadiusPx
                val rad = Math.toRadians(p.bearingDeg - 90.0)
                val x = centerPx.x + rPx * cos(rad).toFloat()
                val y = centerPx.y + rPx * sin(rad).toFloat()
                val towerPos = Offset(x, y)
                val color = if (p.entry.isKnown) QualityExcel else QualityFair
                val dotRadiusPx = (6 + (p.entry.sampleCount.coerceAtMost(20) * 0.25)).dp.toPx()

                if (!p.entry.isKnown) {
                    drawCircle(
                        color = color,
                        radius = dotRadiusPx + 4.dp.toPx() + pulse * 5.dp.toPx(),
                        center = towerPos,
                        alpha = 0.28f * (1f - pulse),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
                drawCircle(color = color, radius = dotRadiusPx, center = towerPos)
                drawCircle(color = BgDeep, radius = dotRadiusPx, center = towerPos, style = Stroke(width = 1.5.dp.toPx()))
            }
        }

        rings.forEach { ringM ->
            val rDp = maxRadiusDp * (ringM / maxDistM).toFloat()
            Text(
                "${ringM.toInt()}m",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = TextDim,
                modifier = Modifier.offset(x = centerDp + rDp + 4.dp, y = centerDp - 11.dp),
            )
        }
        Text(
            "você",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            modifier = Modifier.offset(x = centerDp + 9.dp, y = centerDp + 4.dp),
        )
    }
}

@Composable
private fun TowerRow(point: RadarPoint, onOpenInMaps: () -> Unit) {
    val entry = point.entry
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
            .clickable(onClick = onOpenInMaps)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(towerDisplayName(entry), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                ConfidenceChip(isKnown = entry.isKnown, sampleCount = entry.sampleCount, spreadM = entry.spreadM)
            }
            Text(
                "${entry.networkType} · ${formatDistance(point.distanceM)} · ${formatBearingCompass(point.bearingDeg)}",
                fontSize = 11.sp,
                color = TextMuted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(Icons.AutoMirrored.Filled.OpenInNew, "Abrir no mapa", tint = AccentBlue, modifier = Modifier.size(18.dp))
    }
}

/** "Conhecida" exige DUAS condições em TowerPositionMath.classify(): amostras
 *  suficientes E espalhamento espacial real — mostrar só "N/5 amostras" (o
 *  antigo rótulo) mentia sobre o que falta quando uma torre já tinha 20+
 *  amostras mas ainda não tinha espalhamento (usuário parado no mesmo lugar,
 *  o cenário que o próprio classify() existe pra rejeitar). Mostra os dois
 *  critérios reais em vez de um só. */
@Composable
private fun ConfidenceChip(isKnown: Boolean, sampleCount: Int, spreadM: Double) {
    val color = if (isKnown) QualityExcel else QualityFair
    val label = if (isKnown) {
        "conhecida"
    } else {
        val samples = sampleCount.coerceAtMost(TowerPositionMath.MIN_SAMPLES)
        val spread = spreadM.toInt().coerceAtMost(TowerPositionMath.MIN_SPREAD_METERS.toInt())
        "$samples/${TowerPositionMath.MIN_SAMPLES} · ${spread}/${TowerPositionMath.MIN_SPREAD_METERS.toInt()}m"
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(label, fontSize = 9.5.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

// ── Helpers de exibição ──────────────────────────────────────────────────

/** towerKey é "operadora:rede:tac:cellId:pci" (ver TowerTriangulationController)
 *  — o primeiro campo já é o nome sanitizado da operadora, seguro de mostrar. */
private fun towerDisplayName(entry: TowerDisplayEntry): String {
    val operator = entry.towerKey.substringBefore(':').takeIf { it.isNotBlank() } ?: "Torre"
    return if (entry.pci != null) "$operator · PCI ${entry.pci}" else "$operator · Cell ${entry.cellId}"
}

private fun formatDistance(meters: Double): String =
    if (meters >= 1000) "%.1f km".format(java.util.Locale.US, meters / 1000.0) else "${meters.toInt()} m"

private fun formatBearingCompass(deg: Double): String {
    val points = listOf("N", "NE", "L", "SE", "S", "SO", "O", "NO")
    val index = (((deg + 22.5) / 45.0).toInt()) % 8
    return points[index]
}

/** Escala do anel MAIS EXTERNO do radar — sempre um múltiplo de 3 "redondo"
 *  (150/300/600/1500...), pra cada um dos 3 anéis cair num valor legível
 *  (50/100/150, ou 100/200/300, etc.) e o mais externo bater exatamente na
 *  borda do Canvas, nunca além dela. */
private fun niceOuterScale(maxObservedDistM: Double): Double {
    val scales = listOf(150.0, 300.0, 600.0, 1_500.0, 3_000.0, 6_000.0, 15_000.0, 30_000.0)
    return scales.firstOrNull { it >= max(maxObservedDistM, 1.0) } ?: scales.last()
}

private fun openInMaps(context: android.content.Context, lat: Double, lon: Double, label: String) {
    try {
        val uri = "geo:$lat,$lon?q=$lat,$lon(${Uri.encode(label)})".toUri()
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Nenhum app de mapas encontrado.", Toast.LENGTH_SHORT).show()
    }
}
