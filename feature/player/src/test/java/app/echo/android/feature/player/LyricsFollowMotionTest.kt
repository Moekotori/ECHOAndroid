package app.echo.android.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsFollowMotionTest {
    @Test fun consecutiveLinesKeepTheirForwardMomentum() {
        assertEquals(280f, lyricFollowVelocity(120f, 280f), 0f)
        assertEquals(-280f, lyricFollowVelocity(-120f, -280f), 0f)
    }

    @Test fun backwardSeekAndInvalidVelocityDoNotCarryForwardMomentum() {
        assertEquals(0f, lyricFollowVelocity(-120f, 280f), 0f)
        assertEquals(0f, lyricFollowVelocity(120f, -280f), 0f)
        assertEquals(0f, lyricFollowVelocity(120f, Float.NaN), 0f)
        assertEquals(0f, lyricFollowVelocity(0f, 280f), 0f)
    }

    @Test fun manualBrowsingClearsThePreviousAnchor() {
        val motion = LyricsFollowMotion().apply { anchored = true; velocity = 280f }
        motion.reset()
        assertEquals(false, motion.anchored)
        assertEquals(0f, motion.velocity, 0f)
    }

    @Test fun smallTypographyCorrectionsDoNotOvershootAtThePreviousLineSpeed() {
        assertEquals(20f, lyricFollowVelocity(2f, 280f), 0f)
        assertEquals(-20f, lyricFollowVelocity(-2f, -280f), 0f)
    }
}
