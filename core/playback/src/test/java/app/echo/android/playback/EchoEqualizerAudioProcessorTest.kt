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
    @Test fun realPcmShelfAndPreampMatchReferenceGain() {
        var processingRate: Int? = null
        val processor = EchoEqualizerAudioProcessor { processingRate = it }
        processor.setRuntime(EchoEqualizerRuntime(true, -6f,
            listOf(OpraEqBand(EchoEqFilterType.LowShelf, 100f, 6f, 0.7f, null))))
        processor.configure(AudioProcessor.AudioFormat(48000, 1, C.ENCODING_PCM_FLOAT))
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        val input = java.nio.ByteBuffer.allocateDirect(48000 * 4).order(java.nio.ByteOrder.nativeOrder())
        for (i in 0 until 48000) input.putFloat((0.1 * kotlin.math.sin(2 * Math.PI * 50 * i / 48000)).toFloat())
        input.flip(); processor.queueInput(input)
        val output = processor.output.order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer()
        var power = 0.0
        for (i in 0 until 48000) {
            val sample = output.get().toDouble()
            if (i >= 24000) power += sample * sample
        }
        val gainDb = 20 * kotlin.math.log10(kotlin.math.sqrt(power / 24000) / (0.1 / kotlin.math.sqrt(2.0)))
        assertEquals(-0.401404, gainDb, 0.04)
        assertEquals(48000, processingRate)
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        assertEquals(null, processingRate)
    }

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
