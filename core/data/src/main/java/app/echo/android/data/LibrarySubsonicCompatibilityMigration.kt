package app.echo.android.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Keep existing rows and allow each server playlist occurrence to survive a refresh. */
internal object LibrarySubsonicCompatibilityMigration : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE library_tracks ADD COLUMN remoteAlbumId TEXT DEFAULT NULL")
        db.execSQL("CREATE TABLE library_playlist_tracks_v29 (playlistId TEXT NOT NULL, trackId TEXT NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(playlistId, trackId, position))")
        db.execSQL("INSERT INTO library_playlist_tracks_v29 SELECT playlistId, trackId, position FROM library_playlist_tracks")
        db.execSQL("DROP TABLE library_playlist_tracks")
        db.execSQL("ALTER TABLE library_playlist_tracks_v29 RENAME TO library_playlist_tracks")
        db.execSQL("CREATE INDEX index_library_playlist_tracks_trackId ON library_playlist_tracks(trackId)")
        db.execSQL("CREATE INDEX index_library_playlist_tracks_playlistId_position ON library_playlist_tracks(playlistId, position)")
        // Account identity belongs in the stable resource key, never in persisted auth tokens.
        db.execSQL("UPDATE library_tracks SET contentUri = contentUri || '&echoSource=' || source WHERE source LIKE 'subsonic:%' AND contentUri LIKE '%/rest/%?%' AND contentUri NOT LIKE '%echoSource=%'")
        db.execSQL("UPDATE library_tracks SET artworkUri = artworkUri || '&echoSource=' || source WHERE source LIKE 'subsonic:%' AND artworkUri LIKE '%/rest/%?%' AND artworkUri NOT LIKE '%echoSource=%'")
        db.execSQL("UPDATE library_playlists SET artworkUri = artworkUri || '&echoSource=' || source WHERE source LIKE 'subsonic:%' AND artworkUri LIKE '%/rest/%?%' AND artworkUri NOT LIKE '%echoSource=%'")
    }
}
