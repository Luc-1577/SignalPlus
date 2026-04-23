# Módulo 01 · Aula 03 — Coleções e Programação Funcional

> **Pré-requisito:** Aulas 01 e 02
> **Objetivo:** Manipular listas e mapas com a elegância do estilo funcional do Kotlin.

---

## O que são coleções?

Coleções são estruturas para armazenar múltiplos valores. No Kotlin:

```kotlin
// Lista — ordem importa, pode ter duplicatas
val celulas = listOf("LTE", "NR", "LTE", "WCDMA")   // imutável
val mutavel = mutableListOf(1, 2, 3)                  // mutável
mutavel.add(4)

// Set — sem duplicatas, sem ordem garantida
val tipos = setOf("LTE", "NR", "LTE")   // {"LTE", "NR"}

// Map — chave → valor
val metricas = mapOf("RSRP" to "-87 dBm", "RSRQ" to "-12 dB")
val metricasMut = mutableMapOf<String, String>()
metricasMut["CQI"] = "10"
```

---

## 1. As transformações mais importantes

### `map` — transforme cada item

```kotlin
val dbms = listOf(-87, -95, -102, -78)

// Transforma cada Int em String
val textos = dbms.map { "$it dBm" }
// → ["-87 dBm", "-95 dBm", "-102 dBm", "-78 dBm"]

// Transforma em objetos
val pontos = dbms.map { dbm ->
    SignalPoint(
        timestampMs = System.currentTimeMillis(),
        dbm         = dbm,
        networkType = NetworkType.LTE,
    )
}
```

**Analogia:** `map` é uma fábrica — você dá uma matéria-prima, ela devolve um produto transformado.

No projeto, o repositório usa `map` para converter `CellInfo` em `CellSignalData`:

```kotlin
rawList.mapNotNull { parseCellInfo(it, operator, primaryBwKhz) }
//     ↑
//  mapNotNull = map + filtra nulls automaticamente
```

### `filter` — mantenha só o que interessa

```kotlin
val dbms = listOf(-87, -95, -102, -78, Int.MIN_VALUE, 0)

val validos = dbms.filter { it != Int.MIN_VALUE && it != 0 }
// → [-87, -95, -102, -78]

val fortes = dbms.filter { it >= -90 }
// → [-87, -78]
```

**Analogia:** `filter` é uma peneira — só passa o que satisfaz a condição.

### `forEach` — execute para cada item

```kotlin
val celulas = listOf(celula1, celula2, celula3)

celulas.forEach { celula ->
    println("${celula.networkType}: ${celula.dbm} dBm")
}
```

### `firstOrNull` — o primeiro que satisfaz (ou null)

```kotlin
val celulas: List<CellSignalData> = ...

// Primeira célula registrada, ou null
val principal = celulas.firstOrNull { it.isRegistered }

// Se não há registrada, pega a primeira (qualquer)
val exibida = celulas.firstOrNull { it.isRegistered } ?: celulas.firstOrNull()
```

No projeto, é exatamente assim:

```kotlin
val primary = cells.firstOrNull { it.isRegistered } ?: cells.firstOrNull()
```

### `sortedByDescending` — ordene em ordem decrescente

```kotlin
val celulas = listOf(celA, celB, celC)   // dbms: -95, -78, -102

val ordenadas = celulas.sortedByDescending { it.dbm }
// celB(-78), celA(-95), celC(-102)
// mais forte primeiro!
```

**Nota importante:** A ordenação dupla do projeto:

```kotlin
rawList
    .sortedByDescending { it.dbm }           // 1º: por força de sinal
    .sortedByDescending { it.isRegistered }  // 2º: registrada no topo
```

A ordenação Kotlin é **estável** — ao ordenar por `isRegistered`, as células com mesmo
valor de `isRegistered` mantêm a ordem relativa anterior (do `dbm`). Resultado: célula
registrada no topo, e as não-registradas ordenadas por `dbm` decrescente.

---

## 2. Transformações encadeadas

A magia das coleções funcionais: você pode encadear operações:

```kotlin
val resultado = rawList
    .filter { it.dbm != Int.MIN_VALUE }           // remove inválidos
    .map { formatar(it) }                         // transforma
    .sortedByDescending { it.dbm }                // ordena
    .take(5)                                      // pega só os 5 primeiros
```

Cada operação retorna uma nova lista — a original nunca é modificada.

---

## 3. `mapNotNull` — map que filtra nulls

```kotlin
// Isso:
val lista = rawList.mapNotNull { parseCellInfo(it) }

// É equivalente a:
val lista = rawList
    .map { parseCellInfo(it) }       // retorna List<CellSignalData?>
    .filterNotNull()                  // remove os nulls → List<CellSignalData>
```

`mapNotNull` é mais eficiente porque faz as duas operações em uma única passagem.

---

## 4. Operações de agregação

```kotlin
val pontos = listOf(
    SignalPoint(1000L, -87, NetworkType.LTE),
    SignalPoint(2000L, -95, NetworkType.LTE),
    SignalPoint(3000L, -78, NetworkType.LTE),
)

pontos.minOf { it.dbm }        // → -95 (mais fraco)
pontos.maxOf { it.dbm }        // → -78 (mais forte)
pontos.sumOf { it.dbm }        // → -260
pontos.map { it.dbm }.average() // → -86.67

// minByOrNull retorna o objeto inteiro, não só o valor
val maisFreco = pontos.minByOrNull { it.dbm }
// → SignalPoint(3000L, -95, LTE)
```

No `HistoryScreen`:

```kotlin
val minPt  = points.minByOrNull { it.dbm }   // objeto SignalPoint com menor dBm
val maxPt  = points.maxByOrNull { it.dbm }   // objeto SignalPoint com maior dBm
val avg    = history.average()               // média dos últimos 10
```

---

## 5. `buildList` e `buildMap` — construção declarativa

```kotlin
// Em vez de:
val lista = mutableListOf<String>()
lista.add("A")
if (condicao) lista.add("B")
lista.add("C")
val resultado: List<String> = lista

// Use:
val resultado = buildList {
    add("A")
    if (condicao) add("B")
    add("C")
}
```

No projeto, as permissões são construídas assim:

```kotlin
val perms = remember {
    buildList {
        add(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }.toTypedArray()
}
```

E as métricas:

```kotlin
val allMetrics = buildMap {
    if (!hasDedicated) put("dBm", "${primary.dbm} dBm")
    put("ASU", "${primary.asuLevel}")
    put("Nível", "${primary.level} / 4")
    putAll(primary.extraMetrics)
}
```

---

## 6. `chunked` — divida em grupos

```kotlin
val metricas = listOf("RSRP", "RSRQ", "CQI", "RSSNR", "ASU", "Nível")

// Divide em grupos de 2
val pares = metricas.chunked(2)
// → [["RSRP", "RSRQ"], ["CQI", "RSSNR"], ["ASU", "Nível"]]
```

No projeto, as métricas são exibidas em grade de 2 colunas:

```kotlin
allMetrics.entries.toList().chunked(2).forEach { row ->
    Row {
        row.forEach { (label, valor) ->
            MetricCard(label = label, value = valor, modifier = Modifier.weight(1f))
        }
        if (row.size == 1) Spacer(Modifier.weight(1f))  // preenche a última linha
    }
}
```

---

## 7. Coleções imutáveis vs. mutáveis

```kotlin
// Imutável — não pode adicionar/remover
val lista: List<Int>         = listOf(1, 2, 3)
val mapa : Map<String, Int>  = mapOf("a" to 1)

// Mutável — pode modificar
val listaMut: MutableList<Int>        = mutableListOf(1, 2, 3)
val mapaMut : MutableMap<String, Int> = mutableMapOf("a" to 1)
listaMut.add(4)
mapaMut["b"] = 2
```

**Regra de ouro:** Dentro da camada de dados, use imutável. Construa com mutável se
necessário, mas exponha sempre como imutável:

```kotlin
class SignalHistory {
    // Privado e mutável internamente
    private val _lista = mutableListOf<SignalPoint>()

    // Público e imutável para fora
    val lista: List<SignalPoint> get() = _lista.toList()
}
```

---

## 8. Desestruturação

```kotlin
// data class pode ser desestruturada:
val ponto = SignalPoint(1000L, -87, NetworkType.LTE)
val (timestamp, dbm, tipo) = ponto

// Pair e Triple:
val par = "RSRP" to "-87 dBm"
val (chave, valor) = par

// Em forEach:
metricas.forEach { (chave, valor) ->
    println("$chave = $valor")
}
```

No projeto, o range do histórico retorna um `Pair`:

```kotlin
fun range(): Pair<Int, Int> {
    ...
    return min - 5 to max + 5
}

// Desestruturado na chamada:
val (minDbm, maxDbm) = history.range()
```

---

## 9. `takeLast` e `take` — subconjuntos

```kotlin
val pontos = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)

pontos.take(3)      // → [1, 2, 3]       (primeiros N)
pontos.takeLast(3)  // → [8, 9, 10]      (últimos N)
pontos.drop(3)      // → [4, 5, 6, 7, 8, 9, 10]  (remove primeiros N)
pontos.dropLast(3)  // → [1, 2, 3, 4, 5, 6, 7]   (remove últimos N)
```

Na `SignalHistory`:

```kotlin
fun average(n: Int = 10): Double? {
    val pts = _points.value.takeLast(n)   // últimos 10 pontos
    return if (pts.isEmpty()) null else pts.map { it.dbm }.average()
}
```

---

## 🧪 Desafios

### Desafio 1
Dada uma `List<CellSignalData>`, escreva uma expressão que:
1. Filtre apenas células `isRegistered == true`
2. Mapeie para `"${cell.networkType.label}: ${cell.dbm} dBm"`
3. Ordene por dbm decrescente
4. Pegue as 3 primeiras

### Desafio 2
Implemente uma função `topCells(cells: List<CellSignalData>, n: Int): List<CellSignalData>`
que retorna as N células com maior dbm, excluindo células com `dbm == Int.MIN_VALUE`.

### Desafio 3 — Stats
Dada uma `List<Int>` representando dbms, calcule:
- Média
- Desvio padrão
- Mediana (o valor do meio quando ordenado)

---

## 📌 Resumo da aula

| Operação | O que faz |
|----------|-----------|
| `map { }` | Transforma cada item → nova lista |
| `filter { }` | Mantém só os que passam na condição |
| `mapNotNull { }` | map + remove nulls |
| `firstOrNull { }` | Primeiro que satisfaz, ou null |
| `sortedByDescending { }` | Ordena do maior para o menor |
| `forEach { }` | Itera sem retornar nova lista |
| `minByOrNull { }` | Item com menor valor da propriedade |
| `chunked(n)` | Divide em grupos de tamanho N |
| `takeLast(n)` | Últimos N itens |
| `buildList { }` | Constrói lista com lógica condicional |

**Próximo Módulo →** [Arquitetura MVVM](../modulo-02-arquitetura/01-mvvm.md) — por que organizar o código é tão importante quanto escrevê-lo.
