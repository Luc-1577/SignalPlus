package com.example.ls.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TowerPositionMathTest {

    @Test
    fun `weightForDbm is monotonically increasing as signal gets stronger`() {
        val weak = TowerPositionMath.weightForDbm(-110)
        val mid = TowerPositionMath.weightForDbm(-90)
        val strong = TowerPositionMath.weightForDbm(-70)
        assertTrue(weak < mid)
        assertTrue(mid < strong)
    }

    @Test
    fun `haversineMeters is zero for the same point`() {
        assertEquals(0.0, TowerPositionMath.haversineMeters(-23.5, -46.6, -23.5, -46.6), 0.001)
    }

    @Test
    fun `haversineMeters matches a known approximate distance`() {
        // ~1 degree of latitude is close to 111km.
        val meters = TowerPositionMath.haversineMeters(0.0, 0.0, 1.0, 0.0)
        assertTrue(meters in 110_000.0..112_000.0)
    }

    @Test
    fun `bearingDegrees points due north for a target directly north`() {
        val bearing = TowerPositionMath.bearingDegrees(0.0, 0.0, 1.0, 0.0)
        assertEquals(0.0, bearing, 0.01)
    }

    @Test
    fun `bearingDegrees points due east for a target directly east on the equator`() {
        val bearing = TowerPositionMath.bearingDegrees(0.0, 0.0, 0.0, 1.0)
        assertEquals(90.0, bearing, 0.01)
    }

    @Test
    fun `bearingDegrees points due south for a target directly south`() {
        val bearing = TowerPositionMath.bearingDegrees(0.0, 0.0, -1.0, 0.0)
        assertEquals(180.0, bearing, 0.01)
    }

    @Test
    fun `bearingDegrees points due west for a target directly west on the equator`() {
        val bearing = TowerPositionMath.bearingDegrees(0.0, 0.0, 0.0, -1.0)
        assertEquals(270.0, bearing, 0.01)
    }

    @Test
    fun `bearingDegrees stays within 0 to 360`() {
        val bearing = TowerPositionMath.bearingDegrees(-23.5, -46.6, -23.51, -46.59)
        assertTrue(bearing in 0.0..360.0)
    }

    @Test
    fun `mergeSample creates a fresh aggregate for the first sample`() {
        val agg = TowerPositionMath.mergeSample(null, lat = -23.5, lon = -46.6, dbm = -90)
        assertEquals(1, agg.sampleCount)
        assertEquals(-23.5, agg.farLat1, 0.0)
        assertEquals(-46.6, agg.farLon1, 0.0)
        assertEquals(-23.5, agg.farLat2, 0.0)
        assertEquals(-46.6, agg.farLon2, 0.0)
    }

    @Test
    fun `mergeSample accumulates sample count and tracks the farthest real pair`() {
        val first = TowerPositionMath.mergeSample(null, lat = -23.50, lon = -46.60, dbm = -90)
        val second = TowerPositionMath.mergeSample(first, lat = -23.51, lon = -46.61, dbm = -95)
        assertEquals(2, second.sampleCount)
        val spread = TowerPositionMath.haversineMeters(second.farLat1, second.farLon1, second.farLat2, second.farLon2)
        assertTrue("farthest pair should include both real samples", spread > 0.0)
    }

    @Test
    fun `mergeSample never reports a spread larger than an actual pairwise distance between samples`() {
        // Three points forming a right angle: the bounding-box diagonal would
        // overestimate spread here since no single pair of real samples sits at
        // the two opposite corners of the box.
        var agg: TowerAggregate? = null
        agg = TowerPositionMath.mergeSample(agg, lat = 0.0, lon = 0.0, dbm = -90)
        agg = TowerPositionMath.mergeSample(agg, lat = 0.0, lon = 0.0004, dbm = -90) // ~44.5m east
        agg = TowerPositionMath.mergeSample(agg, lat = 0.0003, lon = 0.0, dbm = -90) // ~33.4m north
        val reportedSpread = TowerPositionMath.haversineMeters(agg.farLat1, agg.farLon1, agg.farLat2, agg.farLon2)
        val truePairwiseMax = listOf(
            TowerPositionMath.haversineMeters(0.0, 0.0, 0.0, 0.0004),
            TowerPositionMath.haversineMeters(0.0, 0.0, 0.0003, 0.0),
            TowerPositionMath.haversineMeters(0.0, 0.0004, 0.0003, 0.0),
        ).max()
        assertEquals(truePairwiseMax, reportedSpread, 0.5)
    }

    @Test
    fun `mergeSample weights stronger samples more in the centroid`() {
        // Two points far apart; the much stronger sample should pull the centroid
        // much closer to its own position than a simple unweighted average would.
        var agg: TowerAggregate? = null
        agg = TowerPositionMath.mergeSample(agg, lat = 0.0, lon = 0.0, dbm = -110) // weak, far weight
        agg = TowerPositionMath.mergeSample(agg, lat = 1.0, lon = 0.0, dbm = -60)  // strong
        val centroidLat = agg.sumWeightedLat / agg.sumWeight
        assertTrue("expected centroid to be pulled toward the strong sample", centroidLat > 0.5)
    }

    @Test
    fun `classify returns Estimating below the minimum sample count`() {
        var agg: TowerAggregate? = null
        repeat(3) { agg = TowerPositionMath.mergeSample(agg, lat = -23.5, lon = -46.6, dbm = -90) }
        val result = TowerPositionMath.classify(agg!!)
        assertTrue(result is TowerPositionEstimate.Estimating)
    }

    @Test
    fun `classify returns Estimating when samples cluster in the same spot`() {
        // Enough samples, but all from (almost) the same GPS point — no real
        // spatial diversity, so the centroid can't be trusted as a real fix.
        var agg: TowerAggregate? = null
        repeat(10) { agg = TowerPositionMath.mergeSample(agg, lat = -23.5, lon = -46.6, dbm = -90) }
        val result = TowerPositionMath.classify(agg!!)
        assertTrue(result is TowerPositionEstimate.Estimating)
    }

    @Test
    fun `classify returns Known once samples are numerous and spatially spread`() {
        var agg: TowerAggregate? = null
        // ~0.001 degree steps are roughly 100m+ apart — well past the 15m floor.
        val offsets = listOf(0.0, 0.001, 0.002, -0.001, -0.002, 0.0015)
        offsets.forEach { d -> agg = TowerPositionMath.mergeSample(agg, lat = -23.5 + d, lon = -46.6, dbm = -90) }
        val result = TowerPositionMath.classify(agg!!)
        assertTrue(result is TowerPositionEstimate.Known)
    }
}
