package app.echo.android.lyrics

import org.junit.Assert.*
import org.junit.Test

class PcLyricsContentCompatibilityTest {
    @Test fun offsetsMoveLrcAndLrcxLinesWordsAndAttachmentsTogether() {
        val raw = "[offset:250]\n[00:01.25]Hello world\n[00:01.25][tt]<0,0><500,6><1500,11>\n[00:01.25][tr:zh]你好"
        val lyrics = EchoLyricsParser.parse(raw, "song.lrcx")
        assertEquals(1000L, lyrics.lines.single().startMs)
        assertEquals(listOf(1000L, 1500L), lyrics.lines.single().words.map { it.startMs })
        assertEquals("你好", lyrics.lines.single().translation)
        assertEquals(-250L, lyrics.offsetMs)
        assertEquals(0L, EchoLyricsParser.parse("[offset:250]\n[00:00.10]Hello", "song.lrc").lines.single().startMs)
        assertEquals(1500L, EchoLyricsParser.parse("[offset:-250]\n[00:01.25]Hello", "song.lrc").lines.single().startMs)
    }

    @Test fun ttmlExternalLanguagesAndSingerNamesSurvivePrivateStorage() {
        val raw = """<tt xmlns:ttm="http://www.w3.org/ns/ttml#metadata"><head><metadata>
            <ttm:agent xml:id="v1" type="person"><ttm:name>Singer</ttm:name></ttm:agent>
            <translations><text for="#L1" xml:lang="zh">你好</text><text for="L1" xml:lang="fr">Bonjour</text></translations>
            <romanizations><text for="L1" xml:lang="rom">hello</text></romanizations>
            </metadata></head><body><p xml:id="L1" begin="1s" end="3s" ttm:agent="v1">Hello</p></body></tt>"""
        val lyrics = EchoLyricsParser.parse(raw, "song.ttml")
        val line = lyrics.lines.single()
        assertEquals("你好", line.translation)
        assertEquals("hello", line.romanization)
        assertEquals("Singer", line.speaker)
        assertEquals("v1", line.speakerId)
        assertEquals(listOf("zh", "fr", "rom"), line.alternateTexts.map { it.language })
        assertEquals(lyrics, EchoLyricsJson.decode(EchoLyricsJson.encode(lyrics)))
    }

    @Test fun subtitleVariantsHaveTheSameReadableBodyAndTimes() {
        val srt = EchoLyricsParser.parse("1\n00:01,250 --> 00:03,000\nHello", "song.srt").lines.single()
        assertEquals(1250L, srt.startMs)
        assertEquals(3000L, srt.endMs)
        assertEquals("Hello", srt.text)
        assertEquals("Hello", EchoLyricsParser.parse("[Events]\nDialogue: 0,0:00:01.25,0:00:03.00,Default,,0,0,0,,Hello", "song.ass").lines.single().text)
    }

    @Test fun companionDiscoveryIncludesLanguagesSubdirectoriesAndFullAudioNames() {
        val candidates = LyricsSidecarNames.candidates("song.flac", "Title")
        val files = listOf("song.lrc", "song.t.lrc", "lyrics/song.rom.lrc", "song.zh.lrc", "lyrics/song.flac.lrc")
        val found = LyricsSidecarDiscovery.discover(candidates, files)
        assertEquals("song.lrc", found.first().primary)
        assertEquals("song.t.lrc", found.first().translation)
        assertEquals("lyrics/song.rom.lrc", found.first().romanization)
        assertTrue(found.any { it.primary == "lyrics/song.flac.lrc" })
        assertEquals("song.zh.lrc", LyricsSidecarDiscovery.discover(candidates, listOf("song.zh.lrc")).single().primary)
        assertTrue(LyricsSidecarDiscovery.discover(candidates, listOf("song.zh.lrc"), complete = false).isEmpty())
        assertTrue(LyricsSidecarNames.isAudioSidecar("lyrics/song.flac.lrc", "song.flac"))
    }

    @Test fun companionsMergeBeforeAndAfterCueClippingWithoutLosingExistingAnnotations() {
        val primary = EchoLyricsParser.parse("[01:01.00]Hello\n[01:02.00]World", "song.lrc")
        val translated = EchoLyricsParser.parse("[01:01.30]你好", "song.t.lrc")
        val romanized = EchoLyricsParser.parse("[01:01.00]hello\n[01:02.00]world", "song.rom.lrc")
        val merged = LyricsSidecarDiscovery.merge(primary, translated, romanized)
        assertEquals("你好", merged.lines.first().translation)
        assertNull(merged.lines.last().translation)
        assertEquals("world", merged.lines.last().romanization)
        val clip = LyricsClipPolicy.sidecar(merged, "song.flac", 60_000, 90_000)!!
        assertEquals(1000L, clip.lines.first().startMs)
        assertEquals("你好", clip.lines.first().translation)
    }
}
