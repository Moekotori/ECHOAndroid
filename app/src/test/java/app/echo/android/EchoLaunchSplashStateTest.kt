package app.echo.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoLaunchSplashStateTest {
    @Test
    fun lateSystemHandoffCannotRestartDismissedSplash() {
        val state = EchoLaunchSplashState()

        // Timeout/background dismissal can happen before the platform exit callback.
        state.finish()
        state.start(background = null, lightweight = false)

        assertTrue(state.finished)
        assertFalse(state.started)
        assertNull(state.lightweight)
        assertNull(state.background)
    }
}
