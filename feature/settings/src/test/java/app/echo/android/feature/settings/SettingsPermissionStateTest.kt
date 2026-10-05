package app.echo.android.feature.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPermissionStateTest {
    @Test
    fun disabledOptionalPermissionsDoNotRequireAttentionUntilFeatureIsEnabled() {
        val state = SettingsPermissionState(overlayGranted = false, bluetoothGranted = false, localNetworkGranted = false)
        assertFalse(state.needsAttention(floatingLyricsEnabled = false))
        assertTrue(state.needsAttention(floatingLyricsEnabled = true))
        assertFalse(state.copy(overlayGranted = true).needsAttention(floatingLyricsEnabled = true))
    }

    @Test
    fun musicOrNotificationsBeingDisabledRequiresAttention() {
        assertTrue(SettingsPermissionState(audioGranted = false).needsAttention(false))
        assertTrue(SettingsPermissionState(notificationsEnabled = false).needsAttention(false))
    }
}
