package app.echo.android.model.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoRemotePinPolicyTest {
    @Test
    fun pinsEchoLinkAndRemoteSourcesOnly() {
        assertTrue(EchoRemotePinPolicy.canPin("echo-link", "echo-link:1", "echo-link://track/1"))
        assertTrue(EchoRemotePinPolicy.canPin("jellyfin", "jellyfin:1", "http://nas/Audio/1"))
        assertFalse(EchoRemotePinPolicy.canPin("mediastore", "mediastore:1", "content://media/1"))
    }

    @Test
    fun mergeCapsPinnedIds() {
        val many = List(EchoRemotePinPolicy.MaxTracks + 5) { "id$it" }
        assertEquals(EchoRemotePinPolicy.MaxTracks, EchoRemotePinPolicy.merge(emptyList(), many).size)
    }
}
