# Módulo 06 · Aula 01 — Design System: Paleta, Tipografia e Material 3

> **Objetivo:** Criar um tema dark profissional e entender por que design consistente importa.

---

## O que é um Design System?

Um Design System é o conjunto de decisões visuais **consistentes** do app:
- Quais cores usar (e quando)
- Quais tamanhos de fonte usar
- Quanto espaço colocar entre elementos
- Qual forma de borda usar nos cards

Sem isso, cada componente "inventa" suas próprias cores e o resultado parece amador.

---

## 1. A paleta de cores (`Color.kt`)

### Filosofia: cores semânticas, não cores por nome

```kotlin
// ❌ Por nome — difícil de usar corretamente
val Blue900 = Color(0xFF060810)
val Blue700 = Color(0xFF141E35)
// O dev precisa SABER que Blue900 é o fundo principal. Não é óbvio.

// ✅ Semântico — o nome diz o propósito
val BgDeep   = Color(0xFF060810)   // fundo mais escuro
val BgCard   = Color(0xFF141E35)   // fundo de card
// O dev sabe exatamente QUANDO usar cada cor.
```

### A paleta completa

```kotlin
package com.example.ls.ui.theme

import androidx.compose.ui.graphics.Color

// ── Backgrounds (do mais escuro ao mais claro) ─────────────────
val BgDeep     = Color(0xFF060810)  // #060810 — quase preto, fundo da tela
val BgMid      = Color(0xFF0B0F1C)  // #0B0F1C — TopBar, BottomBar
val BgSurface  = Color(0xFF0F1628)  // #0F1628 — superfícies intermediárias
val BgCard     = Color(0xFF141E35)  // #141E35 — cards principais
val BgCardAlt  = Color(0xFF19243E)  // #19243E — cards de item (dentro de cards)

// ── Borders (do mais sutil ao mais visível) ────────────────────
val BorderSubtle  = Color(0xFF1C2B48)  // bordas quase invisíveis
val BorderDefault = Color(0xFF243354)  // bordas padrão
val BorderBright  = Color(0xFF2E4270)  // bordas de destaque (célula ativa)

// ── Text (hierarquia de legibilidade) ─────────────────────────
val TextPrimary   = Color(0xFFE2E8F7)  // texto principal — quase branco
val TextSecondary = Color(0xFF7A8EAF)  // texto secundário — cinza azulado
val TextMuted     = Color(0xFF4A5D7A)  // texto de rótulo — bem discreto
val TextDim       = Color(0xFF2E3C56)  // texto mínimo — quase invisível

// ── Accent (ações e destaques) ─────────────────────────────────
val AccentBlue   = Color(0xFF3D84F5)  // ação principal, links, indicadores
val AccentCyan   = Color(0xFF00D4FF)  // dados de frequência, gráfico de média
val AccentPurple = Color(0xFF8B5CF6)  // dados de canal (PCI, ARFCN)

// ── Signal Quality (semáforo visual — vermelho a verde) ────────
val QualityVeryPoor = Color(0xFFEF4545)  // < -110 dBm
val QualityPoor     = Color(0xFFF97020)  // -100 a -110 dBm
val QualityFair     = Color(0xFFF5C020)  // -90 a -100 dBm
val QualityGood     = Color(0xFF7ED348)  // -80 a -90 dBm
val QualityExcel    = Color(0xFF22D36A)  // ≥ -80 dBm
val QualityNone     = Color(0xFF3A4A65)  // sem sinal

// ── Network Generation Badges ──────────────────────────────────
val Net2G = Color(0xFF64748B)  // cinza — 2G é legado
val Net3G = Color(0xFF8B5CF6)  // roxo — 3G
val Net4G = Color(0xFF3B82F6)  // azul — 4G/LTE
val Net5G = Color(0xFF10B981)  // verde esmeralda — 5G (mais novo, melhor)
```

### A lógica das cores de qualidade

A escala de vermelho → verde segue a percepção humana natural:
- **Vermelho** = perigo, problema
- **Laranja** = atenção
- **Amarelo** = alerta
- **Verde claro** = bom
- **Verde vibrante** = excelente

Isso não é arbitrário — é baseado em pesquisa de UX sobre cognição de cores.

---

## 2. O tema Material 3 (`Theme.kt`)

```kotlin
package com.example.ls.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Color Scheme ──────────────────────────────────────────────
private val DarkColorScheme = darkColorScheme(
    // Cor primária — usada em botões, FABs, indicadores de seleção
    primary             = AccentBlue,
    onPrimary           = Color.White,
    primaryContainer    = BgCard,
    onPrimaryContainer  = TextPrimary,

    // Cor secundária
    secondary           = AccentCyan,
    onSecondary         = Color.White,

    // Cor terciária
    tertiary            = AccentPurple,

    // Fundo e superfícies
    background          = BgDeep,
    onBackground        = TextPrimary,
    surface             = BgSurface,
    onSurface           = TextPrimary,
    surfaceVariant      = BgCard,
    onSurfaceVariant    = TextSecondary,

    // Bordas
    outline             = BorderDefault,
    outlineVariant      = BorderSubtle,

    // Erros
    error               = QualityVeryPoor,
    onError             = Color.White,
)

// ── Tipografia ────────────────────────────────────────────────
private val AppTypography = Typography(
    // Display — números grandes como o dBm no gauge
    displayLarge  = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-2).sp),
    displayMedium = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.Bold,      letterSpacing = (-1).sp),
    displaySmall  = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Bold),

    // Headlines — títulos de seção
    headlineLarge  = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall  = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),

    // Title — títulos de cards e itens
    titleLarge  = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    titleSmall  = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium,   letterSpacing = 0.1.sp),

    // Body — texto de conteúdo
    bodyLarge   = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp),
    bodyMedium  = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    bodySmall   = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp),

    // Label — rótulos e badges
    labelLarge  = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
    labelMedium = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium,   letterSpacing = 0.8.sp),
    labelSmall  = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium,   letterSpacing = 1.sp),
)

// ── SignalPlusTheme ──────────────────────────────────────────
@Composable
fun SignalPlusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = AppTypography,
        content     = content,
    )
}
```

---

## 3. Hierarquia visual — como os elementos se organizam visualmente

O segredo de um design bonito é **hierarquia clara**:

```
Nível 1 — O mais importante (maior, mais brilhante):
  → Valor dBm: 54sp ExtraBold, cor de qualidade vibrante

Nível 2 — Contexto (médio, secundário):
  → "dBm", Nome da operadora: 12-15sp, TextSecondary

Nível 3 — Suporte (pequeno, muito discreto):
  → Labels dos campos: 9-10sp, TextMuted, UPPERCASE

Nível 4 — Estrutural (quase invisível):
  → Bordas, separadores: BorderSubtle ou BorderDefault
```

Aplicando ao `MetricCard`:

```kotlin
// Label: nível 3 — discreto
Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
     color = TextMuted, letterSpacing = 1.sp)

// Valor: nível 1 — destaque
Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = accent)
```

---

## 4. Espaçamento consistente

Use múltiplos de 4dp para espaçamento (padrão do Material Design):

```
4dp  — separação mínima entre elementos relacionados
8dp  — separação padrão dentro de um grupo
12dp — separação entre grupos de elementos
16dp — padding interno de cards
20dp — padding horizontal de telas
24dp — separação entre seções maiores
```

No projeto:
```kotlin
contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
verticalArrangement = Arrangement.spacedBy(20.dp)   // 20dp entre seções
```

---

## 5. Profundidade com layers de fundo

O efeito "profundidade" do tema escuro vem das camadas de fundo:

```
Camada mais escura:  BgDeep   #060810  ← fundo da tela
Camada média:        BgMid    #0B0F1C  ← TopBar, BottomBar (parece flutuar)
Superfície:          BgCard   #141E35  ← cards (mais claro que o fundo)
Superfície interna:  BgCardAlt #19243E ← itens dentro de cards
```

Cada camada é apenas ~5% mais clara que a anterior — sutil, mas cria profundidade.

---

## 6. Gradiente radial de qualidade

Na aba de sinal, há um efeito de "aura" ao redor do gauge:

```kotlin
Box(
    modifier = Modifier
        .fillMaxWidth()
        .background(
            Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to quality.color.copy(alpha = 0.09f),  // 9% opacidade no centro
                    0.7f to Color.Transparent,                    // transparente na borda
                ),
                radius = 500f,
            )
        ),
) {
    SignalGauge(...)
}
```

O resultado: quando o sinal é verde, há uma "aura verde" suave ao redor do gauge.
Quando é vermelho, a aura é vermelha. Reforça o estado sem ser excessivo.

---

## 7. Padrão de cards

Todos os cards seguem o mesmo padrão:

```kotlin
Modifier
    .fillMaxWidth()
    .clip(RoundedCornerShape(16.dp))     // cantos com 16dp
    .background(BgCard)                  // fundo de card
    .border(1.dp, BorderSubtle,          // borda discreta
            RoundedCornerShape(16.dp))
    .padding(16.dp)                      // padding interno 16dp
```

Isso cria consistência visual — todos os cards "pertencem" ao mesmo design.

---

## 8. Texto em maiúsculas para rótulos

```kotlin
Text("IDENTIFICADORES DE CÉLULA", fontSize = 10.sp, fontWeight = FontWeight.Bold,
     color = TextMuted, letterSpacing = 1.5.sp)
```

**Por que uppercase com letterSpacing?** É uma técnica tipográfica clássica para rótulos
— o espaço entre letras compensa a densidade visual das maiúsculas, tornando o texto
mais legível em tamanhos pequenos.

---

## 🧪 Desafios

### Desafio 1 — Paleta alternativa
Crie uma variação do tema com fundo azul-marinho (como o projeto usa) mas com acentos
em verde em vez de azul. Ajuste as 5 cores de acento.

### Desafio 2 — Componente novo
Usando o design system do projeto, crie um `OperadoraCard(nome: String, dbm: Int, networkType: NetworkType)`
com o padrão visual dos outros cards.

### Desafio 3 — Light mode
O projeto só tem dark mode. Crie um `LightColorScheme` alternativo com:
- Fundo branco/cinza claro
- Texto escuro
- Bordas sutis
- Mesmos acentos

---

## 📌 Resumo

```
Color.kt → paleta semântica (por função, não por nome)
Theme.kt → darkColorScheme + Typography + MaterialTheme

Hierarquia visual:
  Level 1: dado principal   → grande, colorido (quality.color)
  Level 2: contexto         → médio, TextSecondary
  Level 3: rótulo           → pequeno, TextMuted, UPPERCASE
  Level 4: estrutural       → invisível, BorderSubtle

Backgrounds:
  BgDeep → BgMid → BgCard → BgCardAlt (cada um ~5% mais claro)
```

**Próximo Módulo →** [Testes](../modulo-07-senior/01-testes.md) — garantindo que o código continua funcionando.
