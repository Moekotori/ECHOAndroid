package app.echo.android.design

import org.junit.Assert.assertEquals
import org.junit.Test

class EchoMotionTest {
    @Test
    fun localeFadeShortensInLightweightMode() {
        assertEquals(EchoMotion.LocaleLightweightMs, EchoMotion.localeFadeOut(lightweight = true).durationMillis)
        assertEquals(EchoMotion.LocaleLightweightMs, EchoMotion.localeFadeIn(lightweight = true).durationMillis)
        assertEquals(EchoMotion.LocaleOutMs, EchoMotion.localeFadeOut(lightweight = false).durationMillis)
        assertEquals(EchoMotion.LocaleInMs, EchoMotion.localeFadeIn(lightweight = false).durationMillis)
    }
}
