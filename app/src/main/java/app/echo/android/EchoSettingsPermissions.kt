package app.echo.android

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import app.echo.android.connect.hasEchoLocalNetworkAccess
import app.echo.android.feature.settings.SettingsPermissionState
import app.echo.android.feature.settings.SettingsPermissionTarget
import app.echo.android.feature.settings.R as SettingsR

/** Check when settings becomes visible or resumes; no background observer or polling. */
@Composable
internal fun rememberEchoSettingsPermissions(active: Boolean): SettingsPermissionState {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var state by remember(context) { mutableStateOf(SettingsPermissionState()) }
    DisposableEffect(context, lifecycle, active) {
        if (active) state = context.readSettingsPermissions()
        val observer = LifecycleEventObserver { _, event ->
            if (active && event == Lifecycle.Event.ON_RESUME) state = context.readSettingsPermissions()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return state
}

@androidx.annotation.OptIn(UnstableApi::class)
private fun Context.readSettingsPermissions(): SettingsPermissionState {
    fun granted(permission: String) = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    val notifications = getSystemService(NotificationManager::class.java)
    // Media3 creates this channel on first playback. An absent channel is not a denial.
    val playbackChannel = notifications.getNotificationChannel(DefaultMediaNotificationProvider.DEFAULT_CHANNEL_ID)
    val channelGroupBlocked = Build.VERSION.SDK_INT >= 28 && playbackChannel?.group?.let {
        notifications.getNotificationChannelGroup(it)?.isBlocked
    } == true
    return SettingsPermissionState(
        audioGranted = granted(audioPermissionName()),
        notificationsEnabled = notifications.areNotificationsEnabled() &&
            (notificationPermissionName()?.let(::granted) != false) &&
            playbackChannel?.importance != NotificationManager.IMPORTANCE_NONE && !channelGroupBlocked,
        overlayGranted = Settings.canDrawOverlays(this),
        bluetoothGranted = if (Build.VERSION.SDK_INT >= 31) granted(bluetoothConnectPermissionName()) else null,
        localNetworkGranted = if (Build.VERSION.SDK_INT >= 37 && applicationInfo.targetSdkVersion >= 37)
            hasEchoLocalNetworkAccess() else null,
    )
}

internal fun Context.openEchoPermissionSettings(target: SettingsPermissionTarget) {
    val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
    val intent = when (target) {
        SettingsPermissionTarget.App -> appDetails
        SettingsPermissionTarget.Notifications -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        SettingsPermissionTarget.Overlay -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
    }
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: android.content.ActivityNotFoundException) {
        try {
            startActivity(appDetails.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(this, SettingsR.string.permissions_settings_unavailable, Toast.LENGTH_LONG).show()
        }
    }
}
