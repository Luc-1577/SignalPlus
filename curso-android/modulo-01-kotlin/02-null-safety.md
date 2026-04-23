# Módulo 01 · Aula 02 — Null Safety: O fim do NullPointerException

> **Pré-requisito:** Aula 01 — Kotlin Essencial
> **Objetivo:** Entender o sistema de tipos nullable do Kotlin e nunca mais ter NPE.

---

## O problema que o Kotlin resolve

Em Java, qualquer objeto pode ser `null`. Isso gerou o erro mais famoso da programação:

```java
// Java — pode explodir em runtime
String nome = getNome();    // pode retornar null
int tamanho = nome.length(); // NULLPOINTEREXCEPTION! 💥
```

Tony Hoare, o inventor do null em 1965, chamou isso de seu "erro de um bilhão de dólares".

**O Kotlin resolveu isso em tempo de compilação.** Se o código compilar, não tem NPE.

---

## 1. Tipos nullable vs. não-nullable

No Kotlin, existe uma distinção clara em nível de tipo:

```kotlin
var nome: String  = "Android"   // NUNCA pode ser null
var apelido: String? = null     // PODE ser null (note o ?)

nome = null     // ← ERRO DE COMPILAÇÃO!
apelido = null  // ← OK
```

A `?` depois do tipo transforma-o em "nullable". Sem a `?`, o tipo é garantidamente não-null.

### Por que isso importa no projeto?

Em `CellNetworkInfo`, todos os campos são nullable:

```kotlin
data class CellNetworkInfo(
    val tac    : Int?    = null,   // pode não estar disponível
    val enbId  : Long?   = null,   // pode não estar disponível
    val fdlMhz : Double? = null,   // calculado a partir do ARFCN (que pode não existir)
    ...
)
```

O hardware do Android pode ou não expor cada campo. Ao declarar como `Int?`, o
**compilador nos força a tratar o caso null** — não podemos "esquecer".

---

## 2. Safe Call Operator — `?.`

Para acessar membros de um objeto nullable de forma segura:

```kotlin
val apelido: String? = null

// Jeito perigoso (Java style):
// val tamanho = apelido.length  ← ERRO DE COMPILAÇÃO!

// Jeito seguro:
val tamanho = apelido?.length   // retorna null se apelido é null; Int? 
```

### Encadeamento de safe calls

```kotlin
val cidade: String? = usuario?.endereco?.cidade?.uppercase()
//                          ↑           ↑       ↑
//                     se null,    se null,  se null,
//                     para aqui  para aqui para aqui
//                          └──────── retorna null ──────┘
```

No projeto, vemos isso na conversão de ARFCN:

```kotlin
val freq = arfcn?.let { nrArfcnToMhz(it) }
//              ↑
//         só executa se arfcn != null
//         retorna null se arfcn == null
```

---

## 3. Elvis Operator — `?:`

"Se for null, use este valor padrão":

```kotlin
val nome: String? = null
val nomeSeguro = nome ?: "Desconhecido"   // → "Desconhecido"

val valorOuZero = valor ?: 0
```

**Analogia:** É como o operador ternário, mas só para null:
- "Se `nome` não for null, use `nome`; caso contrário, use `"Desconhecido"`"

No projeto:

```kotlin
val operator = telephony.networkOperatorName
    .takeIf { it.isNotBlank() } ?: "Desconhecido"
//                                ↑
//                    se networkOperatorName for vazio/null,
//                    usa "Desconhecido"
```

---

## 4. `let` — executar código apenas se não for null

```kotlin
val dbm: Int? = lerSinal()

// Sem let (verboso):
if (dbm != null) {
    println("Sinal: $dbm dBm")
}

// Com let (idiomático):
dbm?.let { valor ->
    println("Sinal: $valor dBm")
}

// Se `it` for o único parâmetro:
dbm?.let { println("Sinal: $it dBm") }
```

`let` é uma função de extensão que executa o bloco com o valor como `it` (ou parâmetro nomeado).

No projeto, `let` é usado extensivamente:

```kotlin
val gnbId = nci?.let { it ushr 14 }      // só calcula se nci != null
val freq  = arfcn?.let { nrArfcnToMhz(it) }  // só converte se arfcn != null
```

---

## 5. `also`, `apply`, `run`, `with` — scope functions

Kotlin tem uma família de funções para trabalhar com objetos. Cada uma tem uma diferença
sutil mas importante:

### `also` — "faça algo com isso e retorne o original"

```kotlin
val lista = mutableListOf(1, 2, 3)
    .also { println("Lista criada com ${it.size} itens") }
//   ↑
// retorna a própria lista; `it` = a lista
```

Perfeito para logging ou side effects sem interromper a cadeia.

### `apply` — "configure-me e me retorne"

```kotlin
val extras = mutableMapOf<String, String>()
    .apply {
        put("RSRP", "$rsrp dBm")   // this = o mapa
        put("RSRQ", "$rsrq dB")
    }
// retorna o próprio mapa configurado
```

No projeto, vemos em `parseNr()`:

```kotlin
val extras = mutableMapOf<String, String>()
safeI { ss.ssRsrp }?.let { extras["SS-RSRP"] = "$it dBm" }
safeI { ss.ssRsrq }?.let { extras["SS-RSRQ"] = "$it dB"  }
```

Isso poderia ser reescrito com `apply`:

```kotlin
val extras = buildMap<String, String> {
    safeI { ss.ssRsrp }?.let { put("SS-RSRP", "$it dBm") }
    safeI { ss.ssRsrq }?.let { put("SS-RSRQ", "$it dB")  }
}
```

### `let` — "transforme-me em outra coisa"

```kotlin
val resultado = valor?.let { transformar(it) }
// retorna o resultado de transformar(), não o valor original
```

### `run` — "execute um bloco e retorne o resultado"

```kotlin
val comprimento = "Android".run {
    println("String: $this")   // this = "Android"
    length                     // retorna length
}
// comprimento = 7
```

### Tabela resumida

| Função | `this` ou `it` | Retorna |
|--------|---------------|---------|
| `let`   | `it`    | resultado do bloco |
| `also`  | `it`    | o próprio objeto |
| `apply` | `this`  | o próprio objeto |
| `run`   | `this`  | resultado do bloco |
| `with`  | `this`  | resultado do bloco |

---

## 6. `!!` — o operador "confio em você"

```kotlin
val nome: String? = getNome()
val tamanho = nome!!.length   // lança NPE se nome for null!
```

`!!` diz ao compilador: "confie em mim, não é null". Se for null em runtime, lança
`KotlinNullPointerException`.

**Use `!!` com muito cuidado.** Prefira sempre `?.` ou `?: valor_padrão`.
Em código de produção, `!!` é quase sempre sinal de que algo está errado no design.

---

## 7. `takeIf` e `takeUnless`

```kotlin
// takeIf: retorna o valor SE a condição for true; senão null
val valorPositivo = numero.takeIf { it > 0 }

// takeUnless: o oposto — retorna o valor SE a condição for false
val valorNaoVazio = texto.takeUnless { it.isEmpty() }
```

No projeto, isso é usado para filtrar valores sentinela:

```kotlin
val tac   = safeI { cid.tac.takeIf   { it != Int.MAX_VALUE } }
val arfcn = safeI { cid.nrarfcn.takeIf { it > 0 } }
```

---

## 8. Smart Casts — o compilador é inteligente

```kotlin
fun processar(valor: Any) {   // Any = qualquer tipo (como Object no Java)
    if (valor is String) {
        // Aqui dentro, o compilador sabe que `valor` é String
        println(valor.uppercase())   // sem cast manual!
    }

    if (valor !is Int) return
    // Aqui, o compilador sabe que `valor` é Int
    println(valor + 10)
}
```

No projeto, o `parseCellInfo` usa smart cast com `when`:

```kotlin
private fun parseCellInfo(info: CellInfo, ...): CellSignalData? = when {
    info is CellInfoNr   -> parseNr(info, ...)   // info é automaticamente CellInfoNr aqui
    info is CellInfoLte  -> parseLte(info, ...)  // info é CellInfoLte aqui
    ...
}
```

---

## 9. Padrão `safeI` / `safeL` do projeto

O repositório tem dois helpers para lidar com valores do hardware:

```kotlin
private fun safeI(block: () -> Int?): Int? = try {
    val v = block()
    if (v != null && v != Int.MAX_VALUE && v != Int.MIN_VALUE) v else null
} catch (_: Exception) { null }
```

**O que isso faz:**
1. Executa `block()` — uma função que retorna `Int?`
2. Se lançar qualquer exceção → retorna `null` (hardware falhou graciosamente)
3. Se retornar `Int.MAX_VALUE` ou `Int.MIN_VALUE` → retorna `null` (valor sentinela)
4. Caso contrário → retorna o valor

**Por que usar lambda (`block: () -> Int?`) em vez de passar o valor direto?**

Porque se `block()` lançar exceção, o `try/catch` captura. Se passássemos o valor
avaliado, a exceção aconteceria antes de entrar na função:

```kotlin
// Errado — exceção ocorre antes do try:
safeI(cid.tac)           // cid.tac pode já lançar exceção aqui

// Correto — exceção capturada dentro do try:
safeI { cid.tac }        // a exceção ocorre dentro do bloco, dentro do try
```

---

## 🧪 Desafios

### Desafio 1 — Safe calls
Dado `val info: CellNetworkInfo? = null`, escreva uma expressão que:
- Se `info` não for null, retorne `"FDL: ${info.fdlMhz} MHz"`
- Se `info.fdlMhz` for null, retorne `"FDL: N/D"`
- Se `info` for null, retorne `"Sem informação"`

### Desafio 2 — Elvis chain
Reescreva este código usando Elvis operator e `takeIf`:

```kotlin
var resultado: String
if (dbm != null && dbm != Int.MAX_VALUE && dbm != 0) {
    resultado = "$dbm dBm"
} else {
    resultado = "N/D"
}
```

### Desafio 3 — safeI personalizado
Escreva uma função `safeDouble(block: () -> Double?): Double?` que:
- Retorne `null` se o bloco lançar exceção
- Retorne `null` se o valor for `Double.NaN` ou `Double.POSITIVE_INFINITY`
- Retorne o valor caso contrário

---

## 📌 Resumo da aula

```
String   → nunca null, garantido pelo compilador
String?  → pode ser null

?.  → acesso seguro (retorna null em vez de explodir)
?:  → valor padrão se null (Elvis)
!!  → "confio que não é null" (evite!)
?.let { } → executa bloco só se não null
takeIf { } → filtra baseado em condição
```

**A regra mais importante:**
> Se o compilador reclamar de null, **não** use `!!` para calar ele.
> Trate o caso null de verdade.

**Próxima aula →** [Coleções Funcionais](03-colecoes-funcionais.md) — map, filter e o poder da programação funcional no Kotlin.
