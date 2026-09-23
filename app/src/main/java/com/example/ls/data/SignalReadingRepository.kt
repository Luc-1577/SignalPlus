package com.example.ls.data

import android.content.Context
import com.example.ls.data.db.AppDatabase
import com.example.ls.data.db.SignalReadingDao
import com.example.ls.data.db.SignalReadingEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Camada de persistência do histórico de leituras (Room). As escritas
 * (gravação, limpeza total, poda de retenção) passam por um [Mutex]
 * compartilhado para garantir ordem relativa entre elas — sem isso, uma
 * gravação enfileirada podia "reaparecer" no banco logo depois de [clearAll].
 *
 * [getAll] (usado só pela exportação CSV) DELIBERADAMENTE não entra nesse
 * mutex: é uma consulta que pode levar segundos numa tabela grande (a app
 * documenta cenários de milhões de linhas antes da retenção podar), e
 * segurá-la sob o mesmo lock das gravações fazia [record] (chamado até 5x/s
 * no piso de poll de 200ms) enfileirar e rajar durante toda a duração de uma
 * exportação. O SQLite por baixo do Room já garante que cada instrução SELECT
 * individual enxerga um snapshot consistente por si só; o pior caso aqui é um
 * export que inclua ou não uma única linha que estava sendo gravada bem no
 * instante da leitura — uma imprecisão cosmética aceitável para uma feature
 * de exportação, bem diferente do problema que o Mutex das escritas evita
 * (uma linha "ressuscitada" depois de o usuário já ter apagado tudo).
 */
class SignalReadingRepository internal constructor(private val dao: SignalReadingDao) {

    /** Resolve o singleton file-backed real via [AppDatabase.getDatabase] —
     *  o caminho de produção, usado em todo o resto do app. O construtor
     *  primário acima (internal) existe só para teste instrumentado injetar
     *  um [SignalReadingDao] de um Room in-memory em vez desse singleton —
     *  sem isso, testes gravavam/apagavam no MESMO signalmeter.db que um uso
     *  manual do app no mesmo device/emulador usa (risco de apagar histórico
     *  real), e o singleton reaproveitado entre métodos de teste quebrava o
     *  isolamento entre eles. */
    constructor(context: Context) : this(AppDatabase.getDatabase(context).signalReadingDao())

    private val writeMutex = Mutex()

    suspend fun record(reading: SignalReadingEntity) {
        writeMutex.withLock { dao.insert(reading) }
    }

    suspend fun getAll(): List<SignalReadingEntity> = dao.getAll()

    suspend fun clearAll() {
        writeMutex.withLock { dao.clearAll() }
    }

    /** Apaga leituras mais velhas que [cutoffMs] — política de retenção
     *  configurável pelo usuário (ver SettingsRepository.historyRetentionDays),
     *  diferente de [clearAll] (irreversível e total, disparada manualmente).
     *  Passa pelo mesmo Mutex que record()/clearAll() pelo mesmo motivo de
     *  sempre: sem isso, uma poda podia rodar no meio de uma gravação em
     *  andamento, ou uma gravação "reaparecer" depois de uma poda. */
    suspend fun pruneOlderThan(cutoffMs: Long) {
        writeMutex.withLock { dao.deleteOlderThan(cutoffMs) }
    }
}
