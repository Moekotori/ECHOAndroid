package app.echo.android.data

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [LibraryTrackEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class EchoLibraryDatabase : RoomDatabase() {
    abstract fun trackDao(): LibraryTrackDao

    companion object {
        fun create(context: Context): EchoLibraryDatabase =
            Room.databaseBuilder(context, EchoLibraryDatabase::class.java, "echo-library.db")
                .addMigrations(Migration1To2)
                .build()

        private val Migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE library_tracks ADD COLUMN artworkUri TEXT")
            }
        }
    }
}
