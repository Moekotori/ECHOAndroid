package app.echo.android.playback

import android.content.Context
import dev.t1m3.qplayer.audio.core.AudioBuf
import dev.t1m3.qplayer.audio.core.ChannelLayout
import dev.t1m3.qplayer.audio.decode.Decoder
import dev.t1m3.qplayer.audio.mix.AiMix
import dev.t1m3.qplayer.audio.mix.AutoCueTransition
import dev.t1m3.qplayer.audio.mix.MixStyle
import dev.t1m3.qplayer.audio.mix.MixTuning
import dev.t1m3.qplayer.audio.mix.Transition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/** Owns the supplied AutoMix engine; no analysis or rendering happens on the audio thread. */
internal class EchoAutoMixEngine(context: Context) {
    private val appContext = context.applicationContext

    data class Plan(
        val transition: Transition,
        val startMs: Long,
        val nextStartMs: Long,
        val durationMs: Long,
        val frames: Int,
        val mode: String? = null, val outgoingBpm: Double? = null, val incomingBpm: Double? = null,
        val tempoConfidence: Double? = null,
    )

    suspend fun prepare(current: String, next: String, currentDurationMs: Long, nextDurationMs: Long,
        rate: Int, channels: Int, maxOverlapMs: Int): Plan? = runInterruptible(Dispatchers.IO) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        fun checkBudget() {
            if (Thread.currentThread().isInterrupted) throw InterruptedException("AutoMix cancelled")
            check(System.nanoTime() < deadline) { "AutoMix preparation budget exceeded" }
        }
        try {
            if (maxOverlapMs < 500 || rate !in 8_000..192_000 || channels !in 1..2) return@runInterruptible null
            Decoder.withOpener({ source, _ -> EchoAutoMixDecoder(appContext, source, ::checkBudget) }, ::checkBudget) {
                val tuning = MixTuning.defaults().apply {
                    // Keep the source/output frame mapping exact for queue and lyric continuation.
                    tempoStretch = 0f
                    styleLoopEnabled = false
                    windowScale = minOf(1f, maxOverlapMs / 4_000f)
                }
                val base = AiMix(MixStyle.DEFAULT, rate, channels, tuning).apply {
                    setWindowLimit(maxOverlapMs.toLong() * rate / 1000)
                }
                val transition = AutoCueTransition(base, rate, channels, null, false, tuning)
                val result = transition.prepare(current, next, rate)
                checkBudget()
                val frames = transition.windowFrames(rate)
                val duration = frames * 1000L / rate
                val start = currentDurationMs - duration - (result.outroTailSecs * 1000).toLong()
                val entry = (result.skipSecs * 1000).toLong()
                if (!result.outroTailSecs.isFinite() || !result.skipSecs.isFinite() ||
                    frames !in 1..Int.MAX_VALUE.toLong() || duration !in 500..maxOverlapMs.toLong() ||
                    frames * channels * 4 > EchoSmartTransitionPolicy.MaxMixBytes ||
                    start < 0 || entry < 0 || entry + duration > nextDurationMs ||
                    transition.contentFramesFor(frames) != frames) null
                // MediaCodec windows use millisecond boundaries; keep the consumed frames identical.
                else Plan(transition, start, entry, duration, (duration * rate / 1000).toInt(),
                    base.name(), base.outroBpm()?.toDouble(), base.introBpm()?.toDouble(),
                    if (base.tempoOut() != null && base.tempoIn() != null)
                        minOf(base.tempoOut().confidence, base.tempoIn().confidence).toDouble() else null)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (interrupted: InterruptedException) {
            throw interrupted
        } catch (_: Exception) {
            null
        }
    }

    suspend fun render(plan: Plan, from: FloatArray, to: FloatArray, channels: Int): FloatArray? =
        withContext(Dispatchers.Default) {
            try {
                EchoAutoMixRenderer.render(plan.transition, from, to, plan.frames, channels) {
                    coroutineContext.ensureActive()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
}

/** Bounded block renderer, also usable with synthetic PCM in regression tests. */
internal object EchoAutoMixRenderer {
    fun render(transition: Transition, from: FloatArray, to: FloatArray, frames: Int, channels: Int,
        checkActive: () -> Unit = {}): FloatArray? {
        if (channels !in 1..2 || frames <= 0 || frames > minOf(from.size, to.size) / channels ||
            frames.toLong() * channels * 4 > EchoSmartTransitionPolicy.MaxMixBytes) return null
        val layout = ChannelLayout.of(channels)
        val a = AudioBuf(layout, 256)
        val b = AudioBuf(layout, 256)
        val out = AudioBuf(layout, 256)
        val pcm = FloatArray(frames * channels)
        var position = 0
        while (position < frames) {
            checkActive()
            val count = minOf(256, frames - position)
            for (channel in 0 until channels) {
                for (i in 0 until count) {
                    a.backingArray()[a.channelBase(channel) + i] = from[(position + i) * channels + channel]
                    b.backingArray()[b.channelBase(channel) + i] = to[(position + i) * channels + channel]
                }
            }
            a.setFrames(count)
            b.setFrames(count)
            out.clear()
            val produced = transition.render(out, a, b, (position + count * 0.5f) / frames)
            if (produced != count || out.frames() != count) return null
            for (channel in 0 until channels) {
                for (i in 0 until count) {
                    val value = out.backingArray()[out.channelBase(channel) + i]
                    if (!value.isFinite()) return null
                    pcm[(position + i) * channels + channel] = value.coerceIn(-1f, 1f)
                }
            }
            position += count
        }
        return pcm
    }
}
