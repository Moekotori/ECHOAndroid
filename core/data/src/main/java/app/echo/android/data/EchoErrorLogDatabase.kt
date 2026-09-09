package app.echo.android.data

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [EchoErrorLogEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class EchoErrorLogDatabase : RoomDatabase() {
    abstract fun errorLogDao(): EchoErrorLogDao

    companion object {
        @Volatile
        private var instance: EchoErrorLogDatabase? = null

        fun create(context: Context): EchoErrorLogDatabase {
            instance?.let { return it }
            return synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    EchoErrorLogDatabase::class.java,
                    "echo-error-log.db",
                )
                    .addMigrations(Migration1To2)
                    .build()
                    .also { instance = it }
            }
        }

        internal val Migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE error_records ADD COLUMN threadName TEXT")
                db.execSQL("ALTER TABLE error_records ADD COLUMN appVersion TEXT")
                db.execSQL("ALTER TABLE error_records ADD COLUMN throwableName TEXT")
                db.execSQL(
                    "ALTER TABLE error_records ADD COLUMN firstOccurredAtEpochMs INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    """
                    UPDATE error_records
                    SET firstOccurredAtEpochMs = occurredAtEpochMs
                    WHERE firstOccurredAtEpochMs = 0
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_error_records_source ON error_records(source)",
                )
            }
        }
    }
}
