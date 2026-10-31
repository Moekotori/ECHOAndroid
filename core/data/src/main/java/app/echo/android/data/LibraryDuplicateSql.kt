package app.echo.android.data

/** Snapshots invalidate derived associations immediately when scanned metadata changes. */
internal object LibraryDuplicateSql {
    fun snapshot(alias: String): String = listOf("contentUri", "title", "artist", "album", "albumArtist", "durationMs",
        "clipStartMs", "clipEndMs", "mimeType", "sampleRateHz", "sizeBytes", "dateModifiedSeconds", "albumKey", "fileName", "discNumber", "trackNumber", "year").joinToString(" || ") {
        val value = "COALESCE(CAST($alias.$it AS TEXT),'')"
        "length($value) || ':' || $value"
    }

    fun activeMembers(alias: String = "m"): String = """
        FROM library_duplicate_control cfg
        JOIN library_duplicate_members $alias ON $alias.generation=cfg.generation
        JOIN library_tracks source ON source.id=$alias.trackId
        WHERE cfg.id=0 AND $alias.snapshot=${snapshot("source")}
          AND source.id NOT IN (SELECT trackId FROM library_hidden_tracks)
    """.trimIndent()

    fun visible(alias: String): String = """(
        NOT EXISTS (SELECT 1 FROM library_duplicate_control WHERE id=0 AND enabled=1)
        OR NOT EXISTS (
            SELECT 1 FROM library_duplicate_control cfg
            JOIN library_duplicate_members m ON m.generation=cfg.generation AND m.trackId=$alias.id
            JOIN library_duplicate_members chosen ON chosen.generation=m.generation AND chosen.trackId=m.representativeId
            JOIN library_tracks representative ON representative.id=chosen.trackId
            WHERE cfg.id=0 AND cfg.enabled=1 AND m.trackId!=m.representativeId
              AND m.snapshot=${snapshot(alias)} AND chosen.snapshot=${snapshot("representative")}
              AND representative.id NOT IN (SELECT trackId FROM library_hidden_tracks)
        ))""".trimIndent()
}
