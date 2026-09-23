package com.example.ls.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Uma linha por torre (não por leitura): soma corrida ponderada por sinal —
 *  ver [com.example.ls.data.TowerPositionMath] para o cálculo do centroide. */
@Entity(tableName = "tower_positions")
data class TowerPositionEntity(
    @PrimaryKey val towerKey: String,
    val networkType: String,
    val cellId: Long,
    val pci: Int?,
    val tac: Int?,
    val sumWeightedLat: Double,
    val sumWeightedLon: Double,
    val sumWeight: Double,
    val sampleCount: Int,
    val farLat1: Double,
    val farLon1: Double,
    val farLat2: Double,
    val farLon2: Double,
    val lastUpdatedMs: Long,
)
