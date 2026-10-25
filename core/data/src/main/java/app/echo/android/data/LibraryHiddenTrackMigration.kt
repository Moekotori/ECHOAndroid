package app.echo.android.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal object LibraryHiddenTrackMigration : Migration(24, 25) {
    internal const val CreateTable = "CREATE TABLE IF NOT EXISTS `library_hidden_tracks` (`trackId` TEXT NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `album` TEXT, `hiddenAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`trackId`))"
    internal const val CreateIndex = "CREATE INDEX IF NOT EXISTS `index_library_hidden_tracks_hiddenAtEpochMs_trackId` ON `library_hidden_tracks` (`hiddenAtEpochMs`, `trackId`)"
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(CreateTable)
        db.execSQL(CreateIndex)
        db.execSQL("CREATE VIEW `visible_library_tracks` AS " + VisibleLibraryTrackSql)
    }
}
