package app.echo.android.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EchoAudioFormatReadoutTest {
    @Test
    fun dsd64LibraryRateReportsFamilyAnd88200Pcm() {
        val readout = echoAudioFormatReadout(
            mimeType = EchoDsdMime.MsbfPlanar,
            mediaUri = "content://media/dsd/song.dsf",
            formatSampleRateHz = 352_800,
            sourceSampleRateHz = 2_822_400,
            bitDepth = 16,
            channelCount = 2,
            bitrate = null,
        )
        assertEquals("DSD", readout.codec)
        assertEquals(2_822_400, readout.sampleRateHz)
        assertEquals(88_200, readout.decodedSampleRateHz)
        assertEquals(16, readout.bitDepth)
        assertEquals(2, readout.channelCount)
    }

    @Test
    fun dsd128DecoderPcmMapsTo176400() {
        val readout = echoAudioFormatReadout(
            mimeType = "audio/x-dsf",
            formatSampleRateHz = 705_600,
            sourceSampleRateHz = 5_644_800,
            bitDepth = 16,
            channelCount = 2,
            bitrate = null,
        )
        assertEquals("DSD", readout.codec)
        assertEquals(5_644_800, readout.sampleRateHz)
        assertEquals(176_400, readout.decodedSampleRateHz)
    }

    @Test
    fun dsdWithoutLibraryRateRecoversFamilyFromDecoderPcm() {
        val readout = echoAudioFormatReadout(
            mimeType = EchoDsdMime.Lsbf,
            formatSampleRateHz = 352_800,
            sourceSampleRateHz = null,
            bitDepth = 16,
            channelCount = 2,
            bitrate = null,
        )
        assertEquals("DSD", readout.codec)
        assertEquals(2_822_400, readout.sampleRateHz)
        assertEquals(88_200, readout.decodedSampleRateHz)
    }

    @Test
    fun dsd64DopKeeps176400AndDoesNotCollapseToPcm88200() {
        val readout = echoAudioFormatReadout(
            mimeType = "audio/raw",
            mediaUri = "file:///sdcard/Music/song.dsf",
            formatSampleRateHz = 176_400,
            sourceSampleRateHz = 2_822_400,
            bitDepth = 24,
            channelCount = 2,
            bitrate = null,
        )
        assertEquals("DSD", readout.codec)
        assertEquals(2_822_400, readout.sampleRateHz)
        assertEquals(176_400, readout.decodedSampleRateHz)
        assertEquals(24, readout.bitDepth)
    }

    @Test
    fun ffmpegRemappedPcmStillKeepsDsdSourceWhenLibraryRateIsPresent() {
        val readout = echoAudioFormatReadout(
            mimeType = "audio/raw",
            mediaUri = "file:///sdcard/Music/song.dff",
            formatSampleRateHz = 88_200,
            sourceSampleRateHz = 2_822_400,
            bitDepth = 16,
            channelCount = 2,
            bitrate = null,
        )
        assertEquals("DSD", readout.codec)
        assertEquals(2_822_400, readout.sampleRateHz)
        assertEquals(88_200, readout.decodedSampleRateHz)
    }

    @Test
    fun flacKeepsSourceRateAndOmitsDecodedWhenTheyMatch() {
        val readout = echoAudioFormatReadout(
            mimeType = "audio/flac",
            formatSampleRateHz = 96_000,
            sourceSampleRateHz = 96_000,
            bitDepth = 24,
            channelCount = 2,
            bitrate = 2_304_000,
        )
        assertEquals("FLAC", readout.codec)
        assertEquals(96_000, readout.sampleRateHz)
        assertNull(readout.decodedSampleRateHz)
        assertEquals(24, readout.bitDepth)
        assertEquals(2_304_000, readout.bitrate)
    }
}
