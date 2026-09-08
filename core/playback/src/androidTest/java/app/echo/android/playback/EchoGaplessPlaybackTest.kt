package app.echo.android.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.echo.android.model.playback.EchoTrackTransitionOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class EchoGaplessPlaybackTest {
    @Test fun fadeFollowsPlaybackClockAndStrictModeRestoresUnity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val done = CountDownLatch(1)
        val bypassed = CountDownLatch(1)
        val gains = java.util.Collections.synchronizedList(mutableListOf<Float>())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        var player: ExoPlayer? = null
        var transitions: EchoTrackTransitionController? = null
        var error: PlaybackException? = null
        try {
            instrumentation.runOnMainSync {
                EchoPlaybackProcessRuntime.setUsbOutputMode(false, false)
                EchoPlaybackRuntimeOptionsStore.setTrackTransitions(EchoTrackTransitionOptions(true, 500))
                val p = ExoPlayer.Builder(instrumentation.context).build()
                player = p
                p.volume = 0f
                transitions = EchoTrackTransitionController(p, scope) { gain ->
                    gains.add(gain)
                    if (EchoPlaybackProcessRuntime.usbBitPerfectEnabled && gain == 1f) bypassed.countDown()
                }
                p.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) { if (playbackState == Player.STATE_ENDED) done.countDown() }
                    override fun onPlayerError(e: PlaybackException) { error = e; done.countDown() }
                })
                p.setMediaItem(MediaItem.fromUri("asset:///fade-clock.wav"))
                p.prepare(); p.play()
            }
            assertTrue(done.await(15, TimeUnit.SECONDS))
            assertNull(error)
            instrumentation.runOnMainSync {
                assertTrue(gains.size > 10)
                assertTrue(gains.any { it > 0.99f })
                assertEquals(0f, gains.last(), 0.02f)
                EchoPlaybackProcessRuntime.setUsbOutputMode(false, true)
            }
            assertTrue("Strict mode must disable the gain envelope", bypassed.await(3, TimeUnit.SECONDS))
        } finally {
            instrumentation.runOnMainSync {
                transitions?.close(); player?.release(); scope.cancel()
                EchoPlaybackRuntimeOptionsStore.setTrackTransitions(EchoTrackTransitionOptions())
                EchoPlaybackProcessRuntime.setUsbOutputMode(false, false)
            }
        }
    }

    @Test fun twoWavItemsReuseOutputAndKeepEveryPcmFrame() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val done = CountDownLatch(1)
        val pcm = ByteArrayOutputStream()
        var error: PlaybackException? = null
        var outputs = 0
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        var player: ExoPlayer? = null
        var transitions: EchoTrackTransitionController? = null
        try {
            instrumentation.runOnMainSync {
                EchoPlaybackProcessRuntime.setUsbOutputMode(false, false)
                EchoPlaybackRuntimeOptionsStore.setTrackTransitions(EchoTrackTransitionOptions())
                val tee = TeeAudioProcessor(object : TeeAudioProcessor.AudioBufferSink {
                    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
                        assertEquals(16000, sampleRateHz); assertEquals(1, channelCount)
                        assertEquals(C.ENCODING_PCM_16BIT, encoding)
                    }
                    override fun handleBuffer(buffer: ByteBuffer) {
                        val bytes = ByteArray(buffer.remaining()); buffer.get(bytes); pcm.write(bytes)
                    }
                })
                val p = ExoPlayer.Builder(instrumentation.context)
                    .setRenderersFactory(EchoRenderersFactory(instrumentation.context, tee)).build()
                player = p
                transitions = EchoTrackTransitionController(p, scope) {}
                p.volume = 0f // Exercise real AudioTrack output silently.
                p.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) { if (playbackState == Player.STATE_ENDED) done.countDown() }
                    override fun onPlayerError(e: PlaybackException) { error = e; done.countDown() }
                })
                p.addAnalyticsListener(object : AnalyticsListener {
                    override fun onAudioTrackInitialized(eventTime: AnalyticsListener.EventTime, audioTrackConfig: AudioSink.AudioTrackConfig) { outputs++ }
                })
                p.setMediaItems(listOf(MediaItem.fromUri("asset:///gapless-a.wav"), MediaItem.fromUri("asset:///gapless-b.wav")))
                p.prepare(); p.play()
            }
            assertTrue("Queue did not complete", done.await(15, TimeUnit.SECONDS))
            assertNull(error)
            assertEquals("Compatible tracks should keep one AudioTrack", 1, outputs)
            val samples = ByteBuffer.wrap(pcm.toByteArray()).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            assertEquals(8000, samples.remaining())
            repeat(4000) { assertEquals(1000.toShort(), samples.get()) }
            repeat(4000) { assertEquals(2000.toShort(), samples.get()) }
        } finally {
            instrumentation.runOnMainSync { transitions?.close(); player?.release(); scope.cancel() }
        }
    }
}
