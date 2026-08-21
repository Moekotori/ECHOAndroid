package app.echo.android.playback

import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@UnstableApi
class EchoMediaSessionControllerGateTest {
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
