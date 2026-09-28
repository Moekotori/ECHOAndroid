package app.echo.android.listening

import java.util.Base64
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EchoListeningCodesTest {
    @Test fun readsAnInviteCodeAndBuildsTheSocket() {
        val payload = JSONObject()
            .put("server", "https://listen.example")
            .put("roomId", "room-1")
            .put("invitation", "once")
            .toString()
        val code = "echo-listen:" + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payload.toByteArray())
        val invite = EchoListeningCodes.parse(code)
        assertEquals("https://listen.example", invite?.server)
        assertEquals("room-1", invite?.roomId)
        assertEquals("once", invite?.invitation)
        assertEquals("wss://listen.example/v1/socket", EchoListeningCodes.socketUrl(invite!!.server))
    }

    @Test fun rejectsSecretsInTheHostAndUnknownPaths() {
        assertNull(EchoListeningCodes.socketUrl("wss://user:pass@listen.example"))
        assertNull(EchoListeningCodes.socketUrl("wss://listen.example/other"))
        assertNull(EchoListeningCodes.socketUrl("ws://listen.example"))
        assertEquals("ws://127.0.0.1:9/v1/socket", EchoListeningCodes.socketUrl("ws://127.0.0.1:9"))
        assertNull(EchoListeningCodes.parse("not a server"))
        assertNull(EchoListeningCodes.sanitizeName("bad\nname"))
    }

    @Test fun readsAnUnpaddedCodeInsideOtherText() {
        var roomId = "room"
        var encoded: String
        do {
            roomId += "a"
            val payload = JSONObject().put("server", "wss://listen.example").put("roomId", roomId).toString()
            encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())
        } while (encoded.length % 4 == 0)
        val invite = EchoListeningCodes.parse("code echo-listen:$encoded thanks")
        assertEquals(roomId, invite?.roomId)
        assertEquals("wss://listen.example/v1/socket", EchoListeningCodes.socketUrl(invite!!.server))
    }
}
