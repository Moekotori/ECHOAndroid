package app.echo.android.playback

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
}
