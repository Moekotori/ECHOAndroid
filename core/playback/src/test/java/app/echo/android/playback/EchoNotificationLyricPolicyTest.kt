package app.echo.android.playback

import app.echo.android.model.lyrics.EchoLyricLine
import app.echo.android.model.lyrics.EchoLyricWord
import app.echo.android.model.lyrics.EchoLyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoNotificationLyricPolicyTest {
    @Test
    fun documentDropsUnsyncedAndBlankLines() {
        assertNull(EchoNotificationLyricPolicy.document("track", listOf(-1L to "plain", 1000L to "  ")))
        val doc = EchoNotificationLyricPolicy.document("track", listOf(0L to "One", 2000L to "Two"))
        assertEquals("track", doc?.trackId)
        assertEquals(2, doc?.lines?.size)
    }

    @Test
    fun primaryTextFollowsTimedLines() {
        val lines = listOf(
            EchoNotificationLyricLine(1000, "A"),
            EchoNotificationLyricLine(4000, "B"),
        )
        assertNull(EchoNotificationLyricPolicy.primaryText(lines, 0))
        assertEquals("A", EchoNotificationLyricPolicy.primaryText(lines, 1000))
        assertEquals("A", EchoNotificationLyricPolicy.primaryText(lines, 3999))
        assertEquals("B", EchoNotificationLyricPolicy.primaryText(lines, 4000))
        assertEquals(4000L, EchoNotificationLyricPolicy.nextStartMs(lines, 1500))
        assertNull(EchoNotificationLyricPolicy.nextStartMs(lines, 4000))
    }

    @Test
    fun delayAccountsForSpeedAndHasAFloor() {
        assertEquals(1000L, EchoNotificationLyricPolicy.delayMs(0, 1000, 1f))
        assertEquals(500L, EchoNotificationLyricPolicy.delayMs(0, 1000, 2f))
        assertEquals(EchoNotificationLyricPolicy.MinScheduleDelayMs, EchoNotificationLyricPolicy.delayMs(1000, 1000, 1f))
    }

    @Test
    fun clampCollapsesNewlinesAndCapsLength() {
        assertEquals("hello world", EchoNotificationLyricPolicy.clampText("hello\nworld"))
        val long = "x".repeat(EchoNotificationLyricPolicy.MaxChars + 8)
        val clamped = EchoNotificationLyricPolicy.clampText(long)
        assertEquals(EchoNotificationLyricPolicy.MaxChars, clamped.length)
        assertTrue(clamped.endsWith("…"))
    }

    @Test
    fun publishSkipsUnchangedTextAndRespectsInterval() {
        assertFalse(EchoNotificationLyricPolicy.shouldPublish("A", "A", 5_000L))
        assertTrue(EchoNotificationLyricPolicy.shouldPublish(null, "A", 0L))
        assertTrue(EchoNotificationLyricPolicy.shouldPublish("A", null, 0L))
        assertFalse(EchoNotificationLyricPolicy.shouldPublish("A", "B", 100L))
        assertTrue(EchoNotificationLyricPolicy.shouldPublish("A", "B", EchoNotificationLyricPolicy.MinUpdateIntervalMs))
    }

    @Test
    fun snapshotUsesPrevCurrentNextFromNotificationLines() {
        val lines = listOf(
            EchoNotificationLyricLine(0, "One"),
            EchoNotificationLyricLine(2000, "Two"),
            EchoNotificationLyricLine(4000, "Three"),
        )
        val before = EchoNotificationLyricPolicy.snapshot("t", null, lines, 0, true, 1f, 10L)
        assertEquals("One", before.current?.text)
        assertNull(before.previous)
        assertEquals("Two", before.next?.text)

        val mid = EchoNotificationLyricPolicy.snapshot("t", null, lines, 2500, false, 1f, 20L)
        assertEquals("One", mid.previous?.text)
        assertEquals("Two", mid.current?.text)
        assertEquals("Three", mid.next?.text)
        assertEquals(2000L, mid.currentStartMs)
        assertFalse(mid.isPlaying)
    }

    @Test
    fun snapshotPrefersFullLyricsWords() {
        val lyrics = EchoLyrics(
            lines = listOf(
                EchoLyricLine(0, 2000, "One", words = listOf(EchoLyricWord(0, 1000, "One"))),
                EchoLyricLine(2000, 4000, "Two"),
            ),
        )
        val snap = EchoNotificationLyricPolicy.snapshot(
            trackId = "t",
            lyrics = lyrics,
            lines = listOf(EchoNotificationLyricLine(0, "One"), EchoNotificationLyricLine(2000, "Two")),
            positionMs = 500,
            isPlaying = true,
            speed = 1f,
            publishedAtElapsedRealtimeMs = 0L,
        )
        assertEquals(1, snap.current?.words?.size)
        assertEquals("Two", snap.next?.text)
    }

    @Test
    fun snapshotInterpolatesWhilePlaying() {
        val snap = EchoNotificationLyricPolicy.snapshot(
            trackId = "t",
            lyrics = null,
            lines = listOf(EchoNotificationLyricLine(0, "One")),
            positionMs = 1000,
            isPlaying = true,
            speed = 2f,
            publishedAtElapsedRealtimeMs = 0L,
        )
        assertEquals(3000L, snap.interpolatedPositionMs(1000L))
        assertEquals(1000L, snap.copy(isPlaying = false).interpolatedPositionMs(5000L))
    }

    @Test
    fun emptyDocumentYieldsEmptySnapshot() {
        val snap = EchoNotificationLyricPolicy.snapshot("t", null, emptyList(), 1000, true, 1f, 0L)
        assertNull(snap.current)
        assertEquals("t", snap.trackId)
    }
}
