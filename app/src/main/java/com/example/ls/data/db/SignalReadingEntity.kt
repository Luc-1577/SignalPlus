package com.example.ls.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Index em timestampMs: sem ele, tanto o ORDER BY de getAll() quanto o
// DELETE WHERE timestampMs < :cutoff da poda de retenção fariam table scan
// completo — justamente o tipo de custo que a retenção existe para evitar
// numa tabela que pode acumular milhões de linhas.
@Entity(tableName = "signal_readings", indices = [Index(value = ["timestampMs"])])
data class SignalReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    val dbm: Int,
    val networkType: String,
    val operator: String,
)
