package com.example.ls.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ls.data.*
import com.example.ls.ui.theme.*

/**
 * Card compacto que exibe todos os identificadores de rádio de uma célula
 * em uma grade de 3 colunas dividida em 4 linhas temáticas:
 *
 *   Linha 1 — TAC/LAC | eNB/gNB/CI | Cell ID
 *   Linha 2 — PCI/PSC/BSIC | ARFCN | Banda
 *   Linha 3 — FDL | FUL | BW
 *   Linha 4 — TA | Distância aprox. | (vazio)
 *
 * A linha 4 (TA) só aparece para LTE ou se o TA não for nulo.
 * Os rótulos se adaptam automaticamente à tecnologia:
 *   TAC → LAC (2G/3G), eNB → gNB (5G) → CI (GSM), PCI → PSC → BSIC, etc.
 */
@Composable
fun CellIdentifiersCard(
    cell    : CellSignalData,
    modifier: Modifier = Modifier,
) {
    val info    = cell.netInfo
    val quality = signalQuality(cell.dbm, cell.networkType)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BgCard)
            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {

        // ── Cabeçalho ─────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 14.dp),
        ) {
            Icon(
                imageVector        = Icons.Default.Router,
                contentDescription = null,
                tint               = AccentCyan,
                modifier           = Modifier.size(15.dp),
            )
            Text(
                text          = "IDENTIFICADORES DE CÉLULA",
                fontSize      = 10.sp,
                fontWeight    = FontWeight.Bold,
                color         = TextMuted,
                letterSpacing = 1.5.sp,
            )
        }

        // ── Linha 1: TAC · eNB · Cell ID ──────────────────────────────────
        IdGroup(label = "Identidade de área") {
            IdItem(label = info.tacLabel,     value = info.tac?.toString(),          accent = AccentBlue)
            IdItem(label = info.nodeIdLabel,  value = info.enbId?.toString(),         accent = AccentBlue)
            IdItem(label = "Cell ID",         value = info.cellId?.toString(),        accent = AccentBlue)
        }

        IdSep()

        // ── Linha 2: PCI · ARFCN · Banda ──────────────────────────────────
        IdGroup(label = "Canal de rádio") {
            IdItem(label = info.pciLabel,    value = info.pci?.toString(),  accent = AccentPurple)
            IdItem(label = info.arfcnLabel,  value = info.arfcn?.toString(), accent = AccentPurple)
            IdItem(label = "Banda",          value = info.band?.let { "B$it" }, accent = AccentPurple)
        }

        IdSep()

        // ── Linha 3: FDL · FUL · BW ───────────────────────────────────────
        IdGroup(label = "Frequência / largura de banda") {
            IdItem(
                label  = "FDL",
                value  = info.fdlMhz?.let { formatMhz(it) },
                accent = AccentCyan,
            )
            IdItem(
                label  = "FUL",
                value  = when {
                    info.fulMhz != null -> formatMhz(info.fulMhz)
                    // TDD: DL = UL — apresenta o mesmo valor com indicação TDD
                    info.fdlMhz != null -> "${formatMhz(info.fdlMhz)} (TDD)"
                    else                -> null
                },
                accent = AccentCyan,
            )
            IdItem(
                label  = "BW",
                value  = info.bwKhz?.let { formatBwKhz(it) },
                accent = AccentCyan,
            )
        }

        // ── Linha 4: TA · Distância — só LTE ou quando TA disponível ──────
        val showTa = cell.networkType == NetworkType.LTE || info.timingAdvance != null
        if (showTa) {
            IdSep()
            IdGroup(label = "Timing Advance (distância à ERB)") {
                IdItem(
                    label  = "TA",
                    value  = info.timingAdvance?.toString(),
                    accent = quality.color,
                )
                IdItem(
                    label  = "Distância",
                    value  = info.timingAdvance
                        ?.let { taToDistanceMeters(it) }
                        ?.let { formatDistance(it) },
                    accent = quality.color.copy(alpha = 0.85f),
                )
                Spacer(Modifier.weight(1f))   // mantém a grade de 3 colunas
            }
        }
    }
}

// ── Composables internos ───────────────────────────────────────────────────

/** Agrupa uma linha de 3 itens com um label de seção acima. */
@Composable
private fun IdGroup(
    label  : String,
    content: @Composable RowScope.() -> Unit,
) {
    Text(
        text          = label,
        fontSize      = 9.sp,
        fontWeight    = FontWeight.Medium,
        color         = TextDim,
        letterSpacing = 0.5.sp,
        modifier      = Modifier.padding(bottom = 6.dp),
    )
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content               = content,
    )
}

/** Célula individual da grade com label + valor animado. */
@Composable
private fun RowScope.IdItem(
    label  : String,
    value  : String?,
    accent : Color,
) {
    val display = value ?: "N/D"
    Column(
        modifier = Modifier
            .weight(1f)
            // Agrupa rótulo + valor num único nó de acessibilidade — sem isso o
            // TalkBack parava duas vezes por campo ("PCI", depois "123") em vez
            // de ler "PCI, 123" de uma vez só.
            .semantics(mergeDescendants = true) {}
            .clip(RoundedCornerShape(10.dp))
            .background(BgCardAlt)
            .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Text(
            text          = label,
            fontSize      = 8.5.sp,
            fontWeight    = FontWeight.Bold,
            color         = TextMuted,
            letterSpacing = 0.7.sp,
            maxLines      = 1,
        )
        Spacer(Modifier.height(4.dp))
        AnimatedContent(
            targetState  = display,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) },
            label        = "id_$label",
        ) { v ->
            Text(
                text       = v,
                fontSize   = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color      = if (v == "N/D") TextDim else accent,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Espaçador entre grupos. */
@Composable
private fun IdSep() = Spacer(Modifier.height(10.dp))