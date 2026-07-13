package com.example.ls.ui.components

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ls.ui.theme.AccentCyan
import com.example.ls.ui.theme.TextPrimary

/**
 * Marca de assinatura do app: um retículo de aquisição de sinal (duas hastes
 * cruzadas com respiro central), pulsando no mesmo ritmo do LiveDot da
 * TopBar (1100ms, EaseInOutSine) — o "+" de "Signal+" não é um glifo estático,
 * é o mesmo gesto visual usado para indicar leitura ao vivo em outros pontos do app.
 */
@Composable
fun SignalPlusMark(size: Dp = 14.dp, modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition(label = "plus_mark").animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_100, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "plus_mark_alpha",
    )
    Canvas(modifier = modifier.size(size)) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val strokeW = this.size.minDimension * 0.16f
        val inset = strokeW / 2f
        val gap = this.size.minDimension * 0.20f
        val core = AccentCyan.copy(alpha = 0.8f + pulse * 0.2f)
        val glow = AccentCyan.copy(alpha = pulse * 0.30f)

        drawCircle(color = glow, radius = this.size.minDimension * 0.62f, center = Offset(cx, cy))

        drawLine(core, Offset(cx, inset), Offset(cx, cy - gap), strokeW, StrokeCap.Round)
        drawLine(core, Offset(cx, cy + gap), Offset(cx, this.size.height - inset), strokeW, StrokeCap.Round)
        drawLine(core, Offset(inset, cy), Offset(cx - gap, cy), strokeW, StrokeCap.Round)
        drawLine(core, Offset(cx + gap, cy), Offset(this.size.width - inset, cy), strokeW, StrokeCap.Round)
    }
}

/** Lockup "Signal" + [SignalPlusMark], usado no lugar do antigo texto estático "Signal Meter". */
@Composable
fun Wordmark(fontSize: TextUnit = 17.sp, markSize: Dp = 14.dp, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Signal",
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp,
            color = TextPrimary,
        )
        Spacer(Modifier.width(4.dp))
        SignalPlusMark(size = markSize)
    }
}
