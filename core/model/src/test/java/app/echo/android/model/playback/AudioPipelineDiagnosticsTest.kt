package app.echo.android.model.playback

import org.junit.Assert.*
import org.junit.Test

class AudioPipelineDiagnosticsTest {
    private val pcm = EchoPcmReadout(48_000, 24, 2)
    @Test fun conversionDistinguishesWideningReductionAndFloat() {
        val widened = EchoUsbConversionDiagnostics(pcm, pcm.copy(bitDepth = 32))
        assertEquals(EchoPcmPrecisionChange.IntegerWidening, widened.precision)
        assertFalse(widened.resampled)
        assertEquals(EchoPcmPrecisionChange.IntegerReduction, EchoUsbConversionDiagnostics(pcm, pcm.copy(bitDepth = 16)).precision)
        assertEquals(EchoPcmPrecisionChange.FloatingPointConversion,
            EchoUsbConversionDiagnostics(pcm.copy(bitDepth = 32, floatingPoint = true), pcm.copy(bitDepth = 32)).precision)
        val changed = widened.copy(output = pcm.copy(sampleRateHz = 96_000, channelCount = 1))
        assertTrue(changed.resampled); assertTrue(changed.channelsChanged)
        assertEquals(EchoPcmPrecisionChange.Unknown, widened.copy(input = pcm.copy(sampleRateHz = 0)).precision)
    }
    @Test fun effectsRequireActualSystemSessionAndCannotUseUsbOrOffload() {
        val d = EchoPlaybackDiagnostics(decoder = EchoDecoderDiagnostics(audioEnabled = true, audioSessionId = 42, systemOutput = pcm))
        assertEquals(EchoSystemEffectsAvailability.Available, d.systemEffectsAvailability())
        assertEquals(EchoSystemEffectsAvailability.RemotePlayback, d.systemEffectsAvailability(localPlayback = false))
        assertEquals(EchoSystemEffectsAvailability.DedicatedUsb, d.copy(usbExclusiveEnabled = true).systemEffectsAvailability())
        assertEquals(EchoSystemEffectsAvailability.DedicatedUsb, d.copy(usbBitPerfectEnabled = true).systemEffectsAvailability())
        assertEquals(EchoSystemEffectsAvailability.NoSession, d.copy(decoder = d.decoder.copy(audioEnabled = false)).systemEffectsAvailability())
        assertEquals(EchoSystemEffectsAvailability.NoSession, d.copy(decoder = d.decoder.copy(audioSessionId = 0)).systemEffectsAvailability())
        assertEquals(EchoSystemEffectsAvailability.NoSystemOutput, d.copy(decoder = d.decoder.copy(systemOutput = null)).systemEffectsAvailability())
        assertEquals(EchoSystemEffectsAvailability.Offloaded, d.copy(decoder = d.decoder.copy(systemOffloaded = true, systemOutput = null)).systemEffectsAvailability())
    }
    @Test fun decoderNamesIdentifyImplementationWithoutClaimingHardwareAcceleration() {
        assertEquals(EchoDecoderImplementation.Platform, EchoDecoderDiagnostics.implementation("c2.android.aac.decoder"))
        assertEquals(EchoDecoderImplementation.Platform, EchoDecoderDiagnostics.implementation("OMX.vendor.decoder"))
        assertEquals(EchoDecoderImplementation.Ffmpeg, EchoDecoderDiagnostics.implementation("ffmpegFlac"))
        assertEquals(EchoDecoderImplementation.Other, EchoDecoderDiagnostics.implementation("future.decoder"))
        assertEquals(EchoDecoderImplementation.PcmCopy, EchoDecoderDiagnostics.implementation("EchoRawPcm"))
    }
}
