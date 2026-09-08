package app.echo.android.model.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoBackgroundStyleTest {
    @Test
    fun effectiveBlurStillMatchesWhenPerformanceLimitChanges() {
        val style = EchoBackgroundStyle.Dreamy
        assertTrue(style.matches(4f, style.brightness, style.glass, style.scale, 4f, false))
        assertFalse(style.matches(4f, style.brightness, style.glass, style.scale, 16f, false))
    }

    @Test
    fun manualBrightnessChangeLeavesPreset() {
        val style = EchoBackgroundStyle.Soft
        assertFalse(style.matches(style.blur, 0.7f, style.glass, style.scale, 16f, false))
    }

    @Test
    fun videoMatchingIgnoresUnusedBlur() {
        val style = EchoBackgroundStyle.Focus
        assertTrue(style.matches(0f, style.brightness, style.glass, style.scale, 16f, true))
    }
}
