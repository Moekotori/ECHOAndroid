package app.echo.android.playback

import dev.t1m3.qplayer.audio.core.AudioBuf
import dev.t1m3.qplayer.audio.core.ChannelLayout
import dev.t1m3.qplayer.audio.decode.Decoder
import dev.t1m3.qplayer.audio.decode.TrackSpec
import dev.t1m3.qplayer.audio.mix.AiMix
import dev.t1m3.qplayer.audio.mix.AutoCueTransition
import dev.t1m3.qplayer.audio.mix.AutoCue
import dev.t1m3.qplayer.audio.mix.Crossfade
import dev.t1m3.qplayer.audio.mix.MixStyle
import dev.t1m3.qplayer.audio.mix.MixTuning
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class EchoAutoMixEngineTest {
    @Test fun uriAnalysisIsReusedWithinPreparationAndInvalidatedAfterward() {
        var opened = 0
        val opener = Decoder.Opener { _, _ -> opened++; ToneDecoder(22_050) }
        Decoder.withOpener(opener) {
            val first = AutoCue.scan("content://library/audio/42")
            assertSame(first, AutoCue.scan("content://library/audio/42"))
            assertEquals(1, opened)
        }
        assertNull(Decoder.preparationCache())
        Decoder.withOpener(opener) { AutoCue.scan("content://library/audio/42") }
        assertEquals(2, opened)
    }

    @Test fun suppliedEngineSelectsCuesAndRendersWithinHostBudget() {
        val rate = 22_050
        val tuning = MixTuning.defaults().apply {
            tempoStretch = 0f
            styleLoopEnabled = false
            windowScale = 0.75f
        }
        val base = AiMix(MixStyle.DEFAULT, rate, 2, tuning).apply { setWindowLimit(3L * rate) }
        val engine = AutoCueTransition(base, rate, 2, null, false, tuning)
        val plan = Decoder.withOpener({ _, _ -> ToneDecoder(rate) }) {
            engine.prepare("outgoing", "incoming", rate)
        }
        val frames = engine.windowFrames(rate).toInt()
        assertTrue("window=$frames", frames in rate / 2..3 * rate)
        assertTrue(plan.skipSecs.isFinite() && plan.skipSecs >= 0)
        assertTrue(plan.outroTailSecs.isFinite() && plan.outroTailSecs >= 0)
        assertTrue(plan.skipSecs + frames.toDouble() / rate <= 36.0)
        val pcm = FloatArray(frames * 2) { sin(it * 0.1).toFloat() * 0.3f }
        val mixed = EchoAutoMixRenderer.render(engine, pcm, pcm, frames, 2)
        assertNotNull(mixed)
        assertEquals(frames * 2, mixed!!.size)
        assertTrue(mixed.all { it.isFinite() && it in -1f..1f })
        assertEquals(frames.toLong(), engine.contentFramesFor(frames.toLong()))
    }

    @Test fun rendererKeepsChannelsAndRejectsIncompletePcm() {
        val from = FloatArray(1024) { if (it % 2 == 0) 0.2f else -0.2f }
        val to = FloatArray(1024) { if (it % 2 == 0) 0.4f else -0.4f }
        val mixed = EchoAutoMixRenderer.render(Crossfade(1f), from, to, 512, 2)!!
        assertTrue(mixed.indices.all { if (it % 2 == 0) mixed[it] > 0 else mixed[it] < 0 })
        assertNull(EchoAutoMixRenderer.render(Crossfade(1f), from, to, 513, 2))
        assertNull(EchoAutoMixRenderer.render(Crossfade(1f), floatArrayOf(Float.NaN), floatArrayOf(0f), 1, 1))
    }

    @Test fun rendererChecksCancellationBetweenBlocks() {
        var checks = 0
        val failure = runCatching {
            EchoAutoMixRenderer.render(Crossfade(1f), FloatArray(1024), FloatArray(1024), 1024, 1) {
                if (++checks == 2) throw InterruptedException("cancelled")
            }
        }.exceptionOrNull()
        assertTrue(failure is InterruptedException)
        assertEquals(2, checks)
    }

    @Test fun scopedDecoderIsRestoredAfterFailure() {
        Decoder.withOpener({ _, _ -> ToneDecoder(22_050) }) {
            runCatching {
                Decoder.withOpener<Int>({ _, _ -> error("inner") }) { Decoder.open("test"); 0 }
            }
            Decoder.open("test").use { assertEquals(22_050, it.spec().sampleRate) }
        }
        assertTrue(runCatching { Decoder.open("test") }.isFailure)
    }

    @Test fun analysisHonorsScopedPreparationBudget() {
        val failure = runCatching {
            Decoder.withOpener({ _, _ -> ToneDecoder(22_050) }, { error("budget exhausted") }) {
                val base = AiMix(MixStyle.DEFAULT, 22_050, 2)
                AutoCueTransition(base, 22_050, 2).prepare("budget-out", "budget-in", 22_050)
            }
        }.exceptionOrNull()
        assertNotNull(failure)
        assertTrue(failure!!.message.orEmpty().contains("budget exhausted"))
    }

    private class ToneDecoder(private val rate: Int) : Decoder {
        private var frame = 0L
        override fun spec() = TrackSpec("synthetic", rate, ChannelLayout.STEREO, 16, rate * 36L, false)
        override fun framesDecoded() = frame
        override fun isDrained() = frame >= rate * 36L
        override fun seek(secs: Double): Double { frame = (secs * rate).toLong(); return secs }
        override fun close() = Unit
        override fun decodeInto(out: AudioBuf): Int {
            val count = minOf(out.capacityFrames().toLong(), rate * 36L - frame).coerceAtLeast(0).toInt()
            for (i in 0 until count) {
                val time = (frame + i).toDouble() / rate
                val beat = if (time % 0.5 < 0.08) 0.3 else 0.08
                val sample = (sin(time * 2 * PI * 220) * beat).toFloat()
                out.backingArray()[out.channelBase(0) + i] = sample
                out.backingArray()[out.channelBase(1) + i] = sample * 0.85f
            }
            out.setFrames(count)
            frame += count
            return count
        }
    }
}
