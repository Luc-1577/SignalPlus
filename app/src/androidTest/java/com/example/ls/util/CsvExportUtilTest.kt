package com.example.ls.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ls.data.db.SignalReadingEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * CsvExportUtil has real filesDir/File I/O baked in (exportDir/safeFileIn/
 * uniqueExportFile all take a real Context) — no pure-JVM slice of it exists
 * to unit-test, so this is an androidTest exercising the real filesystem of
 * the instrumentation target app, same pattern as SignalReadingRepositoryTest
 * uses Room in-memory for its Android dependency. Each test wipes filesDir/
 * exports (and any stray victim file it planted directly under filesDir) in
 * @Before/@After so tests don't see each other's files and don't leave litter
 * behind for a real device's app data.
 */
@RunWith(AndroidJUnit4::class)
class CsvExportUtilTest {

    private lateinit var context: Context

    private fun exportsDir(): File = File(context.filesDir, "exports")

    private val sampleReadings = listOf(
        SignalReadingEntity(timestampMs = 1_000L, dbm = -85, networkType = "LTE", operator = "Vivo"),
        SignalReadingEntity(timestampMs = 2_000L, dbm = -70, networkType = "NR_5G", operator = "Claro"),
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        exportsDir().deleteRecursively()
    }

    @After
    fun tearDown() {
        exportsDir().deleteRecursively()
        // Any victim file a path-traversal test planted directly under filesDir
        // (i.e. one level above exports/) to prove it survived untouched.
        context.filesDir.listFiles { f -> f.isFile && f.name.startsWith("victim_") }
            ?.forEach { it.delete() }
    }

    // ── export → listExports ─────────────────────────────────────────────────

    @Test
    fun exportThenListExports_findsTheExportedFile() = runBlocking {
        CsvExportUtil.buildShareIntent(context, sampleReadings)

        val files = CsvExportUtil.listExports(context)

        assertEquals(1, files.size)
        assertTrue(files[0].name.startsWith("signalplus_"))
        assertTrue(files[0].name.endsWith(".csv"))
        assertTrue(files[0].sizeBytes > 0)
    }

    @Test
    fun exportedFile_onDisk_containsTheFormattedCsv() = runBlocking {
        CsvExportUtil.buildShareIntent(context, sampleReadings)

        val file = exportsDir().listFiles()!!.single()
        val text = file.readText()

        assertTrue(text.startsWith("timestamp_utc,dbm,network_type,operator\n"))
        assertTrue(text.contains("-85,LTE,Vivo"))
        assertTrue(text.contains("-70,NR_5G,Claro"))
    }

    @Test
    fun twoExportsInARow_bothSurvive_withDistinctNames() = runBlocking {
        CsvExportUtil.buildShareIntent(context, sampleReadings)
        CsvExportUtil.buildShareIntent(context, sampleReadings)

        val files = CsvExportUtil.listExports(context)

        // uniqueExportFile()'s createNewFile()-based collision suffix exists
        // precisely so two exports never silently overwrite one another, even
        // when they land in the same millisecond.
        assertEquals(2, files.size)
        assertEquals(2, files.map { it.name }.distinct().size)
    }

    @Test
    fun listExports_withNoExportsYet_returnsEmptyList() = runBlocking {
        assertTrue(CsvExportUtil.listExports(context).isEmpty())
    }

    // ── renameExport ─────────────────────────────────────────────────────────

    @Test
    fun renameExport_success_renamesFileOnDiskAndInListExports() = runBlocking {
        CsvExportUtil.buildShareIntent(context, sampleReadings)
        val originalName = CsvExportUtil.listExports(context).single().name

        val result = CsvExportUtil.renameExport(context, originalName, "meu relatorio")

        assertEquals(CsvExportUtil.RenameResult.SUCCESS, result)
        val files = CsvExportUtil.listExports(context)
        assertEquals(1, files.size)
        assertEquals("meu relatorio.csv", files[0].name)
        assertFalse(File(exportsDir(), originalName).exists())
    }

    @Test
    fun renameExport_toAnAlreadyExistingName_returnsNAME_TAKEN() = runBlocking {
        CsvExportUtil.buildShareIntent(context, sampleReadings)
        CsvExportUtil.buildShareIntent(context, sampleReadings)
        val (first, second) = CsvExportUtil.listExports(context).map { it.name }

        // Rename `second` to the sanitized name that `first` would collide with.
        val firstDisplayName = first.removeSuffix(".csv")
        val result = CsvExportUtil.renameExport(context, second, firstDisplayName)

        assertEquals(CsvExportUtil.RenameResult.NAME_TAKEN, result)
        // Neither file was touched/lost by the failed rename.
        assertEquals(2, CsvExportUtil.listExports(context).size)
    }

    @Test
    fun renameExport_nonexistentSourceFile_returnsFAILED() = runBlocking {
        val result = CsvExportUtil.renameExport(context, "does_not_exist.csv", "new name")
        assertEquals(CsvExportUtil.RenameResult.FAILED, result)
    }

    @Test
    fun renameExport_blankSanitizedNewName_returnsFAILED() = runBlocking {
        CsvExportUtil.buildShareIntent(context, sampleReadings)
        val originalName = CsvExportUtil.listExports(context).single().name

        val result = CsvExportUtil.renameExport(context, originalName, "///")

        assertEquals(CsvExportUtil.RenameResult.FAILED, result)
        // Original file untouched.
        assertTrue(File(exportsDir(), originalName).exists())
    }

    @Test
    fun renameExport_toItsOwnSanitizedName_returnsSUCCESS_withoutError() = runBlocking {
        CsvExportUtil.buildShareIntent(context, sampleReadings)
        val originalName = CsvExportUtil.listExports(context).single().name
        val displayName = originalName.removeSuffix(".csv")

        val result = CsvExportUtil.renameExport(context, originalName, displayName)

        assertEquals(CsvExportUtil.RenameResult.SUCCESS, result)
        assertTrue(File(exportsDir(), originalName).exists())
    }

    // ── deleteExport ─────────────────────────────────────────────────────────

    @Test
    fun deleteExport_removesFileFromDiskAndFromListExports() = runBlocking {
        CsvExportUtil.buildShareIntent(context, sampleReadings)
        val name = CsvExportUtil.listExports(context).single().name

        val deleted = CsvExportUtil.deleteExport(context, name)

        assertTrue(deleted)
        assertTrue(CsvExportUtil.listExports(context).isEmpty())
        assertFalse(File(exportsDir(), name).exists())
    }

    @Test
    fun deleteExport_nonexistentFile_returnsFalse() = runBlocking {
        assertFalse(CsvExportUtil.deleteExport(context, "ghost.csv"))
    }

    // ── shareIntentFor ───────────────────────────────────────────────────────

    @Test
    fun shareIntentFor_existingFile_returnsAValidSendIntent() = runBlocking {
        CsvExportUtil.buildShareIntent(context, sampleReadings)
        val name = CsvExportUtil.listExports(context).single().name

        val intent = CsvExportUtil.shareIntentFor(context, name)

        assertNotNull(intent)
        assertEquals(android.content.Intent.ACTION_SEND, intent!!.action)
        assertEquals("text/csv", intent.type)
    }

    @Test
    fun shareIntentFor_nonexistentFileInsideExportsDir_stillBuildsAnIntent() = runBlocking {
        // safeFileIn() only validates that the resolved path's parent is
        // exports/ — it does not call File.exists(). A name that stays inside
        // exports/ but names no real file therefore still produces an Intent
        // (FileProvider.getUriForFile builds a content:// Uri from configured
        // path rules without touching the filesystem); the file simply
        // wouldn't be readable if something tried to open that Uri. This is
        // documented real behavior, not a path-traversal gap — see the
        // "path traversal defense" tests below for the actual security
        // property (staying inside exports/), which this name still satisfies.
        val intent = CsvExportUtil.shareIntentFor(context, "ghost.csv")
        assertNotNull(intent)
    }

    // ── Path traversal defense (safeFileIn) ─────────────────────────────────
    // The real risk: a name that resolves outside filesDir/exports/ must never
    // be read, deleted, renamed-into or shared — verified here against real
    // files on disk, not by reading the source and trusting the intent.

    @Test
    fun deleteExport_dotDotTraversalName_doesNotDeleteFileOutsideExportsDir() = runBlocking {
        val victim = File(context.filesDir, "victim_delete.txt").apply { writeText("do not delete me") }
        exportsDir().apply { mkdirs() } // ensure exports/ exists so "../" has somewhere to climb from

        val deleted = CsvExportUtil.deleteExport(context, "../${victim.name}")

        assertFalse(deleted)
        assertTrue("victim file outside exports/ must survive a traversal attempt", victim.exists())
        assertEquals("do not delete me", victim.readText())
    }

    @Test
    fun deleteExport_absolutePathName_doesNotEscapeExportsDir() = runBlocking {
        val victim = File(context.filesDir, "victim_absolute.txt").apply { writeText("still here") }
        exportsDir().apply { mkdirs() }

        val deleted = CsvExportUtil.deleteExport(context, victim.absolutePath)

        assertFalse(deleted)
        assertTrue(victim.exists())
        assertEquals("still here", victim.readText())
    }

    @Test
    fun shareIntentFor_dotDotTraversalName_returnsNullInsteadOfEscapingExportsDir() = runBlocking {
        val victim = File(context.filesDir, "victim_share.txt").apply { writeText("secret") }
        exportsDir().apply { mkdirs() }

        val intent = CsvExportUtil.shareIntentFor(context, "../${victim.name}")

        assertNull(intent)
    }

    @Test
    fun renameExport_dotDotTraversalOldName_returnsFAILED_insteadOfTouchingFileOutsideExportsDir() = runBlocking {
        val victim = File(context.filesDir, "victim_rename.txt").apply { writeText("keep me") }
        exportsDir().apply { mkdirs() }

        val result = CsvExportUtil.renameExport(context, "../${victim.name}", "stolen name")

        assertEquals(CsvExportUtil.RenameResult.FAILED, result)
        assertTrue(victim.exists())
        assertEquals("keep me", victim.readText())
    }

    @Test
    fun renameExport_newDisplayNameWithTraversalCharacters_isSanitizedAwayNotEscaping() = runBlocking {
        // sanitizeExportFileName() strips '/' entirely (only letters/digits/
        // space/-/_/. survive) before renameExport ever builds a target File,
        // so a "../../etc/passwd"-style newDisplayName can't climb out of
        // exports/ — it just becomes a harmless flattened filename.
        CsvExportUtil.buildShareIntent(context, sampleReadings)
        val originalName = CsvExportUtil.listExports(context).single().name

        val result = CsvExportUtil.renameExport(context, originalName, "../../etc/passwd")

        assertEquals(CsvExportUtil.RenameResult.SUCCESS, result)
        val files = CsvExportUtil.listExports(context)
        assertEquals(1, files.size)
        assertEquals("....etcpasswd.csv", files[0].name)
        // Still physically inside exports/, nowhere else.
        assertTrue(File(exportsDir(), "....etcpasswd.csv").exists())
    }
}
