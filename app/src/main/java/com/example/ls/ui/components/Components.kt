package com.example.ls.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ls.data.*
import com.example.ls.ui.theme.*

@Composable
fun NetworkBadge(type: NetworkType, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier
            .clip(RoundedCornerShape(100.dp))
            .background(type.badgeColor.copy(alpha = 0.18f))
            .border(1.dp, type.badgeColor.copy(alpha = 0.45f), RoundedCornerShape(100.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(type.generation, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = type.badgeColor, letterSpacing = 0.5.sp)
        Text(type.label,      fontSize = 11.sp, fontWeight = FontWeight.Medium,    color = type.badgeColor.copy(alpha = 0.85f))
    }
}

@Composable
fun QualityBadge(quality: SignalQuality, modifier: Modifier = Modifier) {
    AnimatedContent(quality, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) }, modifier = modifier, label = "qb") { q ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(q.color))
            Text(q.label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = q.color, letterSpacing = 1.5.sp)
        }
    }
}

@Composable
fun SignalBars(level: Int, color: Color, modifier: Modifier = Modifier, barCount: Int = 5, barWidth: Dp = 5.dp, barGap: Dp = 2.dp, maxHeight: Dp = 20.dp) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(barGap), modifier = modifier) {
        repeat(barCount) { idx ->
            Box(modifier = Modifier.width(barWidth).height(maxHeight * ((idx + 1f) / barCount)).clip(RoundedCornerShape(2.dp))
                .background(if (idx < level) color else color.copy(alpha = 0.18f)))
        }
    }
}

@Composable
fun MetricCard(label: String, value: String, modifier: Modifier = Modifier, accent: Color = AccentBlue) {
    Column(modifier = modifier.clip(RoundedCornerShape(14.dp)).background(BgCard).border(1.dp, BorderSubtle, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = TextMuted, letterSpacing = 1.sp)
        Spacer(Modifier.height(6.dp))
        AnimatedContent(value, transitionSpec = { slideInVertically { it / 2 } + fadeIn() togetherWith slideOutVertically { -it / 2 } + fadeOut() }, label = "mc_$label") { v ->
            Text(v, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun CellRow(cell: CellSignalData, modifier: Modifier = Modifier) {
    val quality = signalQuality(cell.dbm, cell.networkType)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (cell.isRegistered) BgCardAlt else BgCard)
            .border(1.dp, if (cell.isRegistered) BorderBright else BorderSubtle, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Icon(Icons.Default.CellTower, null, tint = if (cell.isRegistered) quality.color else TextMuted, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(cell.networkType.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (cell.isRegistered) TextPrimary else TextSecondary)
                if (cell.isRegistered) {
                    Box(modifier = Modifier.clip(RoundedCornerShape(100.dp)).background(quality.color.copy(alpha = 0.15f)).border(1.dp, quality.color.copy(alpha = 0.4f), RoundedCornerShape(100.dp)).padding(horizontal = 7.dp, vertical = 2.dp)) {
                        Text("conectado", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = quality.color, letterSpacing = 0.5.sp)
                    }
                }
            }
            Text(cell.operator, fontSize = 11.sp, color = TextMuted)
        }
        Column(horizontalAlignment = Alignment.End) {
            SignalBars(cell.level, quality.color, barCount = 5)
            Spacer(Modifier.height(4.dp))
            Text("${cell.dbm} dBm", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = quality.color)
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(title.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.8.sp, modifier = modifier.padding(bottom = 10.dp))
}