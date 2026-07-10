package com.example.ls.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.ls.data.CsvFormatter
import com.example.ls.data.db.SignalReadingEntity
import java.io.File

object CsvExportUtil {

    /** Escreve o CSV em cacheDir e monta um Intent.ACTION_SEND pronto para o chooser. */
    fun buildShareIntent(context: Context, readings: List<SignalReadingEntity>): Intent {
        val csv = CsvFormatter.toCsv(readings)
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, "signalmeter_${System.currentTimeMillis()}.csv")
        file.writeText(csv)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)

        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
