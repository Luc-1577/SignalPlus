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
        assertEquals("timestamp_utc,dbm,network_type,operator\n1970-01-01T00:00:00.000Z,-85,LTE,Vivo\n", csv)
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
                "1970-01-01T00:00:00.000Z,-85,LTE,Vivo\n" +
                "1970-01-01T00:00:01.000Z,-70,NR_5G,Claro\n",
            csv,
        )
    }

    @Test
    fun `toCsv keeps millisecond precision so readings within the same second stay distinguishable`() {
        // Real scenario: SettingsRepository.MIN_POLL_INTERVAL_MS allows a 200ms
        // poll interval, so up to 5 distinct rows can land in the same second —
        // truncating to whole seconds would make them indistinguishable.
        val readings = listOf(
            SignalReadingEntity(timestampMs = 500L, dbm = -85, networkType = "LTE", operator = "Vivo"),
            SignalReadingEntity(timestampMs = 700L, dbm = -84, networkType = "LTE", operator = "Vivo"),
            SignalReadingEntity(timestampMs = 1_000L, dbm = -70, networkType = "LTE", operator = "Vivo"),
        )
        val csv = CsvFormatter.toCsv(readings)
        assertEquals(
            "timestamp_utc,dbm,network_type,operator\n" +
                "1970-01-01T00:00:00.500Z,-85,LTE,Vivo\n" +
                "1970-01-01T00:00:00.700Z,-84,LTE,Vivo\n" +
                "1970-01-01T00:00:01.000Z,-70,LTE,Vivo\n",
            csv,
        )
    }

    @Test
    fun `toCsv escapes operator names containing commas`() {
        val reading = SignalReadingEntity(timestampMs = 0L, dbm = -85, networkType = "LTE", operator = "Vivo, S.A.")
        val csv = CsvFormatter.toCsv(listOf(reading))
        assertEquals("timestamp_utc,dbm,network_type,operator\n1970-01-01T00:00:00.000Z,-85,LTE,\"Vivo, S.A.\"\n", csv)
    }

    @Test
    fun `toCsv escapes operator names containing quotes`() {
        val reading = SignalReadingEntity(timestampMs = 0L, dbm = -85, networkType = "LTE", operator = "Say \"Hi\"")
        val csv = CsvFormatter.toCsv(listOf(reading))
        assertEquals("timestamp_utc,dbm,network_type,operator\n1970-01-01T00:00:00.000Z,-85,LTE,\"Say \"\"Hi\"\"\"\n", csv)
    }

    @Test
    fun `toCsv escapes operator names containing a lone carriage return`() {
        val reading = SignalReadingEntity(timestampMs = 0L, dbm = -85, networkType = "LTE", operator = "Vivo\rBR")
        val csv = CsvFormatter.toCsv(listOf(reading))
        assertEquals("timestamp_utc,dbm,network_type,operator\n1970-01-01T00:00:00.000Z,-85,LTE,\"Vivo\rBR\"\n", csv)
    }

    @Test
    fun `toCsv preserves unicode and emoji in operator names without escaping`() {
        val reading = SignalReadingEntity(timestampMs = 0L, dbm = -85, networkType = "LTE", operator = "Vivo 📶 Brasil")
        val csv = CsvFormatter.toCsv(listOf(reading))
        assertEquals("timestamp_utc,dbm,network_type,operator\n1970-01-01T00:00:00.000Z,-85,LTE,Vivo 📶 Brasil\n", csv)
    }

    @Test
    fun `toCsv neutralizes operator names starting with an equals sign`() {
        val reading = SignalReadingEntity(timestampMs = 0L, dbm = -85, networkType = "LTE", operator = "=SUM(A1:A9)")
        val csv = CsvFormatter.toCsv(listOf(reading))
        assertEquals("timestamp_utc,dbm,network_type,operator\n1970-01-01T00:00:00.000Z,-85,LTE,'=SUM(A1:A9)\n", csv)
    }

    @Test
    fun `toCsv neutralizes operator names starting with a plus sign`() {
        val reading = SignalReadingEntity(timestampMs = 0L, dbm = -85, networkType = "LTE", operator = "+1234")
        val csv = CsvFormatter.toCsv(listOf(reading))
        assertEquals("timestamp_utc,dbm,network_type,operator\n1970-01-01T00:00:00.000Z,-85,LTE,'+1234\n", csv)
    }

    @Test
    fun `toCsv neutralizes operator names starting with a minus sign`() {
        val reading = SignalReadingEntity(timestampMs = 0L, dbm = -85, networkType = "LTE", operator = "-2+3")
        val csv = CsvFormatter.toCsv(listOf(reading))
        assertEquals("timestamp_utc,dbm,network_type,operator\n1970-01-01T00:00:00.000Z,-85,LTE,'-2+3\n", csv)
    }

    @Test
    fun `toCsv neutralizes operator names starting with an at sign`() {
        val reading = SignalReadingEntity(timestampMs = 0L, dbm = -85, networkType = "LTE", operator = "@SUM(A1)")
        val csv = CsvFormatter.toCsv(listOf(reading))
        assertEquals("timestamp_utc,dbm,network_type,operator\n1970-01-01T00:00:00.000Z,-85,LTE,'@SUM(A1)\n", csv)
    }

    @Test
    fun `toCsv neutralizes a formula payload hidden behind a leading tab`() {
        // Some spreadsheet importers strip leading whitespace/control characters
        // before evaluating the first visible character of a cell — a field[0]-only
        // check would miss this and let "\t=SUM(...)" slip through unescaped.
        val reading = SignalReadingEntity(timestampMs = 0L, dbm = -85, networkType = "LTE", operator = "\t=SUM(A1)")
        val csv = CsvFormatter.toCsv(listOf(reading))
        assertEquals("timestamp_utc,dbm,network_type,operator\n1970-01-01T00:00:00.000Z,-85,LTE,'\t=SUM(A1)\n", csv)
    }

    @Test
    fun `toCsv applies formula-injection prefix before comma quoting`() {
        val reading = SignalReadingEntity(timestampMs = 0L, dbm = -85, networkType = "LTE", operator = "=A1,A2")
        val csv = CsvFormatter.toCsv(listOf(reading))
        assertEquals("timestamp_utc,dbm,network_type,operator\n1970-01-01T00:00:00.000Z,-85,LTE,\"'=A1,A2\"\n", csv)
    }

    // ── sanitizeExportFileName ───────────────────────────────────────────────

    @Test
    fun `sanitizeExportFileName keeps a simple name and appends csv`() {
        assertEquals("relatorio.csv", CsvFormatter.sanitizeExportFileName("relatorio"))
    }

    @Test
    fun `sanitizeExportFileName strips an existing csv extension before re-adding it`() {
        assertEquals("relatorio.csv", CsvFormatter.sanitizeExportFileName("relatorio.csv"))
        assertEquals("relatorio.csv", CsvFormatter.sanitizeExportFileName("relatorio.CSV"))
    }

    @Test
    fun `sanitizeExportFileName strips a mixed-case csv extension instead of double-appending it`() {
        assertEquals("relatorio.csv", CsvFormatter.sanitizeExportFileName("relatorio.Csv"))
        assertEquals("relatorio.csv", CsvFormatter.sanitizeExportFileName("relatorio.cSv"))
    }

    @Test
    fun `sanitizeExportFileName strips path traversal characters`() {
        assertEquals("....etcpasswd.csv", CsvFormatter.sanitizeExportFileName("../../etc/passwd"))
    }

    @Test
    fun `sanitizeExportFileName rejects a name that is blank after sanitizing`() {
        assertEquals("", CsvFormatter.sanitizeExportFileName("///"))
        assertEquals("", CsvFormatter.sanitizeExportFileName("   "))
        assertEquals("", CsvFormatter.sanitizeExportFileName(""))
    }

    @Test
    fun `sanitizeExportFileName truncates very long names to 80 chars before the extension`() {
        val result = CsvFormatter.sanitizeExportFileName("a".repeat(200))
        assertEquals(80 + ".csv".length, result.length)
        assertEquals("a".repeat(80) + ".csv", result)
    }

    @Test
    fun `sanitizeExportFileName keeps spaces dashes underscores and dots`() {
        assertEquals("meu relatorio_2026-07-13.csv", CsvFormatter.sanitizeExportFileName("meu relatorio_2026-07-13"))
    }
}
