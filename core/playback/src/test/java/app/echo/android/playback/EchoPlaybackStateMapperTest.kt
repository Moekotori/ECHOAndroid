package app.echo.android.playback

import androidx.media3.common.Player
import app.echo.android.model.playback.EchoPlaybackState
import org.junit.Assert.assertEquals
import org.junit.Test

class EchoPlaybackStateMapperTest {
    @Test
    fun readyButNotPlayingIsPausedNotLoading() {
        assertEquals(
            EchoPlaybackState.Paused,
            echoPlaybackState(
                playbackState = Player.STATE_READY,
                isPlaying = false,
                hasCurrentMediaItem = true,
            ),
        )
    }

    @Test
    fun playWhenReadyWhileSuppressedIsStillPaused() {
        assertEquals(
            EchoPlaybackState.Paused,
            echoPlaybackState(
                playbackState = Player.STATE_READY,
                isPlaying = false,
                hasCurrentMediaItem = true,
            ),
        )
    }

    @Test
    fun bufferingAndPlayingMapDirectly() {
        assertEquals(
            EchoPlaybackState.Buffering,
            echoPlaybackState(
                playbackState = Player.STATE_BUFFERING,
                isPlaying = false,
                hasCurrentMediaItem = true,
            ),
        )
        assertEquals(
            EchoPlaybackState.Playing,
            echoPlaybackState(
                playbackState = Player.STATE_READY,
                isPlaying = true,
                hasCurrentMediaItem = true,
            ),
        )
    }
}
