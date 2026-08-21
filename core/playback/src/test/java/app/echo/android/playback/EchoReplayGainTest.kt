package app.echo.android.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class EchoReplayGainTest {
    @Test
    fun positiveGainUsesEnhancerInsteadOfClampedPlayerVolume() {
        val output = echoReplayGainOutput(
            enabled = true,
            preampDb = 6f,
            trackGainDb = 0f,
        )
        val legacyVolume = 10.0.pow(6.0 / 20.0).toFloat().coerceIn(0.25f, 1.4f)

        assertEquals(1f, output.playerVolume, 0.001f)
        assertTrue(output.enhancerGainMb > 0)
        assertTrue(output.playerVolume != legacyVolume)
        assertEquals(1.4f, legacyVolume, 0.001f)
    }

    @Test
    fun disabledGainLeavesUnityVolumeWithoutEnhancer() {
        val output = echoReplayGainOutput(
            enabled = false,
            preampDb = 6f,
            trackGainDb = 3f,
        )
        assertEquals(1f, output.playerVolume, 0.001f)
        assertEquals(0, output.enhancerGainMb)
    }

    @Test
    fun boostAboveUnityDoesNotWriteClampedPlayerVolume() {
        val output = echoReplayGainOutput(
            enabled = true,
            preampDb = 6f,
            trackGainDb = 6f,
        )
        val clampedLinear = 10.0.pow(12.0 / 20.0).toFloat().coerceIn(0.25f, 1.4f)
        assertEquals(1f, output.playerVolume, 0.001f)
        assertEquals(1_200, output.enhancerGainMb)
        assertEquals(1.4f, clampedLinear, 0.001f)
        assertTrue(output.playerVolume <= 1f)
        assertTrue(output.playerVolume != clampedLinear)
    }

    @Test
    fun mediaItemChangeKeepsPreviousGainUntilTagIsCached() {
        val previous = -6.5f
        val cached = mapOf("known" to -3f)
        assertEquals(
            previous,
            replayGainAfterMediaItemChange(
                mediaId = "next",
                cachedGains = cached,
                previousGainDb = previous,
            ),
        )
        assertEquals(
            -3f,
            replayGainAfterMediaItemChange(
                mediaId = "known",
                cachedGains = cached,
                previousGainDb = previous,
            ),
        )
        val knownZero = mapOf("known-zero" to null)
        assertEquals(
            null,
            replayGainAfterMediaItemChange(
                mediaId = "known-zero",
                cachedGains = knownZero,
                previousGainDb = previous,
            ),
        )
    }

    @Test
    fun failedReplayGainReadIsNotCachedAsNoTag() {
        val missingStream = replayGainReadOutcome(streamOpened = false, parseResult = Result.success(null))
        val ioFailure = replayGainReadOutcome(
            streamOpened = true,
            parseResult = Result.failure(IllegalStateException("open failed")),
        )
        val parsedMissingTag = replayGainReadOutcome(streamOpened = true, parseResult = Result.success(null))
        val parsedGain = replayGainReadOutcome(streamOpened = true, parseResult = Result.success(-7f))

        assertFalse(shouldCacheReplayGainRead(missingStream))
        assertFalse(shouldCacheReplayGainRead(ioFailure))
        assertTrue(shouldCacheReplayGainRead(parsedMissingTag))
        assertTrue(shouldCacheReplayGainRead(parsedGain))
        assertEquals(ReplayGainReadOutcome.Parsed(-7f), parsedGain)
        assertEquals(ReplayGainReadOutcome.Failed, missingStream)
    }

    @Test
    fun usbMuteBlocksReplayGainVolumeWrites() {
        assertFalse(shouldApplyReplayGainPlayerVolume(usbMuteInProgress = true))
        assertTrue(shouldApplyReplayGainPlayerVolume(usbMuteInProgress = false))
        val attenuated = echoReplayGainOutput(enabled = true, preampDb = 0f, trackGainDb = -6f)
        assertTrue(attenuated.playerVolume < 1f)
        assertTrue(attenuated.playerVolume > 0f)
    }
}
