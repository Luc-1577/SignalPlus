package com.example.ls.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ls.data.db.AppDatabase
import com.example.ls.data.db.SignalReadingEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// Room in-memory, não o singleton file-backed real (AppDatabase.getDatabase) —
// esse é o MESMO signalmeter.db que um uso manual do app no mesmo device/
// emulador usa, então rodar esta suíte contra ele apagava histórico real
// (clearAll() roda em 3 dos 4 testes) e o singleton reaproveitado entre
// métodos de teste no mesmo processo de instrumentação quebrava o isolamento
// entre eles. Uma instância nova por teste, fechada no @After, resolve os dois
// problemas — mesmo padrão já usado por SignalReadingDaoTest.
@RunWith(AndroidJUnit4::class)
class SignalReadingRepositoryTest {

    private lateinit var db: AppDatabase

    private fun newRepository(): SignalReadingRepository {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        return SignalReadingRepository(db.signalReadingDao())
    }

    @After
    fun teardown() { db.close() }

    @Test
    fun record_thenGetAll_returnsWhatWasRecorded() = runBlocking {
        val repo = newRepository()

        repo.record(SignalReadingEntity(timestampMs = 1, dbm = -80, networkType = "LTE", operator = "Vivo"))
        repo.record(SignalReadingEntity(timestampMs = 2, dbm = -70, networkType = "NR_5G", operator = "Vivo"))

        val all = repo.getAll()
        assertEquals(2, all.size)
    }

    @Test
    fun concurrentRecords_areAllPersisted_mutexDoesNotDropWrites() = runBlocking {
        val repo = newRepository()

        // 50 inserts disparados concorrentemente (como 50 ticks de polling em
        // paralelo tentariam gravar ao mesmo tempo) -- o Mutex do repositório
        // deve serializar todas sem perder nenhuma.
        val jobs = (1..50).map { i ->
            async { repo.record(SignalReadingEntity(timestampMs = i.toLong(), dbm = -i, networkType = "LTE", operator = "Vivo")) }
        }
        jobs.awaitAll()

        assertEquals(50, repo.getAll().size)
    }

    @Test
    fun clearAll_afterAwaitedRecord_leavesTableEmpty() = runBlocking {
        val repo = newRepository()
        repo.record(SignalReadingEntity(timestampMs = 1, dbm = -80, networkType = "LTE", operator = "Vivo"))
        assertTrue(repo.getAll().isNotEmpty())

        repo.clearAll()

        assertTrue(repo.getAll().isEmpty())
    }

    @Test
    fun pruneOlderThan_keepsOnlyReadingsAtOrAfterCutoff() = runBlocking {
        val repo = newRepository()

        repo.record(SignalReadingEntity(timestampMs = 100, dbm = -90, networkType = "LTE", operator = "Vivo"))
        repo.record(SignalReadingEntity(timestampMs = 200, dbm = -85, networkType = "LTE", operator = "Vivo"))
        repo.record(SignalReadingEntity(timestampMs = 300, dbm = -80, networkType = "LTE", operator = "Vivo"))

        repo.pruneOlderThan(cutoffMs = 200)

        val remaining = repo.getAll()
        assertEquals(2, remaining.size)
        assertEquals(listOf(200L, 300L), remaining.map { it.timestampMs })
    }
}
