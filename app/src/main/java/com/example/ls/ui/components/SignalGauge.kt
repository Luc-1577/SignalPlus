package com.example.ls.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ls.data.*
import com.example.ls.ui.theme.*
import kotlin.math.*

@Composable
fun SignalGauge(cell: CellSignalData?, modifier: Modifier = Modifier) {
    val dbm      = cell?.dbm ?: 0
    val netType  = cell?.networkType ?: NetworkType.UNKNOWN
    val quality  = if (cell != null) signalQuality(dbm, netType) else SignalQuality.NO_SIGNAL
    val progress = if (cell != null) signalProgress(dbm, netType) else 0f

    val animProgress by animateFloatAsState(progress, tween(900, easing = FastOutSlowInEasing), label = "gp")
    val animColor by animateColorAsState(quality.color, tween(600), label = "gc")
    val glowPulse by rememberInfiniteTransition(label = "glow").animateFloat(
            0.12f, 0.22f,
            infiniteRepeatable(tween(1400, easing = EaseInOutSine), RepeatMode.Reverse),
            label = "ga"
    )

    Box(contentAlignment = Alignment.Center, modifier = modifier.size(280.dp)) {
        Canvas(modifier = Modifier.fillMaxSize().padding(18.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val outerR = min(size.width, size.height) / 2f
            val trackW = 14.dp.toPx()
            val startAngle = 145f
            val totalSweep = 250f
            val filledSweep = totalSweep * animProgress
            val arcSize = Size((outerR - trackW / 2f) * 2f, (outerR - trackW / 2f) * 2f)
            val arcOffset = Offset(cx - arcSize.width / 2f, cy - arcSize.height / 2f)

            // 1. Trilha de fundo
            drawArc(
                color = BgCard,
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = arcOffset,
                size = arcSize,
                style = Stroke(trackW, cap = StrokeCap.Round)
            )

            if (animProgress > 0.02f) {
                // 2. Glow externo pulsante
                drawArc(
                    color = animColor.copy(alpha = glowPulse),
                    startAngle = startAngle,
                    sweepAngle = filledSweep,
                    useCenter = false,
                    topLeft = arcOffset,
                    size = arcSize,
                    style = Stroke(trackW * 2.8f, cap = StrokeCap.Round)
                )
            }
            if (animProgress > 0.01f) {
                // 3. Arco principal
                drawArc(
                    color = animColor,
                    startAngle = startAngle,
                    sweepAngle = filledSweep,
                    useCenter = false,
                    topLeft = arcOffset,
                    size = arcSize,
                    style = Stroke(trackW, cap = StrokeCap.Round)
                )
                // 4. Brilho interno
                val innerArcSize = Size(arcSize.width - trackW * 0.65f, arcSize.height - trackW * 0.65f)
                val innerArcOffset = Offset(arcOffset.x + trackW * 0.325f, arcOffset.y + trackW * 0.325f)
                drawArc(
                    color = animColor.copy(alpha = 0.55f),
                    startAngle = startAngle,
                    sweepAngle = filledSweep,
                    useCenter = false,
                    topLeft = innerArcOffset,
                    size = innerArcSize,
                    style = Stroke(trackW * 0.35f, cap = StrokeCap.Round)
                )
                // 5. Ponto luminoso na ponta
                val tipAngle = Math.toRadians((startAngle + filledSweep).toDouble())
                val tipR = outerR - trackW / 2f
                val tip = Offset(cx + tipR * cos(tipAngle).toFloat(), cy + tipR * sin(tipAngle).toFloat())
                drawCircle(animColor, trackW * 0.55f, tip)
                drawCircle(Color.White.copy(alpha = 0.6f), trackW * 0.22f, tip)
            }
            // 6. Ticks de escala
            listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { frac ->
                val a = Math.toRadians((startAngle + totalSweep * frac).toDouble())
                drawLine(
                    color = BorderDefault,
                    start = Offset(cx + (outerR - trackW * 1.25f) * cos(a).toFloat(), cy + (outerR - trackW * 1.25f) * sin(a).toFloat()),
                    end = Offset(cx + (outerR - trackW * 0.15f) * cos(a).toFloat(), cy + (outerR - trackW * 0.15f) * sin(a).toFloat()),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (quality == SignalQuality.NO_SIGNAL || cell == null) {
                Text("—", fontSize = 56.sp, fontWeight = FontWeight.ExtraLight, color = TextMuted)
                Spacer(Modifier.height(4.dp))
                Text("SEM SINAL", fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp, color = TextDim)
            } else {
                Text("$dbm", fontSize = 54.sp, fontWeight = FontWeight.ExtraBold, color = animColor)
                Text("dBm", fontSize = 12.sp, fontWeight = FontWeight.Normal, letterSpacing = 2.sp, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                NetworkBadge(type = netType)
            }
        }
    }
}
