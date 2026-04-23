# Módulo 04 · Aula 03 — Layouts: Column, Row, Box e o Sistema de Modificadores

> **Objetivo:** Construir qualquer layout de tela usando os blocos fundamentais do Compose.

---

## Os três layouts fundamentais

O Compose tem três containers principais:

```
Column → empilha verticalmente  │  Row → enfileira horizontalmente  │  Box → sobrepõe
         ┌────────────┐         │       ┌───┬───┬───┐              │       ┌────────┐
         │ Elemento 1 │         │       │ A │ B │ C │              │       │ fundo  │
         ├────────────┤         │       └───┴───┴───┘              │       │  ┌──┐  │
         │ Elemento 2 │         │                                   │       │  │  │  │
         ├────────────┤         │                                   │       │  └──┘  │
         │ Elemento 3 │         │                                   │       └────────┘
         └────────────┘         │                                   │
```

---

## 1. `Column` — layout vertical

```kotlin
@Composable
fun ExemploColumn() {
    Column(
        modifier             = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement  = Arrangement.spacedBy(8.dp),   // espaço entre itens
        horizontalAlignment  = Alignment.CenterHorizontally, // centraliza itens
    ) {
        Text("Título", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Subtítulo", color = Color.Gray)
        Button(onClick = { }) { Text("Ação") }
    }
}
```

### Arrangements verticais

```kotlin
Arrangement.Top              // itens no topo
Arrangement.Bottom           // itens na base
Arrangement.Center           // itens no centro
Arrangement.SpaceBetween     // espaço igual entre itens, sem espaço nas bordas
Arrangement.SpaceAround      // espaço igual ao redor dos itens
Arrangement.SpaceEvenly      // espaço igual entre todos (inclusive bordas)
Arrangement.spacedBy(16.dp)  // N dp entre cada item
```

---

## 2. `Row` — layout horizontal

```kotlin
@Composable
fun StatusBar(dbm: Int, networkType: NetworkType) {
    Row(
        modifier            = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment   = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,   // um em cada extremidade
    ) {
        NetworkBadge(networkType)
        Text("$dbm dBm", fontWeight = FontWeight.Bold)
    }
}
```

### `Modifier.weight()` — distribuição proporcional

```kotlin
Row(modifier = Modifier.fillMaxWidth()) {
    // 1/3 da largura
    Box(modifier = Modifier.weight(1f).background(Color.Red))
    // 2/3 da largura
    Box(modifier = Modifier.weight(2f).background(Color.Blue))
}
```

`weight` distribui o espaço **disponível** proporcionalmente. `weight(1f)` e `weight(2f)`
significa que o segundo tem o dobro do espaço do primeiro.

No projeto, as métricas são exibidas em grid de 2 colunas:

```kotlin
Row(modifier = Modifier.fillMaxWidth()) {
    MetricCard(label = "RSRP", value = "-87 dBm", modifier = Modifier.weight(1f))
    Spacer(Modifier.width(12.dp))
    MetricCard(label = "RSRQ", value = "-12 dB",  modifier = Modifier.weight(1f))
}
```

---

## 3. `Box` — sobreposição de elementos

```kotlin
@Composable
fun GaugeComTexto() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(280.dp),
    ) {
        // Elemento 1 (fundo) — o arco
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawArc(...)
        }

        // Elemento 2 (frente) — o texto centralizado sobre o arco
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("-87", fontSize = 54.sp, fontWeight = FontWeight.ExtraBold)
            Text("dBm", fontSize = 12.sp, color = Color.Gray)
        }
    }
}
```

### `contentAlignment` — onde o conteúdo é posicionado

```kotlin
Box(contentAlignment = Alignment.TopStart)     // canto superior esquerdo
Box(contentAlignment = Alignment.TopCenter)    // centro superior
Box(contentAlignment = Alignment.TopEnd)       // canto superior direito
Box(contentAlignment = Alignment.Center)       // centro
Box(contentAlignment = Alignment.BottomCenter) // centro inferior
```

Ou use `Modifier.align()` em cada filho:

```kotlin
Box(modifier = Modifier.fillMaxSize()) {
    Text("Esquerda",  modifier = Modifier.align(Alignment.CenterStart))
    Text("Direita",   modifier = Modifier.align(Alignment.CenterEnd))
    Text("Centro",    modifier = Modifier.align(Alignment.Center))
}
```

---

## 4. `Spacer` — espaço vazio

```kotlin
Column {
    Text("Título")
    Spacer(Modifier.height(24.dp))   // espaço vertical de 24dp
    Text("Conteúdo")
}

Row {
    Text("Esquerda")
    Spacer(Modifier.weight(1f))      // empurra o próximo para a direita
    Text("Direita")
}
```

---

## 5. Construindo um card completo do projeto

Vamos analisar o `CellRow` do projeto passo a passo:

```kotlin
@Composable
fun CellRow(cell: CellSignalData) {
    val quality = signalQuality(cell.dbm, cell.networkType)

    Row(
        // Layout horizontal
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),

        // Estilo
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (cell.isRegistered) BgCardAlt else BgCard)
            .border(
                width = 1.dp,
                color = if (cell.isRegistered) BorderBright else BorderSubtle,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        // Coluna 1: ícone da antena
        Icon(
            Icons.Default.CellTower,
            null,
            tint     = if (cell.isRegistered) quality.color else TextMuted,
            modifier = Modifier.size(20.dp),
        )

        // Coluna 2: nome + operadora (ocupa o espaço disponível)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    cell.networkType.label,
                    fontWeight = FontWeight.Bold,
                    color = if (cell.isRegistered) TextPrimary else TextSecondary,
                )
                if (cell.isRegistered) {
                    Spacer(Modifier.width(8.dp))
                    // Badge "conectado"
                    Box(modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(quality.color.copy(alpha = 0.15f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text("conectado", fontSize = 9.sp, color = quality.color)
                    }
                }
            }
            Text(cell.operator, fontSize = 11.sp, color = TextMuted)
        }

        // Coluna 3: barras de sinal + dBm
        Column(horizontalAlignment = Alignment.End) {
            SignalBars(level = cell.level, color = quality.color)
            Spacer(Modifier.height(4.dp))
            Text("${cell.dbm} dBm", fontWeight = FontWeight.SemiBold, color = quality.color)
        }
    }
}
```

Diagrama visual do layout:

```
┌─────────────────────────────────────────────────────────────┐
│  [ícone]  [LTE    ●conectado]                  [▐▐▐▐▐]     │
│           [Vivo]                               [-87 dBm]    │
└─────────────────────────────────────────────────────────────┘
  20dp        weight(1f) →                        ← fixo
```

---

## 6. `LazyColumn` e `LazyRow` — listas virtualizadas

Para listas com muitos itens, use `Lazy*` — só renderiza o que está visível:

```kotlin
@Composable
fun ListaDeCelulas(cells: List<CellSignalData>) {
    LazyColumn(
        contentPadding = PaddingValues(
            horizontal = 20.dp,
            vertical   = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Itens estáticos com `item { }`
        item {
            SectionHeader("Células detectadas")
        }

        // Itens dinâmicos com `items(lista) { }`
        items(
            items = cells,
            key   = { it.netInfo.cellId ?: it.dbm.toLong() },
        ) { cell ->
            CellRow(cell = cell)
        }

        // Múltiplos itens a partir de uma lista
        itemsIndexed(cells) { index, cell ->
            Text("$index: ${cell.dbm} dBm")
        }

        // Espaçador no final
        item { Spacer(Modifier.height(16.dp)) }
    }
}
```

**Por que `key` importa em `LazyColumn`?** Quando a lista muda (nova célula adicionada,
reordenada), o Compose usa a chave para identificar qual item é qual e animar a
transição corretamente. Sem chave, ele compara por posição — menos eficiente.

---

## 7. `ConstraintLayout` — quando Column/Row/Box não bastam

Para layouts complexos com posicionamento relativo:

```kotlin
// Adicione a dependência primeiro:
// implementation("androidx.constraintlayout:constraintlayout-compose:1.1.0")

@Composable
fun LayoutComplexo() {
    ConstraintLayout(modifier = Modifier.fillMaxSize()) {
        val (icone, titulo, subtitulo, botao) = createRefs()

        Icon(
            Icons.Default.Star,
            null,
            modifier = Modifier.constrainAs(icone) {
                top.linkTo(parent.top, margin = 24.dp)
                centerHorizontallyTo(parent)
            }
        )

        Text(
            "Título",
            modifier = Modifier.constrainAs(titulo) {
                top.linkTo(icone.bottom, margin = 16.dp)
                centerHorizontallyTo(parent)
            }
        )
    }
}
```

**Quando usar:** Para layouts que seriam impossíveis ou ineficientes com Column/Row/Box aninhados.
No geral, Column/Row/Box são preferidos por serem mais simples.

---

## 8. Responsive Layouts — adaptando ao tamanho da tela

```kotlin
@Composable
fun LayoutAdaptivo() {
    val largura = LocalConfiguration.current.screenWidthDp.dp

    if (largura < 600.dp) {
        // Celular — coluna única
        Column { ... }
    } else {
        // Tablet — duas colunas
        Row { ... }
    }
}
```

Ou com `WindowSizeClass`:

```kotlin
val windowSize = calculateWindowSizeClass(this)

when (windowSize.widthSizeClass) {
    WindowWidthSizeClass.Compact  -> LayoutCelular()
    WindowWidthSizeClass.Medium   -> LayoutTabletPequeno()
    WindowWidthSizeClass.Expanded -> LayoutTabletGrande()
}
```

---

## 🧪 Desafios

### Desafio 1 — Card de perfil
Crie um composable `PerfilCard(nome: String, cargo: String, avatarUrl: String?)` com:
- Avatar circular no lado esquerdo
- Nome em negrito à direita
- Cargo abaixo do nome
- Background com bordas arredondadas

### Desafio 2 — Grid de métricas
Dado `Map<String, String>` de métricas, crie uma grade de 2 colunas onde cada
célula é um card com label (pequeno, cinza) e valor (grande, colorido).

### Desafio 3 — Layout do SignalMeter
Recrie o layout da aba "Sinal" do projeto:
1. Gauge centralizado no topo
2. Nome da operadora abaixo do gauge
3. Badge de qualidade abaixo do nome
4. Card de identificadores
5. Grid de métricas 2×N
6. Lista de células detectadas

---

## 📌 Resumo

```
Column                     → vertical, com Arrangement vertical e Alignment horizontal
Row                        → horizontal, com Arrangement horizontal e Alignment vertical
Box                        → sobreposição, com contentAlignment ou Modifier.align()
Spacer(Modifier.X)         → espaço vazio (height ou weight)
Modifier.weight(n)         → distribui espaço disponível proporcionalmente
LazyColumn                 → lista virtualizada (só renderiza o visível)
items(lista) { item -> }   → itens de lista em LazyColumn
item { }                   → item único em LazyColumn
key = { }                  → chave única para recomposição eficiente
```

**Próxima aula →** [Animações no Compose](04-animacoes.md) — deixando o app vivo e responsivo.
