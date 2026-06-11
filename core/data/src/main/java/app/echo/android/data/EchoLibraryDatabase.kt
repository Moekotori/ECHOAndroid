package app.echo.android.data

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        LibraryTrackEntity::class,
        LibraryTrackFtsEntity::class,
        LibraryPlaylistEntity::class,
        LibraryPlaylistTrackEntity::class,
        LibraryPlaybackStatsEntity::class,
    ],
    version = 9,
    exportSchema = true,
)
abstract class EchoLibraryDatabase : RoomDatabase() {
    abstract fun trackDao(): LibraryTrackDao
    abstract fun playlistDao(): LibraryPlaylistDao

    companion object {
        fun create(context: Context): EchoLibraryDatabase =
            Room.databaseBuilder(context, EchoLibraryDatabase::class.java, "echo-library.db")
                .addMigrations(
                    Migration1To2,
                    Migration2To3,
                    Migration3To4,
                    Migration4To5,
                    Migration5To6,
                    Migration6To7,
                    Migration7To8,
                    Migration8To9,
                )
                .build()

        internal val Migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE library_tracks ADD COLUMN artworkUri TEXT")
            }
        }

        internal val Migration2To3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE library_tracks ADD COLUMN lastSeenScanRunId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE library_tracks ADD COLUMN fingerprint TEXT")
                db.execSQL("ALTER TABLE library_tracks ADD COLUMN normalizedTitle TEXT")
                db.execSQL("ALTER TABLE library_tracks ADD COLUMN normalizedArtist TEXT")
                db.execSQL("ALTER TABLE library_tracks ADD COLUMN normalizedAlbum TEXT")
                db.execSQL(
                    """
                    UPDATE library_tracks
                    SET normalizedTitle = lower(trim(title)),
                        normalizedArtist = lower(trim(artist)),
                        normalizedAlbum = CASE WHEN album IS NULL THEN NULL ELSE lower(trim(album)) END
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_library_tracks_source_lastSeenScanRunId
                    ON library_tracks(source, lastSeenScanRunId)
                    """.trimIndent(),
                )
            }
        }

        internal val Migration3To4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE VIRTUAL TABLE IF NOT EXISTS library_tracks_fts
                    USING FTS4(
                        trackId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        album TEXT NOT NULL,
                        albumArtist TEXT NOT NULL,
                        normalizedText TEXT NOT NULL,
                        tokenize=unicode61
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO library_tracks_fts(trackId, title, artist, album, albumArtist, normalizedText)
                    SELECT id,
                           title,
                           artist,
                           IFNULL(album, ''),
                           IFNULL(albumArtist, ''),
                           lower(trim(title || ' ' || artist || ' ' || IFNULL(album, '') || ' ' || IFNULL(albumArtist, '')))
                    FROM library_tracks
                    """.trimIndent(),
                )
            }
        }

        internal val Migration4To5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE library_tracks ADD COLUMN normalizedAlbumArtist TEXT")
                db.execSQL(
                    """
                    UPDATE library_tracks
                    SET normalizedAlbumArtist = CASE
                        WHEN albumArtist IS NULL THEN NULL
                        ELSE lower(trim(albumArtist))
                    END
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_library_tracks_normalizedTitle ON library_tracks(normalizedTitle)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_library_tracks_normalizedAlbum ON library_tracks(normalizedAlbum)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_library_tracks_normalizedArtist ON library_tracks(normalizedArtist)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_library_tracks_normalizedAlbumArtist ON library_tracks(normalizedAlbumArtist)")
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_library_tracks_normalizedAlbum_normalizedAlbumArtist
                    ON library_tracks(normalizedAlbum, normalizedAlbumArtist)
                    """.trimIndent(),
                )
            }
        }

        internal val Migration5To6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE library_tracks ADD COLUMN relativePath TEXT")
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_library_tracks_source_relativePath
                    ON library_tracks(source, relativePath)
                    """.trimIndent(),
                )
            }
        }

        internal val Migration6To7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE library_tracks ADD COLUMN sampleRateHz INTEGER")
            }
        }

        internal val Migration7To8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS library_playlists (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        source TEXT NOT NULL,
                        artworkUri TEXT,
                        trackCount INTEGER NOT NULL,
                        updatedAtEpochMs INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_library_playlists_source ON library_playlists(source)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_library_playlists_updatedAtEpochMs ON library_playlists(updatedAtEpochMs)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS library_playlist_tracks (
                        playlistId TEXT NOT NULL,
                        trackId TEXT NOT NULL,
                        position INTEGER NOT NULL,
                        PRIMARY KEY(playlistId, trackId)
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_library_playlist_tracks_trackId ON library_playlist_tracks(trackId)")
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_library_playlist_tracks_playlistId_position
                    ON library_playlist_tracks(playlistId, position)
                    """.trimIndent(),
                )
            }
        }

        internal val Migration8To9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS library_playback_stats (
                        trackId TEXT NOT NULL PRIMARY KEY,
                        playCount INTEGER NOT NULL,
                        lastPlayedAtEpochMs INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_library_playback_stats_playCount
                    ON library_playback_stats(playCount)
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_library_playback_stats_lastPlayedAtEpochMs
                    ON library_playback_stats(lastPlayedAtEpochMs)
                    """.trimIndent(),
                )
            }
        }
    }
}
