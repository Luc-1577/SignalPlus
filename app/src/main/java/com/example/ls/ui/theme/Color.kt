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
// QualityVeryPoor: #EF4545 original batia só 4.43:1 contra BgCard (abaixo do
// mínimo AA de 4.5:1 pra texto pequeno) quando usada como texto de erro em
// ExportManagerScreen — ajuste mínimo de luminosidade (imperceptível a olho
// nu) fecha a lacuna sem mudar o tom percebido de "vermelho de alerta".
val QualityVeryPoor = Color(0xFFEF4848)
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

// Variantes SÓ para o texto do NetworkBadge (Net2G/3G/4G ficam abaixo de
// 4.5:1 de contraste WCAG AA contra o fundo real do badge — a própria cor a
// 18% de opacidade sobre BgCard, não BgCard puro; Net5G já passa, 4.84:1,
// sem precisar de variante). Mesmo hue/saturação da cor original, só
// levantando a luminosidade via busca binária até bater 4.5:1 contra esse
// fundo — mesma técnica already usada para TextMuted/TextDim/QualityNone.
// badgeColor em si (fundo/borda do chip) fica como está: não é texto, WCAG
// 1.4.3 não se aplica a preenchimento decorativo.
val Net2GText = Color(0xFF8795A9)
val Net3GText = Color(0xFFA47FF8)
val Net4GText = Color(0xFF5C97F8)