# Módulo 04 · Aula 05 — Canvas: Desenho Livre no Compose

> **Objetivo:** Criar visualizações customizadas (gauges, gráficos, ícones) usando a API Canvas do Compose.

---

## O que é o Canvas?

O Canvas é uma área de desenho livre onde você controla pixel a pixel.
Use quando os composables padrão (Box, Column, etc.) não atendem.

```kotlin
@Composable
fun DesenhoSimples() {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
    ) {
        // `this` aqui é DrawScope
        // Temos acesso a: size, center, drawLine, drawCircle, drawArc, drawPath...
    }
}
```

---

## 1. Coordenadas e sistema de referência

O sistema de coordenadas do Canvas:

```
(0,0) ────────────────────────────► X (aumenta para direita)
  │
  │    (100, 50) ●
  │
  │              (center.x, center.y)
  │                      ●
  ▼
  Y (aumenta para baixo)
```

```kotlin
Canvas(modifier = Modifier.fillMaxSize()) {
    val cx = size.width  / 2f   // centro X
    val cy = size.height / 2f   // centro Y

    // size.width e size.height são Float (em pixels, não dp!)
}
```

**Importante:** O Canvas trabalha em pixels, não em dp. Para converter:

```kotlin
val larguraEmPixels = 16.dp.toPx()   // dentro de DrawScope
```

---

## 2. Primitivas básicas de desenho

### `drawCircle`

```kotlin
Canvas(modifier = Modifier.fillMaxSize()) {
    val cx = size.width / 2f
    val cy = size.height / 2f

    drawCircle(
        color  = Color(0xFF3D84F5),
        radius = 50f,
        center = Offset(cx, cy),
    )
}
```

### `drawLine`

```kotlin
drawLine(
    color       = Color.Gray,
    start       = Offset(0f, size.height / 2f),
    end         = Offset(size.width, size.height / 2f),
    strokeWidth = 2f,
    cap         = StrokeCap.Round,
)
```

### `drawRect`

```kotlin
drawRect(
    color   = Color(0xFF141E35),
    topLeft = Offset(10f, 10f),
    size    = Size(200f, 100f),
)
```

### `drawArc` — o elemento do gauge

```kotlin
drawArc(
    color      = Color.Blue,
    startAngle = 135f,   // ângulo de início (graus, sentido horário a partir da direita)
    sweepAngle = 270f,   // quantos graus varrer
    useCenter  = false,  // false = arco aberto; true = fatia de pizza
    topLeft    = Offset(50f, 50f),
    size       = Size(200f, 200f),
    style      = Stroke(width = 20f, cap = StrokeCap.Round),
)
```

**Sistema de ângulos:**
```
         90°
          │
  180° ───┼─── 0° (direita)
          │
         270°

startAngle = 0°   → começa na direita
startAngle = 90°  → começa embaixo
startAngle = 180° → começa na esquerda
startAngle = 135° → começa no canto inferior esquerdo (como no gauge!)
```

---

## 3. Construindo o gauge passo a passo

```kotlin
Canvas(modifier = Modifier.fillMaxSize().padding(18.dp)) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val outerR = min(size.width, size.height) / 2f
    val trackW = 14.dp.toPx()

    // Parâmetros do arco
    val startAngle  = 145f   // canto inferior esquerdo
    val totalSweep  = 250f   // arco de 250°, deixa 110° aberto embaixo
    val filledSweep = totalSweep * animProgress  // 0.0 a totalSweep

    // Geometria: o arco é desenhado inscrito num retângulo
    // O raio efetivo é outerR - trackW/2 (centro da linha)
    val arcR      = outerR - trackW / 2f
    val arcSize   = Size(arcR * 2f, arcR * 2f)
    val arcOffset = Offset(cx - arcR, cy - arcR)

    // ── PASSO 1: Trilha de fundo (arco cinza escuro completo) ────────
    drawArc(
        color      = Color(0xFF141E35),   // BgCard
        startAngle = startAngle,
        sweepAngle = totalSweep,          // arco COMPLETO
        useCenter  = false,
        topLeft    = arcOffset,
        size       = arcSize,
        style      = Stroke(trackW, cap = StrokeCap.Round),
    )

    // ── PASSO 2: Glow externo (halo colorido, mais largo que o arco) ─
    drawArc(
        color      = qualidadeCor.copy(alpha = glowAlpha),  // semitransparente
        startAngle = startAngle,
        sweepAngle = filledSweep,    // arco PREENCHIDO
        useCenter  = false,
        topLeft    = arcOffset,
        size       = arcSize,
        style      = Stroke(trackW * 2.8f, cap = StrokeCap.Round),  // mais largo!
    )

    // ── PASSO 3: Arco principal (colorido e sólido) ──────────────────
    drawArc(
        color      = qualidadeCor,
        startAngle = startAngle,
        sweepAngle = filledSweep,
        useCenter  = false,
        topLeft    = arcOffset,
        size       = arcSize,
        style      = Stroke(trackW, cap = StrokeCap.Round),
    )

    // ── PASSO 4: Brilho interno (linha mais fina dentro do arco) ─────
    val innerArcR = arcR - trackW * 0.325f
    drawArc(
        color      = qualidadeCor.copy(alpha = 0.55f),
        startAngle = startAngle,
        sweepAngle = filledSweep,
        topLeft    = Offset(cx - innerArcR, cy - innerArcR),
        size       = Size(innerArcR * 2f, innerArcR * 2f),
        style      = Stroke(trackW * 0.35f, cap = StrokeCap.Round),
    )

    // ── PASSO 5: Ponto luminoso na ponta do arco ─────────────────────
    // Trigonometria: ponto no círculo = (cx + r*cos(θ), cy + r*sin(θ))
    val tipAngleDeg = startAngle + filledSweep
    val tipAngleRad = Math.toRadians(tipAngleDeg.toDouble())
    val tipX = cx + arcR * cos(tipAngleRad).toFloat()
    val tipY = cy + arcR * sin(tipAngleRad).toFloat()
    val tip  = Offset(tipX, tipY)

    drawCircle(qualidadeCor, trackW * 0.55f, tip)                  // halo colorido
    drawCircle(Color.White.copy(alpha = 0.6f), trackW * 0.22f, tip) // ponto branco

    // ── PASSO 6: Marcações de escala (5 ticks) ────────────────────────
    listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { frac ->
        val tickAngle = Math.toRadians((startAngle + totalSweep * frac).toDouble())
        val innerLen  = trackW * 1.25f
        val outerLen  = trackW * 0.15f

        drawLine(
            color  = Color(0xFF243354),  // BorderDefault
            start  = Offset(
                cx + (outerR - innerLen) * cos(tickAngle).toFloat(),
                cy + (outerR - innerLen) * sin(tickAngle).toFloat()
            ),
            end    = Offset(
                cx + (outerR - outerLen) * cos(tickAngle).toFloat(),
                cy + (outerR - outerLen) * sin(tickAngle).toFloat()
            ),
            strokeWidth = 1.5.dp.toPx(),
            cap    = StrokeCap.Round,
        )
    }
}
```

---

## 4. `Path` — formas complexas e curvas

Para formas que não são arcos ou retângulos simples:

```kotlin
val caminho = Path().apply {
    moveTo(0f, 100f)          // começa aqui
    lineTo(100f, 0f)          // linha reta para (100, 0)
    lineTo(200f, 100f)        // linha reta para (200, 100)
    close()                   // fecha o caminho (volta ao início)
    // Resultado: triângulo
}

drawPath(caminho, color = Color.Blue)
drawPath(caminho, color = Color.White, style = Stroke(2f))
```

### Curvas Bézier cúbicas — o gráfico de linha suave

```kotlin
val path = Path().apply {
    val pontos = listOf(
        Offset(0f, 100f),
        Offset(100f, 50f),
        Offset(200f, 80f),
        Offset(300f, 20f),
    )

    moveTo(pontos[0].x, pontos[0].y)

    pontos.drop(1).forEachIndexed { idx, ponto ->
        val anterior = pontos[idx]  // ponto anterior
        val cp = (ponto.x - anterior.x) * 0.4f  // ponto de controle

        cubicTo(
            anterior.x + cp, anterior.y,  // ponto de controle 1 (tangente saída)
            ponto.x - cp, ponto.y,         // ponto de controle 2 (tangente entrada)
            ponto.x, ponto.y,              // ponto destino
        )
    }
}

drawPath(path, color = Color.Blue, style = Stroke(3f, cap = StrokeCap.Round))
```

**Por que Bézier?** Linhas retas entre pontos criam um gráfico angulado, nervoso.
Bézier cúbicas criam curvas suaves que parecem mais naturais para dados de sinal.

---

## 5. Gradiente no fill do gráfico

```kotlin
// Fill do gráfico — preenchimento com gradiente vertical
val fillPath = Path().apply {
    addPath(linePath)  // a linha do gráfico
    lineTo(pontos.last().x, alturaTotal)  // desce para a base direita
    lineTo(pontos.first().x, alturaTotal)  // vai para a base esquerda
    close()  // fecha
}

drawPath(
    path  = fillPath,
    brush = Brush.verticalGradient(
        colors    = listOf(
            Color(0xFF3D84F5).copy(alpha = 0.25f),  // azul semitransparente no topo
            Color.Transparent,                        // transparente na base
        ),
        startY = 0f,
        endY   = alturaTotal,
    ),
)
```

O gradiente vai de `AccentBlue@25%` no topo para `Transparent` na base — cria
a sensação de profundidade sem ser pesado visualmente.

---

## 6. `drawText` — texto no Canvas

```kotlin
// Requer TextMeasurer para medir e posicionar texto
val textMeasurer = rememberTextMeasurer()

Canvas(modifier = Modifier.fillMaxSize()) {
    val textLayout = textMeasurer.measure(
        text  = "-87 dBm",
        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
    )

    drawText(
        textLayoutResult = textLayout,
        topLeft = Offset(
            cx - textLayout.size.width / 2f,
            cy - textLayout.size.height / 2f,
        ),
    )
}
```

**Nota:** Para a maioria dos casos, é mais fácil usar `Text()` dentro de um `Box` sobre
o `Canvas` do que `drawText`. Reserve `drawText` para quando o texto precisa se integrar
ao desenho (ex.: labels no eixo Y do gráfico que precisam se alinhar às linhas do grid).

---

## 7. Transformações — translate, rotate, scale

```kotlin
Canvas(modifier = Modifier.fillMaxSize()) {

    // Translada o origin do canvas
    translate(left = 100f, top = 50f) {
        drawCircle(Color.Blue, 30f)  // desenhado em (100+cx, 50+cy)
    }

    // Rotaciona ao redor de um pivot
    rotate(45f, pivot = center) {
        drawRect(Color.Red, size = Size(100f, 100f))  // quadrado em 45°
    }

    // Escala ao redor de um pivot
    scale(1.5f, pivot = center) {
        drawCircle(Color.Green, 30f)  // círculo 1.5x maior
    }
}
```

---

## 8. Performance no Canvas

### Use `remember` para objetos caros

```kotlin
@Composable
fun GraficoOtimizado(pontos: List<Float>) {
    // Path é calculado UMA VEZ ou quando pontos mudar
    // Sem remember, seria recalculado a cada frame de animação!
    val path = remember(pontos) {
        Path().apply {
            pontos.forEachIndexed { i, y ->
                if (i == 0) moveTo(0f, y) else lineTo(i * 10f, y)
            }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawPath(path, Color.Blue, style = Stroke(3f))
    }
}
```

### Evite alocações no `DrawScope`

```kotlin
// ❌ Aloca novo Offset a cada frame
Canvas(...) {
    drawLine(Color.Gray, Offset(0f, cy), Offset(width, cy), ...)  // OK para 1 linha
}

// ❌ Aloca List a cada frame (dentro do loop de animação)
Canvas(...) {
    val pontos = listOf(Offset(0f, 0f), ...)  // aloca a cada recomposição!
}

// ✅ Pré-calcula fora do Canvas
val pontos = remember { listOf(Offset(0f, 0f), ...) }
Canvas(...) {
    pontos.forEach { ... }
}
```

---

## 🧪 Desafios

### Desafio 1 — Círculo de progresso
Crie um `CircularProgress(progress: Float, color: Color)` que desenha um arco de
progresso de 0° a 360°, com fundo cinza e arco preenchido na cor especificada.

### Desafio 2 — Barras animadas
Crie um composable `HistogramaBars(valores: List<Int>)` que desenha barras verticais
com altura proporcional ao valor, com gradiente de cor (verde para valores altos, vermelho para baixos).

### Desafio 3 — Mini gráfico
Implemente um gráfico de linha simplificado (`MiniChart(pontos: List<Float>)`) com:
- Eixo Y automático (mínimo e máximo dos pontos)
- Linha Bézier suave
- Fill com gradiente abaixo da linha
- Ponto destacado no último valor

---

## 📌 Resumo

```kotlin
Canvas(modifier) { /* DrawScope */ }

// Primitivas
drawCircle(color, radius, center)
drawLine(color, start, end, strokeWidth)
drawRect(color, topLeft, size)
drawArc(color, startAngle, sweepAngle, ...)
drawPath(path, brush)

// Geometria
Offset(x, y)                          // ponto
Size(width, height)                    // tamanho
Path() { moveTo, lineTo, cubicTo, close }  // caminho

// Brush
Brush.verticalGradient(colors)         // gradiente vertical
Brush.horizontalGradient(colors)       // gradiente horizontal
Brush.radialGradient(colorStops, radius)  // gradiente radial

// Trigonometria para ponto no círculo:
val x = cx + r * cos(anguloEmRadianos)
val y = cy + r * sin(anguloEmRadianos)
```

**Próximo Módulo →** [Construindo o App](../modulo-05-projeto/01-setup.md) — mão na massa!
