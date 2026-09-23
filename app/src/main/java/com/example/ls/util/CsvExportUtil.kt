package com.example.ls.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.ls.data.CsvFormatter
import com.example.ls.data.db.SignalReadingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files

/** Um CSV já exportado, para a tela de gerenciamento em Configurações. */
data class ExportedFile(val name: String, val sizeBytes: Long, val lastModifiedMs: Long)

object CsvExportUtil {

    private const val EXPORT_DIR_NAME = "exports"

    /** filesDir, não cacheDir — precisa sobreviver a limpezas de cache do sistema
     *  para "gerenciar" (ver/renomear/apagar) fazer sentido. */
    private fun exportDir(context: Context): File =
        File(context.filesDir, EXPORT_DIR_NAME).apply { mkdirs() }

    /** Resolve um nome de arquivo dentro de exports/, rejeitando qualquer tentativa
     *  de escapar a pasta (ex.: "../"). `name` pode vir de input do usuário (rename). */
    private fun safeFileIn(context: Context, name: String): File? {
        val dir = exportDir(context)
        return try {
            val file = File(dir, name)
            if (file.canonicalFile.parentFile == dir.canonicalFile) file else null
        } catch (_: Exception) {
            null
        }
    }

    private fun buildShareIntentForFile(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Escreve o CSV em filesDir/exports e monta um Intent.ACTION_SEND pronto para o chooser.
     *  `suspend` (não `fun` puro) para o próprio tipo forçar Dispatchers.IO aqui
     *  dentro, em vez de depender da disciplina de todo chamador presente e
     *  futuro em lembrar de já estar numa corrotina IO antes de chamar. */
    suspend fun buildShareIntent(context: Context, readings: List<SignalReadingEntity>): Intent =
        withContext(Dispatchers.IO) {
            val csv = CsvFormatter.toCsv(readings)
            val file = uniqueExportFile(context)
            file.writeText(csv)
            buildShareIntentForFile(context, file)
        }

    /** Nome baseado em timestamp, mas com sufixo incremental se colidir — duas
     *  exportações no mesmo milissegundo (duplo toque rápido no botão, ou duas
     *  chamadas concorrentes) senão sobrescreveriam uma a outra silenciosamente,
     *  perdendo o CSV da primeira sem nenhum aviso. `createNewFile()` (não
     *  `exists()` seguido de escrita separada) fecha essa mesma janela: é uma
     *  única chamada atômica que cria o arquivo e devolve `false` se ele já
     *  existia, sem intervalo entre checar e reservar o nome. */
    private fun uniqueExportFile(context: Context): File {
        val dir = exportDir(context)
        val base = "signalplus_${System.currentTimeMillis()}"
        var file = File(dir, "$base.csv")
        var suffix = 1
        while (!file.createNewFile()) {
            file = File(dir, "${base}_$suffix.csv")
            suffix++
        }
        return file
    }

    /** Arquivos já exportados, mais recente primeiro. Lista vazia se a pasta não existir/estiver vazia. */
    suspend fun listExports(context: Context): List<ExportedFile> = withContext(Dispatchers.IO) {
        exportDir(context).listFiles { f -> f.isFile }
            ?.map { ExportedFile(it.name, it.length(), it.lastModified()) }
            ?.sortedByDescending { it.lastModifiedMs }
            ?: emptyList()
    }

    suspend fun deleteExport(context: Context, name: String): Boolean = withContext(Dispatchers.IO) {
        safeFileIn(context, name)?.delete() ?: false
    }

    /** Resultado de [renameExport] — distingue "nome já existe" (o usuário pode
     *  corrigir digitando outro nome) de qualquer outra falha (ex.: o arquivo
     *  de origem sumiu no meio do caminho, apagado por outra ação concorrente).
     *  Antes os dois casos caíam no mesmo `false`, e quem chamava sempre
     *  mostrava "já existe um arquivo com esse nome" mesmo quando a causa real
     *  era outra, escondendo o problema de verdade. */
    enum class RenameResult { SUCCESS, NAME_TAKEN, FAILED }

    /** Renomeia um export já salvo. */
    suspend fun renameExport(context: Context, oldName: String, newDisplayName: String): RenameResult =
        withContext(Dispatchers.IO) {
            val oldFile = safeFileIn(context, oldName) ?: return@withContext RenameResult.FAILED
            val sanitized = CsvFormatter.sanitizeExportFileName(newDisplayName)
            if (sanitized.isEmpty()) return@withContext RenameResult.FAILED
            // Confirmar o diálogo sem editar o nome sanitiza pro mesmo valor de
            // oldName — sem esse check, Files.move abaixo acusaria colisão com o
            // próprio arquivo que está sendo "renomeado".
            if (sanitized == oldName) return@withContext RenameResult.SUCCESS
            val newFile = File(exportDir(context), sanitized)
            // Files.move sem REPLACE_EXISTING é atômico: cria o destino e move
            // numa única operação de sistema, lançando FileAlreadyExistsException
            // se já existir — ao contrário de um `newFile.exists()` checado antes
            // e `renameTo()` chamado depois (duas operações separadas), não há
            // janela onde duas renomeações concorrentes para o mesmo nome possam
            // ambas passar pela checagem antes de qualquer uma mover o arquivo.
            try {
                Files.move(oldFile.toPath(), newFile.toPath())
                RenameResult.SUCCESS
            } catch (_: FileAlreadyExistsException) {
                RenameResult.NAME_TAKEN
            } catch (_: Exception) {
                RenameResult.FAILED
            }
        }

    suspend fun shareIntentFor(context: Context, name: String): Intent? = withContext(Dispatchers.IO) {
        safeFileIn(context, name)?.let { buildShareIntentForFile(context, it) }
    }
}
