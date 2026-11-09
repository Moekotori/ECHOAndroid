package app.echo.android.data

import app.echo.android.model.library.PlaylistImportLimits
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

internal data class ITunesPlaylist(val key: String, val name: String, val entries: List<M3uEntry>)

/** Reads iTunes XML exports without opening the DTD or dereferencing track locations. */
internal object ITunesPlaylistCodec {
    private val appleDoctype = Regex(
        """<!DOCTYPE\s+plist\s+PUBLIC\s+"-//Apple(?: Computer)?//DTD PLIST 1\.0//EN"\s+"https?://www\.apple\.com/DTDs/PropertyList-1\.0\.dtd"\s*>""",
    )

    fun parse(text: String): List<ITunesPlaylist> {
        val xml = text.trimStart('\uFEFF').replace(appleDoctype, "")
        require("<!DOCTYPE" !in xml)
        val handler = LibraryHandler()
        val factory = SAXParserFactory.newInstance().apply {
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        factory.newSAXParser().parse(InputSource(StringReader(xml)), handler)
        return handler.playlists()
    }

    private class LibraryHandler : DefaultHandler() {
        private data class Frame(val tag: String, val key: String?, var nextKey: String? = null)
        private data class Playlist(
            var id: String? = null,
            var persistentId: String? = null,
            var name: String? = null,
            var skip: Boolean = false,
            val trackIds: MutableList<String> = ArrayList(),
        )

        private val frames = ArrayList<Frame>()
        private val text = StringBuilder()
        private val tracks = HashMap<String, M3uEntry>()
        private val lists = ArrayList<Playlist>()
        private var location: String? = null
        private var trackName: String? = null
        private var itemTrackId: String? = null
        private var playlist: Playlist? = null
        private var library = false

        override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
            if (frames.size >= 32) throw SAXException("iTunes XML nesting limit")
            val parent = frames.lastOrNull()
            frames += Frame(qName, if (qName == "key") null else parent?.nextKey.also { parent?.nextKey = null })
            text.setLength(0)
            if (frames.size == 3 && qName == "dict" && frames.last().key == "Tracks") library = true
            if (isTrack()) {
                location = null
                trackName = null
            }
            if (isPlaylist()) playlist = Playlist()
            if (isPlaylistItem()) itemTrackId = null
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (frames.lastOrNull()?.tag in TextTags && text.length < 4096) {
                text.append(ch, start, minOf(length, 4096 - text.length))
            }
        }

        override fun endElement(uri: String, localName: String, qName: String) {
            when {
                isTrack() -> {
                    val id = frames.last().key
                    val path = location
                    if (id != null && path != null) tracks[id] = M3uEntry(path, trackName, null)
                }
                isPlaylist() -> {
                    val value = playlist
                    if (value != null && !value.skip && value.name != null && value.trackIds.isNotEmpty() &&
                        value.trackIds.size <= PlaylistImportLimits.MaxEntries && lists.size < 32
                    ) lists += value
                    playlist = null
                }
                isPlaylistItem() -> playlist?.trackIds?.let { ids ->
                    if (ids.size <= PlaylistImportLimits.MaxEntries) itemTrackId?.let(ids::add)
                }
            }
            val frame = frames.removeAt(frames.lastIndex)
            val value = text.toString().trim()
            if (frame.tag == "key") frames.lastOrNull()?.nextKey = value
            if (isTrack() && frame.tag == "string") when (frame.key) {
                "Location" -> location = value.takeIf { it.startsWith("file:", ignoreCase = true) }
                "Name" -> trackName = value
            }
            if (isPlaylistItem() && frame.key == "Track ID" && frame.tag == "integer") itemTrackId = value
            if (isPlaylist()) playlist?.let {
                when (frame.key) {
                    "Name" -> it.name = value.takeIf(String::isNotBlank)
                    "Playlist ID" -> it.id = value
                    "Playlist Persistent ID" -> it.persistentId = value
                    "Master", "Folder" -> if (frame.tag == "true") it.skip = true
                    "Distinguished Kind" -> if (value.toIntOrNull()?.let { kind -> kind > 0 } == true) it.skip = true
                }
            }
            text.setLength(0)
        }

        private fun isTrack() =
            frames.size == 4 && frames[2].key == "Tracks" && frames[3].tag == "dict"

        private fun isPlaylist() =
            frames.size == 4 && frames[2].key == "Playlists" && frames[3].tag == "dict"

        private fun isPlaylistItem() = frames.size == 6 && frames[2].key == "Playlists" &&
            frames[4].key == "Playlist Items" && frames[5].tag == "dict"

        fun playlists(): List<ITunesPlaylist> {
            require(library) { "Expected iTunes library XML" }
            return lists.mapNotNull {
                val name = requireNotNull(it.name)
                val entries = it.trackIds.mapNotNull(tracks::get)
                if (entries.isEmpty()) null else ITunesPlaylist(it.persistentId ?: it.id ?: name, name, entries)
            }.distinctBy { it.key }
        }
    }

    private val TextTags = setOf("key", "string", "integer")
}
