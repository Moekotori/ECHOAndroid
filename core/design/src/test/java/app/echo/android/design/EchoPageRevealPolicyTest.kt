package app.echo.android.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoPageRevealPolicyTest {
    @Test
    fun sectionsStartInOrderAndFinishWithinOneShortTimeline() {
        assertEquals(0f, EchoPageRevealPolicy.progress(0f, 0), 0f)
        assertTrue(EchoPageRevealPolicy.progress(0.2f, 0) > 0f)
        assertEquals(0f, EchoPageRevealPolicy.progress(0.2f, 3), 0f)
        for (order in 0 until EchoPageRevealPolicy.MaxSections) {
            assertEquals(1f, EchoPageRevealPolicy.progress(1f, order), 0f)
        }
        assertTrue(EchoPageRevealPolicy.TimelineMs <= 360)
    }

    @Test
    fun longPagesDoNotAddDelaysForEverySection() {
        assertEquals(1f, EchoPageRevealPolicy.progress(0f, 4), 0f)
        assertEquals(1f, EchoPageRevealPolicy.progress(0f, 10_000), 0f)
        assertEquals(0f, EchoPageRevealPolicy.progress(-1f, 0), 0f)
        assertEquals(1f, EchoPageRevealPolicy.progress(2f, 0), 0f)
    }
}
