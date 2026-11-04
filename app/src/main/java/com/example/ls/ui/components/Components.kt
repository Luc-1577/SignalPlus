package com.example.ls.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ls.data.*
import com.example.ls.ui.theme.*
import java.util.Locale

/** Formata um intervalo em ms de forma legível ("500ms", "1 segundo", "5 segundos"). */
fun formatIntervalLabel(ms: Long): String = when {
    ms < 1000L      -> "${ms}ms"
    ms % 1000L == 0L -> (ms / 1000L).let { s -> if (s == 1L) "1 segundo" else "$s segundos" }
    else            -> "%.1fs".format(Locale.US, ms / 1000.0)
}

/** Formata um tamanho em bytes de forma legível ("114 B", "2.3 KB", "1.1 MB"). */
fun formatFileSize(bytes: Long): String = when {
    bytes < 1024L        -> "$bytes B"
    bytes < 1024L * 1024 -> "%.1f KB".format(Locale.US, bytes / 1024.0)
    else                 -> "%.1f MB".format(Locale.US, bytes / (1024.0 * 1024.0))
}

@Composable
fun NetworkBadge(type: NetworkType, modifier: Modifier = Modifier, showLabel: Boolean = true) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier
            .clip(RoundedCornerShape(100.dp))
            .background(type.badgeColor.copy(alpha = 0.18f))
            .border(1.dp, type.badgeColor.copy(alpha = 0.45f), RoundedCornerShape(100.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(type.generation, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = type.badgeTextColor, letterSpacing = 0.5.sp)
        if (showLabel) {
            // Sem .copy(alpha = 0.85f) aqui: badgeTextColor já foi calculado pro
            // contraste mínimo a 100% de opacidade — aplicar alpha por cima
            // devolveria o texto pro mesmo problema que a variante resolve.
            Text(type.label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = type.badgeTextColor)
        }
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

/** Cartão padrão usado por cada seção de Configurações/Exportações — mesmo
 *  fundo/borda/raio repetidos em toda seção daquelas telas. */
@Composable
fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BgCard)
            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
            .padding(16.dp),
        content = content,
    )
}

/** Fileira de opções em formato de "chip" com uma selecionada por vez — usada
 *  pelos seletores de SIM, intervalo de leitura e retenção de histórico em
 *  SettingsScreen, que tinham o mesmo Box/clip/background/border/clickable
 *  repetido 3x quase idêntico antes desta extração. */
@Composable
fun <T> SelectableChipRow(
    options: List<Pair<T, String>>,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    verticalPadding: Dp = 12.dp,
    fontSize: TextUnit = 14.sp,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.fillMaxWidth()) {
        options.forEach { (value, label) ->
            val selected = isSelected(value)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.weight(1f)
                    // Diferente de Button/IconButton/Switch do M3 (que usam
                    // Surface por baixo e por isso já garantem 48dp), este Box
                    // cru não tinha nenhum piso de altura — o chip de retenção
                    // (padding menor) ficava com ~34dp de área de toque real,
                    // abaixo do mínimo recomendado (WCAG 2.5.5 / Material).
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) AccentBlue else BgCardAlt)
                    .border(1.dp, if (selected) AccentBlue else BorderSubtle, RoundedCornerShape(12.dp))
                    .clickable { onSelect(value) }
                    .padding(vertical = verticalPadding),
            ) {
                Text(label, fontSize = fontSize, fontWeight = FontWeight.Bold, color = if (selected) TextPrimary else TextSecondary)
            }
        }
    }
}

/** Campo de texto numérico + botão "Aplicar" — usado pelo intervalo de leitura
 *  personalizado e pela retenção de histórico personalizada em SettingsScreen,
 *  que tinham o mesmo OutlinedTextField+Button repetido antes desta extração.
 *  O clamping/coerção de valor fica por conta de [onApply] (cada chamador tem
 *  seu próprio piso/sentinela), não deste composable. */
@Composable
fun CustomNumericField(
    text: String,
    onTextChange: (String) -> Unit,
    label: String,
    onApply: (Long) -> Unit,
    modifier: Modifier = Modifier,
    maxDigits: Int = 6,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = text,
            onValueChange = { onTextChange(it.filter(Char::isDigit).take(maxDigits)) },
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = AccentBlue,
                unfocusedBorderColor = BorderSubtle,
                focusedLabelColor = AccentBlue,
                unfocusedLabelColor = TextMuted,
            ),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Button(
            onClick = { text.toLongOrNull()?.let(onApply) },
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
        ) { Text("Aplicar") }
    }
}

/** Diálogo padrão de confirmação para ações destrutivas irreversíveis (apagar
 *  histórico, estimativas de torre, ou um export individual). */
@Composable
fun DestructiveConfirmDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    extraContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(text)
                extraContent?.invoke(this)
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = QualityVeryPoor),
            ) { Text("Apagar") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}