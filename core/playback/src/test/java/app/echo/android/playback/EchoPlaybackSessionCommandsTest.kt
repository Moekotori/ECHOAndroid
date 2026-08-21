package app.echo.android.playback

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoPlaybackSessionCommandsTest {
    @Test
    fun repeatModeCyclesOffAllOne() {
        assertEquals(Player.REPEAT_MODE_ALL, nextPlayerRepeatMode(Player.REPEAT_MODE_OFF))
        assertEquals(Player.REPEAT_MODE_ONE, nextPlayerRepeatMode(Player.REPEAT_MODE_ALL))
        assertEquals(Player.REPEAT_MODE_OFF, nextPlayerRepeatMode(Player.REPEAT_MODE_ONE))
    }

    @Test
    fun launchActionsMatchShortcutIntents() {
        assertTrue(EchoPlaybackIntents.isPlayLast(EchoPlaybackIntents.ACTION_PLAY_LAST))
        assertTrue(EchoPlaybackIntents.isOpenLibrary(EchoPlaybackIntents.ACTION_OPEN_LIBRARY))
        assertFalse(EchoPlaybackIntents.isPlayLast(EchoPlaybackIntents.ACTION_OPEN_LYRICS))
        assertFalse(EchoPlaybackIntents.isOpenLibrary(null))
        assertFalse(EchoPlaybackIntents.isPlayLast("android.intent.action.MAIN"))
    }
}
