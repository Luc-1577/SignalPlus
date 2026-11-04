package com.example.ls.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ls.data.db.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// Room in-memory, mesmo padrão de SignalReadingRepositoryTest: uma instância
// nova por teste (fechada no @After) evita colidir com o signalmeter.db real
// de um uso manual do app no mesmo device/emulador, e evita vazar estado de
// um método de teste pro outro via singleton reaproveitado.
@RunWith(AndroidJUnit4::class)
class TowerPositionRepositoryTest {

    private lateinit var db: AppDatabase

    // Ponto base e um segundo ponto ~55m ao norte (0.0005 grau de latitude ≈
    // 55.7m) — bem acima do piso de TowerPositionMath.MIN_SPREAD_METERS (40m),
    // usado pros testes que precisam empurrar uma torre até "Known".
    private val baseLat = -23.5
    private val baseLon = -46.6
    private val farLat = -23.5005
    private val farLon = -46.6

    private fun newRepository(): TowerPositionRepository {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        return TowerPositionRepository(db.towerPositionDao())
    }

    @After
    fun teardown() { db.close() }

    @Test
    fun foldSample_withCurrentEpoch_recordsAndReturnsMatchingEstimate() = runBlocking {
        val repo = newRepository()

        val result = repo.foldSample(
            epoch = 0,
            towerKey = "tower-1",
            networkType = "LTE",
            cellId = 111L,
            pci = 5,
            tac = 9,
            lat = baseLat,
            lon = baseLon,
            dbm = -80,
        )

        assertTrue(result is TowerPositionEstimate.Estimating)
        assertEquals(1, (result as TowerPositionEstimate.Estimating).sampleCount)

        val all = repo.getAllEstimatesDetailed()
        assertEquals(1, all.size)
        val entry = all[0]
        assertEquals("tower-1", entry.towerKey)
        assertEquals("LTE", entry.networkType)
        assertEquals(111L, entry.cellId)
        assertEquals(5, entry.pci)
        assertEquals(9, entry.tac)
        assertEquals(baseLat, entry.lat, 0.0001)
        assertEquals(baseLon, entry.lon, 0.0001)
        assertEquals(1, entry.sampleCount)
    }

    @Test
    fun foldSample_calledRepeatedlyForSameTower_accumulatesSampleCount() = runBlocking {
        val repo = newRepository()

        repeat(3) { i ->
            val result = repo.foldSample(
                epoch = 0,
                towerKey = "tower-1",
                networkType = "LTE",
                cellId = 111L,
                pci = null,
                tac = null,
                lat = baseLat,
                lon = baseLon,
                dbm = -80,
            )
            assertEquals(i + 1, (result as TowerPositionEstimate.Estimating).sampleCount)
        }

        val all = repo.getAllEstimatesDetailed()
        assertEquals(1, all.size)
        assertEquals(3, all[0].sampleCount)
    }

    @Test
    fun foldSample_withEnoughSamplesAndSpread_classifiesAsKnown() = runBlocking {
        val repo = newRepository()

        var lastResult: TowerPositionEstimate? = null
        repeat(5) { i ->
            lastResult = repo.foldSample(
                epoch = 0,
                towerKey = "tower-1",
                networkType = "LTE",
                cellId = 111L,
                pci = null,
                tac = null,
                // Alterna entre os dois pontos separados por ~55m pra
                // acumular espalhamento real (farthest-real-pair), não só
                // contagem de amostra.
                lat = if (i % 2 == 0) baseLat else farLat,
                lon = if (i % 2 == 0) baseLon else farLon,
                dbm = -80,
            )
        }

        assertTrue(lastResult is TowerPositionEstimate.Known)
        assertEquals(5, (lastResult as TowerPositionEstimate.Known).sampleCount)

        val all = repo.getAllEstimatesDetailed()
        assertEquals(1, all.size)
        assertTrue(all[0].isKnown)
        assertTrue(all[0].sampleCount >= TowerPositionMath.MIN_SAMPLES)
        assertTrue(all[0].spreadM >= TowerPositionMath.MIN_SPREAD_METERS)
    }

    @Test
    fun foldSample_withFewSamplesOrNoSpread_staysEstimating() = runBlocking {
        val repo = newRepository()

        // 5 amostras, mas todas no MESMO ponto -> sem espalhamento real.
        repeat(5) {
            repo.foldSample(
                epoch = 0,
                towerKey = "tower-1",
                networkType = "LTE",
                cellId = 111L,
                pci = null,
                tac = null,
                lat = baseLat,
                lon = baseLon,
                dbm = -80,
            )
        }

        val all = repo.getAllEstimatesDetailed()
        assertEquals(1, all.size)
        assertTrue("torre sem espalhamento real não deveria virar Known", !all[0].isKnown)
    }

    @Test
    fun clearAll_thenFoldSampleWithStaleEpoch_isDiscarded_databaseStaysEmpty() = runBlocking {
        val repo = newRepository()

        // Uma torre já existente antes do clear.
        repo.foldSample(
            epoch = 0,
            towerKey = "tower-1",
            networkType = "LTE",
            cellId = 111L,
            pci = null,
            tac = null,
            lat = baseLat,
            lon = baseLon,
            dbm = -80,
        )
        assertEquals(1, repo.getAllEstimatesDetailed().size)

        // "Apagar estimativas" roda com epoch mais novo (5).
        repo.clearAll(epoch = 5)
        assertTrue(repo.getAllEstimatesDetailed().isEmpty())

        // Um fold que estava "em voo" antes do clear chega DEPOIS, carregando
        // um epoch antigo (3 < 5) -- deve ser descartado em vez de
        // ressuscitar a torre que acabou de ser apagada.
        val staleResult = repo.foldSample(
            epoch = 3,
            towerKey = "tower-1",
            networkType = "LTE",
            cellId = 111L,
            pci = null,
            tac = null,
            lat = baseLat,
            lon = baseLon,
            dbm = -80,
        )

        assertNull("fold com epoch obsoleto deveria ser descartado (null)", staleResult)
        assertTrue(
            "banco deveria continuar vazio depois de um fold obsoleto pós-clear",
            repo.getAllEstimatesDetailed().isEmpty(),
        )
    }

    @Test
    fun foldSample_withCurrentEpochAfterClear_recordsNormallyAgain() = runBlocking {
        val repo = newRepository()

        repo.clearAll(epoch = 5)
        assertTrue(repo.getAllEstimatesDetailed().isEmpty())

        // Um fold novo, disparado DEPOIS do clear, carrega o epoch atual (5) --
        // não deve ser descartado.
        val result = repo.foldSample(
            epoch = 5,
            towerKey = "tower-2",
            networkType = "NR_5G",
            cellId = 222L,
            pci = 1,
            tac = 2,
            lat = baseLat,
            lon = baseLon,
            dbm = -70,
        )

        assertTrue(result is TowerPositionEstimate.Estimating)
        val all = repo.getAllEstimatesDetailed()
        assertEquals(1, all.size)
        assertEquals("tower-2", all[0].towerKey)
    }

    @Test
    fun getAllEstimatesDetailed_onEmptyDatabase_returnsEmptyList() = runBlocking {
        val repo = newRepository()
        assertTrue(repo.getAllEstimatesDetailed().isEmpty())
    }

    @Test
    fun getAllEstimatesDetailed_withMultipleTowers_reflectsEachIndependently() = runBlocking {
        val repo = newRepository()

        repo.foldSample(
            epoch = 0, towerKey = "tower-1", networkType = "LTE", cellId = 111L,
            pci = null, tac = null, lat = baseLat, lon = baseLon, dbm = -80,
        )
        repo.foldSample(
            epoch = 0, towerKey = "tower-2", networkType = "NR_5G", cellId = 222L,
            pci = null, tac = null, lat = farLat, lon = farLon, dbm = -60,
        )

        val all = repo.getAllEstimatesDetailed()
        assertEquals(2, all.size)
        assertEquals(setOf("tower-1", "tower-2"), all.map { it.towerKey }.toSet())
    }
}
