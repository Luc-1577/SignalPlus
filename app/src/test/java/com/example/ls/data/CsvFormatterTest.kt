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

    @Test
    fun `toCsv neutralizes operator names starting with an equals sign`() {
        val reading = SignalReadingEntity(timestampMs = 0L, dbm = -85, networkType = "LTE", operator = "=SUM(A1:A9)")
        val csv = CsvFormatter.toCsv(listOf(reading))
        assertEquals("timestamp_utc,dbm,network_type,operator\n1970-01-01T00:00:00Z,-85,LTE,'=SUM(A1:A9)\n", csv)
    }

    @Test
    fun `toCsv neutralizes operator names starting with a plus sign`() {
        val reading = SignalReadingEntity(timestampMs = 0L, dbm = -85, networkType = "LTE", operator = "+1234")
        val csv = CsvFormatter.toCsv(listOf(reading))
        assertEquals("timestamp_utc,dbm,network_type,operator\n1970-01-01T00:00:00Z,-85,LTE,'+1234\n", csv)
    }

    @Test
    fun `toCsv neutralizes operator names starting with a minus sign`() {
        val reading = SignalReadingEntity(timestampMs = 0L, dbm = -85, networkType = "LTE", operator = "-2+3")
        val csv = CsvFormatter.toCsv(listOf(reading))
        assertEquals("timestamp_utc,dbm,network_type,operator\n1970-01-01T00:00:00Z,-85,LTE,'-2+3\n", csv)
    }

    @Test
    fun `toCsv neutralizes operator names starting with an at sign`() {
        val reading = SignalReadingEntity(timestampMs = 0L, dbm = -85, networkType = "LTE", operator = "@SUM(A1)")
        val csv = CsvFormatter.toCsv(listOf(reading))
        assertEquals("timestamp_utc,dbm,network_type,operator\n1970-01-01T00:00:00Z,-85,LTE,'@SUM(A1)\n", csv)
    }

    @Test
    fun `toCsv applies formula-injection prefix before comma quoting`() {
        val reading = SignalReadingEntity(timestampMs = 0L, dbm = -85, networkType = "LTE", operator = "=A1,A2")
        val csv = CsvFormatter.toCsv(listOf(reading))
        assertEquals("timestamp_utc,dbm,network_type,operator\n1970-01-01T00:00:00Z,-85,LTE,\"'=A1,A2\"\n", csv)
    }
}
