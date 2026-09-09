package app.echo.android.playback

import app.echo.android.model.library.LibrarySource
import app.echo.android.model.playback.EchoTrackTransitionOptions
import app.echo.android.model.settings.EchoEffectivePerformanceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class EchoSmartTransitionPolicyTest {
    @Test
    fun defaultOffAndLightweightAndUsbNeverMix() {
        assertEquals(
            EchoSmartTransitionPolicy.BypassReason.Disabled,
            EchoSmartTransitionPolicy.bypassReason(candidate()),
        )
        assertEquals(
            EchoSmartTransitionPolicy.BypassReason.Lightweight,
            EchoSmartTransitionPolicy.bypassReason(
                candidate(options = enabled(), mode = EchoEffectivePerformanceMode.Lightweight),
            ),
        )
        assertEquals(
            EchoSmartTransitionPolicy.BypassReason.UsbExclusive,
            EchoSmartTransitionPolicy.bypassReason(candidate(options = enabled(), usbExclusive = true)),
        )
        assertEquals(
            EchoSmartTransitionPolicy.BypassReason.BitPerfect,
            EchoSmartTransitionPolicy.bypassReason(candidate(options = enabled(), usbBitPerfect = true)),
        )
    }

    @Test
    fun remoteLiveSpeedRepeatAndShortTrackBypass() {
        assertEquals(
            EchoSmartTransitionPolicy.BypassReason.Remote,
            EchoSmartTransitionPolicy.bypassReason(candidate(options = enabled(), nextLocal = false)),
        )
        assertEquals(
            EchoSmartTransitionPolicy.BypassReason.Live,
            EchoSmartTransitionPolicy.bypassReason(candidate(options = enabled(), live = true)),
        )
        assertEquals(
            EchoSmartTransitionPolicy.BypassReason.PlaybackSpeed,
            EchoSmartTransitionPolicy.bypassReason(candidate(options = enabled(), speed = 1.25f)),
        )
        assertEquals(
            EchoSmartTransitionPolicy.BypassReason.RepeatOne,
            EchoSmartTransitionPolicy.bypassReason(candidate(options = enabled(), repeatOne = true)),
        )
        assertEquals(
            EchoSmartTransitionPolicy.BypassReason.ShortTrack,
            EchoSmartTransitionPolicy.bypassReason(
                candidate(options = enabled(), currentDurationMs = 3_000, currentPositionMs = 100),
            ),
        )
        assertEquals(
            EchoSmartTransitionPolicy.BypassReason.SampleRateMismatch,
            EchoSmartTransitionPolicy.bypassReason(
                candidate(options = enabled(), currentSampleRateHz = 44_100, nextSampleRateHz = 48_000),
            ),
        )
    }

    @Test
    fun albumAdjacentConsecutiveTracksBypass() {
        assertEquals(
            EchoSmartTransitionPolicy.BypassReason.AlbumAdjacent,
            EchoSmartTransitionPolicy.bypassReason(
                candidate(
                    options = enabled(),
                    currentAlbum = "Album",
                    nextAlbum = "Album",
                    currentTrackNumber = 3,
                    nextTrackNumber = 4,
                ),
            ),
        )
        assertNull(
            EchoSmartTransitionPolicy.bypassReason(
                candidate(
                    options = enabled(),
                    currentAlbum = "Album",
                    nextAlbum = "Album",
                    currentTrackNumber = 3,
                    nextTrackNumber = 5,
                ),
            ),
        )
    }

    @Test
    fun eligibleLocalPairHasNoBypass() {
        assertNull(EchoSmartTransitionPolicy.bypassReason(candidate(options = enabled())))
        assertNull(
            EchoSmartTransitionPolicy.bypassReason(
                candidate(options = enabled(), currentSampleRateHz = null, nextSampleRateHz = null),
            ),
        )
    }

    @Test
    fun holdFramesWaitsUntilTheOverlapWindow() {
        assertEquals(0, EchoSmartTransitionPolicy.holdFrames(4_000, 4_000, 48_000))
        assertEquals(48_000, EchoSmartTransitionPolicy.holdFrames(5_000, 4_000, 48_000))
        assertEquals(0, EchoSmartTransitionPolicy.holdFrames(3_000, 4_000, 48_000))
    }

    @Test
    fun overlapClampsByEnergyAndRemainingTime() {
        val dense = EchoSmartTransitionPolicy.overlapMs(0.8f, 0.8f, 20_000, 180_000, 4_000)
        val quiet = EchoSmartTransitionPolicy.overlapMs(0.2f, 0.2f, 20_000, 180_000, 4_000)
        assertEquals(1_500, dense)
        assertEquals(3_200, quiet)
        assertNull(EchoSmartTransitionPolicy.overlapMs(0.5f, 0.5f, 1_200, 180_000, 4_000))
        val capped = EchoSmartTransitionPolicy.overlapMs(0.2f, 0.2f, 20_000, 180_000, 2_000)
        assertEquals(2_000, capped)
    }

    @Test
    fun localUriDetection() {
        assertTrue(EchoSmartTransitionPolicy.isLocalUri("content://media/1", LibrarySource.MediaStore.id))
        assertTrue(EchoSmartTransitionPolicy.isLocalUri("file:///sdcard/a.flac", LibrarySource.Saf.id))
        assertFalse(EchoSmartTransitionPolicy.isLocalUri("https://host/echo-link/media/x", null))
        assertFalse(EchoSmartTransitionPolicy.isLocalUri("http://nas/track", LibrarySource.Subsonic.id))
        assertFalse(EchoSmartTransitionPolicy.isLocalUri("echo-link://track/abc", null))
    }

    @Test
    fun equalPowerIsConstantPower() {
        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { progress ->
            val out = EchoSmartTransitionPolicy.equalPowerOut(progress)
            val incoming = EchoSmartTransitionPolicy.equalPowerIn(progress)
            assertEquals(1f, out * out + incoming * incoming, 0.001f)
        }
        assertEquals(1f, EchoSmartTransitionPolicy.equalPowerOut(0f), 0.0001f)
        assertEquals(0f, EchoSmartTransitionPolicy.equalPowerOut(1f), 0.0001f)
        assertTrue(abs(EchoSmartTransitionPolicy.equalPowerIn(0.5f) - EchoSmartTransitionPolicy.equalPowerOut(0.5f)) < 0.001f)
    }

    private fun enabled() = EchoTrackTransitionOptions(smartEnabled = true)

    private fun candidate(
        options: EchoTrackTransitionOptions = EchoTrackTransitionOptions(),
        mode: EchoEffectivePerformanceMode = EchoEffectivePerformanceMode.Balanced,
        usbExclusive: Boolean = false,
        usbBitPerfect: Boolean = false,
        live: Boolean = false,
        currentDurationMs: Long = 180_000,
        currentPositionMs: Long = 150_000,
        nextDurationMs: Long = 200_000,
        speed: Float = 1f,
        skipSilence: Boolean = false,
        repeatOne: Boolean = false,
        currentLocal: Boolean = true,
        nextLocal: Boolean = true,
        currentAlbum: String? = "A",
        nextAlbum: String? = "B",
        currentTrackNumber: Int? = 1,
        nextTrackNumber: Int? = 1,
        currentSampleRateHz: Int? = 48_000,
        nextSampleRateHz: Int? = 48_000,
    ) = EchoSmartTransitionPolicy.Candidate(
        options = options,
        performanceMode = mode,
        usbExclusive = usbExclusive,
        usbBitPerfect = usbBitPerfect,
        live = live,
        currentDurationMs = currentDurationMs,
        currentPositionMs = currentPositionMs,
        nextDurationMs = nextDurationMs,
        playbackSpeed = speed,
        skipSilence = skipSilence,
        repeatOne = repeatOne,
        sleepEndOfTrack = false,
        currentLocal = currentLocal,
        nextLocal = nextLocal,
        currentAlbum = currentAlbum,
        nextAlbum = nextAlbum,
        currentDisc = 1,
        nextDisc = 1,
        currentTrackNumber = currentTrackNumber,
        nextTrackNumber = nextTrackNumber,
        currentSampleRateHz = currentSampleRateHz,
        nextSampleRateHz = nextSampleRateHz,
        outputSampleRateHz = currentSampleRateHz,
        outputChannelCount = 2,
        hasNext = true,
        isPlaying = true,
    )
}
