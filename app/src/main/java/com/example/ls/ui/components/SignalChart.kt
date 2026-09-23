package com.example.ls.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ls.data.SignalPoint
import com.example.ls.data.signalQuality
import com.example.ls.ui.theme.*

@Composable
fun SignalChart(points: List<SignalPoint>, minDbm: Int, maxDbm: Int, modifier: Modifier = Modifier) {
    if (points.isEmpty()) {
        Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)).background(BgCard).border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Aguardando leituras…", fontSize = 14.sp, color = TextMuted, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text("O gráfico aparecerá após as primeiras leituras", fontSize = 12.sp, color = TextDim)
            }
        }
        return
    }

    // remember(0f) + LaunchedEffect(Unit), não animateFloatAsState(1f): o
    // Animatable interno de animateFloatAsState(targetValue) já nasce em
    // targetValue na primeira composição — animateTo(1f) partindo de 1f não
    // interpola nada, então a linha "entrando" nunca de fato animava (bug).
    // LaunchedEffect(Unit), não LaunchedEffect(points): points muda a cada
    // tick de poll (até 5x/s) enquanto a tela fica aberta — reanimar a cada
    // leitura nova faria o gráfico "resetar" continuamente em vez de entrar
    // uma vez só ao montar a tela (Unit dispara uma vez por entrada na
    // composição, ex.: trocar de aba e voltar para Histórico).
    val drawProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        drawProgress.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
    }

    Canvas(modifier = modifier.fillMaxWidth().height(220.dp)) {
        val w = size.width; val h = size.height
        val padL = 52f; val padR = 16f; val padT = 16f; val padB = 32f
        val chartW = w - padL - padR; val chartH = h - padT - padB
        val dbmRange = (maxDbm - minDbm).toFloat().coerceAtLeast(1f)
        val n = points.size

        fun xOf(i: Int) = padL + (i.toFloat() / (n - 1).coerceAtLeast(1)) * chartW
        fun yOf(dbm: Int) = padT + chartH - ((dbm - minDbm) / dbmRange) * chartH

        // Grid
        listOf(minDbm, (minDbm + maxDbm) / 2, maxDbm).forEach { dbm ->
            drawLine(BorderSubtle, Offset(padL, yOf(dbm)), Offset(w - padR, yOf(dbm)), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        }

        val visible = points.takeLast((n * drawProgress.value).toInt().coerceAtLeast(2))
        val off = n - visible.size
        if (visible.size < 2) return@Canvas

        // Linha Bézier
        val linePath = Path().apply {
            visible.forEachIndexed { idx, pt ->
                val ri = off + idx; val x = xOf(ri); val y = yOf(pt.dbm)
                if (idx == 0) moveTo(x, y)
                else { val px = xOf(ri - 1); val py = yOf(visible[idx - 1].dbm); val cp = (x - px) * 0.4f; cubicTo(px + cp, py, x - cp, y, x, y) }
            }
        }

        // Fill
        drawPath(Path().apply {
            addPath(linePath)
            lineTo(xOf(off + visible.size - 1), padT + chartH)
            lineTo(xOf(off), padT + chartH); close()
        }, Brush.verticalGradient(listOf(AccentBlue.copy(alpha = 0.25f), Color.Transparent), padT, padT + chartH))

        // Média tracejada
        val avgY = yOf(visible.map { it.dbm }.average().toInt())
        drawLine(AccentCyan.copy(alpha = 0.5f), Offset(padL, avgY), Offset(w - padR, avgY), 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f)))

        // Linha principal
        drawPath(linePath, AccentBlue, style = Stroke(2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Pontos coloridos (só se não tiver muitos)
        if (n <= 30) {
            visible.forEachIndexed { idx, pt ->
                val ri = off + idx; val c = Offset(xOf(ri), yOf(pt.dbm)); val q = signalQuality(pt.dbm, pt.networkType)
                drawCircle(q.color, 4f, c); drawCircle(BgDeep, 2f, c)
            }
        }
    }
}

@Composable
fun ChartYLabels(minDbm: Int, maxDbm: Int, modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.SpaceBetween, modifier = modifier.height(220.dp).padding(top = 16.dp, bottom = 32.dp)) {
        listOf(maxDbm, (minDbm + maxDbm) / 2, minDbm).forEach { dbm ->
            Text("$dbm", fontSize = 9.sp, color = TextDim, fontWeight = FontWeight.Medium)
        }
    }
}