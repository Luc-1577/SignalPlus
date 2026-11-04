package com.example.ls.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class TowersScreenTest {

    @Test
    fun `radarContentDescription with no towers`() {
        assertEquals("Radar de torres: nenhuma torre", radarContentDescription(0, 0, null, null))
    }

    @Test
    fun `radarContentDescription singular forms and nearest in meters`() {
        assertEquals(
            "Radar de torres: 1 torre, 1 conhecida. Mais próxima a 850 m ao nordeste",
            radarContentDescription(1, 1, 850.0, 45.0),
        )
    }

    @Test
    fun `radarContentDescription plural forms and nearest in km spells out east`() {
        assertEquals(
            "Radar de torres: 4 torres, 0 conhecidas. Mais próxima a 1.2 km ao leste",
            radarContentDescription(4, 0, 1_200.0, 90.0),
        )
    }

    @Test
    fun `radarContentDescription wraps 350 degrees to north`() {
        assertEquals(
            "Radar de torres: 2 torres, 1 conhecida. Mais próxima a 300 m ao norte",
            radarContentDescription(2, 1, 300.0, 350.0),
        )
    }
}
