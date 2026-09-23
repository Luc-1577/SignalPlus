package com.example.ls.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// exportSchema = true (não false, como antes): AppDatabaseMigrationTest usa
// MigrationTestHelper.runMigrationsAndValidate(), que SEMPRE carrega o JSON do
// schema da versão de DESTINO em app/schemas/ pra validar o resultado de uma
// migration (colunas/tipos/índices) — não existe fallback pra um validador
// compilado sem esse arquivo. Só precisamos do JSON da versão atual (3): o
// Room gera esse arquivo automaticamente a partir das entidades de hoje a
// cada build; versões antigas (1, 2) não são necessárias porque o "antes" de
// cada teste de migration é montado com SQL cru, não recriado a partir de um
// schema exportado.
@Database(
    entities = [SignalReadingEntity::class, TowerPositionEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun signalReadingDao(): SignalReadingDao
    abstract fun towerPositionDao(): TowerPositionDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        /** Só cria a tabela nova — não toca signal_readings, então o histórico
         *  já salvo sobrevive ao upgrade intacto. Nunca usar
         *  fallbackToDestructiveMigration() aqui: apagaria o histórico de todo
         *  mundo por uma mudança que não tem nada a ver com aquela tabela. */
        // internal, não private: AppDatabaseMigrationTest (androidTest) precisa
        // passar essas mesmas instâncias pro MigrationTestHelper — sem isso, o
        // teste rodaria contra uma cópia reescrita à mão em vez de validar as
        // migrations de verdade que rodam em produção.
        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
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
            }
        }

        /** Só cria o índice — não apaga nem reescreve nenhuma linha existente,
         *  então o histórico já salvo sobrevive ao upgrade intacto. Necessário
         *  pra poda de retenção (DELETE WHERE timestampMs < :cutoff) e pro
         *  ORDER BY de getAll() não virarem table scan numa tabela que pode ter
         *  milhões de linhas. */
        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_signal_readings_timestampMs " +
                        "ON signal_readings(timestampMs)"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "signalmeter.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { INSTANCE = it }
            }
    }
}
