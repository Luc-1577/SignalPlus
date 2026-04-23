package com.example.ls.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SignalPoint(
    val timestampMs : Long,
    val dbm         : Int,
    val networkType : NetworkType,
)

class SignalHistory(val capacity: Int = 60) {

    private val _points = MutableStateFlow<List<SignalPoint>>(emptyList())
    val points: StateFlow<List<SignalPoint>> = _points.asStateFlow()

    fun add(point: SignalPoint) {
        val current = _points.value
        _points.value = if (current.size >= capacity) {
            current.drop(1) + point
        } else {
            current + point
        }
    }

    fun clear() { _points.value = emptyList() }

    fun range(): Pair<Int, Int> {
        val pts = _points.value
        if (pts.isEmpty()) return -120 to -40
        val min = pts.minOf { it.dbm }.coerceAtMost(-40)
        val max = pts.maxOf { it.dbm }.coerceAtLeast(min + 10)
        return min - 5 to max + 5
    }

    fun average(n: Int = 10): Double? {
        val pts = _points.value.takeLast(n)
        return if (pts.isEmpty()) null else pts.map { it.dbm }.average()
    }

    fun stdDev(n: Int = 10): Double {
        val pts = _points.value.takeLast(n)
        if (pts.size < 2) return 0.0
        val avg = pts.map { it.dbm }.average()
        val variance = pts.sumOf { (it.dbm - avg) * (it.dbm - avg) } / pts.size
        return Math.sqrt(variance)
    }
}