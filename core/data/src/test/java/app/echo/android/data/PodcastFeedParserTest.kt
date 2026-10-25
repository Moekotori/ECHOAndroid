package app.echo.android.data

import app.echo.android.model.podcast.PodcastLimits
import org.junit.Assert.*
import org.junit.Test

class PodcastFeedParserTest {
    private val url = "https://podcast.example/feed.xml"
    private fun rss(items: String, extra: String = "") = """<rss version="2.0" xmlns:i="http://www.itunes.com/dtds/podcast-1.0.dtd" xmlns:c="http://purl.org/rss/1.0/modules/content/"><channel><title>Show</title>$extra$items</channel></rss>"""
    private fun parse(xml: String) = PodcastFeedParser.parse(xml.toByteArray(), url)

    @Test fun namespacesRelativeBasesAndFirstAudioEnclosure() {
        val result = parse(rss("""<item xml:base="episodes/"><title>Episode</title><guid>raw id</guid><enclosure url="video.mp4" type="video/mp4"/><enclosure xml:base="../audio/" url="one.mp3" type="audio/mpeg"/><enclosure url="two.mp3" type="audio/mpeg"/><i:duration>1:02:03</i:duration><c:encoded><![CDATA[<b>A story</b>]]></c:encoded></item>""",
            """<i:author>Author</i:author><i:image href="cover.jpg"/>"""))
        assertEquals("Author", result.feed.author)
        assertEquals("https://podcast.example/cover.jpg", result.feed.artworkUrl)
        val episode = result.episodes.single()
        assertEquals("https://podcast.example/audio/one.mp3", episode.audioUrl)
        assertEquals(3_723_000L, episode.durationMs)
        assertEquals("A story", episode.description)
    }

    @Test fun rawGuidAndFeedIdentityRemainStableWhenMetadataChanges() {
        val first = parse(rss("""<item><title>One</title><guid> x </guid><enclosure url="https://audio.example/a.mp3" type="audio/mpeg"/></item>""")).episodes.single()
        val second = parse(rss("""<item><title>Renamed</title><guid> x </guid><enclosure url="https://audio.example/b.mp3" type="audio/mpeg"/></item>""")).episodes.single()
        assertEquals(first.id, second.id)
        assertEquals(first.id, parse(rss("""<item><guid>x</guid><enclosure url="https://audio.example/a.mp3" type="audio/mpeg"/></item>""")).episodes.single().id)
    }

    @Test fun boundedNewestWindowRetainsPublisherOrderOnTies() {
        val result = parse(rss(buildString {
            repeat(520) { append("<item><title>Episode $it</title><guid>$it</guid><enclosure url=\"https://audio.example/$it.mp3\" type=\"audio/mpeg\"/></item>") }
            append("<item><title>Newest</title><guid>new</guid><pubDate>Sun, 25 Oct 2026 00:00:00 GMT</pubDate><enclosure url=\"https://audio.example/new.mp3\" type=\"audio/mpeg\"/></item>")
        }))
        assertEquals(PodcastLimits.Episodes, result.episodes.size)
        assertEquals("Newest", result.episodes.first().title)
        assertEquals("Episode 0", result.episodes[1].title)
        assertEquals("Episode 498", result.episodes.last().title)
    }

    @Test fun credentialUrlsAndNonAudioItemsAreIgnored() {
        val result = runCatching { parse(rss("""<item><enclosure url="https://user:pass@audio.example/a.mp3" type="audio/mpeg"/></item><item><enclosure url="file:///tmp/music.mp3" type="audio/mpeg"/></item><item><enclosure url="https://example.com/page" type="text/html"/></item>""")) }
        assertTrue(result.isFailure)
    }

    @Test fun utf16BomAndNonAsciiTitles() {
        val xml = """<?xml version="1.0" encoding="UTF-16"?>""" + rss("<item><title>声音</title><enclosure url=\"https://audio.example/a.mp3\" type=\"audio/mpeg\"/></item>")
        assertEquals("声音", PodcastFeedParser.parse(xml.toByteArray(Charsets.UTF_16), url).episodes.single().title)
    }

    @Test fun rejectsDtdNestingHtmlAndOversizedFeeds() {
        val invalid = listOf("<!DOCTYPE rss [<!ENTITY title 'secret'>]>" + rss("<item><title>&title;</title></item>"),
            "<html><title>Not RSS</title></html>", rss("<x>".repeat(70) + "</x>".repeat(70)))
        invalid.forEach { xml -> assertTrue(runCatching { parse(xml) }.isFailure) }
        assertTrue(runCatching { PodcastFeedParser.parse(ByteArray(PodcastLimits.FeedBytes + 1), url) }.isFailure)
    }
}
