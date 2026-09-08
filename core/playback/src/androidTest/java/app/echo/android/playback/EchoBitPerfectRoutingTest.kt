package app.echo.android.playback

import android.hardware.usb.UsbManager
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
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
        var audioSessionCreated = false
        var player: ExoPlayer? = null
        try {
            instrumentation.runOnMainSync {
                EchoPlaybackProcessRuntime.setUsbExclusiveEnabled(true)
                EchoPlaybackProcessRuntime.setUsbBitPerfectEnabled(true)
                player = ExoPlayer.Builder(context).setRenderersFactory(
                    EchoRenderersFactory(context, EchoEqualizerAudioProcessor())).build()
                player!!.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) { stopped.countDown() }
                    override fun onAudioSessionIdChanged(audioSessionId: Int) {
                        if (audioSessionId > 0) audioSessionCreated = true
                    }
                })
                player!!.setMediaItem(MediaItem.fromUri("asset:///bitperfect.wav"))
                player!!.prepare() // Never start audible playback in this test.
            }
            assertTrue("Strict mode must report the missing DAC", stopped.await(10, TimeUnit.SECONDS))
            assertEquals(EchoBitPerfectState.UsbUnavailable, EchoPlaybackProcessRuntime.bitPerfectStates.value.state)
            assertFalse("Strict mode must not fall back to AudioTrack", audioSessionCreated)
        } finally {
            instrumentation.runOnMainSync {
                player?.release()
                EchoPlaybackProcessRuntime.setUsbBitPerfectEnabled(false)
                EchoPlaybackProcessRuntime.setUsbExclusiveEnabled(false)
            }
        }
    }
}
