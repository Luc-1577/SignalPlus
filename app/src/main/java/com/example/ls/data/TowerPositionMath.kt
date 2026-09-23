package com.example.ls.data

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Estado agregado de uma torre: somas ponderadas acumuladas, nunca as amostras
 *  brutas — é isso que mantém a tabela pequena (uma linha por torre, não uma por leitura).
 *  [farLat1]/[farLon1]/[farLat2]/[farLon2] são o melhor par de amostras REAIS já
 *  observado (heurística incremental "farthest pair") — ver [classify]. */
data class TowerAggregate(
    val sumWeightedLat: Double,
    val sumWeightedLon: Double,
    val sumWeight: Double,
    val sampleCount: Int,
    val farLat1: Double,
    val farLon1: Double,
    val farLat2: Double,
    val farLon2: Double,
)

sealed class TowerPositionEstimate {
    data class Estimating(val sampleCount: Int) : TowerPositionEstimate()
    data class Known(val lat: Double, val lon: Double, val sampleCount: Int) : TowerPositionEstimate()
}

/**
 * Matemática pura da auto-triangulação de torres por centroide ponderado por
 * força de sinal — sem dependência de Android, testável isoladamente. A
 * camada com acesso a Room/Context é [com.example.ls.data.TowerPositionRepository].
 */
object TowerPositionMath {

    // Não-private: TowersScreen usa os dois pra mostrar o progresso real rumo
    // a "conhecida" (amostras E espalhamento), não só a contagem de amostras.
    const val MIN_SAMPLES = 5

    // Ruído comum de GPS parado (~10-15m, independente em lat e em lon) pode
    // produzir ~15-20m de distância entre duas leituras do mesmo lugar sem
    // nenhum deslocamento real. Por isso o piso fica bem acima do ruído
    // típico — 15m deixaria um usuário parado "destravar" uma posição falsa
    // só por causa do jitter do GPS.
    const val MIN_SPREAD_METERS = 40.0
    private const val EARTH_RADIUS_M = 6_371_000.0

    /** dBm é logarítmico; converte pra potência linear antes de ponderar, senão
     *  uma torre 10dB mais forte pesaria só um pouco mais em vez de 10x mais. */
    fun weightForDbm(dbm: Int): Double = 10.0.pow(dbm / 10.0)

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a))
    }

    /** Rumo inicial (bearing) de (lat1,lon1) até (lat2,lon2) em graus, 0-360,
     *  0 = norte, crescendo no sentido horário — usado pela tela de Torres
     *  pra plotar cada torre no escopo de radar relativa à posição atual. */
    fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lon2 - lon1)
        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        val theta = Math.toDegrees(atan2(y, x))
        return (theta + 360.0) % 360.0
    }

    /** Funde uma nova amostra (posição do aparelho + dBm da torre naquele instante)
     *  no agregado existente daquela torre (ou cria um novo, se for a primeira vez). */
    fun mergeSample(existing: TowerAggregate?, lat: Double, lon: Double, dbm: Int): TowerAggregate {
        val weight = weightForDbm(dbm)
        if (existing == null) {
            return TowerAggregate(
                sumWeightedLat = weight * lat,
                sumWeightedLon = weight * lon,
                sumWeight = weight,
                sampleCount = 1,
                farLat1 = lat, farLon1 = lon, farLat2 = lat, farLon2 = lon,
            )
        }

        // Atualiza o par de amostras REAIS mais distante já observado (heurística
        // incremental de "farthest pair"). Ao contrário da diagonal de um bounding
        // box — que mede entre dois cantos hipotéticos que nenhuma amostra
        // necessariamente ocupou —, este par são sempre dois pontos realmente
        // amostrados, então a distância nunca SUPERESTIMA o espalhamento
        // verdadeiro, só pode subestimá-lo: o lado seguro do erro, já que atrasa
        // a promoção pra "posição conhecida" em vez de fabricar uma cedo demais.
        val distToFar1 = haversineMeters(lat, lon, existing.farLat1, existing.farLon1)
        val distToFar2 = haversineMeters(lat, lon, existing.farLat2, existing.farLon2)
        val currentBest = haversineMeters(existing.farLat1, existing.farLon1, existing.farLat2, existing.farLon2)
        var farLat1 = existing.farLat1
        var farLon1 = existing.farLon1
        var farLat2 = existing.farLat2
        var farLon2 = existing.farLon2
        if (distToFar1 >= distToFar2 && distToFar1 > currentBest) {
            farLat2 = lat; farLon2 = lon
        } else if (distToFar2 > currentBest) {
            farLat1 = lat; farLon1 = lon
        }

        return existing.copy(
            sumWeightedLat = existing.sumWeightedLat + weight * lat,
            sumWeightedLon = existing.sumWeightedLon + weight * lon,
            sumWeight = existing.sumWeight + weight,
            sampleCount = existing.sampleCount + 1,
            farLat1 = farLat1, farLon1 = farLon1, farLat2 = farLat2, farLon2 = farLon2,
        )
    }

    /** Só promove a "posição conhecida" quando há amostras suficientes E
     *  espalhamento espacial real entre elas — um usuário parado tomando várias
     *  leituras do mesmo lugar passaria na contagem, mas o centroide seria só
     *  onde ele ficou parado, não a torre. Sem espalhamento mínimo, cai em
     *  [TowerPositionEstimate.Estimating] em vez de inventar uma posição. */
    fun classify(agg: TowerAggregate): TowerPositionEstimate {
        val spreadM = haversineMeters(agg.farLat1, agg.farLon1, agg.farLat2, agg.farLon2)
        return if (agg.sampleCount >= MIN_SAMPLES && spreadM >= MIN_SPREAD_METERS) {
            TowerPositionEstimate.Known(
                lat = agg.sumWeightedLat / agg.sumWeight,
                lon = agg.sumWeightedLon / agg.sumWeight,
                sampleCount = agg.sampleCount,
            )
        } else {
            TowerPositionEstimate.Estimating(agg.sampleCount)
        }
    }
}
