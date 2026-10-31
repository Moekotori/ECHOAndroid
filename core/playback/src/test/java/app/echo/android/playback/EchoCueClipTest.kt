package app.echo.android.playback

import org.junit.Assert.*
import org.junit.Test
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi

@UnstableApi
class EchoCueClipTest {
    private val first = EchoCueClip("file#cue:1", "content://audio/file", 0, 93_000)
    private val next = first.copy(id = "file#cue:2", startUs = 93_000, endUs = 250_000)

    @Test fun onlyContiguousClipsFromTheSameSourceContinue() {
        assertTrue(first.continuesInto(next))
        assertTrue(first.continuesInto(next.copy(endUs = 0)))
        assertFalse(first.continuesInto(null))
        assertFalse(first.continuesInto(next.copy(startUs = 94_000)))
        assertFalse(first.continuesInto(next.copy(startUs = 92_000)))
        assertFalse(first.continuesInto(next.copy(uri = "content://audio/other")))
        assertFalse(first.continuesInto(next.copy(id = "other#cue:2")))
        assertFalse(first.copy(endUs = 0).continuesInto(next))
        assertFalse(first.continuesInto(first))
    }

    @Test fun frameBoundariesRoundUpAndStayBounded() {
        assertEquals(0, cueFramesBefore(-1, 16_000, 1600))
        assertEquals(1488, cueFramesBefore(93_000, 16_000, 1600))
        assertEquals(1, cueFramesBefore(1, 44_100, 4096))
        assertEquals(4096, cueFramesBefore(Long.MAX_VALUE, 44_100, 4096))
    }

    @Test fun decoderRestoresOriginalFormatAndKeepsTheClippedDelayPadding() {
        val original = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_FLAC).setSampleRate(44_100)
            .setChannelCount(2).setEncoderDelay(100).setEncoderPadding(200).build()
        val marked = original.buildUpon().setSampleMimeType("audio/x-echo-cue")
            .setEncoderDelay(0).setEncoderPadding(0).setCustomData(EchoCueFormat(first, original)).build()
        val restored = marked.cueInputFormat()
        assertEquals(MimeTypes.AUDIO_FLAC, restored.sampleMimeType)
        assertEquals(first, restored.customData)
        assertEquals(0, restored.encoderDelay)
        assertEquals(0, restored.encoderPadding)
        assertEquals(44_100, restored.sampleRate)
    }
}
