package com.example.ls.data

import android.content.Context
import com.example.ls.data.db.AppDatabase
import com.example.ls.data.db.SignalReadingEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Camada de persistência do histórico de leituras (Room). Todo acesso passa
 * por um [Mutex] compartilhado para garantir ordem relativa entre gravações,
 * leitura completa e limpeza — sem isso, uma leitura enfileirada podia
 * "reaparecer" no banco logo depois de [clearAll], ou ficar de fora de um
 * [getAll] disparado no meio de uma gravação em andamento.
 */
class SignalReadingRepository(context: Context) {

    private val dao = AppDatabase.getDatabase(context).signalReadingDao()
    private val mutex = Mutex()

    suspend fun record(reading: SignalReadingEntity) {
        mutex.withLock { dao.insert(reading) }
    }

    suspend fun getAll(): List<SignalReadingEntity> = mutex.withLock { dao.getAll() }

    suspend fun clearAll() {
        mutex.withLock { dao.clearAll() }
    }
}
