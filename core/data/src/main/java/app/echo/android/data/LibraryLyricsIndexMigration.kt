package app.echo.android.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal object LibraryLyricsIndexMigration : Migration(26, 27) {
    internal const val CreateTable = "CREATE TABLE IF NOT EXISTS `library_lyrics_index` (`trackId` TEXT NOT NULL, `trackFingerprint` TEXT, `contentUri` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `dateModifiedSeconds` INTEGER NOT NULL, `status` INTEGER NOT NULL, `checkedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`trackId`), FOREIGN KEY(`trackId`) REFERENCES `library_tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
    internal const val CreateIndex = "CREATE INDEX IF NOT EXISTS `index_library_lyrics_index_status_trackId` ON `library_lyrics_index` (`status`, `trackId`)"
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(CreateTable)
        db.execSQL(CreateIndex)
    }
}
