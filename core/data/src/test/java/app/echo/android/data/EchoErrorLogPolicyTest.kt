package app.echo.android.data

import app.echo.android.model.error.EchoErrorDraft
import app.echo.android.model.error.EchoErrorRecord
import app.echo.android.model.error.EchoErrorSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoErrorLogPolicyTest {
    @Test
    fun sanitizesCredentialsInUrlsAndHeaders() {
        val raw = "GET https://user:secret@host/stream?token=abc&title=ok Authorization: Bearer xyz"
        val sanitized = EchoErrorLogPolicy.sanitize(raw)
        assertFalse(sanitized.contains("secret"))
        assertFalse(sanitized.contains("abc"))
        assertFalse(sanitized.contains("xyz"))
        assertTrue(sanitized.contains("<redacted>"))
        assertTrue(sanitized.contains("title=ok"))
    }

    @Test
    fun clipsLongSummary() {
        val clipped = EchoErrorLogPolicy.clip("a".repeat(50), 8)
        assertEquals(8, clipped.length)
        assertTrue(clipped.endsWith("…"))
    }

    @Test
    fun mergeRequiresSameSourceSummaryAndWindow() {
        val latest = record(source = EchoErrorSource.Playback, summary = "boom", at = 1_000L)
        assertTrue(
            EchoErrorLogPolicy.shouldMerge(
                latest,
                EchoErrorDraft(EchoErrorSource.Playback, "boom", occurredAtEpochMs = 1_000L + EchoErrorLogPolicy.MergeWindowMs),
            ),
        )
        assertFalse(
            EchoErrorLogPolicy.shouldMerge(
                latest,
                EchoErrorDraft(EchoErrorSource.Playback, "boom", occurredAtEpochMs = 1_000L + EchoErrorLogPolicy.MergeWindowMs + 1),
            ),
        )
        assertFalse(
            EchoErrorLogPolicy.shouldMerge(
                latest,
                EchoErrorDraft(EchoErrorSource.Library, "boom", occurredAtEpochMs = 1_100L),
            ),
        )
        assertFalse(
            EchoErrorLogPolicy.shouldMerge(
                latest,
                EchoErrorDraft(EchoErrorSource.Playback, "other", occurredAtEpochMs = 1_100L),
            ),
        )
    }

    @Test
    fun mergeKeepsEarlierFirstSeen() {
        val latest = record(source = EchoErrorSource.Playback, summary = "boom", at = 2_000L)
            .copy(firstOccurredAtEpochMs = 1_000L)
        assertEquals(
            1_000L,
            EchoErrorLogPolicy.mergedFirstOccurredAt(
                latest,
                EchoErrorDraft(EchoErrorSource.Playback, "boom", occurredAtEpochMs = 2_500L),
            ),
        )
    }

    @Test
    fun stackTraceCapturesCauseType() {
        val stack = EchoErrorLogPolicy.stackTrace(IllegalStateException("nope"))
        assertTrue(stack!!.contains("IllegalStateException"))
        assertTrue(stack.contains("nope"))
    }

    @Test
    fun blankDetailBecomesNull() {
        assertNull(EchoErrorLogPolicy.normalizeDetail("  "))
        assertEquals("kept", EchoErrorLogPolicy.normalizeDetail(" kept "))
    }

    private fun record(
        source: EchoErrorSource,
        summary: String,
        at: Long,
    ) = EchoErrorRecord(
        id = 1L,
        occurredAtEpochMs = at,
        source = source,
        summary = summary,
        detail = null,
        stackTrace = null,
        count = 1,
    )
}
