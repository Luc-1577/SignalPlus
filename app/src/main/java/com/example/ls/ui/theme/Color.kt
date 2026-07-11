package com.example.ls.ui.theme

import androidx.compose.ui.graphics.Color

// ── Backgrounds ──────────────────────────────────────────────────────────
val BgDeep       = Color(0xFF060810)
val BgMid        = Color(0xFF0B0F1C)
val BgSurface    = Color(0xFF0F1628)
val BgCard       = Color(0xFF141E35)
val BgCardAlt    = Color(0xFF19243E)

// ── Borders ───────────────────────────────────────────────────────────────
val BorderSubtle  = Color(0xFF1C2B48)
val BorderDefault = Color(0xFF243354)
val BorderBright  = Color(0xFF2E4270)

// ── Text ──────────────────────────────────────────────────────────────────
// TextMuted/TextDim: usadas lado a lado em CellIdentifiersCard.kt (TextDim para
// labels de seção, mais sutil; TextMuted para labels de campo, mais proeminente).
// Ambas ≥4.5:1 de contraste AA contra BgCard; TextMuted foi propositalmente
// separada de TextDim (~6.7:1 vs ~4.6:1) para a hierarquia entre elas ficar
// perceptível — mesmo que isso deixe TextMuted com contraste maior que
// TextSecondary (que nunca aparece ao lado de TextMuted/TextDim, então essa
// comparação não importa na prática).
val TextPrimary   = Color(0xFFE2E8F7)
val TextSecondary = Color(0xFF7A8EAF)
val TextMuted     = Color(0xFF95A6BF)
val TextDim       = Color(0xFF7087B2)

// ── Accent ────────────────────────────────────────────────────────────────
val AccentBlue    = Color(0xFF3D84F5)
val AccentCyan    = Color(0xFF00D4FF)
val AccentPurple  = Color(0xFF8B5CF6)

// ── Qualidade do sinal ────────────────────────────────────────────────────
val QualityVeryPoor = Color(0xFFEF4545)
val QualityPoor     = Color(0xFFF97020)
val QualityFair     = Color(0xFFF5C020)
val QualityGood     = Color(0xFF7ED348)
val QualityExcel    = Color(0xFF22D36A)
val QualityNone     = Color(0xFF7188AE)

// ── Badge de tipo de rede ─────────────────────────────────────────────────
val Net2G = Color(0xFF64748B)
val Net3G = Color(0xFF8B5CF6)
val Net4G = Color(0xFF3B82F6)
val Net5G = Color(0xFF10B981)