package com.example.ls.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "signal_readings")
data class SignalReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    val dbm: Int,
    val networkType: String,
    val operator: String,
)
