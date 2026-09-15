package app.echo.android

import android.Manifest
import android.os.Build
import app.echo.android.model.platform.EchoPlatformCapabilities

private fun platformCapabilities(): EchoPlatformCapabilities =
    EchoPlatformCapabilities.fromSdk(Build.VERSION.SDK_INT)

fun audioPermissionName(): String =
    if (platformCapabilities().granularMediaAudioPermission) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

fun writeStoragePermissionName(): String? =
    if (platformCapabilities().legacyWriteExternalStorage) {
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    } else {
        null
    }

fun notificationPermissionName(): String? =
    if (platformCapabilities().notificationRuntimePermission) {
        Manifest.permission.POST_NOTIFICATIONS
    } else {
        null
    }

fun bluetoothConnectPermissionName(): String =
    if (platformCapabilities().bluetoothConnectRuntimePermission) {
        Manifest.permission.BLUETOOTH_CONNECT
    } else {
        Manifest.permission.BLUETOOTH
    }

const val ECHO_PERMISSION_DIALOG_SHOWN_KEY = "echo_permission_dialog_shown_v1"
