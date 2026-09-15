package app.echo.android.model.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoPlatformCapabilitiesTest {
    @Test
    fun android8KeepsBaselinePlaybackAndLegacyStorage() {
        val caps = EchoPlatformCapabilities.fromSdk(26)
        assertEquals(26, caps.sdkInt)
        assertTrue(caps.legacyWriteExternalStorage)
        assertFalse(caps.dynamicColor)
        assertFalse(caps.renderEffectBlur)
        assertFalse(caps.hapticPrimitives)
        assertFalse(caps.mediaStoreRelativePath)
        assertFalse(caps.mediaStoreAlbumArtistColumn)
        assertFalse(caps.mediaStoreSampleRateColumn)
        assertFalse(caps.notificationRuntimePermission)
        assertFalse(caps.granularMediaAudioPermission)
        assertFalse(caps.systemAppLocales)
        assertFalse(caps.usbSystemMixerBitPerfect)
        assertFalse(caps.foregroundServiceType)
        assertFalse(caps.explicitPendingIntentMutability)
        assertFalse(caps.packageSigningInfo)
    }

    @Test
    fun pieAddsSigningInfoAndDropsNothingElseFromOreo() {
        val oreo = EchoPlatformCapabilities.fromSdk(26)
        val pie = EchoPlatformCapabilities.fromSdk(28)
        assertTrue(pie.packageSigningInfo)
        assertTrue(pie.audioDeviceAddress)
        assertTrue(pie.legacyWriteExternalStorage)
        assertEquals(oreo.mediaStoreRelativePath, pie.mediaStoreRelativePath)
        assertEquals(oreo.dynamicColor, pie.dynamicColor)
    }

    @Test
    fun qStartsScopedMediaStoreAndForegroundServiceType() {
        val pie = EchoPlatformCapabilities.fromSdk(28)
        val q = EchoPlatformCapabilities.fromSdk(29)
        assertFalse(pie.mediaStoreRelativePath)
        assertTrue(q.mediaStoreRelativePath)
        assertTrue(q.foregroundServiceType)
        assertFalse(q.legacyWriteExternalStorage)
        assertFalse(q.mediaStoreAlbumArtistColumn)
    }

    @Test
    fun android11AddsAlbumArtistGenreAndMediaStoreWriteRequest() {
        val caps = EchoPlatformCapabilities.fromSdk(30)
        assertTrue(caps.mediaStoreAlbumArtistColumn)
        assertTrue(caps.mediaStoreGenreColumn)
        assertTrue(caps.mediaStoreCreateWriteRequest)
        assertTrue(caps.activityDisplay)
        assertFalse(caps.dynamicColor)
        assertFalse(caps.mediaStoreSampleRateColumn)
    }

    @Test
    fun android12AddsAppearanceHapticsAndSampleRateColumn() {
        val caps = EchoPlatformCapabilities.fromSdk(31)
        assertTrue(caps.dynamicColor)
        assertTrue(caps.renderEffectBlur)
        assertTrue(caps.hapticPrimitives)
        assertTrue(caps.bluetoothConnectRuntimePermission)
        assertTrue(caps.mediaStoreSampleRateColumn)
        assertTrue(caps.mediaMetadataSampleRate)
        assertTrue(caps.explicitPendingIntentMutability)
        assertFalse(caps.notificationRuntimePermission)
        assertFalse(caps.systemAppLocales)
        assertFalse(caps.usbSystemMixerBitPerfect)
    }

    @Test
    fun android13AddsNotificationLocaleAndTypedExtras() {
        val caps = EchoPlatformCapabilities.fromSdk(33)
        assertTrue(caps.notificationRuntimePermission)
        assertTrue(caps.granularMediaAudioPermission)
        assertTrue(caps.systemAppLocales)
        assertTrue(caps.typedParcelableExtras)
        assertTrue(caps.audioDevicesForAttributes)
        assertFalse(caps.usbSystemMixerBitPerfect)
        assertFalse(caps.nsdHostAddresses)
    }

    @Test
    fun android14AddsUsbMixerBitPerfectAndNsdHosts() {
        val caps = EchoPlatformCapabilities.fromSdk(34)
        assertTrue(caps.usbSystemMixerBitPerfect)
        assertTrue(caps.nsdHostAddresses)
        assertTrue(caps.dynamicColor)
        assertTrue(caps.systemAppLocales)
        assertFalse(caps.legacyWriteExternalStorage)
    }

    @Test
    fun modernFlagsStayOnOnceEnabled() {
        for (sdk in EchoPlatformCapabilities.MinSdk..36) {
            val current = EchoPlatformCapabilities.fromSdk(sdk)
            val next = EchoPlatformCapabilities.fromSdk(sdk + 1)
            if (current.dynamicColor) assertTrue(next.dynamicColor)
            if (current.renderEffectBlur) assertTrue(next.renderEffectBlur)
            if (current.notificationRuntimePermission) assertTrue(next.notificationRuntimePermission)
            if (current.usbSystemMixerBitPerfect) assertTrue(next.usbSystemMixerBitPerfect)
            if (current.mediaStoreRelativePath) assertTrue(next.mediaStoreRelativePath)
            if (current.systemAppLocales) assertTrue(next.systemAppLocales)
            if (current.foregroundServiceType) assertTrue(next.foregroundServiceType)
            if (!current.legacyWriteExternalStorage) assertFalse(next.legacyWriteExternalStorage)
        }
    }
}
