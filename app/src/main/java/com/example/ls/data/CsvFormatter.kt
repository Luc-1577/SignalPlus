package com.example.ls.data

import com.example.ls.data.db.SignalReadingEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object CsvFormatter {

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun toCsv(readings: List<SignalReadingEntity>): String = buildString {
        appendLine("timestamp_utc,dbm,network_type,operator")
        readings.forEach { r ->
            appendLine("${isoFormat.format(Date(r.timestampMs))},${r.dbm},${r.networkType},${escape(r.operator)}")
        }
    }

    private fun escape(field: String): String {
        // Prefix with a single quote if the field could be interpreted as a formula by
        // spreadsheet apps (Excel/Sheets) when the CSV is opened — CSV/formula injection
        // mitigation. The leading quote forces the cell to be treated as text.
        val safe = if (field.isNotEmpty() && field[0] in "=+-@") "'$field" else field
        return if (safe.contains(',') || safe.contains('"') || safe.contains('\n') || safe.contains('\r'))
            "\"${safe.replace("\"", "\"\"")}\""
        else safe
    }
}
