package app.echo.android.data

import app.echo.android.model.library.PlaylistImportLimits

internal fun parsePlaylistTxt(text: String): List<M3uEntry> {
    val first = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return emptyList()
    if (first.substringBefore('\t').toIntOrNull() == null || '\t' !in first) {
        return M3uPlaylistCodec.parse(text, PlaylistImportLimits.MaxEntries)
    }
    return buildList {
        text.lineSequence().filter(String::isNotBlank).forEach { line ->
            val fields = line.split('\t', limit = 5)
            require(fields.size >= 2 && fields.first().toIntOrNull() != null) { "Invalid playlist text row" }
            val entry = structuredPlaylistEntry(null, fields[1], fields.getOrNull(2))
            if (entry != null) {
                checkPlaylistEntryCount(size + 1)
                add(entry)
            }
        }
    }
}

internal fun parsePlaylistCsv(text: String): List<M3uEntry> {
    val rows = csvRows(text).filter { row -> row.any(String::isNotBlank) }.iterator()
    if (!rows.hasNext()) return emptyList()
    val header = rows.next().map { it.trim().lowercase() }
    val path = header.indexOfFirst { it in setOf("location", "path", "file", "filename", "file path", "url", "uri") }
    val title = header.indexOfFirst { it in setOf("title", "name", "track", "track name", "song", "song title") }
    val artist = header.indexOfFirst { it in setOf("artist", "artists", "artist name", "artist name(s)", "creator") }
    require(path >= 0 || title >= 0) { "Playlist CSV needs a title or location column" }
    val echoCsv = header == listOf("#", "title", "artist", "album", "duration")
    return buildList {
        while (rows.hasNext()) {
            val row = rows.next()
            fun value(index: Int): String? = row.getOrNull(index)?.let {
                if (echoCsv && it.startsWith('\'') && it.trimStart('\'').startsWithSpreadsheetFormula()) it.drop(1)
                else it
            }
            val entry = structuredPlaylistEntry(value(path), value(title), value(artist))
            if (entry != null) {
                checkPlaylistEntryCount(size + 1)
                add(entry)
            }
        }
    }
}

/** Commas and line endings inside quoted fields are data. Two quotes encode one quote. */
private fun csvRows(text: String): Sequence<List<String>> = sequence {
    var offset = 0
    while (offset < text.length) {
        val row = ArrayList<String>()
        do {
            require(row.size < 128) { "Too many playlist CSV columns" }
            val field = StringBuilder()
            if (text.getOrNull(offset) == '"') {
                offset++
                while (true) {
                    val ch = text.getOrNull(offset++) ?: throw IllegalArgumentException("Unclosed CSV field")
                    if (ch == '"') {
                        if (text.getOrNull(offset) != '"') break
                        offset++
                    }
                    field.append(ch)
                }
                require(text.getOrNull(offset) in listOf(',', '\r', '\n', null)) { "Invalid quoted CSV field" }
            } else {
                while (offset < text.length && text[offset] !in ",\r\n") field.append(text[offset++])
            }
            row += field.toString()
            val separator = text.getOrNull(offset++)
        } while (separator == ',')
        if (text.getOrNull(offset - 1) == '\r' && text.getOrNull(offset) == '\n') offset++
        yield(row)
    }
}
