package com.example.ls.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TowerPositionDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: TowerPositionDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.towerPositionDao()
    }

    @After
    fun teardown() { db.close() }

    private fun entity(
        towerKey: String = "tower-1",
        sumWeightedLat: Double = -23.5,
        sumWeightedLon: Double = -46.6,
        sumWeight: Double = 1.0,
        sampleCount: Int = 1,
        lastUpdatedMs: Long = 1_000L,
    ) = TowerPositionEntity(
        towerKey = towerKey,
        networkType = "LTE",
        cellId = 12345L,
        pci = 10,
        tac = 20,
        sumWeightedLat = sumWeightedLat,
        sumWeightedLon = sumWeightedLon,
        sumWeight = sumWeight,
        sampleCount = sampleCount,
        farLat1 = -23.5,
        farLon1 = -46.6,
        farLat2 = -23.5,
        farLon2 = -46.6,
        lastUpdatedMs = lastUpdatedMs,
    )

    @Test
    fun getByKey_withNoMatchingRow_returnsNull() = runBlocking {
        assertNull(dao.getByKey("missing"))
    }

    @Test
    fun upsert_thenGetByKey_returnsInsertedRow() = runBlocking {
        dao.upsert(entity(towerKey = "tower-1"))

        val fetched = dao.getByKey("tower-1")
        assertEquals("tower-1", fetched?.towerKey)
        assertEquals(1, fetched?.sampleCount)
    }

    @Test
    fun upsert_withSameKey_replacesExistingRow() = runBlocking {
        dao.upsert(entity(towerKey = "tower-1", sampleCount = 1))
        dao.upsert(entity(towerKey = "tower-1", sampleCount = 7, sumWeight = 3.0))

        val all = dao.getAll()
        assertEquals(1, all.size)
        assertEquals(7, all[0].sampleCount)
        assertEquals(3.0, all[0].sumWeight, 0.0001)
    }

    @Test
    fun getAll_returnsEveryDistinctTower() = runBlocking {
        dao.upsert(entity(towerKey = "tower-1"))
        dao.upsert(entity(towerKey = "tower-2"))

        val all = dao.getAll()
        assertEquals(2, all.size)
        assertEquals(setOf("tower-1", "tower-2"), all.map { it.towerKey }.toSet())
    }

    @Test
    fun clearAll_removesEveryRow() = runBlocking {
        dao.upsert(entity(towerKey = "tower-1"))
        dao.upsert(entity(towerKey = "tower-2"))

        dao.clearAll()

        assertTrue(dao.getAll().isEmpty())
        assertNull(dao.getByKey("tower-1"))
    }
}
