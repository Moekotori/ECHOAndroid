package app.echo.android.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Rebuild search rows once so indexed contains queries never see a partially populated index. */
internal object LibrarySearchSpeedMigration : Migration(23, 24) {
    internal val sortIndexes = listOf(
        "CREATE INDEX IF NOT EXISTS index_library_tracks_durationMs_title_id ON library_tracks(durationMs DESC, title ASC, id ASC)",
        "CREATE INDEX IF NOT EXISTS index_library_tracks_dateModifiedSeconds_title_id ON library_tracks(dateModifiedSeconds DESC, title ASC, id ASC)",
    )

    override fun migrate(db: SupportSQLiteDatabase) {
        sortIndexes.forEach { db.execSQL(it) }
        db.execSQL("DELETE FROM library_tracks_fts")
        db.compileStatement(
            "INSERT INTO library_tracks_fts(trackId, title, artist, album, albumArtist, normalizedText) VALUES (?, ?, ?, ?, ?, ?)",
        ).use { insert ->
            db.query(
                "SELECT id, title, artist, album, albumArtist, normalizedTitle, normalizedArtist, normalizedAlbum, " +
                    "normalizedAlbumArtist, pinyinTitle, pinyinArtist, pinyinAlbum FROM library_tracks",
            ).use { rows ->
                // The cursor window and one song's token set bound memory independently of library size.
                while (rows.moveToNext()) {
                    insert.clearBindings()
                    insert.bindString(1, rows.getString(0))
                    val parts = Array(11) { i -> if (rows.isNull(i + 1)) "" else rows.getString(i + 1) }
                    repeat(4) { i -> insert.bindString(i + 2, parts[i]) }
                    insert.bindString(6, buildFtsNormalizedText(*parts))
                    insert.executeInsert()
                }
            }
        }
    }
}
