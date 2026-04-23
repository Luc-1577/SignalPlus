# Módulo 04 · Aula 01 — Jetpack Compose: Pensando de Forma Declarativa

> **Pré-requisito:** Módulo 03 completo
> **Objetivo:** Entender o paradigma declarativo do Compose e criar seus primeiros composables.

---

## Imperativo vs. Declarativo

Existem dois jeitos de construir UIs:

### O jeito imperativo (XML + View system)

```xml
<!-- res/layout/activity_main.xml -->
<TextView
    android:id="@+id/tvSinal"
    android:text="—"
    android:textColor="#FFFFFF" />
```

```kotlin
// No código, você diz COMO mudar a UI
val tvSinal = findViewById<TextView>(R.id.tvSinal)

if (dbm != null) {
    tvSinal.text = "$dbm dBm"
    tvSinal.setTextColor(Color.GREEN)
    tvSinal.visibility = View.VISIBLE
} else {
    tvSinal.text = "—"
    tvSinal.setTextColor(Color.GRAY)
    tvSinal.visibility = View.INVISIBLE
}
```

Problema: você precisa gerenciar **transições de estado** — o que muda quando vai de "sem sinal"
para "com sinal" e vice-versa. Isso fica exponencialmente complexo com estados combinados.

### O jeito declarativo (Compose)

```kotlin
// Você descreve COMO a UI deve parecer dado um estado
@Composable
fun SinalText(dbm: Int?) {
    if (dbm != null) {
        Text(
            text  = "$dbm dBm",
            color = Color.Green,
        )
    } else {
        Text(
            text  = "—",
            color = Color.Gray,
        )
    }
}
```

**Você não gerencia transições.** Sempre que `dbm` mudar, o Compose **recompõe**
(re-executa) a função e a UI reflete o novo estado automaticamente.

**Analogia:** Imagine uma planilha. Você não diz "quando A1 mudar, atualize B1".
Você declara "B1 = A1 * 2" — a planilha se atualiza automaticamente.

---

## 1. A anotação `@Composable`

```kotlin
@Composable
fun MeuComponente() {
    Text("Olá, Compose!")
}
```

Uma função `@Composable`:
- Só pode ser chamada de outra função `@Composable`
- Pode ser recomposta (re-executada) quantas vezes o Compose precisar
- **Não retorna nada** (Unit) — ela emite elementos de UI

---

## 2. Os building blocks básicos

### `Text` — exibindo texto

```kotlin
@Composable
fun ExemploTexto() {
    Text(
        text       = "Signal: -87 dBm",
        fontSize   = 18.sp,            // sp = scale-independent pixels
        fontWeight = FontWeight.Bold,
        color      = Color(0xFF22D36A),
    )
}
```

### `Icon` — ícones do Material

```kotlin
@Composable
fun ExemploIcone() {
    Icon(
        imageVector = Icons.Default.SignalCellularAlt,
        contentDescription = "Sinal celular",
        tint   = Color.Blue,
        modifier = Modifier.size(24.dp),
    )
}
```

### `Button` — botão clicável

```kotlin
@Composable
fun ExemploBotao(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors  = ButtonDefaults.buttonColors(containerColor = Color.Blue),
    ) {
        Text("Clique aqui")
    }
}
```

### `Box` — sobreposição de elementos

```kotlin
@Composable
fun Sobreposicao() {
    Box(contentAlignment = Alignment.Center) {
        // Fundo
        CircularProgressIndicator()
        // Sobre o fundo
        Text("75%", fontWeight = FontWeight.Bold)
    }
}
```

---

## 3. Modificadores (Modifier) — o sistema de estilo do Compose

`Modifier` é uma cadeia de configurações aplicadas a qualquer composable:

```kotlin
@Composable
fun CartaoExemplo() {
    Box(
        modifier = Modifier
            .fillMaxWidth()                          // ocupa toda a largura
            .height(100.dp)                          // altura fixa
            .padding(16.dp)                          // margem interna
            .clip(RoundedCornerShape(12.dp))         // bordas arredondadas
            .background(Color(0xFF141E35))           // cor de fundo
            .border(1.dp, Color(0xFF1C2B48),         // borda
                    RoundedCornerShape(12.dp)),
    ) {
        Text("Dentro do card", color = Color.White)
    }
}
```

### Ordem importa!

```kotlin
// padding DENTRO do background:
Modifier
    .background(Color.Blue)
    .padding(16.dp)       // espaço interno, dentro do azul

// background DENTRO do padding:
Modifier
    .padding(16.dp)       // espaço externo, fora do azul
    .background(Color.Blue)
```

### Modificadores mais usados

```kotlin
Modifier.fillMaxSize()                  // ocupa todo o espaço disponível
Modifier.fillMaxWidth()                 // ocupa toda a largura
Modifier.fillMaxHeight()                // ocupa toda a altura
Modifier.size(48.dp)                    // tamanho fixo quadrado
Modifier.width(100.dp)                  // largura fixa
Modifier.height(50.dp)                  // altura fixa
Modifier.padding(16.dp)                 // padding em todos os lados
Modifier.padding(horizontal = 16.dp)   // padding horizontal
Modifier.padding(top = 8.dp, bottom = 4.dp)  // lados específicos
Modifier.clip(RoundedCornerShape(8.dp)) // cortar cantos
Modifier.background(color)             // cor de fundo
Modifier.border(1.dp, color, shape)    // borda
Modifier.clickable { }                  // clicável
Modifier.weight(1f)                     // proporcional em Row/Column
```

---

## 4. Recomposição — entendendo o coração do Compose

**Recomposição** é quando o Compose re-executa funções `@Composable` para atualizar a UI.

```kotlin
var contador by remember { mutableStateOf(0) }
//                 ↑                ↑
//           persiste entre        rastreia mudanças
//           recomposições

Button(onClick = { contador++ }) {   // incrementa e DISPARA recomposição
    Text("Clicado $contador vezes")
}
```

### O que dispara recomposição?

Qualquer **State** que muda dispara recomposição dos composables que o leram:

```kotlin
var nome by remember { mutableStateOf("Android") }

// Este composable é recomposto quando `nome` muda:
Text("Olá, $nome!")

// Este composable NÃO é recomposto (não leu `nome`):
Text("Compose é incrível!")
```

**O Compose é inteligente:** Ele recompõe apenas os composables que dependem do state
que mudou. Isso é chamado de recomposição inteligente (*smart recomposition*).

### Armadilha: não crie State dentro de composables sem `remember`

```kotlin
// ❌ ERRADO — recria o estado a cada recomposição!
@Composable
fun Errado() {
    var count = mutableStateOf(0)   // recriado a cada recomposição → sempre 0!
    Button(onClick = { count.value++ }) {
        Text("${count.value}")   // nunca muda!
    }
}

// ✅ CORRETO — remember persiste entre recomposições
@Composable
fun Correto() {
    var count by remember { mutableStateOf(0) }
    Button(onClick = { count++ }) {
        Text("$count")   // atualiza corretamente!
    }
}
```

---

## 5. `Scaffold` — a estrutura de uma tela completa

`Scaffold` implementa o layout básico do Material Design:

```kotlin
@Composable
fun TelaPrincipal(vm: SignalViewModel = viewModel()) {
    Scaffold(
        containerColor = Color(0xFF060810),
        topBar = {
            TopAppBar(
                title = { Text("Signal Meter") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0B0F1C)
                ),
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = true,
                    onClick  = { },
                    icon     = { Icon(Icons.Default.SignalCellularAlt, null) },
                    label    = { Text("Sinal") },
                )
            }
        },
    ) { paddingValues ->
        // O conteúdo principal fica aqui
        // paddingValues garante que não fica embaixo da TopBar/BottomBar
        Box(modifier = Modifier.padding(paddingValues)) {
            ConteudoPrincipal()
        }
    }
}
```

---

## 6. Passando eventos para cima (hoisting de estado)

Um princípio do Compose: **state hoisting** — mova o estado para o nível mais alto necessário.

```kotlin
// ❌ Estado dentro do componente — não reutilizável
@Composable
fun BotaoComEstado() {
    var texto by remember { mutableStateOf("Clique") }
    Button(onClick = { texto = "Clicado!" }) {
        Text(texto)
    }
}

// ✅ Estado hoisted — componente puro e reutilizável
@Composable
fun BotaoPuro(
    texto  : String,
    onClick: () -> Unit,
) {
    Button(onClick = onClick) {
        Text(texto)
    }
}

// O pai gerencia o estado
@Composable
fun PaiComEstado() {
    var texto by remember { mutableStateOf("Clique") }
    BotaoPuro(
        texto   = texto,
        onClick = { texto = "Clicado!" },
    )
}
```

**Por que hoisting?**
- Composables "burros" (só recebem dados, não têm estado) são reutilizáveis em qualquer lugar
- Fácil de testar (você controla o estado externamente)
- O ViewModel é o "nível mais alto" para estado que precisa sobreviver à recomposição

No projeto, todos os composables em `components/` são puros:

```kotlin
// MetricCard não tem estado — recebe tudo como parâmetro
@Composable
fun MetricCard(
    label   : String,
    value   : String,
    modifier: Modifier = Modifier,
    accent  : Color    = AccentBlue,
) { ... }
```

---

## 7. `LazyColumn` — listas eficientes

Para listas longas, use `LazyColumn` (equivalent ao `RecyclerView`):

```kotlin
@Composable
fun ListaDeCelulas(cells: List<CellSignalData>) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // item = um item estático
        item {
            Text("Células detectadas", fontWeight = FontWeight.Bold)
        }

        // items = lista de itens
        items(cells, key = { it.netInfo.cellId ?: it.dbm }) { cell ->
            CellRow(cell = cell)
        }

        // item ao final
        item { Spacer(Modifier.height(16.dp)) }
    }
}
```

**Por que `key`?** A chave única permite ao Compose identificar cada item mesmo quando a lista
muda. Sem key, o Compose pode recompor itens desnecessários quando a lista é reordenada.

---

## 🧪 Desafios

### Desafio 1 — Primeiro composable
Crie um composable `DbmCard(dbm: Int, networkType: String)` que exibe:
- O valor dBm em fonte grande
- O tipo de rede abaixo
- Fundo escuro com bordas arredondadas
- Cor do texto variando conforme a força do sinal (verde, amarelo, vermelho)

### Desafio 2 — Estado hoisting
Crie um `@Composable fun Contador()` com botões `+` e `-` e display do valor.
Depois, refatore para hoisting completo: crie `ContadorView(count, onIncrement, onDecrement)`
e `ContadorScreen()` que gerencia o estado.

### Desafio 3 — LazyColumn
Dado `List<CellSignalData>`, crie uma `LazyColumn` com:
- Header fixo com o total de células
- Cada célula exibindo networkType, dbm e operadora
- Footer com o maior e menor dbm

---

## 📌 Resumo

```
@Composable fun  → descreve a UI para um dado estado
Recomposição     → Compose re-executa quando State muda
Modifier         → cadeia de configurações (tamanho, padding, cor...)
remember { }     → persiste State entre recomposições
State hoisting   → mova estado para o nível mais alto necessário
Scaffold         → estrutura padrão (TopBar + BottomBar + conteúdo)
LazyColumn       → lista eficiente para muitos itens
```

**Próxima aula →** [Estado no Compose](02-estado.md) — `remember`, `mutableStateOf` e como a UI conecta com o ViewModel.
