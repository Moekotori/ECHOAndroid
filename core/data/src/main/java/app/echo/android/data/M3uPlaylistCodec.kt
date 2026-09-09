package app.echo.android.data

data class M3uEntry(
    val location: String,
    val title: String? = null,
    val durationSeconds: Int? = null,
)

data class M3uExportTrack(
    val title: String,
    val artist: String,
    val durationMs: Long,
    val location: String,
)

data class M3uMatchRow(
    val id: String,
    val title: String,
    val artist: String,
    val relativePath: String?,
    val contentUri: String,
)

object M3uPlaylistCodec {
    fun parse(text: String): List<M3uEntry> {
        val entries = ArrayList<M3uEntry>()
        var pendingTitle: String? = null
        var pendingDuration: Int? = null
        text.lineSequence().forEach { raw ->
            val line = raw.trim().trimStart('\uFEFF')
            if (line.isEmpty() || line.equals("#EXTM3U", ignoreCase = true) || line.startsWith("#EXTENC")) {
                return@forEach
            }
            if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                val payload = line.substringAfter(':')
                val comma = payload.indexOf(',')
                pendingDuration = payload.substring(0, if (comma >= 0) comma else payload.length)
                    .trim()
                    .toIntOrNull()
                    ?.takeIf { it >= 0 }
                pendingTitle = if (comma >= 0) payload.substring(comma + 1).trim().takeIf { it.isNotEmpty() } else null
                return@forEach
            }
            if (line.startsWith("#")) return@forEach
            entries += M3uEntry(
                location = normalizeM3uLocation(line),
                title = pendingTitle,
                durationSeconds = pendingDuration,
            )
            pendingTitle = null
            pendingDuration = null
        }
        return entries
    }

    fun write(tracks: List<M3uExportTrack>): String = buildString {
        appendLine("#EXTM3U")
        tracks.forEach { track ->
            val seconds = if (track.durationMs > 0L) (track.durationMs / 1000L).toInt() else -1
            val display = listOf(track.artist.trim(), track.title.trim())
                .filter { it.isNotEmpty() }
                .joinToString(" - ")
                .ifBlank { track.title }
            append("#EXTINF:")
            append(seconds)
            append(',')
            appendLine(display)
            appendLine(track.location)
        }
    }

    fun matchTrackId(entry: M3uEntry, rows: List<M3uMatchRow>): String? {
        val location = normalizeM3uLocation(entry.location)
        if (location.isBlank()) return null
        val fileName = location.substringAfterLast('/')
        rows.firstOrNull { row ->
            val relative = normalizeM3uLocation(row.relativePath.orEmpty())
            relative.isNotBlank() && (relative == location || relative.endsWith("/$location") || location.endsWith("/$relative"))
        }?.id?.let { return it }
        if (fileName.isNotBlank()) {
            rows.firstOrNull { row ->
                normalizeM3uLocation(row.relativePath.orEmpty()).substringAfterLast('/') == fileName
            }?.id?.let { return it }
        }
        val inferred = inferredTitleArtist(entry.title)
        if (inferred != null) {
            val (artist, title) = inferred
            rows.firstOrNull { row ->
                row.title.equals(title, ignoreCase = true) &&
                    (artist.isBlank() || row.artist.equals(artist, ignoreCase = true))
            }?.id?.let { return it }
        }
        return null
    }
}

internal fun normalizeM3uLocation(raw: String): String {
    var value = raw.trim().replace('\\', '/')
    if (value.startsWith("file:", ignoreCase = true)) {
        value = value.removePrefix("file://").removePrefix("file:")
    }
    while (value.startsWith("//")) value = value.removePrefix("/")
    if (value.length >= 2 && value[1] == ':' && value[0].isLetter()) {
        value = value.substring(2)
    }
    return value.trimStart('/')
}

private fun inferredTitleArtist(extinfTitle: String?): Pair<String, String>? {
    val raw = extinfTitle?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val separator = raw.indexOf(" - ")
    return if (separator > 0) {
        raw.substring(0, separator).trim() to raw.substring(separator + 3).trim()
    } else {
        "" to raw
    }
}
