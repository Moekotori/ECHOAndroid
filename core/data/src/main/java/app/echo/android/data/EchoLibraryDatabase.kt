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
        LibraryFavoriteEntity::class,
        LibraryPlaybackStatsEntity::class,
        LibraryAlbumSummaryEntity::class,
        LibraryArtistSummaryEntity::class,
        LibraryFolderSummaryEntity::class,
    ],
    version = 13,
    exportSchema = true,
)
abstract class EchoLibraryDatabase : RoomDatabase() {
    abstract fun trackDao(): LibraryTrackDao
    abstract fun playlistDao(): LibraryPlaylistDao

    companion object {
        @Volatile
        private var instance: EchoLibraryDatabase? = null

        fun create(context: Context): EchoLibraryDatabase {
            instance?.let { return it }
            return synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    EchoLibraryDatabase::class.java,
                    "echo-library.db",
                )
                    .addMigrations(
                        Migration1To2,
                        Migration2To3,
                        Migration3To4,
                        Migration4To5,
                        Migration5To6,
                        Migration6To7,
                        Migration7To8,
                        Migration8To9,
                        Migration9To10,
                        Migration10To11,
                        Migration11To12,
                        Migration12To13,
                    )
                    .build()
                    .also { instance = it }
            }
        }

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

        internal val Migration9To10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE library_tracks ADD COLUMN metadataEditedAtEpochMs INTEGER")
            }
        }

        internal val Migration10To11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE library_tracks ADD COLUMN pinyinTitle TEXT")
                db.execSQL("ALTER TABLE library_tracks ADD COLUMN pinyinArtist TEXT")
                db.execSQL("ALTER TABLE library_tracks ADD COLUMN pinyinAlbum TEXT")
                db.execSQL(
                    """
                    UPDATE library_tracks
                    SET pinyinTitle = lower(trim(title)),
                        pinyinArtist = lower(trim(artist)),
                        pinyinAlbum = CASE WHEN album IS NULL THEN NULL ELSE lower(trim(album)) END
                    """.trimIndent(),
                )
            }
        }

        internal val Migration11To12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE library_tracks ADD COLUMN albumKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE library_tracks ADD COLUMN artistKey TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    """
                    UPDATE library_tracks
                    SET albumKey = (
                            COALESCE(NULLIF(normalizedAlbum, ''), '未知专辑') ||
                            '::' ||
                            COALESCE(NULLIF(normalizedAlbumArtist, ''), NULLIF(normalizedArtist, ''), '未知艺术家')
                        ),
                        artistKey = COALESCE(NULLIF(normalizedArtist, ''), '未知艺术家')
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_library_tracks_albumKey ON library_tracks(albumKey)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_library_tracks_artistKey ON library_tracks(artistKey)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_library_tracks_source_albumKey ON library_tracks(source, albumKey)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS library_album_summaries (
                        albumKey TEXT NOT NULL PRIMARY KEY,
                        isRemote INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        albumArtist TEXT,
                        artist TEXT,
                        artworkUri TEXT,
                        trackCount INTEGER NOT NULL,
                        durationMs INTEGER NOT NULL,
                        year INTEGER,
                        addedAtSeconds INTEGER NOT NULL,
                        pinyinTitle TEXT,
                        pinyinArtist TEXT
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_library_album_summaries_isRemote_title ON library_album_summaries(isRemote, title)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_library_album_summaries_isRemote_addedAtSeconds ON library_album_summaries(isRemote, addedAtSeconds)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS library_artist_summaries (
                        artistKey TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        artworkUri TEXT,
                        albumCount INTEGER NOT NULL,
                        trackCount INTEGER NOT NULL,
                        durationMs INTEGER NOT NULL,
                        pinyinName TEXT
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS library_folder_summaries (
                        folderKey TEXT NOT NULL PRIMARY KEY,
                        path TEXT,
                        artworkUri TEXT,
                        trackCount INTEGER NOT NULL,
                        albumCount INTEGER NOT NULL,
                        artistCount INTEGER NOT NULL,
                        durationMs INTEGER NOT NULL,
                        totalSizeBytes INTEGER NOT NULL,
                        latestModifiedSeconds INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(RebuildAlbumSummariesSql)
                db.execSQL(RebuildArtistSummariesSql)
                db.execSQL(RebuildFolderSummariesSql)
            }
        }

        internal val Migration12To13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS library_favorites (
                        trackId TEXT NOT NULL PRIMARY KEY,
                        favoritedAtEpochMs INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_library_favorites_favoritedAtEpochMs
                    ON library_favorites(favoritedAtEpochMs)
                    """.trimIndent(),
                )
            }
        }

        internal const val RebuildAlbumSummariesSql =
            """
            INSERT INTO library_album_summaries (
                albumKey, isRemote, title, albumArtist, artist, artworkUri,
                trackCount, durationMs, year, addedAtSeconds, pinyinTitle, pinyinArtist
            )
            SELECT
                CASE
                    WHEN source = 'mediastore' OR source = 'saf' THEN albumKey
                    ELSE 'remote||' || source || '||' || albumKey
                END,
                CASE WHEN source = 'mediastore' OR source = 'saf' THEN 0 ELSE 1 END,
                CASE WHEN album IS NULL OR trim(album) = '' THEN '未知专辑' ELSE album END,
                CASE
                    WHEN albumArtist IS NOT NULL AND trim(albumArtist) != '' THEN albumArtist
                    WHEN artist IS NOT NULL AND trim(artist) != '' THEN artist
                    ELSE NULL
                END,
                CASE WHEN artist IS NULL OR trim(artist) = '' THEN NULL ELSE artist END,
                MAX(artworkUri),
                COUNT(*),
                COALESCE(SUM(durationMs), 0),
                MIN(CASE WHEN year IS NOT NULL AND year > 0 THEN year ELSE NULL END),
                MAX(dateModifiedSeconds),
                MAX(pinyinAlbum),
                MAX(pinyinArtist)
            FROM library_tracks
            WHERE albumKey IS NOT NULL AND trim(albumKey) != ''
            GROUP BY
                CASE
                    WHEN source = 'mediastore' OR source = 'saf' THEN albumKey
                    ELSE 'remote||' || source || '||' || albumKey
                END
            """

        internal const val RebuildArtistSummariesSql =
            """
            INSERT INTO library_artist_summaries (
                artistKey, name, artworkUri, albumCount, trackCount, durationMs, pinyinName
            )
            SELECT
                artistKey,
                CASE WHEN artist IS NULL OR trim(artist) = '' THEN '未知艺术家' ELSE artist END,
                MAX(artworkUri),
                COUNT(DISTINCT albumKey),
                COUNT(*),
                COALESCE(SUM(durationMs), 0),
                MAX(pinyinArtist)
            FROM library_tracks
            WHERE (source = 'mediastore' OR source = 'saf')
              AND artistKey IS NOT NULL AND trim(artistKey) != ''
            GROUP BY artistKey
            """

        internal const val RebuildFolderSummariesSql =
            """
            INSERT INTO library_folder_summaries (
                folderKey, path, artworkUri, trackCount, albumCount, artistCount,
                durationMs, totalSizeBytes, latestModifiedSeconds
            )
            SELECT
                COALESCE(NULLIF(relativePath, ''), ''),
                CASE WHEN relativePath IS NULL OR trim(relativePath) = '' THEN NULL ELSE relativePath END,
                MAX(NULLIF(artworkUri, '')),
                COUNT(*),
                COUNT(DISTINCT albumKey),
                COUNT(DISTINCT artistKey),
                COALESCE(SUM(durationMs), 0),
                COALESCE(SUM(sizeBytes), 0),
                MAX(dateModifiedSeconds)
            FROM library_tracks
            WHERE (source = 'mediastore' OR source = 'saf')
            GROUP BY COALESCE(NULLIF(relativePath, ''), '')
            """

        internal const val RebuildAlbumSummariesForKeysSql =
            """
            INSERT INTO library_album_summaries (
                albumKey, isRemote, title, albumArtist, artist, artworkUri,
                trackCount, durationMs, year, addedAtSeconds, pinyinTitle, pinyinArtist
            )
            SELECT
                CASE
                    WHEN source = 'mediastore' OR source = 'saf' THEN albumKey
                    ELSE 'remote||' || source || '||' || albumKey
                END,
                CASE WHEN source = 'mediastore' OR source = 'saf' THEN 0 ELSE 1 END,
                CASE WHEN album IS NULL OR trim(album) = '' THEN '未知专辑' ELSE album END,
                CASE
                    WHEN albumArtist IS NOT NULL AND trim(albumArtist) != '' THEN albumArtist
                    WHEN artist IS NOT NULL AND trim(artist) != '' THEN artist
                    ELSE NULL
                END,
                CASE WHEN artist IS NULL OR trim(artist) = '' THEN NULL ELSE artist END,
                MAX(artworkUri),
                COUNT(*),
                COALESCE(SUM(durationMs), 0),
                MIN(CASE WHEN year IS NOT NULL AND year > 0 THEN year ELSE NULL END),
                MAX(dateModifiedSeconds),
                MAX(pinyinAlbum),
                MAX(pinyinArtist)
            FROM library_tracks
            WHERE albumKey IS NOT NULL AND trim(albumKey) != ''
              AND (
                CASE
                    WHEN source = 'mediastore' OR source = 'saf' THEN albumKey
                    ELSE 'remote||' || source || '||' || albumKey
                END
              ) IN (:keys)
            GROUP BY
                CASE
                    WHEN source = 'mediastore' OR source = 'saf' THEN albumKey
                    ELSE 'remote||' || source || '||' || albumKey
                END
            """

        internal const val RebuildArtistSummariesForKeysSql =
            """
            INSERT INTO library_artist_summaries (
                artistKey, name, artworkUri, albumCount, trackCount, durationMs, pinyinName
            )
            SELECT
                artistKey,
                CASE WHEN artist IS NULL OR trim(artist) = '' THEN '未知艺术家' ELSE artist END,
                MAX(artworkUri),
                COUNT(DISTINCT albumKey),
                COUNT(*),
                COALESCE(SUM(durationMs), 0),
                MAX(pinyinArtist)
            FROM library_tracks
            WHERE (source = 'mediastore' OR source = 'saf')
              AND artistKey IS NOT NULL AND trim(artistKey) != ''
              AND artistKey IN (:keys)
            GROUP BY artistKey
            """

        internal const val RebuildFolderSummariesForKeysSql =
            """
            INSERT INTO library_folder_summaries (
                folderKey, path, artworkUri, trackCount, albumCount, artistCount,
                durationMs, totalSizeBytes, latestModifiedSeconds
            )
            SELECT
                COALESCE(NULLIF(relativePath, ''), ''),
                CASE WHEN relativePath IS NULL OR trim(relativePath) = '' THEN NULL ELSE relativePath END,
                MAX(NULLIF(artworkUri, '')),
                COUNT(*),
                COUNT(DISTINCT albumKey),
                COUNT(DISTINCT artistKey),
                COALESCE(SUM(durationMs), 0),
                COALESCE(SUM(sizeBytes), 0),
                MAX(dateModifiedSeconds)
            FROM library_tracks
            WHERE (source = 'mediastore' OR source = 'saf')
              AND COALESCE(NULLIF(relativePath, ''), '') IN (:keys)
            GROUP BY COALESCE(NULLIF(relativePath, ''), '')
            """
    }
}
