package com.example.ls.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SignalReadingDao {

    @Insert
    suspend fun insert(reading: SignalReadingEntity)

    @Query("SELECT * FROM signal_readings ORDER BY timestampMs ASC")
    suspend fun getAll(): List<SignalReadingEntity>

    @Query("DELETE FROM signal_readings")
    suspend fun clearAll()

    /** Poda de retenção — ver [com.example.ls.data.SignalReadingRepository.pruneOlderThan]. */
    @Query("DELETE FROM signal_readings WHERE timestampMs < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long)
}
