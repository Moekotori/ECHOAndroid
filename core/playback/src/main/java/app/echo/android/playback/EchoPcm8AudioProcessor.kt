package app.echo.android.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/** Widen unsigned WAV PCM before the signed PCM DSP chain. */
@UnstableApi
internal class EchoPcm8AudioProcessor : BaseAudioProcessor() {
    override fun onConfigure(format: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat =
        if (format.encoding == C.ENCODING_PCM_8BIT)
            AudioProcessor.AudioFormat(format.sampleRate, format.channelCount, C.ENCODING_PCM_16BIT)
        else AudioProcessor.AudioFormat.NOT_SET

    override fun queueInput(input: ByteBuffer) {
        val output = replaceOutputBuffer(input.remaining() * 2)
        while (input.hasRemaining()) output.put(0).put((input.get().toInt() - 128).toByte())
        output.flip()
    }
}
