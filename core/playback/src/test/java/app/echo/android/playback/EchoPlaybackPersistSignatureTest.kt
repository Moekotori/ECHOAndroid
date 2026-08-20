package app.echo.android.playback

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class EchoPlaybackPersistSignatureTest {
    @Test
    fun shuffleRepeatAndSpeedChangeThePersistSignature() {
        val mediaIds = listOf("track-1", "track-2")
        val base = playbackSessionPersistSignature(
            currentIndex = 1,
            playWhenReady = true,
            mediaIds = mediaIds,
            shuffleEnabled = false,
            repeatMode = Player.REPEAT_MODE_OFF,
            playbackSpeed = 1f,
            playbackPitch = 1f,
        )
        val shuffled = playbackSessionPersistSignature(
            currentIndex = 1,
            playWhenReady = true,
            mediaIds = mediaIds,
            shuffleEnabled = true,
            repeatMode = Player.REPEAT_MODE_OFF,
            playbackSpeed = 1f,
            playbackPitch = 1f,
        )
        val repeatAll = playbackSessionPersistSignature(
            currentIndex = 1,
            playWhenReady = true,
            mediaIds = mediaIds,
            shuffleEnabled = false,
            repeatMode = Player.REPEAT_MODE_ALL,
            playbackSpeed = 1f,
            playbackPitch = 1f,
        )
        val faster = playbackSessionPersistSignature(
            currentIndex = 1,
            playWhenReady = true,
            mediaIds = mediaIds,
            shuffleEnabled = false,
            repeatMode = Player.REPEAT_MODE_OFF,
            playbackSpeed = 1.25f,
            playbackPitch = 1.25f,
        )

        assertNotEquals(base, shuffled)
        assertNotEquals(base, repeatAll)
        assertNotEquals(base, faster)
        assertEquals(
            base,
            playbackSessionPersistSignature(
                currentIndex = 1,
                playWhenReady = true,
                mediaIds = mediaIds,
                shuffleEnabled = false,
                repeatMode = Player.REPEAT_MODE_OFF,
                playbackSpeed = 1f,
                playbackPitch = 1f,
            ),
        )
    }
}
