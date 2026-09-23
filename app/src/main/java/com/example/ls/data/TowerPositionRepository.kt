package com.example.ls.data

import android.content.Context
import androidx.compose.runtime.Immutable
import com.example.ls.data.db.AppDatabase
import com.example.ls.data.db.TowerPositionDao
import com.example.ls.data.db.TowerPositionEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Persistência da estimativa de posição por torre (Room). Um [Mutex] próprio
 * (independente do de [SignalReadingRepository] — tabelas diferentes, sem
 * relação) serializa o read-modify-write do centroide: cada ciclo de poll
 * dispara sua própria corrotina "fire-and-forget" para gravar, então
 * gravações concorrentes da mesma torre são estruturalmente possíveis sem
 * essa exclusão.
 *
 * Upsert atômico via SQL (`INSERT ... ON CONFLICT DO UPDATE`) foi descartado
 * de propósito: exige SQLite >= 3.24, e o minSdk 26 deste projeto (Android
 * 8.0/8.1) roda SQLite ~3.18–3.19 — o Mutex funciona em qualquer versão
 * suportada e segue o mesmo padrão já usado em [SignalReadingRepository].
 */
/** Uma torre já mapeada, pronta pra exibição (TowersScreen) — diferente de
 *  [TowerPositionEstimate], sempre carrega lat/lon (o centroide bruto,
 *  independente de já ter espalhamento suficiente pra ser "confiável") junto
 *  com os metadados da célula. Sem isso, o escopo de radar não teria posição
 *  nenhuma pra plotar uma torre ainda em "Estimando" — só saberia exibi-la
 *  depois dela já virar [TowerPositionEstimate.Known]. [isKnown] usa o MESMO
 *  critério de [TowerPositionMath.classify] (não uma checagem própria), então
 *  os dois nunca divergem sobre o que conta como confiável. */
@Immutable
data class TowerDisplayEntry(
    val towerKey: String,
    val networkType: String,
    val cellId: Long,
    val pci: Int?,
    val tac: Int?,
    val lastUpdatedMs: Long,
    val lat: Double,
    val lon: Double,
    val sampleCount: Int,
    val spreadM: Double,
    val isKnown: Boolean,
)

class TowerPositionRepository(context: Context) {

    private val dao: TowerPositionDao = AppDatabase.getDatabase(context).towerPositionDao()
    private val mutex = Mutex()

    /** Epoch da última limpeza, só lido/escrito sob [mutex] — junto com o
     *  parâmetro `epoch` de [foldSample], é o que fecha a corrida entre "Apagar
     *  estimativas" e um fold já em voo: o guard de epoch que existia só na
     *  ViewModel (sobre o StateFlow _towerEstimates) não impedia a gravação em
     *  si no Room, só a UI de mostrar o resultado. Agora o check e a escrita
     *  acontecem dentro da MESMA seção crítica de [foldSample], então não há
     *  janela entre "checar se ainda vale a pena" e "gravar". */
    private var clearedAtEpoch = 0

    /** Funde a amostra e devolve a classificação já atualizada daquela ÚNICA
     *  torre, ou `null` se um "Apagar estimativas" (epoch mais recente que
     *  `epoch`) já rodou depois que este fold foi disparado — nesse caso a
     *  gravação é descartada em vez de ressuscitar uma torre que acabou de ser
     *  apagada. Devolver o resultado aqui (em vez de o chamador reconsultar
     *  tudo via [getAllEstimates]) também evita que cada fold dispare um full
     *  table scan + reclassificação de toda torre já vista, quando só uma
     *  linha mudou. */
    suspend fun foldSample(
        epoch: Int,
        towerKey: String,
        networkType: String,
        cellId: Long,
        pci: Int?,
        tac: Int?,
        lat: Double,
        lon: Double,
        dbm: Int,
    ): TowerPositionEstimate? = mutex.withLock {
        if (epoch < clearedAtEpoch) return@withLock null
        val existing = dao.getByKey(towerKey)
        val merged = TowerPositionMath.mergeSample(existing?.toAggregate(), lat, lon, dbm)
        dao.upsert(
            TowerPositionEntity(
                towerKey = towerKey,
                networkType = networkType,
                cellId = cellId,
                pci = pci,
                tac = tac,
                sumWeightedLat = merged.sumWeightedLat,
                sumWeightedLon = merged.sumWeightedLon,
                sumWeight = merged.sumWeight,
                sampleCount = merged.sampleCount,
                farLat1 = merged.farLat1,
                farLon1 = merged.farLon1,
                farLat2 = merged.farLat2,
                farLon2 = merged.farLon2,
                lastUpdatedMs = System.currentTimeMillis(),
            ),
        )
        TowerPositionMath.classify(merged)
    }

    /** Full table scan + reclassificação de toda torre — não usado no caminho
     *  quente do poll (ver [foldSample]), é o que alimenta TowersScreen. */
    suspend fun getAllEstimatesDetailed(): List<TowerDisplayEntry> = mutex.withLock {
        dao.getAll().map { entity ->
            val agg = entity.toAggregate()
            TowerDisplayEntry(
                towerKey = entity.towerKey,
                networkType = entity.networkType,
                cellId = entity.cellId,
                pci = entity.pci,
                tac = entity.tac,
                lastUpdatedMs = entity.lastUpdatedMs,
                lat = agg.sumWeightedLat / agg.sumWeight,
                lon = agg.sumWeightedLon / agg.sumWeight,
                sampleCount = agg.sampleCount,
                spreadM = TowerPositionMath.haversineMeters(agg.farLat1, agg.farLon1, agg.farLat2, agg.farLon2),
                isKnown = TowerPositionMath.classify(agg) is TowerPositionEstimate.Known,
            )
        }
    }

    /** Simétrico ao "Apagar histórico salvo" de [SignalReadingRepository] — sem
     *  isso não havia nenhuma forma, dentro do app, de apagar as estimativas de
     *  posição já acumuladas. `epoch` vem do mesmo contador que a ViewModel usa
     *  pra descartar folds obsoletos (ver [foldSample]). */
    suspend fun clearAll(epoch: Int) {
        mutex.withLock {
            dao.clearAll()
            clearedAtEpoch = epoch
        }
    }
}

private fun TowerPositionEntity.toAggregate() = TowerAggregate(
    sumWeightedLat = sumWeightedLat,
    sumWeightedLon = sumWeightedLon,
    sumWeight = sumWeight,
    sampleCount = sampleCount,
    farLat1 = farLat1,
    farLon1 = farLon1,
    farLat2 = farLat2,
    farLon2 = farLon2,
)
