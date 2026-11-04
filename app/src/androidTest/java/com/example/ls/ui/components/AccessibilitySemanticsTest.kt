package com.example.ls.ui.components

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ls.data.CellNetworkInfo
import com.example.ls.data.CellSignalData
import com.example.ls.data.NetworkType
import com.example.ls.data.SignalPoint
import com.example.ls.ui.theme.SignalPlusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Garante, na árvore semântica real (a que o TalkBack consome), as correções
 *  de acessibilidade que não dá pra verificar só com teste JVM. */
@RunWith(AndroidJUnit4::class)
class AccessibilitySemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun cellIdentifierItem_mergesLabelAndValueIntoOneNode() {
        val cell = CellSignalData(
            networkType = NetworkType.LTE, dbm = -95, asuLevel = 20, level = 2,
            isRegistered = true, operator = "Teste",
            netInfo = CellNetworkInfo(tac = 8514, pci = 555),
        )
        composeRule.setContent { SignalPlusTheme { CellIdentifiersCard(cell) } }

        // Árvore mesclada: o nó que contém "TAC" também deve conter o valor —
        // ou seja, o TalkBack lê os dois numa parada só.
        val texts = composeRule.onNodeWithText("TAC").fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.Text)?.map { it.text }
        assertEquals(listOf("TAC", "8514"), texts)
    }

    @Test
    fun signalChart_exposesSummaryToScreenReaders() {
        val pts = listOf(-95, -110, -85).mapIndexed { i, d -> SignalPoint(i * 1000L, d, NetworkType.LTE) }
        composeRule.setContent { SignalPlusTheme { SignalChart(pts, minDbm = -120, maxDbm = -80) } }

        composeRule.onNode(hasContentDescription("Gráfico de sinal", substring = true))
            .assertContentDescriptionEquals(
                "Gráfico de sinal: 3 leituras, mínimo -110 dBm, máximo -85 dBm, última -85 dBm",
            )
    }
}
