package app.echo.android.data

import app.echo.android.model.library.LibraryFileFormat
import app.echo.android.model.library.LibraryPlayedFilter
import app.echo.android.model.library.LibraryPresenceFilter
import app.echo.android.model.library.LibraryTrackFilter

/** Values stay bound, including wildcard characters in user-entered paths and tag text. */
internal data class LibraryTrackFilterSql(val clause: String, val args: List<Any>)

internal fun LibraryTrackFilter.toSql(): LibraryTrackFilterSql {
    val conditions = mutableListOf<String>()
    val args = mutableListOf<Any>()
    fun text(column: String, value: String) {
        if (value.isNotBlank()) {
            val normalizedColumn = when (column) {
                "artist" -> "normalizedArtist"
                "album" -> "normalizedAlbum"
                "genre" -> "genreKey"
                "composer" -> "composerKey"
                else -> null
            }
            if (normalizedColumn != null) {
                // SQLite lower() only folds ASCII; these stored keys use Unicode normalization.
                conditions += "instr(COALESCE(NULLIF(library_tracks.$normalizedColumn, ''), lower(COALESCE(library_tracks.$column, ''))), ?) > 0"
                args += value.normalizedForSearch().ifBlank { value.trim().lowercase() }
            } else {
                conditions += "instr(lower(COALESCE(library_tracks.$column, '')), lower(?)) > 0"
                args += value.trim()
            }
        }
    }
    fun range(column: String, min: Number?, max: Number?) {
        if (min != null) { conditions += "library_tracks.$column >= ?"; args += min }
        if (max != null) { conditions += "library_tracks.$column <= ?"; args += max }
    }
    text("artist", artist)
    text("album", album)
    text("genre", genre)
    text("composer", composer)
    text("relativePath", folder)
    range("year", minYear, maxYear)
    range("durationMs", minDurationMs, maxDurationMs)
    range("sizeBytes", minSizeBytes, maxSizeBytes)
    range("sampleRateHz", minSampleRateHz, maxSampleRateHz)
    if (formats.isNotEmpty()) {
        conditions += "($LibraryFileFormatSql IN (${formats.joinToString { "?" }}))"
        args.addAll(formats.sortedBy { it.ordinal }.map { it.name })
    }
    // These predicates have no bound values, so moving them before text/format expressions
    // preserves argument order while allowing rejected rows to skip expensive evaluation.
    if (favoritesOnly) conditions.add(0, "library_tracks.id IN (SELECT trackId FROM library_favorites)")
    if (lyrics != LibraryPresenceFilter.Any) {
        val status = if (lyrics == LibraryPresenceFilter.Present) 1 else 0
        conditions.add(0, """
            library_tracks.id IN (
                SELECT i.trackId FROM library_lyrics_index i JOIN library_tracks indexedTrack ON indexedTrack.id = i.trackId
                WHERE i.status = $status AND i.trackFingerprint IS indexedTrack.fingerprint
                    AND i.contentUri = indexedTrack.contentUri AND i.sizeBytes = indexedTrack.sizeBytes
                    AND i.dateModifiedSeconds = indexedTrack.dateModifiedSeconds
            )
        """.trimIndent())
    }
    when (played) {
        LibraryPlayedFilter.Any -> Unit
        LibraryPlayedFilter.Played -> conditions.add(0, "library_tracks.id IN (SELECT trackId FROM library_playback_stats WHERE playCount > 0)")
        LibraryPlayedFilter.NeverPlayed -> conditions.add(0, "library_tracks.id NOT IN (SELECT trackId FROM library_playback_stats WHERE playCount > 0)")
    }
    when (artwork) {
        LibraryPresenceFilter.Any -> Unit
        LibraryPresenceFilter.Present -> conditions.add(0, "trim(COALESCE(library_tracks.artworkUri, '')) <> ''")
        LibraryPresenceFilter.Missing -> conditions.add(0, "trim(COALESCE(library_tracks.artworkUri, '')) = ''")
    }
    if (duplicatesOnly) {
        // Metadata candidates, not an audio fingerprint match. Build groups once, not once per row.
        conditions += """
            (lower(library_tracks.title), lower(library_tracks.artist), library_tracks.durationMs) IN (
                SELECT lower(title), lower(artist), durationMs FROM library_tracks
                WHERE ${LibraryScanPolicy.LocalSourceSql}
                AND id NOT IN (SELECT trackId FROM library_hidden_tracks)
                AND trim(title) <> ''
                GROUP BY lower(title), lower(artist), durationMs HAVING count(*) > 1
            )
        """.trimIndent()
    }
    return LibraryTrackFilterSql(conditions.joinToString(separator = " AND ", prefix = if (conditions.isEmpty()) "" else " AND "), args)
}

/** Prefer a known filename extension; MIME is a fallback for MediaStore rows without a name. */
internal val LibraryFileFormatSql: String = run {
    val extensions = linkedMapOf(
        LibraryFileFormat.Flac to listOf("flac"), LibraryFileFormat.Mp3 to listOf("mp3", "mp2"),
        LibraryFileFormat.M4a to listOf("m4a", "mp4", "m4b"), LibraryFileFormat.Aac to listOf("aac"),
        LibraryFileFormat.Wav to listOf("wav", "wave"), LibraryFileFormat.Aiff to listOf("aif", "aiff", "aifc"),
        LibraryFileFormat.Ogg to listOf("ogg", "oga"), LibraryFileFormat.Opus to listOf("opus"),
        LibraryFileFormat.Dsd to listOf("dsf", "dff"), LibraryFileFormat.Ape to listOf("ape"),
        LibraryFileFormat.WavPack to listOf("wv"),
    )
    val mimes = mapOf(
        LibraryFileFormat.Flac to listOf("audio/flac", "audio/x-flac"),
        LibraryFileFormat.Mp3 to listOf("audio/mpeg", "audio/mp3"),
        LibraryFileFormat.M4a to listOf("audio/mp4", "audio/x-m4a", "audio/alac"),
        LibraryFileFormat.Aac to listOf("audio/aac", "audio/aac-adts", "audio/mp4a-latm"),
        LibraryFileFormat.Wav to listOf("audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave"),
        LibraryFileFormat.Aiff to listOf("audio/aiff", "audio/x-aiff"),
        LibraryFileFormat.Ogg to listOf("audio/ogg", "application/ogg"),
        LibraryFileFormat.Opus to listOf("audio/opus", "audio/x-opus"),
        LibraryFileFormat.Dsd to listOf("audio/dsd", "audio/x-dsd", "audio/dsf", "audio/x-dsf", "audio/dff", "audio/x-dff"),
        LibraryFileFormat.Ape to listOf("audio/ape", "audio/x-ape", "audio/monkeys-audio"),
        LibraryFileFormat.WavPack to listOf("audio/wavpack", "audio/x-wavpack"),
    )
    buildString {
        append("CASE ")
        extensions.forEach { (format, values) ->
            append("WHEN ")
            append(values.joinToString(" OR ") { "lower(COALESCE(NULLIF(trim(library_tracks.fileName), ''), library_tracks.contentUri)) LIKE '%.$it'" })
            append(" THEN '${format.name}' ")
        }
        mimes.forEach { (format, values) ->
            append("WHEN lower(library_tracks.mimeType) IN (${values.joinToString { "'$it'" }}) THEN '${format.name}' ")
        }
        append("ELSE 'Other' END")
    }
}
