package app.echo.android.data

import app.echo.android.model.library.PlaylistImportLimits

object PlaylistFileCodec {
    fun parse(text: String): List<M3uEntry> {
        val first = text.lineSequence().firstOrNull { it.isNotBlank() }
            ?.trim()?.trimStart('\uFEFF').orEmpty()
        return if (first.equals("[playlist]", ignoreCase = true)) parsePls(text)
            else M3uPlaylistCodec.parse(text, PlaylistImportLimits.MaxEntries)
    }

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
            require(index in entries || entries.size < PlaylistImportLimits.MaxEntries) { "Too many playlist entries" }
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
}
