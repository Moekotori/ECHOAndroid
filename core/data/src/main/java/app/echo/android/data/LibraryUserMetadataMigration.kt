package app.echo.android.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal object LibraryUserMetadataMigration : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE library_tracks ADD COLUMN rating INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE library_tracks ADD COLUMN embeddedRating INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE library_tracks ADD COLUMN localRating INTEGER DEFAULT NULL")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_library_tracks_rating ON library_tracks(rating)")
        db.execSQL("ALTER TABLE library_smart_rules ADD COLUMN minimumRating INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE TABLE IF NOT EXISTS file_playlist_entries (id TEXT NOT NULL PRIMARY KEY, playlistId TEXT NOT NULL, position INTEGER NOT NULL, uriKey TEXT NOT NULL, durationMs INTEGER NOT NULL, document TEXT NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_file_playlist_entries_playlistId_position ON file_playlist_entries(playlistId, position)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_file_playlist_entries_uriKey ON file_playlist_entries(uriKey)")
    }
}
