package app.echo.android.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoCastStatusParserTest {
    @Test
    fun parsesPlayingStatus() {
        val status = EchoCastStatusParser.media(
            """{"type":"MEDIA_STATUS","status":[{"mediaSessionId":3,"playerState":"PLAYING","currentTime":12.5,"media":{"duration":200.0}}]}""",
        )!!
        assertEquals(3, status.mediaSessionId)
        assertEquals(12_500L, status.positionMs)
        assertEquals(200_000L, status.durationMs)
        assertTrue(status.isPlaying)
        assertFalse(status.finished)
    }

    @Test
    fun finishedWhenIdleWithReason() {
        val status = EchoCastStatusParser.media(
            """{"type":"MEDIA_STATUS","status":[{"mediaSessionId":3,"playerState":"IDLE","idleReason":"FINISHED"}]}""",
        )!!
        assertTrue(status.finished)
    }

    @Test
    fun ignoresOtherMessages() {
        assertNull(EchoCastStatusParser.media("""{"type":"PONG"}"""))
        assertNull(EchoCastStatusParser.media("not json"))
    }

    @Test
    fun readsReceiverVolume() {
        assertEquals(
            0.4f,
            EchoCastStatusParser.receiverVolume("""{"type":"RECEIVER_STATUS","status":{"volume":{"level":0.4,"muted":false}}}""")!!,
            0.0001f,
        )
    }
}
