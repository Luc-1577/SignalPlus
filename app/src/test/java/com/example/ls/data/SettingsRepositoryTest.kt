package com.example.ls.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newRepository(): SettingsRepository {
        val file = tempFolder.newFile("test_settings.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { file })
        return SettingsRepository(dataStore)
    }

    @Test
    fun `default poll interval is 1000ms when never set`() = runBlocking {
        val repo = newRepository()
        assertEquals(1_000L, repo.pollIntervalMs.first())
    }

    @Test
    fun `setPollIntervalMs persists and is read back`() = runBlocking {
        val repo = newRepository()
        repo.setPollIntervalMs(5_000L)
        assertEquals(5_000L, repo.pollIntervalMs.first())
    }
}
