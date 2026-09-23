package com.example.ls.data

import com.example.ls.data.db.SignalReadingEntity
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object CsvFormatter {

    // Formato fixo com milissegundos (não Instant.toString(), que omite o sufixo
    // ".SSS" quando o timestamp cai num segundo exato e inclui quando não cai —
    // linhas com formatos diferentes no mesmo arquivo). Precisão de milissegundo
    // é necessária, não só estética: com o piso de poll em 200ms (ver
    // SettingsRepository.MIN_POLL_INTERVAL_MS), truncar para segundos fazia até 5
    // leituras distintas por segundo compartilharem o mesmo timestamp_utc,
    // tornando-as indistinguíveis numa análise posterior.
    private val TIMESTAMP_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

    fun toCsv(readings: List<SignalReadingEntity>): String = buildString {
        appendLine("timestamp_utc,dbm,network_type,operator")
        readings.forEach { r ->
            // DateTimeFormatter é imutável e thread-safe — diferente do
            // SimpleDateFormat que estava aqui antes, suporta chamada concorrente
            // de duas exportações disparadas em paralelo sem risco de corromper o
            // timestamp de uma exportação com o estado interno da outra.
            val timestamp = TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(r.timestampMs))
            appendLine("$timestamp,${r.dbm},${r.networkType},${escape(r.operator)}")
        }
    }

    /** Sanitiza um nome digitado pelo usuário para renomear um export: remove a
     *  extensão .csv se presente (em qualquer combinação de maiúsculas/minúsculas —
     *  ".Csv" não pode sobreviver e virar "nome.Csv.csv"), mantém só caracteres
     *  seguros (letras/números/espaço/-/_/.), corta em 80 chars e devolve sempre
     *  com sufixo .csv. String vazia após sanitizar = nome inválido. */
    fun sanitizeExportFileName(input: String): String {
        val trimmed = input.trim()
        val withoutExtension = if (trimmed.endsWith(".csv", ignoreCase = true)) trimmed.dropLast(4) else trimmed
        val base = withoutExtension
            .filter { it.isLetterOrDigit() || it in " _-." }
            .take(80)
            .trim()
        return if (base.isEmpty()) "" else "$base.csv"
    }

    private fun escape(field: String): String {
        // Prefix with a single quote if the field could be interpreted as a formula by
        // spreadsheet apps (Excel/Sheets) when the CSV is opened — CSV/formula injection
        // mitigation. The leading quote forces the cell to be treated as text.
        // Checks the first non-whitespace/control character, not field[0] directly:
        // some importers strip a leading tab/CR before evaluating the first visible
        // character, so "\t=cmd()" would otherwise slip past a field[0]-only check.
        val firstSignificant = field.trimStart(' ', '\t', '\r', '\n').firstOrNull()
        val safe = if (firstSignificant != null && firstSignificant in "=+-@") "'$field" else field
        return if (safe.contains(',') || safe.contains('"') || safe.contains('\n') || safe.contains('\r'))
            "\"${safe.replace("\"", "\"\"")}\""
        else safe
    }
}
