package app.echo.android.playback

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import app.echo.android.usbaudio.UsbAudioClassVersion
import app.echo.android.usbaudio.UsbAudioDescriptorInfo
import app.echo.android.usbaudio.UsbAudioDeviceSnapshot
import app.echo.android.usbaudio.UsbAudioStreamingFormat
import app.echo.android.usbaudio.UsbEndpointDirection
import app.echo.android.usbaudio.UsbEndpointTransferType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@UnstableApi
class EchoStrictUsbSourceTest {
    @Test
    fun selectsStrictOnlyForSupportedSourcesIncludingCueTracks() {
        val aac = format(MimeTypes.AUDIO_AAC)
        val flac = format(MimeTypes.AUDIO_FLAC, C.ENCODING_PCM_24BIT)
        val cue = EchoCueClip("album#cue:1", "album", 0, 1_000_000)

        assertFalse(aac.canUseStrictUsb())
        assertFalse(aac.buildUpon().setCustomData(EchoCueFormat(cue, aac)).build().canUseStrictUsb())
        assertTrue(flac.canUseStrictUsb())
        assertTrue(flac.buildUpon().setCustomData(EchoCueFormat(cue, flac)).build().canUseStrictUsb())
        assertTrue(format(MimeTypes.AUDIO_ALAC).canUseStrictUsb())
        assertFalse(format(EchoDsdMime.LsbfPlanar).canUseStrictUsb())
        assertFalse(format(MimeTypes.AUDIO_RAW, C.ENCODING_PCM_32BIT).canUseStrictUsb())
        assertFalse(flac.buildUpon().setChannelCount(6).build().canUseStrictUsb())
    }

    @Test
    fun usesCompatibleOutputWhenDacCannotAcceptSourceOrDopIsDisabled() {
        val usbFormat = UsbAudioStreamingFormat(
            1, 1, UsbAudioClassVersion.Uac2, formatType = 1, pcmIntegerSupported = true,
            channelCount = 2, subslotSize = 3, bitResolution = 24, sampleRates = listOf(48_000, 176_400),
            endpointDirection = UsbEndpointDirection.Out, endpointTransferType = UsbEndpointTransferType.Isochronous,
        )
        val device = UsbAudioDeviceSnapshot(connected = true, vendorId = 1, productId = 2, permissionGranted = true,
            descriptor = UsbAudioDescriptorInfo(streamingFormats = listOf(usbFormat)))
        val flac = format(MimeTypes.AUDIO_FLAC, C.ENCODING_PCM_24BIT)
        assertTrue(flac.buildUpon().setSampleRate(48_000).build().canUseStrictUsb(device))
        assertFalse(flac.buildUpon().setSampleRate(96_000).build().canUseStrictUsb(device))
        val dsd = format(EchoDsdMime.LsbfPlanar).buildUpon()
            .setSampleRate(EchoDsdPcm.decoderPcmRateHz(2_822_400)).build()
        assertFalse(dsd.canUseStrictUsb(device))
        assertTrue(dsd.canUseStrictUsb(device, mapOf("1:2" to 2_822_400)))
        val alac32 = format(MimeTypes.AUDIO_ALAC).buildUpon()
            .setInitializationData(listOf(ByteArray(24).apply { this[5] = 32 })).build()
        assertFalse(alac32.canUseStrictUsb(device))
    }

    private fun format(mime: String, encoding: Int = Format.NO_VALUE): Format =
        Format.Builder().setSampleMimeType(mime).setPcmEncoding(encoding).setChannelCount(2).build()
}
