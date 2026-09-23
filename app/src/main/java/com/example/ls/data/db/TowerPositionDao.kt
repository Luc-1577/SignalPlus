package com.example.ls.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TowerPositionDao {

    @Query("SELECT * FROM tower_positions WHERE towerKey = :towerKey")
    suspend fun getByKey(towerKey: String): TowerPositionEntity?

    /** REPLACE é seguro aqui porque o merge do centroide já acontece no
     *  repositório (sob Mutex) antes de chegar nesta chamada — este upsert
     *  sempre grava uma linha já mesclada, nunca faz aritmética no banco. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TowerPositionEntity)

    @Query("SELECT * FROM tower_positions")
    suspend fun getAll(): List<TowerPositionEntity>

    @Query("DELETE FROM tower_positions")
    suspend fun clearAll()
}
