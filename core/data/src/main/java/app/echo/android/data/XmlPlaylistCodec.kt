package app.echo.android.data

import app.echo.android.model.library.PlaylistExportFormat
import java.io.StringReader
import java.net.URI
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

internal object XmlPlaylistCodec {
    private const val XspfNamespace = "http://xspf.org/ns/0/"

    fun parse(text: String): List<M3uEntry> {
        require("<!DOCTYPE" !in text) { "Playlist XML cannot contain a DTD" }
        val handler = PlaylistHandler()
        val parser = SAXParserFactory.newInstance().apply { isNamespaceAware = true }.newSAXParser()
        parser.parse(InputSource(StringReader(text)), handler)
        return handler.entries
    }

    private class PlaylistHandler : DefaultHandler() {
        private data class Element(val name: String, val base: URI?)

        val entries = ArrayList<M3uEntry>()
        private val stack = ArrayList<Element>()
        private val value = StringBuilder()
        private var root = ""
        private var entryDepth = 0
        private var location: String? = null
        private var title: String? = null
        private var artist: String? = null
        private var duration: Int? = null

        override fun resolveEntity(publicId: String?, systemId: String?): InputSource =
            throw SAXException("External XML entities are disabled")

        override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
            require(stack.size < 32) { "Playlist XML nesting limit" }
            val name = localName.lowercase()
            if (stack.isEmpty()) {
                require(name == "asx" || name == "smil" ||
                    (name == "playlist" && (uri == XspfNamespace || uri.isEmpty()))) { "Unsupported XML playlist" }
                root = name
            }
            val parent = stack.lastOrNull()
            val base = if (root == "playlist") attributes.getValue("http://www.w3.org/XML/1998/namespace", "base")
                ?.let { URI(it) }?.let { parent?.base?.resolve(it) ?: it } ?: parent?.base else null
            stack += Element(name, base)
            value.setLength(0)
            when {
                root == "playlist" && stack.size == 3 && parent?.name == "tracklist" && name == "track" -> startEntry()
                root == "asx" && name == "entry" && (parent?.name == "asx" || parent?.name == "repeat") && entryDepth == 0 -> startEntry()
                root == "asx" && name == "ref" && entryDepth > 0 && stack.size == entryDepth + 1 ->
                    location = location ?: attributes.valueIgnoreCase("href")
                root == "smil" && name == "media" && parent?.name == "seq" && stack.getOrNull(1)?.name == "body" -> {
                    val entry = structuredPlaylistEntry(attributes.valueIgnoreCase("src"), null, null)
                    if (entry != null) add(entry)
                }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (entryDepth > 0 && stack.size == entryDepth + 1) value.append(ch, start, length)
        }

        override fun endElement(uri: String, localName: String, qName: String) {
            val element = stack.last()
            if (entryDepth > 0 && stack.size == entryDepth + 1) {
                val field = value.toString().trim().takeIf(String::isNotEmpty)
                when (element.name) {
                    "location" -> if (root == "playlist" && location == null && field != null) {
                        location = xspfLocation(field, element.base)
                    }
                    "title" -> title = field
                    "creator", "author" -> artist = field
                    "duration" -> if (root == "playlist") duration = field?.toLongOrNull()?.playlistDurationSeconds()
                }
            } else if (entryDepth == stack.size) {
                structuredPlaylistEntry(location, title, artist, duration)?.let(::add)
                entryDepth = 0
            }
            stack.removeAt(stack.lastIndex)
            value.setLength(0)
        }

        private fun startEntry() {
            entryDepth = stack.size
            location = null
            title = null
            artist = null
            duration = null
        }

        private fun add(entry: M3uEntry) {
            checkPlaylistEntryCount(entries.size + 1)
            entries += entry
        }
    }

    private fun Attributes.valueIgnoreCase(name: String): String? =
        (0 until length).firstOrNull { getLocalName(it).equals(name, true) }
            ?.let(::getValue)?.takeIf(String::isNotBlank)

    private fun xspfLocation(raw: String, base: URI?): String {
        val uri = runCatching { URI(raw) }.getOrNull() ?: return raw
        val resolved = base?.resolve(uri) ?: uri
        return if (resolved.scheme == null) resolved.path ?: raw else resolved.toString()
    }

    private fun toXspfLocation(path: String): String {
        val existing = runCatching { URI(path) }.getOrNull()
        if (existing?.isAbsolute == true) return existing.toASCIIString()
        val relative = if (':' in path.substringBefore('/')) "./$path" else path
        return URI(null, null, relative, null).toASCIIString()
    }

    fun write(name: String, tracks: List<M3uExportTrack>, format: PlaylistExportFormat): String = buildString {
        when (format) {
            PlaylistExportFormat.Xspf -> {
                appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                appendLine("<playlist version=\"1\" xmlns=\"$XspfNamespace\">")
                appendLine("<title>${name.xmlValue()}</title><trackList>")
                tracks.forEach { track ->
                    appendLine("<track><location>${toXspfLocation(track.location).xmlValue()}</location>")
                    appendLine("<title>${track.title.xmlValue()}</title><creator>${track.artist.xmlValue()}</creator>")
                    track.album?.let { appendLine("<album>${it.xmlValue()}</album>") }
                    if (track.durationMs > 0) appendLine("<duration>${track.durationMs}</duration>")
                    appendLine("</track>")
                }
                appendLine("</trackList></playlist>")
            }
            PlaylistExportFormat.Wpl -> {
                appendLine("<?wpl version=\"1.0\"?>")
                appendLine("<smil><head><title>${name.xmlValue()}</title></head><body><seq>")
                tracks.forEach { appendLine("<media src=\"${it.location.xmlValue()}\"/>") }
                appendLine("</seq></body></smil>")
            }
            PlaylistExportFormat.Asx -> {
                appendLine("<ASX version=\"3.0\"><TITLE>${name.xmlValue()}</TITLE>")
                tracks.forEach { track ->
                    appendLine("<ENTRY><TITLE>${track.title.xmlValue()}</TITLE><AUTHOR>${track.artist.xmlValue()}</AUTHOR>")
                    appendLine("<REF HREF=\"${track.location.xmlValue()}\"/></ENTRY>")
                }
                appendLine("</ASX>")
            }
            else -> error("Unsupported XML playlist format: $format")
        }
    }

    private fun String.xmlValue() = buildString(length) {
        for (ch in this@xmlValue) when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\r' -> append("&#13;")
            '\n' -> append("&#10;")
            '\t' -> append("&#9;")
            else -> if (ch >= ' ' && ch != '\uFFFE' && ch != '\uFFFF') append(ch)
        }
    }
}
