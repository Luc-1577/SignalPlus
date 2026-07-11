package com.example.ls.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ls.data.db.SignalReadingEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SignalReadingRepositoryTest {

    private fun newRepository() = SignalReadingRepository(ApplicationProvider.getApplicationContext())

    @Test
    fun record_thenGetAll_returnsWhatWasRecorded() = runBlocking {
        val repo = newRepository()
        repo.clearAll()

        repo.record(SignalReadingEntity(timestampMs = 1, dbm = -80, networkType = "LTE", operator = "Vivo"))
        repo.record(SignalReadingEntity(timestampMs = 2, dbm = -70, networkType = "NR_5G", operator = "Vivo"))

        val all = repo.getAll()
        assertEquals(2, all.size)
    }

    @Test
    fun concurrentRecords_areAllPersisted_mutexDoesNotDropWrites() = runBlocking {
        val repo = newRepository()
        repo.clearAll()

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
}
