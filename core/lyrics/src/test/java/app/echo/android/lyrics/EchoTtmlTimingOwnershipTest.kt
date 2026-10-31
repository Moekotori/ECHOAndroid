package app.echo.android.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoTtmlTimingOwnershipTest {
    @Test
    fun backingWordTimesDoNotMakeTheLeadWordSynced() {
        val lyrics = EchoLyricsParser.parse(
            """<tt xmlns:ttm="http://www.w3.org/ns/ttml#metadata"><body>
                <p begin="1s" end="5s" ttm:agent="v1">Lead vocal<span ttm:role="x-bg" begin="2s" end="4s"><span begin="2s" end="3s">Oh</span><span begin="3s" end="4s">yeah</span></span></p>
            </body></tt>""",
            "track.ttml",
        )

        val lead = lyrics.lines.single { !it.isBackground }
        assertEquals("Lead vocal", lead.text)
        assertEquals(1000L, lead.startMs)
        assertEquals(5000L, lead.endMs)
        assertTrue(lead.words.isEmpty())

        val backing = lyrics.lines.single { it.isBackground }
        assertEquals("Ohyeah", backing.text)
        assertEquals("v1", backing.speaker)
        assertEquals(listOf(2000L, 3000L), backing.words.map { it.startMs })
        assertEquals(listOf(3000L, 4000L), backing.words.map { it.endMs })
    }

    @Test
    fun annotationAndEmptySpanTimesDoNotMakeTheLeadWordSynced() {
        val lyrics = EchoLyricsParser.parse(
            """<tt xmlns:ttm="http://www.w3.org/ns/ttml#metadata"><body>
                <p begin="1s" end="5s">Hello<span begin="2s"></span><span ttm:role="x-translation"><span begin="1s" end="3s">你好</span></span><span ttm:role="x-roman"><span begin="1s" end="3s">ni hao</span></span></p>
            </body></tt>""",
            "track.ttml",
        )

        val line = lyrics.lines.single()
        assertEquals("Hello", line.text)
        assertEquals("你好", line.translation)
        assertEquals("ni hao", line.romanization)
        assertTrue(line.words.isEmpty())
    }
}
