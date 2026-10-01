package app.echo.android.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import org.junit.Test

class EchoMotionTest {
    @Test
    fun localeFadeShortensInLightweightMode() {
        assertEquals(EchoMotion.LocaleLightweightMs, EchoMotion.localeFadeOut(lightweight = true).durationMillis)
        assertEquals(EchoMotion.LocaleLightweightMs, EchoMotion.localeFadeIn(lightweight = true).durationMillis)
        assertEquals(EchoMotion.LocaleOutMs, EchoMotion.localeFadeOut(lightweight = false).durationMillis)
        assertEquals(EchoMotion.LocaleInMs, EchoMotion.localeFadeIn(lightweight = false).durationMillis)
    }

    @Test
    fun lightweightPagesKeepAShortFadeWithoutMovement() {
        val motion = EchoContentMotion(lightweight = true)
        assertEquals(90, EchoMotion.pageFadeIn(true).durationMillis)
        assertEquals(90, EchoMotion.pageFadeOut(true).durationMillis)
        for (transform in listOf(motion.pagePush(), motion.pagePop(), motion.tabSwitch(true), motion.tabSwitch(false))) {
            assertEquals(fadeIn(EchoMotion.pageFadeIn(true)), transform.targetContentEnter)
            assertEquals(fadeOut(EchoMotion.pageFadeOut(true)), transform.initialContentExit)
            assertNull(transform.sizeTransform)
        }
        assertEquals(fadeIn(EchoMotion.pageFadeIn(true)), motion.overlayEnter())
        assertEquals(fadeOut(EchoMotion.pageFadeOut(true)), motion.overlayExit())
    }

    @Test
    fun fullPageTransitionsKeepViewportSizeAndReverseLayerOrder() {
        val motion = EchoContentMotion(lightweight = false)
        assertNull(motion.pagePush().sizeTransform)
        assertNull(motion.pagePop().sizeTransform)
        assertNull(motion.tabSwitch(true).sizeTransform)
        assertNull(motion.tabSwitch(false).sizeTransform)
        assertEquals(1f, motion.pagePush().targetContentZIndex)
        assertEquals(0f, motion.pagePop().targetContentZIndex)
    }
}
