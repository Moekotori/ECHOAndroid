package app.echo.android.playback

import androidx.media3.common.Player
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class EchoManualFadePlayerTest {
    private class RawPlayer {
        var ready = true
        var index = 0
        val listeners = mutableListOf<Player.Listener>()
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { proxy, method, args ->
            when (method.name) {
                "addListener" -> { listeners += args!![0] as Player.Listener; null }
                "removeListener" -> { listeners -= args!![0] as Player.Listener; null }
                "getPlayWhenReady", "isPlaying" -> ready
                "getPlaybackState" -> Player.STATE_READY
                "getMediaItemCount" -> 1
                "getCurrentMediaItemIndex" -> index
                "getCurrentMediaItem" -> null
                "play" -> { changeReady(true); null }
                "pause" -> { changeReady(false); null }
                "setPlayWhenReady" -> { changeReady(args!![0] as Boolean); null }
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args!![0]
                "toString" -> "RawPlayer"
                else -> when (method.returnType) { java.lang.Boolean.TYPE -> false; java.lang.Integer.TYPE -> 0; java.lang.Long.TYPE -> 0L; java.lang.Float.TYPE -> 0f; else -> null }
            }
        } as Player
        fun changeReady(value: Boolean) {
            if (ready == value) return
            ready = value
            listeners.toList().forEach { it.onPlayWhenReadyChanged(value, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST); it.onIsPlayingChanged(value) }
        }
        fun changeTrack(reason: Int = Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) { index++; listeners.toList().forEach { it.onMediaItemTransition(null, reason) } }
    }
    @Test fun interruptionAndTrackChangesCancelDelayedPauseAndRestoreGain() = runBlocking {
        val raw = RawPlayer()
        val state = EchoManualFadeState()
        val enabled = MutableStateFlow(true)
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val facade = EchoManualFadePlayer(raw.player, scope, state, { enabled.value }, enabled, {})
        try {
            yield()
            facade.pause(); delay(40)
            assertTrue(state.gain < 1f)
            assertFalse(EchoManualFadePolicy.shouldRememberResume(raw.ready, state.intent))
            raw.player.pause() // Focus/noisy/timer owners bypass the facade.
            assertFalse(raw.ready)
            delay(220)
            assertFalse(raw.ready); assertEquals(1f, state.gain, 0f)
            raw.player.play()
            facade.pause(); delay(30); raw.changeTrack()
            delay(220)
            assertTrue(raw.ready); assertEquals(1f, state.gain, 0f)
            facade.pause(); delay(30); facade.play()
            withTimeout(1500) { while (state.intent != null) delay(10) }
            assertTrue(raw.ready); assertEquals(1f, state.gain, 0f)
            facade.pause(); raw.changeTrack(Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
            assertFalse(raw.ready) // Automatic advance must preserve an outstanding pause request.
            raw.player.play()
            facade.pause(); enabled.value = false; yield()
            assertFalse(raw.ready)
        } finally { facade.close(); scope.cancel() }
    }
    @Test fun delayedPauseCannotTargetAnotherRequestOrQueueOccurrence() {
        assertTrue(EchoManualFadePolicy.shouldRememberResume(true, null))
        assertFalse(EchoManualFadePolicy.shouldRememberResume(true, false))
        assertFalse(EchoManualFadePolicy.shouldRememberResume(false, true))
        assertTrue(EchoManualFadePolicy.sameRequest(1,1,"one","one",0,0))
        assertFalse(EchoManualFadePolicy.sameRequest(1,2,"one","one",0,0))
        assertFalse(EchoManualFadePolicy.sameRequest(1,1,"one","two",0,0))
        assertFalse(EchoManualFadePolicy.sameRequest(1,1,"one","one",0,1))
    }
}
