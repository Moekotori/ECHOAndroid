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
    fun enhancerMillibelsConvertToExclusiveMakeupGain() {
        assertEquals(1f, echoReplayGainMakeupLinear(0), 0.001f)
        assertEquals(1f, echoReplayGainMakeupLinear(-100), 0.001f)
        assertEquals(10.0.pow(6.0 / 20.0).toFloat(), echoReplayGainMakeupLinear(600), 0.001f)
        assertEquals(10.0.pow(12.0 / 20.0).toFloat(), echoReplayGainMakeupLinear(1_200), 0.001f)
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
    fun remoteHttpReplayGainWaitsForMatchingCredentials() {
        assertEquals(
            ReplayGainStreamKind.RemoteHttp,
            replayGainStreamKind("https://dav.example/music/a.flac"),
        )
        assertEquals(
            ReplayGainStreamKind.LocalContent,
            replayGainStreamKind("content://media/external/audio/media/1"),
        )
        assertEquals(
            ReplayGainStreamKind.LocalFile,
            replayGainStreamKind("file:///storage/emulated/0/Music/a.flac"),
        )
        assertTrue(
            canOpenReplayGainStream(
                uri = "content://media/external/audio/media/1",
                webDavAuthReadyForUri = false,
                subsonicAuthReadyForUri = false,
            ),
        )
        assertFalse(
            canOpenReplayGainStream(
                uri = "https://dav.example/music/a.flac",
                webDavAuthReadyForUri = false,
                subsonicAuthReadyForUri = true,
            ),
        )
        assertTrue(
            canOpenReplayGainStream(
                uri = "https://dav.example/music/a.flac",
                webDavAuthReadyForUri = true,
                subsonicAuthReadyForUri = false,
            ),
        )
        assertFalse(
            canOpenReplayGainStream(
                uri = "https://navidrome.example/rest/stream.view?id=s1",
                webDavAuthReadyForUri = true,
                subsonicAuthReadyForUri = false,
            ),
        )
        assertTrue(
            canOpenReplayGainStream(
                uri = "https://navidrome.example/rest/stream.view?id=s1",
                webDavAuthReadyForUri = false,
                subsonicAuthReadyForUri = true,
            ),
        )
        val missingRemote = replayGainReadOutcome(streamOpened = false, parseResult = Result.success(null))
        assertFalse(shouldCacheReplayGainRead(missingRemote))
    }

    @Test
    fun remoteReplayGainReadIsCapped() {
        val source = ByteArray(64) { 1 }
        LimitedInputStream(source.inputStream(), maxBytes = 8).use { limited ->
            val buffer = ByteArray(32)
            val read = limited.read(buffer)
            assertEquals(8, read)
            assertEquals(-1, limited.read())
        }
    }

    @Test
    fun exclusiveMakeupConvertsEnhancerMillibelsToLinearGain() {
        val boosted = echoReplayGainOutput(enabled = true, preampDb = 6f, trackGainDb = 0f)
        assertEquals(1f, boosted.playerVolume, 0.001f)
        assertTrue(boosted.enhancerGainMb > 0)
        val makeup = echoReplayGainMakeupLinear(boosted.enhancerGainMb)
        assertTrue(makeup > 1f)
        assertEquals(1f, echoReplayGainMakeupLinear(0), 0.001f)
        assertEquals(1f, echoReplayGainMakeupLinear(-100), 0.001f)
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
