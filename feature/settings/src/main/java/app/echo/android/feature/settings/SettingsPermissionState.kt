package app.echo.android.feature.settings

/** Platform checks belong to :app; settings only presents this snapshot. */
data class SettingsPermissionState(
    val audioGranted: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val overlayGranted: Boolean = false,
    val bluetoothGranted: Boolean? = null,
    val localNetworkGranted: Boolean? = null,
) {
    fun needsAttention(floatingLyricsEnabled: Boolean): Boolean =
        !audioGranted || !notificationsEnabled || (floatingLyricsEnabled && !overlayGranted)
}

enum class SettingsPermissionTarget { App, Notifications, Overlay }
