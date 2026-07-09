package com.example.ls.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalHistoryTest {

    private val DELTA = 0.001

    private fun point(dbm: Int, ts: Long = 0L) =
        SignalPoint(timestampMs = ts, dbm = dbm, networkType = NetworkType.LTE)

    // ── add() / circular buffer ──────────────────────────────────────────────

    @Test
    fun `add keeps points under capacity`() {
        val history = SignalHistory(capacity = 5)
        history.add(point(-80, 1))
        history.add(point(-81, 2))

        assertEquals(2, history.points.value.size)
    }

    @Test
    fun `add drops oldest point once capacity is exceeded`() {
        val history = SignalHistory(capacity = 3)
        history.add(point(-70, 1))
        history.add(point(-71, 2))
        history.add(point(-72, 3))
        history.add(point(-73, 4))
        history.add(point(-74, 5))

        val pts = history.points.value
        assertEquals(3, pts.size)
        // Oldest two (ts=1, ts=2) should have been dropped; last 3 remain in order.
        assertEquals(listOf(3L, 4L, 5L), pts.map { it.timestampMs })
        assertEquals(listOf(-72, -73, -74), pts.map { it.dbm })
    }

    // ── range() ───────────────────────────────────────────────────────────

    @Test
    fun `range on empty history returns default -120 to -40`() {
        val history = SignalHistory()
        assertEquals(-120 to -40, history.range())
    }

    @Test
    fun `range with data expands around min and max with 5 unit padding`() {
        val history = SignalHistory()
        history.add(point(-100))
        history.add(point(-90))
        history.add(point(-50))

        // min = min(-100, -90, -50).coerceAtMost(-40) = -100
        // max = max(-100, -90, -50).coerceAtLeast(min + 10 = -90) = -50
        val (min, max) = history.range()
        assertEquals(-105, min)
        assertEquals(-45, max)
    }

    @Test
    fun `range coerces min to at most -40`() {
        val history = SignalHistory()
        history.add(point(-30))

        // min = (-30).coerceAtMost(-40) = -40
        // max = (-30).coerceAtLeast(min + 10 = -30) = -30
        val (min, max) = history.range()
        assertEquals(-45, min)
        assertEquals(-25, max)
    }

    @Test
    fun `range coerces max to at least min plus 10 when all points are equal`() {
        val history = SignalHistory()
        history.add(point(-80))
        history.add(point(-80))

        // min = (-80).coerceAtMost(-40) = -80
        // max = (-80).coerceAtLeast(min + 10 = -70) = -70
        val (min, max) = history.range()
        assertEquals(-85, min)
        assertEquals(-65, max)
    }

    // ── average() ─────────────────────────────────────────────────────────

    @Test
    fun `average on empty history is null`() {
        val history = SignalHistory()
        assertNull(history.average())
    }

    @Test
    fun `average over all points when n exceeds available points`() {
        val history = SignalHistory()
        history.add(point(-90))
        history.add(point(-80))
        history.add(point(-70))

        assertEquals(-80.0, history.average(10)!!, DELTA)
    }

    @Test
    fun `average respects n by taking only the last n points`() {
        val history = SignalHistory()
        history.add(point(-90))
        history.add(point(-80))
        history.add(point(-70))

        assertEquals(-75.0, history.average(2)!!, DELTA)
    }

    // ── stdDev() ──────────────────────────────────────────────────────────

    @Test
    fun `stdDev is zero when history is empty`() {
        val history = SignalHistory()
        assertEquals(0.0, history.stdDev(), DELTA)
    }

    @Test
    fun `stdDev is zero with a single point`() {
        val history = SignalHistory()
        history.add(point(-80))
        assertEquals(0.0, history.stdDev(), DELTA)
    }

    @Test
    fun `stdDev computes population standard deviation over last n points`() {
        val history = SignalHistory()
        history.add(point(-80))
        history.add(point(-70))

        assertEquals(5.0, history.stdDev(2), DELTA)
    }

    // ── clear() ───────────────────────────────────────────────────────────

    @Test
    fun `clear empties the history`() {
        val history = SignalHistory()
        history.add(point(-80))
        history.add(point(-90))

        history.clear()

        assertTrue(history.points.value.isEmpty())
        assertEquals(-120 to -40, history.range())
        assertNull(history.average())
    }
}
