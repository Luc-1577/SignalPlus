package com.example.ls.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrequencyUtilsTest {

    private val DELTA = 0.001

    // ── earfcnToInfo ────────────────────────────────────────────────────────

    @Test
    fun `earfcnToInfo band1 low edge`() {
        val info = earfcnToInfo(0)
        assertEquals(1, info!!.first)
        assertEquals(2110.0, info.second, DELTA)
        assertEquals(1920.0, info.third!!, DELTA)
    }

    @Test
    fun `earfcnToInfo band1 high edge`() {
        val info = earfcnToInfo(599)
        assertEquals(1, info!!.first)
        assertEquals(2169.9, info.second, DELTA)
        assertEquals(1979.9, info.third!!, DELTA)
    }

    @Test
    fun `earfcnToInfo band28 700MHz Brazil low edge`() {
        val info = earfcnToInfo(9210)
        assertEquals(28, info!!.first)
        assertEquals(758.0, info.second, DELTA)
        assertEquals(703.0, info.third!!, DELTA)
    }

    @Test
    fun `earfcnToInfo TDD band sets ful equal to fdl`() {
        val info = earfcnToInfo(37750)
        assertEquals(38, info!!.first)
        assertEquals(2570.0, info.second, DELTA)
        assertEquals(2570.0, info.third!!, DELTA)
    }

    @Test
    fun `earfcnToInfo SDL band has null ful`() {
        val info = earfcnToInfo(9660)
        assertEquals(29, info!!.first)
        assertEquals(717.0, info.second, DELTA)
        assertNull(info.third)
    }

    @Test
    fun `earfcnToInfo gap between bands returns null`() {
        assertNull(earfcnToInfo(2700))
    }

    @Test
    fun `earfcnToInfo negative returns null`() {
        assertNull(earfcnToInfo(-1))
    }

    // ── nrArfcnToMhz ────────────────────────────────────────────────────────

    @Test
    fun `nrArfcnToMhz negative returns null`() {
        assertNull(nrArfcnToMhz(-1))
    }

    @Test
    fun `nrArfcnToMhz first range low edge`() {
        assertEquals(0.0, nrArfcnToMhz(0)!!, DELTA)
    }

    @Test
    fun `nrArfcnToMhz first range high edge`() {
        assertEquals(2999.995, nrArfcnToMhz(599999)!!, DELTA)
    }

    @Test
    fun `nrArfcnToMhz second range low edge`() {
        assertEquals(3000.0, nrArfcnToMhz(600000)!!, DELTA)
    }

    @Test
    fun `nrArfcnToMhz second range high edge`() {
        assertEquals(24249.99, nrArfcnToMhz(2016666)!!, DELTA)
    }

    @Test
    fun `nrArfcnToMhz third range low edge`() {
        assertEquals(24250.08, nrArfcnToMhz(2016667)!!, DELTA)
    }

    @Test
    fun `nrArfcnToMhz third range high edge`() {
        assertEquals(99999.96, nrArfcnToMhz(3279165)!!, DELTA)
    }

    @Test
    fun `nrArfcnToMhz beyond max returns null`() {
        assertNull(nrArfcnToMhz(3279166))
    }

    // ── uarfcnToMhz ─────────────────────────────────────────────────────────

    @Test
    fun `uarfcnToMhz zero returns null`() {
        assertNull(uarfcnToMhz(0))
    }

    @Test
    fun `uarfcnToMhz negative returns null`() {
        assertNull(uarfcnToMhz(-5))
    }

    @Test
    fun `uarfcnToMhz positive value`() {
        assertEquals(2112.4, uarfcnToMhz(10562)!!, DELTA)
    }

    // ── gsmArfcnToInfo ──────────────────────────────────────────────────────

    @Test
    fun `gsmArfcnToInfo range 1 to 124 low edge`() {
        val (fdl, ful) = gsmArfcnToInfo(1)!!
        assertEquals(935.2, fdl, DELTA)
        assertEquals(890.2, ful, DELTA)
    }

    @Test
    fun `gsmArfcnToInfo range 1 to 124 high edge`() {
        val (fdl, ful) = gsmArfcnToInfo(124)!!
        assertEquals(959.8, fdl, DELTA)
        assertEquals(914.8, ful, DELTA)
    }

    @Test
    fun `gsmArfcnToInfo arfcn zero special case`() {
        val (fdl, ful) = gsmArfcnToInfo(0)!!
        assertEquals(935.0, fdl, DELTA)
        assertEquals(890.0, ful, DELTA)
    }

    @Test
    fun `gsmArfcnToInfo range 128 to 251 low edge`() {
        val (fdl, ful) = gsmArfcnToInfo(128)!!
        assertEquals(869.2, fdl, DELTA)
        assertEquals(824.2, ful, DELTA)
    }

    @Test
    fun `gsmArfcnToInfo range 128 to 251 high edge`() {
        val (fdl, ful) = gsmArfcnToInfo(251)!!
        assertEquals(893.8, fdl, DELTA)
        assertEquals(848.8, ful, DELTA)
    }

    @Test
    fun `gsmArfcnToInfo range 512 to 885 low edge`() {
        val (fdl, ful) = gsmArfcnToInfo(512)!!
        assertEquals(1805.2, fdl, DELTA)
        assertEquals(1710.2, ful, DELTA)
    }

    @Test
    fun `gsmArfcnToInfo range 512 to 885 high edge`() {
        val (fdl, ful) = gsmArfcnToInfo(885)!!
        assertEquals(1879.8, fdl, DELTA)
        assertEquals(1784.8, ful, DELTA)
    }

    @Test
    fun `gsmArfcnToInfo range 975 to 1023 uses 935 constant not 935 point 2`() {
        // Regression test: the base constant for this band is 935.0, not 935.2.
        // arfcn=1024 would be the "offset zero" point but is out of range,
        // so we verify via the documented low/high edges of 975..1023.
        val (fdlLow, fulLow) = gsmArfcnToInfo(975)!!
        assertEquals(925.2, fdlLow, DELTA)
        assertEquals(880.2, fulLow, DELTA)

        val (fdlHigh, fulHigh) = gsmArfcnToInfo(1023)!!
        assertEquals(934.8, fdlHigh, DELTA)
        assertEquals(889.8, fulHigh, DELTA)
    }

    @Test
    fun `gsmArfcnToInfo out of all ranges returns null`() {
        assertNull(gsmArfcnToInfo(300))
        assertNull(gsmArfcnToInfo(-1))
        assertNull(gsmArfcnToInfo(1024))
    }

    // ── taToDistanceMeters ──────────────────────────────────────────────────

    @Test
    fun `taToDistanceMeters zero returns null`() {
        assertNull(taToDistanceMeters(0))
    }

    @Test
    fun `taToDistanceMeters negative returns null`() {
        assertNull(taToDistanceMeters(-1))
    }

    @Test
    fun `taToDistanceMeters MAX_VALUE returns null`() {
        assertNull(taToDistanceMeters(Int.MAX_VALUE))
    }

    @Test
    fun `taToDistanceMeters valid ta converts to meters`() {
        assertEquals(78.125, taToDistanceMeters(1)!!, DELTA)
        assertEquals(781.25, taToDistanceMeters(10)!!, DELTA)
    }

    // ── formatMhz ───────────────────────────────────────────────────────────

    @Test
    fun `formatMhz below 1000 uses MHz with one decimal`() {
        assertEquals("850.0 MHz", formatMhz(850.0))
    }

    @Test
    fun `formatMhz at 1000 boundary switches to GHz`() {
        assertEquals("1.000 GHz", formatMhz(1000.0))
    }

    @Test
    fun `formatMhz above 1000 uses GHz with three decimals`() {
        assertEquals("1.500 GHz", formatMhz(1500.0))
    }

    // ── formatBwKhz ─────────────────────────────────────────────────────────

    @Test
    fun `formatBwKhz below 1000 uses kHz`() {
        assertEquals("500 kHz", formatBwKhz(500))
    }

    @Test
    fun `formatBwKhz at 1000 boundary switches to MHz`() {
        assertEquals("1 MHz", formatBwKhz(1000))
    }

    @Test
    fun `formatBwKhz above 1000 uses MHz`() {
        assertEquals("20 MHz", formatBwKhz(20000))
    }

    // ── formatDistance ──────────────────────────────────────────────────────

    @Test
    fun `formatDistance below 1000 uses meters`() {
        assertEquals("~500 m", formatDistance(500.0))
    }

    @Test
    fun `formatDistance at 1000 boundary switches to km`() {
        assertEquals("~1.0 km", formatDistance(1000.0))
    }

    @Test
    fun `formatDistance above 1000 uses km with one decimal`() {
        assertEquals("~2.5 km", formatDistance(2500.0))
    }
}
