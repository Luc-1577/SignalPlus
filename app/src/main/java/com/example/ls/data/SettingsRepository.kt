package com.example.ls.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.dataStore)

    companion object {
        val POLL_INTERVAL_KEY = longPreferencesKey("poll_interval_ms")
        const val DEFAULT_POLL_INTERVAL_MS = 1_000L
    }

    val pollIntervalMs: Flow<Long> = dataStore.data.map { prefs ->
        prefs[POLL_INTERVAL_KEY] ?: DEFAULT_POLL_INTERVAL_MS
    }

    suspend fun setPollIntervalMs(value: Long) {
        dataStore.edit { prefs -> prefs[POLL_INTERVAL_KEY] = value }
    }
}
