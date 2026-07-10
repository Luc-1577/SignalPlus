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

    private fun escape(field: String): String =
        if (field.contains(',') || field.contains('"') || field.contains('\n') || field.contains('\r'))
            "\"${field.replace("\"", "\"\"")}\""
        else field
}
