package com.example.ls.data.db

import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * exportSchema = false (ver AppDatabase.kt) significa que não existem arquivos
 * de schema versionados em disco — e MigrationTestHelper.createDatabase()
 * (verificado na prática, não só na documentação) exige um desses arquivos em
 * assets pra recriar uma versão antiga automaticamente; sem eles, falha com
 * FileNotFoundException. Em vez de ligar exportSchema (o que exigiria também
 * autorar à mão os JSONs de schema das versões 1 e 2, já que o compilador só
 * consegue gerar o da versão ATUAL a partir do código de hoje — as entidades
 * de versões antigas não existem mais pra ele introspectar), cada teste cria a
 * versão "antes" com android.database.sqlite.SQLiteDatabase puro (fora do
 * MigrationTestHelper), reproduzindo exatamente o SQL que o Room gerava pra
 * aquela versão, e só entrega pro helper.runMigrationsAndValidate() de fato
 * rodar e validar a migration. Essa validação (colunas/tipos/índices) usa o
 * validador compilado no _AppDatabase_Impl, que só conhece a versão MAIS
 * RECENTE declarada em @Database — por isso os dois testes miram a versão 3
 * (não uma intermediária), o que ainda cobre o que importa: uma migration
 * quebrada (typo, coluna esquecida, índice faltando) ou um
 * fallbackToDestructiveMigration() introduzido por engano fariam um desses
 * dois testes falhar.
 */
class AppDatabaseMigrationTest {

    private companion object {
        const val TEST_DB = "migration-test"
    }

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    /** Schema exato da v1: só signal_readings, sem o índice (adicionado só na
     *  migration 2->3) — igual ao que SignalReadingEntity gerava antes de
     *  tower_positions existir. */
    private fun createV1Schema(dbName: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(dbName)
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(dbName), null)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `signal_readings` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `timestampMs` INTEGER NOT NULL,
                `dbm` INTEGER NOT NULL,
                `networkType` TEXT NOT NULL,
                `operator` TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            "INSERT INTO signal_readings (timestampMs, dbm, networkType, operator) " +
                "VALUES (1000, -85, 'LTE', 'Vivo')"
        )
        // Room lê a versão do banco via PRAGMA user_version, não de um arquivo
        // separado — é isso que runMigrationsAndValidate() usa pra saber a
        // partir de onde começar a aplicar as migrations passadas a ele.
        db.version = 1
        db.close()
    }

    /** Schema exato da v2: signal_readings (ainda sem índice) + tower_positions
     *  (criada pela migration 1->2), igual ao que existia antes do índice de
     *  retenção ser adicionado na migration 2->3. */
    private fun createV2Schema(dbName: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(dbName)
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(dbName), null)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `signal_readings` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `timestampMs` INTEGER NOT NULL,
                `dbm` INTEGER NOT NULL,
                `networkType` TEXT NOT NULL,
                `operator` TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            "INSERT INTO signal_readings (timestampMs, dbm, networkType, operator) " +
                "VALUES (2000, -70, 'NR_5G', 'Claro')"
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tower_positions (
                towerKey TEXT NOT NULL PRIMARY KEY,
                networkType TEXT NOT NULL,
                cellId INTEGER NOT NULL,
                pci INTEGER,
                tac INTEGER,
                sumWeightedLat REAL NOT NULL,
                sumWeightedLon REAL NOT NULL,
                sumWeight REAL NOT NULL,
                sampleCount INTEGER NOT NULL,
                farLat1 REAL NOT NULL,
                farLon1 REAL NOT NULL,
                farLat2 REAL NOT NULL,
                farLon2 REAL NOT NULL,
                lastUpdatedMs INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.version = 2
        db.close()
    }

    @Test
    fun migrate1To3_addsTowerPositionsAndIndexWithoutLosingExistingReadings() {
        createV1Schema(TEST_DB)

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB, 3, true, AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3,
        )
        helper.closeWhenFinished(migrated)

        migrated.query("SELECT timestampMs, dbm, networkType, operator FROM signal_readings").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals(1000L, cursor.getLong(0))
            assertEquals(-85, cursor.getInt(1))
            assertEquals("LTE", cursor.getString(2))
            assertEquals("Vivo", cursor.getString(3))
        }
    }

    @Test
    fun migrate2To3_addsIndexWithoutLosingExistingReadingsOrTowerPositionsTable() {
        createV2Schema(TEST_DB)

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 3, true, AppDatabase.MIGRATION_2_3)
        helper.closeWhenFinished(migrated)

        migrated.query("SELECT timestampMs, dbm, networkType, operator FROM signal_readings").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals(2000L, cursor.getLong(0))
            assertEquals(-70, cursor.getInt(1))
            assertEquals("NR_5G", cursor.getString(2))
            assertEquals("Claro", cursor.getString(3))
        }

        migrated.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_signal_readings_timestampMs'"
        ).use { indexCursor ->
            assertEquals(1, indexCursor.count)
        }
    }
}
