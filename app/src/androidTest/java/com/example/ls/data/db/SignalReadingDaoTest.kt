package com.example.ls.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SignalReadingDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: SignalReadingDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.signalReadingDao()
    }

    @After
    fun teardown() { db.close() }

    @Test
    fun insertAndGetAll_returnsInsertedReadingsOrderedByTimestamp() = runBlocking {
        dao.insert(SignalReadingEntity(timestampMs = 200, dbm = -80, networkType = "LTE", operator = "Vivo"))
        dao.insert(SignalReadingEntity(timestampMs = 100, dbm = -70, networkType = "NR_5G", operator = "Vivo"))

        val all = dao.getAll()
        assertEquals(2, all.size)
        assertEquals(100L, all[0].timestampMs)
        assertEquals(200L, all[1].timestampMs)
    }

    @Test
    fun clearAll_removesAllReadings() = runBlocking {
        dao.insert(SignalReadingEntity(timestampMs = 1, dbm = -90, networkType = "GSM_2G", operator = "Claro"))
        dao.clearAll()
        assertTrue(dao.getAll().isEmpty())
    }

    @Test
    fun deleteOlderThan_removesOnlyReadingsBeforeCutoff() = runBlocking {
        dao.insert(SignalReadingEntity(timestampMs = 100, dbm = -90, networkType = "LTE", operator = "Vivo"))
        dao.insert(SignalReadingEntity(timestampMs = 200, dbm = -85, networkType = "LTE", operator = "Vivo"))
        dao.insert(SignalReadingEntity(timestampMs = 300, dbm = -80, networkType = "LTE", operator = "Vivo"))

        dao.deleteOlderThan(cutoffMs = 200)

        val remaining = dao.getAll()
        assertEquals(2, remaining.size)
        assertEquals(listOf(200L, 300L), remaining.map { it.timestampMs })
    }

    @Test
    fun deleteOlderThan_withCutoffAfterEverything_removesAllReadings() = runBlocking {
        dao.insert(SignalReadingEntity(timestampMs = 100, dbm = -90, networkType = "LTE", operator = "Vivo"))
        dao.insert(SignalReadingEntity(timestampMs = 200, dbm = -85, networkType = "LTE", operator = "Vivo"))

        dao.deleteOlderThan(cutoffMs = 1_000)

        assertTrue(dao.getAll().isEmpty())
    }
}
