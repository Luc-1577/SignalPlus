# Módulo 05 · Aula 06 — Componentes Visuais: Gauge, Chart e Cards

> **Objetivo:** Implementar os componentes de visualização mais avançados do app.

---

## Filosofia dos componentes

Todos os componentes em `ui/components/` seguem um princípio único:

> **Receber apenas dados primitivos. Não acessar o ViewModel. Não ter estado próprio.**

```kotlin
// ✅ Correto — componente puro
@Composable
fun SignalGauge(cell: CellSignalData?, modifier: Modifier = Modifier)

// ❌ Errado — componente que acessa o ViewModel diretamente
@Composable
fun SignalGauge() {
    val vm: SignalViewModel = viewModel()   // ← acopla o componente ao ViewModel!
    val state by vm.uiState.collectAsState()
}
```

Componentes puros são reutilizáveis em qualquer tela, testáveis com Preview, e fáceis de entender.

---

## Componente 1: `SignalBars`

As barras de sinal clássicas — simples mas com bastante expressividade:

```kotlin
@Composable
fun SignalBars(
    level    : Int,        // 0–4 barras preenchidas
    color    : Color,
    modifier : Modifier = Modifier,
    barCount : Int      = 5,
    barWidth : Dp       = 5.dp,
    barGap   : Dp       = 2.dp,
    maxHeight: Dp       = 20.dp,
) {
    Row(
        verticalAlignment = Alignment.Bottom,   // alinha todas pela base
        horizontalArrangement = Arrangement.spacedBy(barGap),
        modifier = modifier,
    ) {
        repeat(barCount) { idx ->
            Box(
                modifier = Modifier
                    .width(barWidth)
                    // Altura proporcional ao índice: barra 0 = 20%, barra 4 = 100%
                    .height(maxHeight * ((idx + 1f) / barCount))
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (idx < level) color               // preenchida = sólida
                        else color.copy(alpha = 0.18f)       // vazia = translúcida
                    ),
            )
        }
    }
}
```

**Truque visual:** `Alignment.Bottom` garante que todas as barras se alinham pela base,
criando o efeito escalonado clássico de barras de sinal.

---

## Componente 2: `NetworkBadge` e `QualityBadge`

```kotlin
@Composable
fun NetworkBadge(type: NetworkType, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier
            .clip(RoundedCornerShape(100.dp))
            // Fundo: cor com 18% opacidade
            .background(type.badgeColor.copy(alpha = 0.18f))
            // Borda: cor com 45% opacidade
            .border(1.dp, type.badgeColor.copy(alpha = 0.45f), RoundedCornerShape(100.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(type.generation, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
             color = type.badgeColor)
        Text(type.label, fontSize = 11.sp, fontWeight = FontWeight.Medium,
             color = type.badgeColor.copy(alpha = 0.85f))
    }
}

@Composable
fun QualityBadge(quality: SignalQuality, modifier: Modifier = Modifier) {
    // AnimatedContent para animar a transição de qualidade (ex.: Bom → Excelente)
    AnimatedContent(
        targetState  = quality,
        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
        modifier     = modifier,
        label        = "qb",
    ) { q ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(q.color)
            )
            Text(q.label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                 color = q.color, letterSpacing = 1.5.sp)
        }
    }
}
```

---

## Componente 3: `MetricCard`

```kotlin
@Composable
fun MetricCard(
    label   : String,
    value   : String,
    modifier: Modifier = Modifier,
    accent  : Color    = AccentBlue,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
             color = TextMuted, letterSpacing = 1.sp)
        Spacer(Modifier.height(6.dp))

        // O valor anima quando muda: entra de baixo, sai por cima
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                slideInVertically  { it / 2 } + fadeIn() togetherWith
                slideOutVertically { -it / 2 } + fadeOut()
            },
            label = "mc_$label",
        ) { v ->
            Text(
                text       = v,
                fontSize   = 20.sp,
                fontWeight = FontWeight.Bold,
                color      = accent,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
        }
    }
}
```

---

## Componente 4: `SignalGauge` (Canvas)

```kotlin
@Composable
fun SignalGauge(cell: CellSignalData?, modifier: Modifier = Modifier) {
    val dbm      = cell?.dbm ?: 0
    val netType  = cell?.networkType ?: NetworkType.UNKNOWN
    val quality  = if (cell != null) signalQuality(dbm, netType) else SignalQuality.NO_SIGNAL
    val progress = if (cell != null) signalProgress(dbm, netType) else 0f

    // Estados animados
    val animProgress by animateFloatAsState(progress, tween(900, easing = FastOutSlowInEasing), label = "gp")
    val animColor    by animateColorAsState(quality.color, tween(600), label = "gc")
    val glowPulse    by rememberInfiniteTransition(label = "glow").animateFloat(
        0.12f, 0.22f,
        infiniteRepeatable(tween(1400, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "ga",
    )

    Box(contentAlignment = Alignment.Center, modifier = modifier.size(280.dp)) {

        // O Canvas desenha o arco
        Canvas(modifier = Modifier.fillMaxSize().padding(18.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val outerR = min(size.width, size.height) / 2f
            val trackW = 14.dp.toPx()

            val startAngle  = 145f
            val totalSweep  = 250f
            val filledSweep = totalSweep * animProgress

            val arcR      = outerR - trackW / 2f
            val arcSize   = Size(arcR * 2f, arcR * 2f)
            val arcOffset = Offset(cx - arcR, cy - arcR)

            // 1. Trilha de fundo
            drawArc(BgCard, startAngle, totalSweep, false, arcOffset, arcSize,
                style = Stroke(trackW, cap = StrokeCap.Round))

            if (animProgress > 0.02f) {
                // 2. Glow pulsante
                drawArc(animColor.copy(alpha = glowPulse), startAngle, filledSweep,
                    false, arcOffset, arcSize,
                    style = Stroke(trackW * 2.8f, cap = StrokeCap.Round))
            }

            if (animProgress > 0.01f) {
                // 3. Arco principal
                drawArc(animColor, startAngle, filledSweep, false, arcOffset, arcSize,
                    style = Stroke(trackW, cap = StrokeCap.Round))

                // 4. Brilho interno (linha mais fina, translúcida)
                val innerR = arcR - trackW * 0.325f
                drawArc(animColor.copy(alpha = 0.55f), startAngle, filledSweep, false,
                    Offset(cx - innerR, cy - innerR), Size(innerR * 2f, innerR * 2f),
                    style = Stroke(trackW * 0.35f, cap = StrokeCap.Round))

                // 5. Ponto luminoso na ponta do arco
                val tipRad = Math.toRadians((startAngle + filledSweep).toDouble())
                val tip    = Offset(
                    cx + arcR * cos(tipRad).toFloat(),
                    cy + arcR * sin(tipRad).toFloat()
                )
                drawCircle(animColor, trackW * 0.55f, tip)
                drawCircle(Color.White.copy(alpha = 0.6f), trackW * 0.22f, tip)
            }

            // 6. Ticks de escala
            listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { frac ->
                val a = Math.toRadians((startAngle + totalSweep * frac).toDouble())
                drawLine(
                    BorderDefault,
                    Offset(cx + (outerR - trackW * 1.25f) * cos(a).toFloat(),
                           cy + (outerR - trackW * 1.25f) * sin(a).toFloat()),
                    Offset(cx + (outerR - trackW * 0.15f) * cos(a).toFloat(),
                           cy + (outerR - trackW * 0.15f) * sin(a).toFloat()),
                    1.5.dp.toPx(), cap = StrokeCap.Round,
                )
            }
        }

        // O texto fica no centro, sobre o Canvas (por causa do Box)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (quality == SignalQuality.NO_SIGNAL || cell == null) {
                Text("—", fontSize = 56.sp, fontWeight = FontWeight.ExtraLight, color = TextMuted)
                Text("SEM SINAL", fontSize = 10.sp, letterSpacing = 2.sp, color = TextDim)
            } else {
                Text("$dbm", fontSize = 54.sp, fontWeight = FontWeight.ExtraBold, color = animColor)
                Text("dBm", fontSize = 12.sp, letterSpacing = 2.sp, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                NetworkBadge(type = netType)
            }
        }
    }
}
```

---

## Componente 5: `SignalChart` (Canvas + Bézier)

```kotlin
@Composable
fun SignalChart(
    points: List<SignalPoint>,
    minDbm: Int,
    maxDbm: Int,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.fillMaxWidth().height(220.dp)
                .clip(RoundedCornerShape(16.dp)).background(BgCard),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Aguardando leituras…", fontSize = 14.sp, color = TextMuted)
                Text("O gráfico aparecerá após as primeiras leituras", fontSize = 12.sp, color = TextDim)
            }
        }
        return
    }

    // Animação de "reveal" ao aparecer
    val drawProgress by animateFloatAsState(1f, tween(800, easing = FastOutSlowInEasing), label = "chart")

    Canvas(modifier = modifier.fillMaxWidth().height(220.dp)) {
        val w = size.width; val h = size.height
        val padL = 52f; val padR = 16f; val padT = 16f; val padB = 32f
        val chartW = w - padL - padR; val chartH = h - padT - padB
        val dbmRange = (maxDbm - minDbm).toFloat().coerceAtLeast(1f)
        val n = points.size

        // Funções de mapeamento: dado → pixel
        fun xOf(i: Int) = padL + (i.toFloat() / (n - 1).coerceAtLeast(1)) * chartW
        fun yOf(dbm: Int) = padT + chartH - ((dbm - minDbm) / dbmRange) * chartH

        // Grid com linhas tracejadas
        listOf(minDbm, (minDbm + maxDbm) / 2, maxDbm).forEach { dbm ->
            drawLine(
                BorderSubtle,
                Offset(padL, yOf(dbm)), Offset(w - padR, yOf(dbm)),
                1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )
        }

        // Pontos visíveis (animação de reveal)
        val visible = points.takeLast((n * drawProgress).toInt().coerceAtLeast(2))
        val off = n - visible.size
        if (visible.size < 2) return@Canvas

        // Linha Bézier cúbica (curva suave entre pontos)
        val linePath = Path().apply {
            visible.forEachIndexed { idx, pt ->
                val ri = off + idx; val x = xOf(ri); val y = yOf(pt.dbm)
                if (idx == 0) {
                    moveTo(x, y)
                } else {
                    val px = xOf(ri - 1); val py = yOf(visible[idx - 1].dbm)
                    val cp = (x - px) * 0.4f   // ponto de controle = 40% da distância
                    cubicTo(px + cp, py, x - cp, y, x, y)
                }
            }
        }

        // Fill com gradiente abaixo da linha
        drawPath(
            Path().apply {
                addPath(linePath)
                lineTo(xOf(off + visible.size - 1), padT + chartH)
                lineTo(xOf(off), padT + chartH)
                close()
            },
            brush = Brush.verticalGradient(
                listOf(AccentBlue.copy(alpha = 0.25f), Color.Transparent),
                padT, padT + chartH,
            ),
        )

        // Linha da média (tracejada, cyan)
        val avgY = yOf(visible.map { it.dbm }.average().toInt())
        drawLine(
            AccentCyan.copy(alpha = 0.5f),
            Offset(padL, avgY), Offset(w - padR, avgY),
            1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f)),
        )

        // Linha principal do sinal (azul sólido)
        drawPath(linePath, AccentBlue, style = Stroke(2.5f, cap = StrokeCap.Round))

        // Pontos coloridos (apenas se houver poucos — até 30)
        if (n <= 30) {
            visible.forEachIndexed { idx, pt ->
                val ri = off + idx
                val c  = Offset(xOf(ri), yOf(pt.dbm))
                val q  = signalQuality(pt.dbm, pt.networkType)
                drawCircle(q.color, 4f, c)      // ponto colorido
                drawCircle(BgDeep, 2f, c)        // "buraco" no centro
            }
        }
    }
}
```

---

## Visualizando o resultado

```
┌──────────────────────────────────────────────────────┐
│  dBm × tempo          ■ sinal  ─ média               │
│                                                      │
│ -78 ─────────────────────────────────────────────── │
│   │   ●           ●                                  │
│   │    \         / \        ●───────●                │
│ -90 ─ ─\─ ─ ─ ─/─ ─ ─\─ ─/─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ │
│   │     \     /     \  /                            │
│   │      \   /       \/                             │
│ -102 ─────────────────────────────────────────────── │
│                                  12 leituras          │
└──────────────────────────────────────────────────────┘
```

---

## 📌 Resumo dos componentes

```
SignalBars         → barras de intensidade (Canvas não necessário — só Box)
NetworkBadge       → pill com geração (2G/3G/4G/5G) e nome
QualityBadge       → label de qualidade com AnimatedContent
MetricCard         → card label + valor com animação de slide
CellRow            → linha de célula detectada (ícone + nome + barras + dBm)
SignalGauge        → arco semicircular com glow, animação e texto central
SignalChart        → gráfico de linha com Bézier, fill gradiente e média
CellIdentifiersCard → grade 3×4 de identificadores de rádio
```

**Próximo Módulo →** [Design System](../modulo-06-design/01-design-system.md) — a paleta semântica que faz o app parecer profissional.
