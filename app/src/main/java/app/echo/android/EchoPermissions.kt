package app.echo.android

import android.Manifest
import android.os.Build
import app.echo.android.model.platform.EchoPlatformCapabilities

fun audioPermissionName(): String =
    if (Build.VERSION.SDK_INT >= EchoPlatformCapabilities.GranularMediaAudioPermissionSdk) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

fun writeStoragePermissionName(): String? =
    if (Build.VERSION.SDK_INT <= EchoPlatformCapabilities.LegacyWriteExternalStorageMaxSdk) {
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    } else {
        null
    }

fun notificationPermissionName(): String? =
    if (Build.VERSION.SDK_INT >= EchoPlatformCapabilities.NotificationRuntimePermissionSdk) {
        Manifest.permission.POST_NOTIFICATIONS
    } else {
        null
    }

fun bluetoothConnectPermissionName(): String =
    if (Build.VERSION.SDK_INT >= EchoPlatformCapabilities.BluetoothConnectPermissionSdk) {
        Manifest.permission.BLUETOOTH_CONNECT
    } else {
        Manifest.permission.BLUETOOTH
    }

const val ECHO_PERMISSION_DIALOG_SHOWN_KEY = "echo_permission_dialog_shown_v1"
