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

    @Test
    fun `setPollIntervalMs enforces the minimum floor even when called with an invalid value`() = runBlocking {
        val repo = newRepository()
        repo.setPollIntervalMs(0L)
        assertEquals(SettingsRepository.MIN_POLL_INTERVAL_MS, repo.pollIntervalMs.first())

        repo.setPollIntervalMs(-500L)
        assertEquals(SettingsRepository.MIN_POLL_INTERVAL_MS, repo.pollIntervalMs.first())
    }

    @Test
    fun `default selected subscription id is AUTOMATIC when never set`() = runBlocking {
        val repo = newRepository()
        assertEquals(AUTOMATIC_SUBSCRIPTION_ID, repo.selectedSubscriptionId.first())
    }

    @Test
    fun `setSelectedSubscriptionId persists and is read back`() = runBlocking {
        val repo = newRepository()
        repo.setSelectedSubscriptionId(3)
        assertEquals(3, repo.selectedSubscriptionId.first())
    }

    @Test
    fun `tower triangulation is disabled by default`() = runBlocking {
        val repo = newRepository()
        assertEquals(false, repo.towerTriangulationEnabled.first())
    }

    @Test
    fun `setTowerTriangulationEnabled persists and is read back`() = runBlocking {
        val repo = newRepository()
        repo.setTowerTriangulationEnabled(true)
        assertEquals(true, repo.towerTriangulationEnabled.first())
    }

    @Test
    fun `default history retention is 30 days when never set`() = runBlocking {
        val repo = newRepository()
        assertEquals(SettingsRepository.DEFAULT_HISTORY_RETENTION_DAYS, repo.historyRetentionDays.first())
    }

    @Test
    fun `setHistoryRetentionDays persists and is read back`() = runBlocking {
        val repo = newRepository()
        repo.setHistoryRetentionDays(7)
        assertEquals(7, repo.historyRetentionDays.first())
    }

    @Test
    fun `setHistoryRetentionDays accepts the keep-forever sentinel`() = runBlocking {
        val repo = newRepository()
        repo.setHistoryRetentionDays(SettingsRepository.RETENTION_KEEP_FOREVER)
        assertEquals(SettingsRepository.RETENTION_KEEP_FOREVER, repo.historyRetentionDays.first())
    }

    @Test
    fun `setHistoryRetentionDays coerces a negative value up to keep-forever`() = runBlocking {
        val repo = newRepository()
        repo.setHistoryRetentionDays(-10)
        assertEquals(SettingsRepository.RETENTION_KEEP_FOREVER, repo.historyRetentionDays.first())
    }
}
