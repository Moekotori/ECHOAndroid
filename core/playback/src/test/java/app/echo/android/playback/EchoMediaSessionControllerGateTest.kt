package app.echo.android.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@UnstableApi
class EchoMediaSessionControllerGateTest {
    @Test
    fun legacyBrowserPlaceholderCanSelectServiceSession() {
        assertTrue(EchoMediaSessionControllerGate.isLegacyBrowserServicePlaceholder(
            "android.media.session.MediaController",
            -1,
            MediaSession.ControllerInfo.LEGACY_CONTROLLER_VERSION,
        ))
        // This identity must not become an allowlisted external controller.
        assertFalse(EchoMediaSessionControllerGate.isAllowedPackage("android.media.session.MediaController"))
    }

    @Test
    fun realOrUnrelatedControllersCannotUsePlaceholderException() {
        assertFalse(EchoMediaSessionControllerGate.isLegacyBrowserServicePlaceholder(
            "android.media.session.MediaController",
            10_001,
            MediaSession.ControllerInfo.LEGACY_CONTROLLER_VERSION,
        ))
        assertFalse(EchoMediaSessionControllerGate.isLegacyBrowserServicePlaceholder(
            "com.random.app",
            -1,
            MediaSession.ControllerInfo.LEGACY_CONTROLLER_VERSION,
        ))
        assertFalse(EchoMediaSessionControllerGate.isLegacyBrowserServicePlaceholder(
            "android.media.session.MediaController",
            -1,
            1,
        ))
    }

    @Test
    fun autoBluetoothAndSystemPackagesAreAllowlisted() {
        assertTrue(EchoMediaSessionControllerGate.isAllowedPackage("com.google.android.projection.gearhead"))
        assertTrue(EchoMediaSessionControllerGate.isAllowedPackage("com.android.bluetooth"))
        assertTrue(EchoMediaSessionControllerGate.isAllowedPackage("com.android.systemui"))
        assertTrue(EchoMediaSessionControllerGate.isAllowedPackage("com.google.android.gms"))
    }

    @Test
    fun randomPackagesStayBlocked() {
        assertFalse(EchoMediaSessionControllerGate.isAllowedPackage("com.random.app"))
        assertFalse(EchoMediaSessionControllerGate.isAllowedPackage(""))
    }
}
