# Módulo 07 · Aula 01 — Testes: Garantindo que o Código Funciona

> **Objetivo:** Escrever testes unitários para a camada de dados e entender a pirâmide de testes Android.

---

## Por que testar?

Sem testes, você descobre bugs em produção — quando o usuário já foi afetado.
Com testes, você descobre bugs durante o desenvolvimento — quando é fácil corrigir.

**A pirâmide de testes Android:**

```
         /\
        /  \      ← E2E Tests (lentos, frágeis, caros)
       / UI \        → Espresso, UIAutomator
      /──────\
     /        \
    / Integração\   ← Integration Tests
   /────────────\      → Testes com Room, ViewModel + fake repo
  /              \
 / Unit Tests     \  ← Unit Tests (rápidos, baratos, robustos)
/──────────────────\    → JUnit, Mockito/MockK, sem Android

Mais testes na base = mais confiança com menos custo
```

---

## 1. Testando `FrequencyUtils` — puro JUnit

Crie `app/src/test/java/com/example/ls/data/FrequencyUtilsTest.kt`:

```kotlin
package com.example.ls.data

import org.junit.Assert.*
import org.junit.Test

class FrequencyUtilsTest {

    // ── earfcnToInfo ──────────────────────────────────────────

    @Test
    fun `EARFCN 0 deve retornar Banda 1 com FDL 2110 MHz`() {
        val result = earfcnToInfo(0)
        assertNotNull("Deve encontrar a banda", result)
        assertEquals(1, result!!.first)
        assertEquals(2110.0, result.second, 0.001)
        assertEquals(1920.0, result.third, 0.001)
    }

    @Test
    fun `EARFCN 1300 deve retornar Banda 3 com FDL 1815 MHz e FUL 1720 MHz`() {
        val result = earfcnToInfo(1300)
        assertNotNull(result)
        assertEquals(3, result!!.first)
        assertEquals(1815.0, result.second, 0.001)   // 1805 + 0.1*(1300-1200)
        assertEquals(1720.0, result.third, 0.001)    // 1710 + 0.1*(1300-1200)
    }

    @Test
    fun `EARFCN 9210 deve retornar Banda 28 com FDL 758 MHz`() {
        val result = earfcnToInfo(9210)
        assertNotNull(result)
        assertEquals(28, result!!.first)
        assertEquals(758.0, result.second, 0.001)
    }

    @Test
    fun `EARFCN fora de qualquer banda deve retornar null`() {
        assertNull(earfcnToInfo(99999))
        assertNull(earfcnToInfo(-1))
    }

    @Test
    fun `Banda TDD deve ter FUL igual a FDL`() {
        // Band 41 (TDD) — EARFCN 39650
        val result = earfcnToInfo(39650)
        assertNotNull(result)
        // Em TDD, FUL == FDL
        assertEquals(result!!.second, result.third)
    }

    // ── nrArfcnToMhz ─────────────────────────────────────────

    @Test
    fun `NRARFCN 0 deve retornar 0 MHz`() {
        assertEquals(0.0, nrArfcnToMhz(0)!!, 0.001)
    }

    @Test
    fun `NRARFCN 599999 deve estar no limite FR1`() {
        val freq = nrArfcnToMhz(599999)
        assertNotNull(freq)
        assertEquals(2999.995, freq!!, 0.001)
    }

    @Test
    fun `NRARFCN 627264 deve retornar aproximadamente 3500 MHz`() {
        val freq = nrArfcnToMhz(627264)
        assertNotNull(freq)
        // 3000 + (627264 - 600000) * 0.015 = 3000 + 408.96 = 3408.96
        assertEquals(3408.96, freq!!, 1.0)
    }

    @Test
    fun `NRARFCN negativo deve retornar null`() {
        assertNull(nrArfcnToMhz(-1))
    }

    // ── taToDistanceMeters ────────────────────────────────────

    @Test
    fun `TA 1 deve retornar 78_125 metros`() {
        assertEquals(78.125, taToDistanceMeters(1)!!, 0.001)
    }

    @Test
    fun `TA 10 deve retornar 781_25 metros`() {
        assertEquals(781.25, taToDistanceMeters(10)!!, 0.001)
    }

    @Test
    fun `TA 0 deve retornar null (invalido)`() {
        assertNull(taToDistanceMeters(0))
    }

    @Test
    fun `TA MAX_VALUE deve retornar null (sentinela)`() {
        assertNull(taToDistanceMeters(Int.MAX_VALUE))
    }

    // ── formatMhz ────────────────────────────────────────────

    @Test
    fun `formatMhz deve usar MHz para valores abaixo de 1000`() {
        assertEquals("700.0 MHz", formatMhz(700.0))
    }

    @Test
    fun `formatMhz deve usar GHz para valores acima de 1000`() {
        assertEquals("2.100 GHz", formatMhz(2100.0))
        assertEquals("3.500 GHz", formatMhz(3500.0))
    }
}
```

---

## 2. Testando `SignalHistory` — JUnit com StateFlow

```kotlin
package com.example.ls.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SignalHistoryTest {

    private lateinit var history: SignalHistory

    @Before
    fun setUp() {
        history = SignalHistory(capacity = 5)
    }

    private fun ponto(dbm: Int) = SignalPoint(
        timestampMs = System.currentTimeMillis(),
        dbm         = dbm,
        networkType = NetworkType.LTE,
    )

    @Test
    fun `add deve adicionar pontos ate a capacidade`() {
        history.add(ponto(-87))
        history.add(ponto(-90))
        assertEquals(2, history.points.value.size)
    }

    @Test
    fun `add deve remover o mais antigo quando capacity for atingida`() {
        repeat(5) { i -> history.add(ponto(-80 - i)) }
        val primeiroAntes = history.points.value.first().dbm

        // Adiciona o 6º — deve remover o 1º
        history.add(ponto(-99))

        val primeiroDepois = history.points.value.first().dbm
        assertNotEquals(primeiroAntes, primeiroDepois)
        assertEquals(5, history.points.value.size)
        assertEquals(-99, history.points.value.last().dbm)
    }

    @Test
    fun `clear deve esvaziar a lista`() {
        history.add(ponto(-87))
        history.add(ponto(-90))
        history.clear()
        assertTrue(history.points.value.isEmpty())
    }

    @Test
    fun `average deve calcular a media dos ultimos N pontos`() {
        history.add(ponto(-80))
        history.add(ponto(-90))
        history.add(ponto(-100))

        // Média dos últimos 3 = (-80 + -90 + -100) / 3 = -90
        val avg = history.average(3)
        assertNotNull(avg)
        assertEquals(-90.0, avg!!, 0.001)
    }

    @Test
    fun `average deve retornar null se vazio`() {
        assertNull(history.average())
    }

    @Test
    fun `range deve retornar intervalo com margem de 5`() {
        history.add(ponto(-80))
        history.add(ponto(-100))

        val (min, max) = history.range()
        assertEquals(-100 - 5, min)
        assertEquals(-80 + 5, max)
    }

    @Test
    fun `range deve retornar padrao se vazio`() {
        val (min, max) = history.range()
        assertEquals(-120, min)
        assertEquals(-40, max)
    }

    @Test
    fun `stdDev deve ser 0 se menos de 2 pontos`() {
        history.add(ponto(-87))
        assertEquals(0.0, history.stdDev(), 0.001)
    }

    @Test
    fun `stdDev deve ser baixo para sinal estavel`() {
        // Pontos muito próximos = desvio padrão baixo
        repeat(5) { history.add(ponto(-87)) }
        assertEquals(0.0, history.stdDev(5), 0.001)
    }

    @Test
    fun `stdDev deve ser alto para sinal instavel`() {
        history.add(ponto(-60))
        history.add(ponto(-120))
        // Desvio alto
        assertTrue(history.stdDev(2) > 20.0)
    }
}
```

---

## 3. Testando `signalQuality` — funções puras

```kotlin
class SignalQualityTest {

    @Test
    fun `dbm 0 deve ser NO_SIGNAL independente do tipo`() {
        assertEquals(SignalQuality.NO_SIGNAL, signalQuality(0, NetworkType.LTE))
        assertEquals(SignalQuality.NO_SIGNAL, signalQuality(0, NetworkType.NR_5G))
    }

    @Test
    fun `LTE em -87 deve ser GOOD`() {
        assertEquals(SignalQuality.GOOD, signalQuality(-87, NetworkType.LTE))
    }

    @Test
    fun `LTE em -80 exato deve ser EXCELLENT`() {
        assertEquals(SignalQuality.EXCELLENT, signalQuality(-80, NetworkType.LTE))
    }

    @Test
    fun `LTE em -81 deve ser GOOD e nao EXCELLENT`() {
        assertEquals(SignalQuality.GOOD, signalQuality(-81, NetworkType.LTE))
    }

    @Test
    fun `WCDMA tem thresholds diferentes do LTE`() {
        // -77 é GOOD no LTE mas EXCELLENT no WCDMA (limiar >= -75)
        assertEquals(SignalQuality.GOOD,      signalQuality(-77, NetworkType.LTE))
        assertEquals(SignalQuality.EXCELLENT, signalQuality(-77, NetworkType.WCDMA))
    }

    @Test
    fun `signalProgress deve retornar 0 para dbm 0`() {
        assertEquals(0f, signalProgress(0, NetworkType.LTE), 0.001f)
    }

    @Test
    fun `signalProgress deve retornar 1 para dbm maximo`() {
        // Para LTE: max = -44 dBm
        assertEquals(1f, signalProgress(-44, NetworkType.LTE), 0.001f)
    }

    @Test
    fun `signalProgress nunca sai do intervalo 0 a 1`() {
        // Valor absurdamente alto
        val p1 = signalProgress(0, NetworkType.LTE)
        assertEquals(0f, p1, 0.001f)

        // Valor absurdamente baixo
        val p2 = signalProgress(-200, NetworkType.LTE)
        assertEquals(0f, p2, 0.001f)
    }
}
```

---

## 4. Como rodar os testes

```bash
# Todos os unit tests (rápido, sem device)
./gradlew test

# Apenas os testes de uma classe
./gradlew test --tests "com.example.ls.data.FrequencyUtilsTest"

# Com relatório HTML
./gradlew test && open app/build/reports/tests/testDebugUnitTest/index.html

# Testes com cobertura (requer configuração de JaCoCo)
./gradlew testDebugUnitTestCoverage
```

---

## 5. Dependências necessárias para testes

```kotlin
// app/build.gradle.kts
dependencies {
    // Testes unitários
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    // Testes instrumentados (device/emulador)
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}
```

---

## 6. `@ParameterizedTest` — testando múltiplos casos

```kotlin
@RunWith(Parameterized::class)
class EarfcnParameterizedTest(
    private val earfcn: Int,
    private val expectedBand: Int,
    private val expectedFdl: Double,
) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "EARFCN={0} → Banda {1}, {2} MHz")
        fun data() = listOf(
            arrayOf(0,    1,  2110.0),
            arrayOf(600,  2,  1930.0),
            arrayOf(1200, 3,  1805.0),
            arrayOf(9210, 28, 758.0),
        )
    }

    @Test
    fun `deve converter EARFCN para banda e frequencia corretamente`() {
        val result = earfcnToInfo(earfcn)
        assertNotNull(result)
        assertEquals(expectedBand, result!!.first)
        assertEquals(expectedFdl, result.second, 0.001)
    }
}
```

---

## 7. O que NÃO testar (regra de ouro)

```
✅ Teste:
→ Lógica de negócio pura (FrequencyUtils, signalQuality)
→ Transformações de dados
→ Comportamento de classes com estado (SignalHistory)
→ Edge cases (null, MAX_VALUE, valores fora de range)

❌ Não teste:
→ Código trivial (getters/setters de data class)
→ Frameworks externos (Compose, Room, Retrofit)
→ Android APIs (testam-se com integration tests)
→ toString() gerado automaticamente
```

---

## 📌 Resumo

```
Pirâmide de testes:
  Unit Tests      → JUnit, sem Android, muito rápidos
  Integration     → ViewModel + fake repository
  UI/E2E          → Compose Test, Espresso

Boas classes para unit test:
  FrequencyUtils.kt → funções puras, fáceis de verificar
  SignaData.kt      → signalQuality, signalProgress
  SignalHistory.kt  → add, clear, average, stdDev, range

Rodar: ./gradlew test
```

**Próxima aula →** [Próximos Passos](02-proximos-passos.md) — o roadmap para se tornar sênior.
