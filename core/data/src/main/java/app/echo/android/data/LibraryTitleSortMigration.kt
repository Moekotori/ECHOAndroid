package app.echo.android.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Rebuild only the title collation; preserve the CASCADE child rows before dropping the parent. */
internal object LibraryTitleSortMigration : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        statements.forEach { db.execSQL(it) }
    }

    internal val statements = listOf(
        """CREATE TEMP TABLE echo_saved_track_artists AS SELECT * FROM library_track_artists""",
        """DELETE FROM library_track_artists""",
        """CREATE TABLE IF NOT EXISTS `library_tracks_sorted` (`id` TEXT NOT NULL, `contentUri` TEXT NOT NULL, `title` TEXT COLLATE NOCASE NOT NULL, `artist` TEXT NOT NULL, `album` TEXT, `albumArtist` TEXT, `artworkUri` TEXT, `durationMs` INTEGER NOT NULL, `trackNumber` INTEGER, `discNumber` INTEGER, `year` INTEGER, `mimeType` TEXT, `sizeBytes` INTEGER NOT NULL, `sampleRateHz` INTEGER, `dateModifiedSeconds` INTEGER NOT NULL, `source` TEXT NOT NULL, `relativePath` TEXT, `metadataEditedAtEpochMs` INTEGER, `lastSeenScanRunId` INTEGER NOT NULL, `fingerprint` TEXT, `normalizedTitle` TEXT, `normalizedArtist` TEXT, `normalizedAlbum` TEXT, `normalizedAlbumArtist` TEXT, `pinyinTitle` TEXT, `pinyinArtist` TEXT, `pinyinAlbum` TEXT, `albumKey` TEXT NOT NULL, `artistKey` TEXT NOT NULL, `genre` TEXT, `genreKey` TEXT NOT NULL, `composer` TEXT, `composerKey` TEXT NOT NULL, `clipStartMs` INTEGER NOT NULL, `clipEndMs` INTEGER NOT NULL, `fileName` TEXT DEFAULT NULL, PRIMARY KEY(`id`))""",
        """INSERT INTO library_tracks_sorted (`id`, `contentUri`, `title`, `artist`, `album`, `albumArtist`, `artworkUri`, `durationMs`, `trackNumber`, `discNumber`, `year`, `mimeType`, `sizeBytes`, `sampleRateHz`, `dateModifiedSeconds`, `source`, `relativePath`, `metadataEditedAtEpochMs`, `lastSeenScanRunId`, `fingerprint`, `normalizedTitle`, `normalizedArtist`, `normalizedAlbum`, `normalizedAlbumArtist`, `pinyinTitle`, `pinyinArtist`, `pinyinAlbum`, `albumKey`, `artistKey`, `genre`, `genreKey`, `composer`, `composerKey`, `clipStartMs`, `clipEndMs`, `fileName`) SELECT `id`, `contentUri`, `title`, `artist`, `album`, `albumArtist`, `artworkUri`, `durationMs`, `trackNumber`, `discNumber`, `year`, `mimeType`, `sizeBytes`, `sampleRateHz`, `dateModifiedSeconds`, `source`, `relativePath`, `metadataEditedAtEpochMs`, `lastSeenScanRunId`, `fingerprint`, `normalizedTitle`, `normalizedArtist`, `normalizedAlbum`, `normalizedAlbumArtist`, `pinyinTitle`, `pinyinArtist`, `pinyinAlbum`, `albumKey`, `artistKey`, `genre`, `genreKey`, `composer`, `composerKey`, `clipStartMs`, `clipEndMs`, `fileName` FROM library_tracks""",
        """DROP TABLE library_tracks""",
        """ALTER TABLE library_tracks_sorted RENAME TO library_tracks""",
        """CREATE INDEX IF NOT EXISTS `index_library_tracks_title_id` ON `library_tracks` (`title`, `id`)""",
        """CREATE INDEX IF NOT EXISTS `index_library_tracks_album` ON `library_tracks` (`album`)""",
        """CREATE INDEX IF NOT EXISTS `index_library_tracks_artist` ON `library_tracks` (`artist`)""",
        """CREATE UNIQUE INDEX IF NOT EXISTS `index_library_tracks_contentUri` ON `library_tracks` (`contentUri`)""",
        """CREATE INDEX IF NOT EXISTS `index_library_tracks_source_lastSeenScanRunId` ON `library_tracks` (`source`, `lastSeenScanRunId`)""",
        """CREATE INDEX IF NOT EXISTS `index_library_tracks_normalizedTitle` ON `library_tracks` (`normalizedTitle`)""",
        """CREATE INDEX IF NOT EXISTS `index_library_tracks_normalizedAlbum` ON `library_tracks` (`normalizedAlbum`)""",
        """CREATE INDEX IF NOT EXISTS `index_library_tracks_normalizedArtist` ON `library_tracks` (`normalizedArtist`)""",
        """CREATE INDEX IF NOT EXISTS `index_library_tracks_normalizedAlbumArtist` ON `library_tracks` (`normalizedAlbumArtist`)""",
        """CREATE INDEX IF NOT EXISTS `index_library_tracks_normalizedAlbum_normalizedAlbumArtist` ON `library_tracks` (`normalizedAlbum`, `normalizedAlbumArtist`)""",
        """CREATE INDEX IF NOT EXISTS `index_library_tracks_source_relativePath` ON `library_tracks` (`source`, `relativePath`)""",
        """CREATE INDEX IF NOT EXISTS `index_library_tracks_albumKey` ON `library_tracks` (`albumKey`)""",
        """CREATE INDEX IF NOT EXISTS `index_library_tracks_artistKey` ON `library_tracks` (`artistKey`)""",
        """CREATE INDEX IF NOT EXISTS `index_library_tracks_source_albumKey` ON `library_tracks` (`source`, `albumKey`)""",
        """CREATE INDEX IF NOT EXISTS `index_library_tracks_genreKey` ON `library_tracks` (`genreKey`)""",
        """CREATE INDEX IF NOT EXISTS `index_library_tracks_composerKey` ON `library_tracks` (`composerKey`)""",
        """INSERT INTO library_track_artists SELECT * FROM echo_saved_track_artists""",
        """DROP TABLE echo_saved_track_artists""",
    )
}
