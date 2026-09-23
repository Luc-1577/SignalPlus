package com.example.ls.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SignaDataTest {

    private val DELTA = 0.0001f

    // ── AUTOMATIC_SUBSCRIPTION_ID ────────────────────────────────────────────

    @Test
    fun `AUTOMATIC_SUBSCRIPTION_ID matches SubscriptionManager INVALID_SUBSCRIPTION_ID`() {
        assertEquals(-1, AUTOMATIC_SUBSCRIPTION_ID)
    }

    // ── NetworkType.hasDedicatedDbmMetric ───────────────────────────────────

    @Test
    fun `hasDedicatedDbmMetric is true only for LTE and NR_5G`() {
        assertEquals(true, NetworkType.LTE.hasDedicatedDbmMetric)
        assertEquals(true, NetworkType.NR_5G.hasDedicatedDbmMetric)
        // WCDMA/HSPA/GSM_2G/CDMA: SignalRepository's parsers only put "ASU"
        // (and, for CDMA, "EVDO dBm" — a different carrier) in extraMetrics,
        // never a dedicated dBm-named entry for the serving cell itself.
        assertEquals(false, NetworkType.WCDMA.hasDedicatedDbmMetric)
        assertEquals(false, NetworkType.HSPA.hasDedicatedDbmMetric)
        assertEquals(false, NetworkType.GSM_2G.hasDedicatedDbmMetric)
        assertEquals(false, NetworkType.CDMA.hasDedicatedDbmMetric)
        assertEquals(false, NetworkType.UNKNOWN.hasDedicatedDbmMetric)
    }

    // ── signalQuality: sentinels ────────────────────────────────────────────

    @Test
    fun `signalQuality MIN_VALUE is always NO_SIGNAL`() {
        for (type in NetworkType.values()) {
            assertEquals(SignalQuality.NO_SIGNAL, signalQuality(Int.MIN_VALUE, type))
        }
    }

    @Test
    fun `signalQuality MAX_VALUE is always NO_SIGNAL`() {
        for (type in NetworkType.values()) {
            assertEquals(SignalQuality.NO_SIGNAL, signalQuality(Int.MAX_VALUE, type))
        }
    }

    @Test
    fun `signalQuality zero is always NO_SIGNAL`() {
        for (type in NetworkType.values()) {
            assertEquals(SignalQuality.NO_SIGNAL, signalQuality(0, type))
        }
    }

    // ── signalQuality: NR_5G and LTE thresholds ─────────────────────────────

    @Test
    fun `signalQuality NR_5G and LTE thresholds`() {
        for (type in listOf(NetworkType.NR_5G, NetworkType.LTE)) {
            assertEquals(SignalQuality.EXCELLENT, signalQuality(-80, type))
            assertEquals(SignalQuality.GOOD, signalQuality(-90, type))
            assertEquals(SignalQuality.FAIR, signalQuality(-100, type))
            assertEquals(SignalQuality.POOR, signalQuality(-110, type))
            assertEquals(SignalQuality.VERY_POOR, signalQuality(-111, type))
        }
    }

    // ── signalQuality: WCDMA/HSPA thresholds ────────────────────────────────

    @Test
    fun `signalQuality WCDMA and HSPA thresholds`() {
        for (type in listOf(NetworkType.WCDMA, NetworkType.HSPA)) {
            assertEquals(SignalQuality.EXCELLENT, signalQuality(-75, type))
            assertEquals(SignalQuality.GOOD, signalQuality(-85, type))
            assertEquals(SignalQuality.FAIR, signalQuality(-95, type))
            assertEquals(SignalQuality.POOR, signalQuality(-105, type))
            assertEquals(SignalQuality.VERY_POOR, signalQuality(-106, type))
        }
    }

    // ── signalQuality: else branch (GSM/CDMA/UNKNOWN) ───────────────────────

    @Test
    fun `signalQuality else branch thresholds`() {
        for (type in listOf(NetworkType.GSM_2G, NetworkType.CDMA, NetworkType.UNKNOWN)) {
            assertEquals(SignalQuality.EXCELLENT, signalQuality(-70, type))
            assertEquals(SignalQuality.GOOD, signalQuality(-80, type))
            assertEquals(SignalQuality.FAIR, signalQuality(-90, type))
            assertEquals(SignalQuality.POOR, signalQuality(-100, type))
            assertEquals(SignalQuality.VERY_POOR, signalQuality(-101, type))
        }
    }

    // ── signalProgress: sentinels ───────────────────────────────────────────

    @Test
    fun `signalProgress sentinels return zero`() {
        for (type in NetworkType.values()) {
            assertEquals(0f, signalProgress(Int.MIN_VALUE, type), DELTA)
            assertEquals(0f, signalProgress(Int.MAX_VALUE, type), DELTA)
            assertEquals(0f, signalProgress(0, type), DELTA)
        }
    }

    // ── signalProgress: NR_5G/LTE range -130..-44 ───────────────────────────

    @Test
    fun `signalProgress NR_5G and LTE range edges`() {
        for (type in listOf(NetworkType.NR_5G, NetworkType.LTE)) {
            assertEquals(0f, signalProgress(-130, type), DELTA)
            assertEquals(1f, signalProgress(-44, type), DELTA)
            assertEquals(0.5f, signalProgress(-87, type), DELTA)
        }
    }

    @Test
    fun `signalProgress NR_5G and LTE coerces out-of-range values`() {
        for (type in listOf(NetworkType.NR_5G, NetworkType.LTE)) {
            assertEquals(0f, signalProgress(-200, type), DELTA)
            assertEquals(1f, signalProgress(100, type), DELTA)
        }
    }

    // ── signalProgress: WCDMA/HSPA range -120..-25 ──────────────────────────

    @Test
    fun `signalProgress WCDMA and HSPA range edges`() {
        for (type in listOf(NetworkType.WCDMA, NetworkType.HSPA)) {
            assertEquals(0f, signalProgress(-120, type), DELTA)
            assertEquals(1f, signalProgress(-25, type), DELTA)
        }
    }

    @Test
    fun `signalProgress WCDMA and HSPA coerces out-of-range values`() {
        for (type in listOf(NetworkType.WCDMA, NetworkType.HSPA)) {
            assertEquals(0f, signalProgress(-200, type), DELTA)
            assertEquals(1f, signalProgress(-10, type), DELTA) // above max (-25)
        }
    }

    // ── signalProgress: else branch range -110..-51 ─────────────────────────

    @Test
    fun `signalProgress else branch range edges`() {
        for (type in listOf(NetworkType.GSM_2G, NetworkType.CDMA, NetworkType.UNKNOWN)) {
            assertEquals(0f, signalProgress(-110, type), DELTA)
            assertEquals(1f, signalProgress(-51, type), DELTA)
        }
    }

    @Test
    fun `signalProgress else branch coerces out-of-range values`() {
        for (type in listOf(NetworkType.GSM_2G, NetworkType.CDMA, NetworkType.UNKNOWN)) {
            assertEquals(0f, signalProgress(-200, type), DELTA)
            assertEquals(1f, signalProgress(10, type), DELTA)
        }
    }
}
