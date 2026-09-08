package app.echo.android.playback

import android.hardware.usb.UsbManager
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.echo.android.model.playback.EchoBitPerfectState
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class EchoBitPerfectRoutingTest {
    @Test fun noDacStopsStrictPlaybackWithoutOpeningAudioTrack() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.context
        assumeTrue((context.getSystemService(Context.USB_SERVICE) as UsbManager).deviceList.isEmpty())
        val stopped = CountDownLatch(1)
        val audioTrackCreated = CountDownLatch(1)
        var player: ExoPlayer? = null
        try {
            instrumentation.runOnMainSync {
                EchoPlaybackProcessRuntime.setUsbExclusiveEnabled(true)
                EchoPlaybackProcessRuntime.setUsbBitPerfectEnabled(true)
                player = ExoPlayer.Builder(context).setRenderersFactory(
                    EchoRenderersFactory(context, EchoEqualizerAudioProcessor())).build()
                player!!.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) { stopped.countDown() }
                })
                player!!.addAnalyticsListener(object : AnalyticsListener {
                    override fun onAudioTrackInitialized(eventTime: AnalyticsListener.EventTime, audioTrackConfig: AudioSink.AudioTrackConfig) {
                        audioTrackCreated.countDown()
                    }
                })
                player!!.setMediaItem(MediaItem.fromUri("asset:///bitperfect.wav"))
                player!!.prepare() // Never start audible playback in this test.
            }
            assertTrue("Strict mode must report the missing DAC", stopped.await(10, TimeUnit.SECONDS))
            assertEquals(EchoBitPerfectState.UsbUnavailable, EchoPlaybackProcessRuntime.bitPerfectStates.value.state)
            assertEquals("Strict mode must not fall back to AudioTrack", 1L, audioTrackCreated.count)
            instrumentation.runOnMainSync {
                EchoPlaybackProcessRuntime.setUsbBitPerfectEnabled(false)
                player!!.stop()
                player!!.prepare()
            }
            assertTrue("Disabling strict mode restores normal output", audioTrackCreated.await(5, TimeUnit.SECONDS))
        } finally {
            instrumentation.runOnMainSync {
                player?.release()
                EchoPlaybackProcessRuntime.setUsbBitPerfectEnabled(false)
                EchoPlaybackProcessRuntime.setUsbExclusiveEnabled(false)
            }
        }
    }
}
