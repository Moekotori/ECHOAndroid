package app.echo.android.playback

import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

@UnstableApi
class EchoAlacCodecSelectorTest {
    private val affected = decoder("c2.qti.alac.sw.decoder")
    private val alternative = decoder("c2.android.alac.decoder")

    @Test
    fun unencryptedAlacCanUseAnAlternativeOrFallThroughToFfmpeg() {
        val mixed = MediaCodecSelector { _, _, _ -> listOf(affected, alternative) }
        val qualcommOnly = MediaCodecSelector { _, _, _ -> listOf(affected) }

        assertEquals(listOf(alternative), mixed.withAlacWorkaround { true }
            .getDecoderInfos(MimeTypes.AUDIO_ALAC, false, false))
        assertEquals(emptyList<MediaCodecInfo>(), qualcommOnly.withAlacWorkaround { true }
            .getDecoderInfos(MimeTypes.AUDIO_ALAC, false, false))
    }

    @Test
    fun leavesOtherQueriesAndDevicesWithoutFfmpegAlone() {
        val platformDecoders = listOf(affected, alternative)
        val platform = MediaCodecSelector { _, _, _ -> platformDecoders }
        val selector = platform.withAlacWorkaround { error("FFmpeg must not be queried here") }

        assertSame(platformDecoders, selector.getDecoderInfos(MimeTypes.AUDIO_FLAC, false, false))
        assertSame(platformDecoders, selector.getDecoderInfos(MimeTypes.AUDIO_ALAC, true, false))
        assertSame(platformDecoders, selector.getDecoderInfos(MimeTypes.AUDIO_ALAC, false, true))
        assertSame(platformDecoders, platform.withAlacWorkaround { false }
            .getDecoderInfos(MimeTypes.AUDIO_ALAC, false, false))
    }

    private fun decoder(name: String): MediaCodecInfo = MediaCodecInfo.newInstance(
        name, MimeTypes.AUDIO_ALAC, MimeTypes.AUDIO_ALAC, null, false, true, true, false, false,
    )
}
