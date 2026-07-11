package com.example.ls.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ComponentsTest {

    @Test
    fun `formatIntervalLabel below 1000ms shows milliseconds`() {
        assertEquals("200ms", formatIntervalLabel(200))
        assertEquals("999ms", formatIntervalLabel(999))
    }

    @Test
    fun `formatIntervalLabel exact seconds uses singular for 1s`() {
        assertEquals("1 segundo", formatIntervalLabel(1000))
    }

    @Test
    fun `formatIntervalLabel exact seconds uses plural for more than 1s`() {
        assertEquals("2 segundos", formatIntervalLabel(2000))
        assertEquals("5 segundos", formatIntervalLabel(5000))
    }

    @Test
    fun `formatIntervalLabel non-exact seconds shows one decimal`() {
        assertEquals("1.5s", formatIntervalLabel(1500))
    }
}
