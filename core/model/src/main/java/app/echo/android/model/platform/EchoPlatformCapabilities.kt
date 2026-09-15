package app.echo.android.model.platform

/**
 * What this Android release can do. Built from [sdkInt] only.
 * UI and policy read [fromSdk]. Framework calls must still branch on
 * `Build.VERSION.SDK_INT >= XxxSdk` so NewApi lint can see the guard.
 */
data class EchoPlatformCapabilities(
    val sdkInt: Int,
    val dynamicColor: Boolean,
    val renderEffectBlur: Boolean,
    val hapticPrimitives: Boolean,
    val notificationRuntimePermission: Boolean,
    val granularMediaAudioPermission: Boolean,
    val bluetoothConnectRuntimePermission: Boolean,
    val legacyWriteExternalStorage: Boolean,
    val mediaStoreRelativePath: Boolean,
    val mediaStoreGenreColumn: Boolean,
    val mediaStoreAlbumArtistColumn: Boolean,
    val mediaStoreSampleRateColumn: Boolean,
    val mediaStoreCreateWriteRequest: Boolean,
    val mediaMetadataSampleRate: Boolean,
    val systemAppLocales: Boolean,
    val usbSystemMixerBitPerfect: Boolean,
    val audioDevicesForAttributes: Boolean,
    val audioDeviceAddress: Boolean,
    val nsdHostAddresses: Boolean,
    val packageSigningInfo: Boolean,
    val foregroundServiceType: Boolean,
    val typedParcelableExtras: Boolean,
    val explicitPendingIntentMutability: Boolean,
    val activityDisplay: Boolean,
) {
    companion object {
        const val MinSdk = 26
        const val PackageSigningInfoSdk = 28
        const val AudioDeviceAddressSdk = 28
        const val LegacyWriteExternalStorageMaxSdk = 28
        const val ForegroundServiceTypeSdk = 29
        const val MediaStoreRelativePathSdk = 29
        const val MediaStoreGenreColumnSdk = 30
        const val MediaStoreAlbumArtistColumnSdk = 30
        const val MediaStoreCreateWriteRequestSdk = 30
        const val ActivityDisplaySdk = 30
        const val DynamicColorSdk = 31
        const val RenderEffectBlurSdk = 31
        const val HapticPrimitivesSdk = 31
        const val BluetoothConnectPermissionSdk = 31
        const val MediaStoreSampleRateColumnSdk = 31
        const val ExplicitPendingIntentMutabilitySdk = 31
        const val MediaMetadataSampleRateSdk = 31
        const val NotificationRuntimePermissionSdk = 33
        const val GranularMediaAudioPermissionSdk = 33
        const val SystemAppLocalesSdk = 33
        const val AudioDevicesForAttributesSdk = 33
        const val TypedParcelableExtrasSdk = 33
        const val UsbSystemMixerBitPerfectSdk = 34
        const val NsdHostAddressesSdk = 34

        fun fromSdk(sdkInt: Int): EchoPlatformCapabilities =
            EchoPlatformCapabilities(
                sdkInt = sdkInt,
                dynamicColor = sdkInt >= DynamicColorSdk,
                renderEffectBlur = sdkInt >= RenderEffectBlurSdk,
                hapticPrimitives = sdkInt >= HapticPrimitivesSdk,
                notificationRuntimePermission = sdkInt >= NotificationRuntimePermissionSdk,
                granularMediaAudioPermission = sdkInt >= GranularMediaAudioPermissionSdk,
                bluetoothConnectRuntimePermission = sdkInt >= BluetoothConnectPermissionSdk,
                legacyWriteExternalStorage = sdkInt <= LegacyWriteExternalStorageMaxSdk,
                mediaStoreRelativePath = sdkInt >= MediaStoreRelativePathSdk,
                mediaStoreGenreColumn = sdkInt >= MediaStoreGenreColumnSdk,
                mediaStoreAlbumArtistColumn = sdkInt >= MediaStoreAlbumArtistColumnSdk,
                mediaStoreSampleRateColumn = sdkInt >= MediaStoreSampleRateColumnSdk,
                mediaStoreCreateWriteRequest = sdkInt >= MediaStoreCreateWriteRequestSdk,
                mediaMetadataSampleRate = sdkInt >= MediaMetadataSampleRateSdk,
                systemAppLocales = sdkInt >= SystemAppLocalesSdk,
                usbSystemMixerBitPerfect = sdkInt >= UsbSystemMixerBitPerfectSdk,
                audioDevicesForAttributes = sdkInt >= AudioDevicesForAttributesSdk,
                audioDeviceAddress = sdkInt >= AudioDeviceAddressSdk,
                nsdHostAddresses = sdkInt >= NsdHostAddressesSdk,
                packageSigningInfo = sdkInt >= PackageSigningInfoSdk,
                foregroundServiceType = sdkInt >= ForegroundServiceTypeSdk,
                typedParcelableExtras = sdkInt >= TypedParcelableExtrasSdk,
                explicitPendingIntentMutability = sdkInt >= ExplicitPendingIntentMutabilitySdk,
                activityDisplay = sdkInt >= ActivityDisplaySdk,
            )
    }
}
