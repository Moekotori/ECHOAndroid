package app.echo.android.data

import app.echo.android.model.podcast.*
import app.echo.android.model.radio.EchoRadioStation
import java.io.ByteArrayInputStream
import java.net.URI
import java.security.MessageDigest
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.PriorityQueue
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.ext.DefaultHandler2

data class ParsedPodcast(val feed: EchoPodcastFeed, val episodes: List<EchoPodcastEpisode>)

/** Streaming RSS reader. No DOM, external entities, platform extraction or HTML rendering. */
object PodcastFeedParser {
    fun identity(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun parse(bytes: ByteArray, feedUrl: String, baseUrl: String = feedUrl, charset: String? = null): ParsedPodcast {
        require(bytes.size <= PodcastLimits.FeedBytes && EchoRadioStation.validUrl(feedUrl))
        val handler = FeedHandler(feedUrl, baseUrl)
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        val reader = factory.newSAXParser().xmlReader
        reader.contentHandler = handler
        reader.errorHandler = handler
        reader.setProperty("http://xml.org/sax/properties/lexical-handler", handler)
        reader.entityResolver = org.xml.sax.EntityResolver { _, _ -> throw SAXException("External entity") }
        reader.parse(InputSource(ByteArrayInputStream(bytes)).apply { encoding = charset })
        return handler.result()
    }

    private class FeedHandler(private val url: String, base: String) : DefaultHandler2() {
        override fun startDTD(name: String?, publicId: String?, systemId: String?) { throw SAXException("DTD is not allowed") }
        private val feedId = identity(url)
        private val bases = mutableListOf(base)
        private val names = mutableListOf<String>()
        private val show = HashMap<String, String>()
        private var item: MutableMap<String, String>? = null
        private var capture: String? = null
        private var captureDepth = 0
        private val text = StringBuilder()
        private var order = 0
        private var channelSeen = false
        private var rootSeen = false
        private val newest = compareBy<EchoPodcastEpisode> { it.publishedMs }.thenBy { -it.sourceOrder }
        private val episodes = PriorityQueue(newest)
        // At most 500 identities are retained; a refresh cannot allocate a full-feed ID set.
        private val retained = HashSet<String>()

        override fun startElement(uri: String, localName: String, qName: String, attrs: Attributes) {
            if (names.size >= 64) throw SAXException("RSS nesting limit")
            if (names.isEmpty()) {
                require(localName == "rss" && uri.isEmpty()) { "Expected RSS 2.0" }
                rootSeen = true
            }
            val parent = names.lastOrNull()
            names += localName
            val base = attrs.getValue("http://www.w3.org/XML/1998/namespace", "base")
            bases += if (base == null) bases.last() else resolve(base, bases.last()) ?: bases.last()
            val itunes = uri == "http://www.itunes.com/dtds/podcast-1.0.dtd"
            if (localName == "channel" && parent == "rss" && uri.isEmpty()) channelSeen = true
            if (localName == "item" && parent == "channel" && uri.isEmpty()) item = HashMap()
            val values = item ?: show
            if (localName == "enclosure" && parent == "item" && uri.isEmpty() && values["audio"] == null) {
                val type = attrs.getValue("type").orEmpty().lowercase()
                val audio = resolve(attrs.getValue("url"), bases.last())
                if (audio != null && (type.startsWith("audio/") ||
                        (type.isBlank() && URI(audio).path.orEmpty().matches(Regex("(?i).*\\.(mp3|m4a|aac|ogg|opus|flac|wav)$"))))) {
                    values["audio"] = audio
                }
            }
            if (itunes && localName == "image" && parent in listOf("channel", "item")) {
                resolve(attrs.getValue("href"), bases.last())?.let { values["artwork"] = it }
            }
            val key = when {
                uri.isEmpty() && parent in listOf("channel", "item") && localName in
                    listOf("title", "description", "link", "guid", "pubDate", "managingEditor") -> localName
                uri.isEmpty() && parent == "image" && localName == "url" && item == null -> "artwork"
                itunes && parent in listOf("channel", "item") && localName in listOf("author", "summary", "duration") -> localName
                uri == "http://purl.org/rss/1.0/modules/content/" && localName == "encoded" && parent == "item" -> "encoded"
                else -> null
            }
            if (key != null && capture == null) { capture = key; captureDepth = names.size; text.setLength(0) }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (capture != null && text.length < 32768) text.append(ch, start, minOf(length, 32768 - text.length))
        }

        override fun endElement(uri: String, localName: String, qName: String) {
            if (capture != null && captureDepth == names.size) {
                val key = capture!!
                val value = text.toString()
                (item ?: show)[key] = if (key in listOf("link", "artwork")) resolve(value.trim(), bases.last()).orEmpty() else value
                capture = null
            }
            if (localName == "item" && uri.isEmpty() && names.size == 3) {
                item?.let(::retain)
                item = null
            }
            names.removeAt(names.lastIndex); bases.removeAt(bases.lastIndex)
        }

        private fun retain(values: Map<String, String>) {
            val sourceOrder = order++
            val audio = values["audio"] ?: return
            val guid = values["guid"].orEmpty().trim().take(4096).takeIf { it.isNotBlank() } ?: audio
            val id = "podcast:" + identity("$feedId\n$guid")
            if (id in retained) return
            val episode = EchoPodcastEpisode(id, feedId, plain(values["title"]).ifBlank { "Episode" }, audio,
                description(values), date(values["pubDate"]), duration(values["duration"]),
                values["artwork"], values["link"]?.takeIf { it.isNotBlank() }, sourceOrder)
            if (episodes.size == PodcastLimits.Episodes && newest.compare(episode, episodes.peek()) <= 0) return
            if (episodes.size == PodcastLimits.Episodes) episodes.poll()?.id?.let(retained::remove)
            episodes.add(episode); retained.add(id)
        }

        fun result(): ParsedPodcast {
            require(rootSeen && channelSeen && !show["title"].isNullOrBlank()) { "Invalid podcast feed" }
            require(episodes.isNotEmpty()) { "No audio episodes" }
            return ParsedPodcast(EchoPodcastFeed(feedId, url, plain(show["title"]), plain(show["author"]?.takeIf { it.isNotBlank() } ?: show["managingEditor"]),
                description(show), show["artwork"], show["link"]?.takeIf { it.isNotBlank() }),
                episodes.sortedWith(newest.reversed()))
        }
    }

    private fun resolve(value: String?, base: String): String? = try {
        value?.trim()?.takeIf { it.isNotEmpty() }?.let { URI(base).resolve(it).toString() }
            ?.takeIf(EchoRadioStation::validUrl)
    } catch (_: Exception) { null }

    private fun plain(value: String?, limit: Int = 512): String = value.orEmpty().replace(Regex("<[^>]*>"), " ")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace(Regex("\\s+"), " ").trim().take(limit)
    private fun description(values: Map<String, String>) = plain(
        values["description"]?.takeIf { it.isNotBlank() } ?: values["encoded"]?.takeIf { it.isNotBlank() } ?: values["summary"], 2000)
    private fun date(value: String?): Long = try {
        ZonedDateTime.parse(value?.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
    } catch (_: Exception) { try { java.time.Instant.parse(value?.trim()).toEpochMilli() } catch (_: Exception) { 0L } }
    private fun duration(value: String?): Long {
        val raw = value?.trim() ?: return 0
        if (!raw.matches(Regex("\\d+(?::\\d{1,2}){0,2}(?:\\.\\d+)?"))) return 0
        val parts = raw.split(':')
        var seconds = 0.0
        for (part in parts) {
            val number = part.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: return 0
            seconds = seconds * 60 + number
        }
        return if (seconds > 0 && seconds <= 7 * 86400) (seconds * 1000).toLong() else 0
    }
}
