package com.example.ls.data

import com.example.ls.data.db.SignalReadingEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class CsvFormatterTest {

    @Test
    fun `toCsv with no readings returns only the header`() {
        val csv = CsvFormatter.toCsv(emptyList())
        assertEquals("timestamp_utc,dbm,network_type,operator\n", csv)
    }

    @Test
    fun `toCsv formats a reading as one row`() {
        val reading = SignalReadingEntity(timestampMs = 0L, dbm = -85, networkType = "LTE", operator = "Vivo")
        val csv = CsvFormatter.toCsv(listOf(reading))
        assertEquals("timestamp_utc,dbm,network_type,operator\n1970-01-01T00:00:00Z,-85,LTE,Vivo\n", csv)
    }

    @Test
    fun `toCsv formats multiple readings as multiple rows in order`() {
        val readings = listOf(
            SignalReadingEntity(timestampMs = 0L, dbm = -85, networkType = "LTE", operator = "Vivo"),
            SignalReadingEntity(timestampMs = 1_000L, dbm = -70, networkType = "NR_5G", operator = "Claro"),
        )
        val csv = CsvFormatter.toCsv(readings)
        assertEquals(
            "timestamp_utc,dbm,network_type,operator\n" +
                "1970-01-01T00:00:00Z,-85,LTE,Vivo\n" +
                "1970-01-01T00:00:01Z,-70,NR_5G,Claro\n",
            csv,
        )
    }

    @Test
    fun `toCsv escapes operator names containing commas`() {
        val reading = SignalReadingEntity(timestampMs = 0L, dbm = -85, networkType = "LTE", operator = "Vivo, S.A.")
        val csv = CsvFormatter.toCsv(listOf(reading))
        assertEquals("timestamp_utc,dbm,network_type,operator\n1970-01-01T00:00:00Z,-85,LTE,\"Vivo, S.A.\"\n", csv)
    }

    @Test
    fun `toCsv escapes operator names containing quotes`() {
        val reading = SignalReadingEntity(timestampMs = 0L, dbm = -85, networkType = "LTE", operator = "Say \"Hi\"")
        val csv = CsvFormatter.toCsv(listOf(reading))
        assertEquals("timestamp_utc,dbm,network_type,operator\n1970-01-01T00:00:00Z,-85,LTE,\"Say \"\"Hi\"\"\"\n", csv)
    }

    @Test
    fun `toCsv escapes operator names containing a lone carriage return`() {
        val reading = SignalReadingEntity(timestampMs = 0L, dbm = -85, networkType = "LTE", operator = "Vivo\rBR")
        val csv = CsvFormatter.toCsv(listOf(reading))
        assertEquals("timestamp_utc,dbm,network_type,operator\n1970-01-01T00:00:00Z,-85,LTE,\"Vivo\rBR\"\n", csv)
    }

    @Test
    fun `toCsv preserves unicode and emoji in operator names without escaping`() {
        val reading = SignalReadingEntity(timestampMs = 0L, dbm = -85, networkType = "LTE", operator = "Vivo 📶 Brasil")
        val csv = CsvFormatter.toCsv(listOf(reading))
        assertEquals("timestamp_utc,dbm,network_type,operator\n1970-01-01T00:00:00Z,-85,LTE,Vivo 📶 Brasil\n", csv)
    }
}
