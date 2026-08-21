package app.echo.android.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import app.echo.android.model.playback.EchoEqFilterType
import app.echo.android.model.playback.OpraEqBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@UnstableApi
class EchoEqualizerAudioProcessorTest {
    @Test
    fun configureKeepsPackedTwentyFourBitWhenEqIsActive() {
        val processor = EchoEqualizerAudioProcessor()
        processor.setRuntime(
            EchoEqualizerRuntime(
                enabled = true,
                preampDb = 0f,
                filters = listOf(
                    OpraEqBand(EchoEqFilterType.PeakDip, 1_000f, 3f, 1f, null),
                ),
            ),
        )
        val output = processor.configure(
            AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_24BIT),
        )
        assertEquals(C.ENCODING_PCM_24BIT, output.encoding)
        assertEquals(2, output.channelCount)
        assertTrue(processor.isActive)
    }

    @Test
    fun configureStaysInactiveWhenEqIsOffSoHighResCanPassThrough() {
        val processor = EchoEqualizerAudioProcessor()
        processor.setRuntime(EchoEqualizerRuntime())
        val output = processor.configure(
            AudioProcessor.AudioFormat(96_000, 2, C.ENCODING_PCM_24BIT),
        )
        assertEquals(AudioProcessor.AudioFormat.NOT_SET, output)
        assertTrue(!processor.isActive)
    }
}
