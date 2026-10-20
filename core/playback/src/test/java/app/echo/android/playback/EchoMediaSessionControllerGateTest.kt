package app.echo.android.playback

import android.content.pm.ApplicationInfo
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@UnstableApi
class EchoMediaSessionControllerGateTest {
    @Test
    fun legacyCompanionCanControlPlaybackWithoutBecomingALibraryController() {
        assertTrue(EchoMediaSessionControllerGate.isLegacyTransportController(
            10_123, MediaSession.ControllerInfo.LEGACY_CONTROLLER_VERSION,
        ))
        assertFalse(EchoMediaSessionControllerGate.isLegacyTransportController(
            -1, MediaSession.ControllerInfo.LEGACY_CONTROLLER_VERSION,
        ))
        assertFalse(EchoMediaSessionControllerGate.isLegacyTransportController(10_123, 1))
        assertFalse(EchoMediaSessionControllerGate.isAllowedPackage("com.xiaomi.wearable"))
        assertTrue(EchoLegacyTransportCommandIds.containsAll(listOf(
            Player.COMMAND_PLAY_PAUSE, Player.COMMAND_PREPARE, Player.COMMAND_STOP,
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
        )))
        assertFalse(EchoLegacyTransportCommandIds.contains(Player.COMMAND_CHANGE_MEDIA_ITEMS))
        assertFalse(EchoLegacyTransportCommandIds.contains(Player.COMMAND_SET_MEDIA_ITEM))
        assertFalse(EchoLegacyTransportCommandIds.contains(Player.COMMAND_RELEASE))
    }

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

    @Test
    fun xiaomiMediaControllerRequiresMatchingUidAndSystemInstallation() {
        for (flags in listOf(ApplicationInfo.FLAG_SYSTEM, ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) {
            assertTrue(EchoMediaSessionControllerGate.isPreinstalledXiaomiMediaController(
                "com.milink.service", 10_123, 10_123, flags,
            ))
        }
        assertFalse(EchoMediaSessionControllerGate.isAllowedPackage("com.milink.service"))
        assertFalse(EchoMediaSessionControllerGate.isPreinstalledXiaomiMediaController(
            "com.milink.service", 10_123, 10_123, 0,
        ))
        assertFalse(EchoMediaSessionControllerGate.isPreinstalledXiaomiMediaController(
            "com.milink.service", 10_124, 10_123, ApplicationInfo.FLAG_SYSTEM,
        ))
        assertFalse(EchoMediaSessionControllerGate.isPreinstalledXiaomiMediaController(
            "com.milink.service", -1, -1, ApplicationInfo.FLAG_SYSTEM,
        ))
        assertFalse(EchoMediaSessionControllerGate.isPreinstalledXiaomiMediaController(
            "com.milink.service.fake", 10_123, 10_123, ApplicationInfo.FLAG_SYSTEM,
        ))
    }
}
