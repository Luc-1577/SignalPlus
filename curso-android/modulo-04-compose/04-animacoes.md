# Módulo 04 · Aula 04 — Animações no Compose: Vida e Fluidez na UI

> **Objetivo:** Criar animações fluidas que tornam o app agradável de usar.

---

## Por que animações importam?

Animações não são enfeite — elas comunicam:
- **Causalidade:** "este elemento surgiu porque você tocou aqui"
- **Transição de estado:** "o valor mudou de X para Y"
- **Hierarquia:** "esta tela veio de baixo desta outra"
- **Atenção:** "esta informação é importante"

Um app sem animações parece "travado" mesmo que funcione perfeitamente.

---

## 1. `animateFloatAsState` — animando valores contínuos

O mais simples: anima um Float de um valor para outro:

```kotlin
@Composable
fun BarraDeProgresso(progresso: Float) {

    // Sempre que `progresso` muda, a animação roda suavemente
    val progressoAnimado by animateFloatAsState(
        targetValue = progresso,
        animationSpec = tween(
            durationMillis = 900,              // duração: 900ms
            easing = FastOutSlowInEasing,      // acelera rápido, desacelera suave
        ),
        label = "progress",   // para debug no Layout Inspector
    )

    LinearProgressIndicator(progress = progressoAnimado)
}
```

### Variantes para outros tipos

```kotlin
val cor by animateColorAsState(
    targetValue = qualidade.color,
    animationSpec = tween(600),
    label = "cor",
)

val tamanho by animateDpAsState(
    targetValue = if (expandido) 200.dp else 80.dp,
    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
)

val offset by animateIntOffsetAsState(
    targetValue = if (visivel) IntOffset.Zero else IntOffset(-300, 0),
)
```

### No projeto — o gauge

```kotlin
val animProgress by animateFloatAsState(
    progress,
    tween(900, easing = FastOutSlowInEasing),
    label = "gp",
)

val animColor by animateColorAsState(
    quality.color,
    tween(600),
    label = "gc",
)
```

O `animProgress` anima o arco de `0.0` para `0.73` (por exemplo) em 900ms com
aceleração FastOutSlowIn — começa rápido, desacelera suavemente ao final.

---

## 2. `AnimatedContent` — animando entre composables diferentes

Quando o **conteúdo inteiro** precisa mudar com animação:

```kotlin
@Composable
fun ExibirEstado(estado: UiState) {
    AnimatedContent(
        targetState = estado,
        transitionSpec = {
            fadeIn(tween(300)) togetherWith fadeOut(tween(200))
        },
        label = "estado",
    ) { estadoAtual ->
        // Este bloco é recomposto com animação quando `estado` muda
        when (estadoAtual) {
            is UiState.Loading -> CircularProgressIndicator()
            is UiState.Success -> ContentView(estadoAtual.data)
            is UiState.Error   -> ErrorView(estadoAtual.message)
        }
    }
}
```

### Slide direcional — como o projeto faz

```kotlin
AnimatedContent(
    targetState = currentTab,
    transitionSpec = {
        // Determina a direção baseado no ordinal da aba
        val direcao = if (targetState.ordinal > initialState.ordinal) 1 else -1

        // Entra deslizando da direita/esquerda
        slideInHorizontally { direcao * it } + fadeIn() togetherWith
            // Sai deslizando para a esquerda/direita
            slideOutHorizontally { -direcao * it } + fadeOut()
    },
    label = "tabs",
) { aba ->
    when (aba) {
        AppTab.SIGNAL  -> SignalTab()
        AppTab.HISTORY -> HistoryTab()
        AppTab.INFO    -> InfoTab()
    }
}
```

**`togetherWith`:** combina as animações de entrada e saída.

### Animações de entrada/saída

```kotlin
// Entrada
fadeIn(tween(300))
slideInHorizontally { fullWidth -> fullWidth }   // da direita
slideInVertically { fullHeight -> fullHeight }    // de baixo
expandVertically()                                // expande verticalmente
expandHorizontally()                              // expande horizontalmente

// Saída
fadeOut(tween(200))
slideOutHorizontally { fullWidth -> -fullWidth }  // para a esquerda
slideOutVertically { fullHeight -> -fullHeight }  // para cima
shrinkVertically()
shrinkHorizontally()

// Combinando
slideInHorizontally { it } + fadeIn() togetherWith
    slideOutHorizontally { -it } + fadeOut()
```

---

## 3. `AnimatedVisibility` — mostrar/esconder com animação

```kotlin
@Composable
fun MensagemCondicional(visivel: Boolean) {
    AnimatedVisibility(
        visible = visivel,
        enter   = slideInVertically() + fadeIn(),
        exit    = slideOutVertically() + fadeOut(),
    ) {
        Text("Esta mensagem aparece com animação!")
    }
}
```

No projeto, `QualityBadge` usa `AnimatedContent` para animar quando a qualidade muda:

```kotlin
@Composable
fun QualityBadge(quality: SignalQuality) {
    AnimatedContent(
        targetState = quality,
        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
        label = "qb",
    ) { q ->
        Row {
            Box(Modifier.background(q.color))   // cor anima junto com a mudança!
            Text(q.label.uppercase(), color = q.color)
        }
    }
}
```

---

## 4. `rememberInfiniteTransition` — animações que nunca param

Para animações em loop (pulsação, rotação, brilho):

```kotlin
@Composable
fun PontoPulsante() {
    val pulso by rememberInfiniteTransition(label = "pulso")
        .animateFloat(
            initialValue = 0.3f,
            targetValue  = 1.0f,
            animationSpec = infiniteRepeatable(
                animation  = tween(1_100, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse,   // vai e volta
            ),
            label = "pulso_a",
        )

    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(Color.Green.copy(alpha = pulso))
    )
}
```

### No projeto — o glow pulsante do gauge

```kotlin
val glowPulse by rememberInfiniteTransition(label = "glow")
    .animateFloat(
        initialValue = 0.12f,
        targetValue  = 0.22f,
        animationSpec = infiniteRepeatable(
            tween(1400, easing = EaseInOutSine),
            RepeatMode.Reverse,
        ),
        label = "ga",
    )

// Usado como alpha do glow externo:
drawArc(
    color      = animColor.copy(alpha = glowPulse),  // ← pulsação
    sweepAngle = filledSweep,
    style      = Stroke(trackW * 2.8f, cap = StrokeCap.Round)
)
```

O alpha oscila entre 12% e 22% num ciclo de 1,4 segundos com easing sinusoidal —
cria um efeito de "respiração" suave.

---

## 5. `spring` vs `tween` vs `keyframes` — especificações de animação

### `tween` — baseado em duração e easing

```kotlin
tween(
    durationMillis = 300,
    delayMillis    = 0,
    easing         = FastOutSlowInEasing,
)
```

Easings disponíveis:
```
FastOutSlowInEasing   → rápido no início, lento no fim (entrada)
LinearOutSlowInEasing → linear no início, lento no fim (saída de tela)
FastOutLinearInEasing → rápido, sem desaceleração (sair de cena)
LinearEasing          → velocidade constante
EaseInOutSine         → sinusoidal — suave em ambas as extremidades
```

### `spring` — física real (mola)

```kotlin
spring(
    dampingRatio   = Spring.DampingRatioMediumBouncy,
    stiffness      = Spring.StiffnessLow,
)
```

```
DampingRatioNoBouncy     → sem quique (amortecimento crítico)
DampingRatioLowBouncy    → quique suave
DampingRatioMediumBouncy → quique médio
DampingRatioHighBouncy   → quique acentuado

StiffnessLow    → animação lenta e suave
StiffnessMedium → animação média
StiffnessHigh   → animação rápida e rígida
```

**Quando usar spring?** Para elementos que o usuário interage (arrastar, soltar)
— a física natural cria sensação de peso e responsividade.

### `keyframes` — controle total do tempo

```kotlin
keyframes {
    durationMillis = 1000
    0.0f at 0            // início: 0.0
    0.8f at 300          // em 300ms: 0.8
    0.6f at 500          // em 500ms: recua para 0.6
    1.0f at 1000         // no final: 1.0
}
```

Útil para animações com "overshoot" ou múltiplas fases.

---

## 6. `updateTransition` — animando múltiplos valores juntos

```kotlin
enum class EstadoBotao { Normal, Pressionado, Desabilitado }

@Composable
fun BotaoAnimado(estado: EstadoBotao) {
    // Uma transição que anima vários valores coordenados
    val transition = updateTransition(targetState = estado, label = "botao")

    val escala by transition.animateFloat(label = "escala") { e ->
        when (e) {
            EstadoBotao.Normal       -> 1.0f
            EstadoBotao.Pressionado  -> 0.95f
            EstadoBotao.Desabilitado -> 1.0f
        }
    }

    val cor by transition.animateColor(label = "cor") { e ->
        when (e) {
            EstadoBotao.Normal       -> Color.Blue
            EstadoBotao.Pressionado  -> Color.DarkBlue
            EstadoBotao.Desabilitado -> Color.Gray
        }
    }

    Box(
        modifier = Modifier
            .scale(escala)           // animação de escala
            .background(cor),        // animação de cor
    ) { Text("Botão") }
}
```

---

## 7. Animação de entrada na `MetricCard`

```kotlin
@Composable
fun MetricCard(label: String, value: String, accent: Color) {
    Column(...) {
        Text(label, ...)
        Spacer(Modifier.height(6.dp))

        // Animação: novo valor entra de baixo, antigo sai por cima
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                slideInVertically  { it / 2 } + fadeIn() togetherWith
                slideOutVertically { -it / 2 } + fadeOut()
            },
            label = "mc_$label",
        ) { v ->
            Text(
                text  = v,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
        }
    }
}
```

**Por que `it / 2` em vez de `it`?** A animação desliza metade da altura do elemento —
não a altura total. Isso cria um efeito mais sutil, como se o número "emergisse" de baixo.

---

## 8. Boas práticas de animação

### Sempre adicione `label` para debug

```kotlin
animateFloatAsState(value, label = "gauge_progress")
//                               ↑
// Aparece no Android Studio Layout Inspector / Animation Preview
```

### Prefira animações curtas para feedback imediato

```kotlin
// Feedback de toque — rápido
tween(100)

// Transição de estado — médio
tween(300)

// Animação de destaque — pode ser mais longa
tween(600)

// Animações de mais de 1 segundo parecem lentas
tween(1_500)  // ← evite para interações
```

### Use `spring` para elementos que o usuário controla

```kotlin
// Usuário arrasta e solta → spring para o lugar
val offset by animateDpAsState(
    targetValue = if (dropped) finalPosition else currentDragPosition,
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness    = Spring.StiffnessMedium,
    ),
)
```

---

## 🧪 Desafios

### Desafio 1 — Pulsação
Crie um componente `Heartbeat` que exibe um ícone de coração que bate com animação
periódica: escala de 1.0 → 1.2 → 1.0 a cada 1 segundo.

### Desafio 2 — Contador animado
Modifique o `ContadorView` do módulo anterior para que o número anime com
`slideInVertically + fadeIn` (para cima ao incrementar, para baixo ao decrementar).

### Desafio 3 — Transição de tela
Crie duas telas (`TelaA` e `TelaB`) e implemente transição entre elas com:
- Entrar: slide da direita + fadeIn
- Sair: slide para a esquerda + fadeOut
- Voltar: slide da esquerda + fadeIn, slide para direita + fadeOut

---

## 📌 Resumo

```kotlin
animateFloatAsState(valor, tween(ms))      → Float que anima para um alvo
animateColorAsState(cor, tween(ms))        → Color que anima
AnimatedContent(estado) { estado -> }      → composable que anima entre estados
AnimatedVisibility(visivel)                → mostra/esconde com animação
rememberInfiniteTransition().animateFloat  → loop infinito (pulsação)
updateTransition(estado)                   → múltiplos valores coordenados

tween(durationMs, easing)                 → baseado em tempo
spring(dampingRatio, stiffness)           → física de mola
infiniteRepeatable(anim, RepeatMode.Reverse) → vai e volta
```

**Próxima aula →** [Canvas no Compose](05-canvas.md) — desenho livre de gauges e gráficos do zero.
