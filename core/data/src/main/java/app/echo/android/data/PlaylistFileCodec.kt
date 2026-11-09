package app.echo.android.data

import app.echo.android.model.library.PlaylistImportLimits
import app.echo.android.model.library.PlaylistExportFormat
import org.json.JSONArray
import org.json.JSONObject

object PlaylistFileCodec {
    fun parse(text: String, fileName: String? = null): List<M3uEntry> {
        val payload = text.trimStart('\uFEFF', ' ', '\r', '\n', '\t')
        val extension = fileName?.substringAfterLast('.', "")?.lowercase()
        val first = payload.lineSequence().firstOrNull()?.trim().orEmpty()
        return when {
            first.equals("[playlist]", ignoreCase = true) -> parsePls(payload)
            first.equals("#EXTM3U", ignoreCase = true) -> M3uPlaylistCodec.parse(payload, PlaylistImportLimits.MaxEntries)
            payload.startsWith('<') -> XmlPlaylistCodec.parse(payload)
            payload.startsWith('{') -> parseJson(payload)
            extension == "csv" -> parsePlaylistCsv(payload)
            extension == "txt" -> parsePlaylistTxt(payload)
            extension == null || extension in setOf("", "m3u", "m3u8") ->
                M3uPlaylistCodec.parse(payload, PlaylistImportLimits.MaxEntries)
            else -> throw IllegalArgumentException("Unsupported playlist format")
        }
    }

    internal fun write(name: String, tracks: List<M3uExportTrack>, format: PlaylistExportFormat): String =
        when (format) {
            PlaylistExportFormat.M3u, PlaylistExportFormat.LegacyM3u -> M3uPlaylistCodec.write(tracks)
            PlaylistExportFormat.Pls -> writePls(tracks)
            PlaylistExportFormat.Xspf, PlaylistExportFormat.Wpl, PlaylistExportFormat.Asx ->
                XmlPlaylistCodec.write(name, tracks, format)
            PlaylistExportFormat.Json -> writeJson(name, tracks)
            PlaylistExportFormat.Csv, PlaylistExportFormat.Text -> error("Use text export for $format")
        }

    internal val DiscoverableExtensions = setOf("m3u", "m3u8", "pls", "xspf", "wpl", "asx")

    private fun parsePls(text: String): List<M3uEntry> {
        data class Entry(var location: String? = null, var title: String? = null, var duration: Int? = null)
        val entries = sortedMapOf<Int, Entry>()
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            val separator = line.indexOf('=')
            if (separator < 0) return@forEach
            val key = line.substring(0, separator).trim()
            val field = listOf("File", "Title", "Length").firstOrNull { key.startsWith(it, true) }
                ?: return@forEach
            val index = key.substring(field.length).toIntOrNull()?.takeIf { it > 0 } ?: return@forEach
            if (index !in entries) checkPlaylistEntryCount(entries.size + 1)
            val entry = entries.getOrPut(index) { Entry() }
            val value = line.substring(separator + 1).trim()
            when (field) {
                "File" -> entry.location = value.takeIf(String::isNotBlank)
                "Title" -> entry.title = value.takeIf(String::isNotBlank)
                "Length" -> entry.duration = value.toIntOrNull()?.takeIf { it >= 0 }
            }
        }
        return entries.values.mapNotNull { entry ->
            entry.location?.let { M3uEntry(normalizeM3uLocation(it), entry.title, entry.duration) }
        }
    }

    private fun writePls(tracks: List<M3uExportTrack>): String = buildString {
        appendLine("[playlist]")
        tracks.forEachIndexed { index, track ->
            val number = index + 1
            appendLine("File$number=${track.location}")
            val title = listOf(track.artist, track.title).filter(String::isNotBlank).joinToString(" - ")
            appendLine("Title$number=${title.playlistSingleLine()}")
            val seconds = if (track.durationMs > 0) track.durationMs / 1000 else -1
            appendLine("Length$number=$seconds")
        }
        appendLine("NumberOfEntries=${tracks.size}")
        appendLine("Version=2")
    }

    private fun parseJson(text: String): List<M3uEntry> {
        checkPlaylistJsonDepth(text)
        val root = JSONObject(text)
        require(root.optString("format") == "echo-playlist" && root.optInt("version") == 1) {
            "Unsupported JSON playlist"
        }
        val tracks = root.getJSONArray("tracks")
        checkPlaylistEntryCount(tracks.length())
        return List(tracks.length()) { index ->
            val track = tracks.getJSONObject(index)
            requireNotNull(structuredPlaylistEntry(
                track.opt("location") as? String, track.opt("title") as? String, track.opt("artist") as? String,
                track.optLong("durationMs", -1).playlistDurationSeconds(),
            )) { "Playlist entry needs a location or title" }
        }
    }

    private fun writeJson(name: String, tracks: List<M3uExportTrack>): String = JSONObject()
        .put("format", "echo-playlist").put("version", 1).put("name", name)
        .put("tracks", JSONArray().apply {
            tracks.forEach { track ->
                put(JSONObject().put("location", track.location).put("title", track.title)
                    .put("artist", track.artist).put("album", track.album).put("durationMs", track.durationMs))
            }
        }).toString()

    // Android's JSON parser is recursive. Cap structural depth before handing it external files.
    private fun checkPlaylistJsonDepth(text: String) {
        var depth = 0
        var inString = false
        var escaped = false
        var previous = '{'
        for (ch in text) {
            if (inString) {
                if (escaped) escaped = false
                else if (ch == '\\') escaped = true
                else if (ch == '"') {
                    inString = false
                    previous = '"'
                }
                continue
            }
            when (ch) {
                '"' -> {
                    require(previous in "{[:,") { "Invalid playlist JSON string" }
                    inString = true
                }
                '{', '[' -> require(++depth <= 16) { "Playlist JSON nesting limit" }
                '}', ']' -> require(--depth >= 0) { "Invalid playlist JSON" }
                '/', '#', '\'', '=', ';' -> throw IllegalArgumentException("Invalid playlist JSON token")
            }
            if (!ch.isWhitespace()) previous = ch
        }
        require(depth == 0 && !inString) { "Invalid playlist JSON" }
    }
}

internal const val TooManyPlaylistEntries = "Too many playlist entries"

internal fun checkPlaylistEntryCount(count: Int) {
    require(count <= PlaylistImportLimits.MaxEntries) { TooManyPlaylistEntries }
}

internal fun structuredPlaylistEntry(
    location: String?, title: String?, artist: String?, durationSeconds: Int? = null,
): M3uEntry? {
    val path = location?.trim().orEmpty()
    val name = title?.trim()?.takeIf(String::isNotEmpty)
    if (path.isEmpty() && name == null) return null
    return M3uEntry(path, name, durationSeconds, artist?.trim().orEmpty())
}

internal fun String.playlistSingleLine() = replace('\r', ' ').replace('\n', ' ')

internal fun Long.playlistDurationSeconds() = takeIf { it >= 0 }?.div(1000)?.takeIf { it <= Int.MAX_VALUE }?.toInt()
