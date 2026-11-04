package com.example.ls.ui.components

import com.example.ls.data.NetworkType
import com.example.ls.data.SignalPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class SignalChartTest {

    @Test
    fun `signalChartDescription empty`() {
        assertEquals("Gráfico de sinal sem leituras", signalChartDescription(emptyList()))
    }

    @Test
    fun `signalChartDescription summarizes range and latest`() {
        val pts = listOf(-95, -110, -85, -92).mapIndexed { i, d -> SignalPoint(i * 1000L, d, NetworkType.LTE) }
        assertEquals(
            "Gráfico de sinal: 4 leituras, mínimo -110 dBm, máximo -85 dBm, última -92 dBm",
            signalChartDescription(pts),
        )
    }
}
