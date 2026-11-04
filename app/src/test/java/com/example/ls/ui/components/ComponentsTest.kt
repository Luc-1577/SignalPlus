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

    // ── formatFileSize ───────────────────────────────────────────────────────

    @Test
    fun `formatFileSize below 1024 bytes shows bytes`() {
        assertEquals("0 B", formatFileSize(0))
        assertEquals("114 B", formatFileSize(114))
        assertEquals("1023 B", formatFileSize(1023))
    }

    @Test
    fun `formatFileSize at 1024 boundary switches to KB`() {
        assertEquals("1.0 KB", formatFileSize(1024))
    }

    @Test
    fun `formatFileSize below 1MB shows KB with one decimal`() {
        assertEquals("2.3 KB", formatFileSize((2.3 * 1024).toLong()))
    }

    @Test
    fun `formatFileSize at 1MB boundary switches to MB`() {
        assertEquals("1.0 MB", formatFileSize(1024L * 1024))
    }

    @Test
    fun `formatFileSize above 1MB shows MB with one decimal`() {
        assertEquals("1.1 MB", formatFileSize((1.1 * 1024 * 1024).toLong()))
    }
}
