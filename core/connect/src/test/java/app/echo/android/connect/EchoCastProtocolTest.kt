package app.echo.android.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoCastProtocolTest {
    @Test
    fun encodeDecodeRoundTrip() {
        val original = EchoCastMessage(
            sourceId = "sender-0",
            destinationId = "receiver-0",
            namespace = EchoCastProtocol.ReceiverNamespace,
            payloadUtf8 = """{"type":"LAUNCH","appId":"CC1AD845","requestId":1}""",
        )
        val framed = EchoCastProtocol.encode(original)
        val body = framed.copyOfRange(4, framed.size)
        assertEquals(original, EchoCastProtocol.decode(body))
    }

    @Test
    fun extractsTransportIdFromReceiverStatus() {
        val payload = """{"type":"RECEIVER_STATUS","status":{"applications":[{"transportId":"web-1","appId":"CC1AD845"}]}}"""
        assertEquals("web-1", EchoCastProtocol.transportId(payload))
    }

    @Test
    fun extractsMediaSessionId() {
        assertEquals(7, EchoCastProtocol.mediaSessionId("""{"type":"MEDIA_STATUS","status":[{"mediaSessionId":7}]}"""))
    }

    @Test
    fun loadJsonContainsStreamUrlAndDoesNotBreakQuotes() {
        val json = EchoCastProtocol.loadJson(
            requestId = 2,
            contentId = "http://192.168.1.8:8090/echo-link/cast/abc",
            contentType = "audio/mpeg",
            positionMs = 1500,
            title = "Song \"One\"",
            artist = "Artist",
        )
        assertTrue(json.contains("http://192.168.1.8:8090/echo-link/cast/abc"))
        assertTrue(json.contains("\\\"One\\\""))
        assertTrue(json.contains("\"currentTime\":1.5"))
    }
}

class EchoChromecastCastPolicyTest {
    @Test
    fun acceptsMpegAndRejectsFlac() {
        val mp3 = app.echo.android.model.connect.EchoRemoteStreamItem(
            id = "1",
            streamUrl = "http://phone/cast/a",
            title = "A",
            artist = "B",
            audio = app.echo.android.model.connect.EchoRemoteAudioFormat(codec = "mp3", mimeType = "audio/mpeg"),
        )
        val flac = mp3.copy(audio = app.echo.android.model.connect.EchoRemoteAudioFormat(codec = "flac", mimeType = "audio/flac"))
        org.junit.Assert.assertNull(EchoChromecastCastPolicy.rejectReason(mp3))
        org.junit.Assert.assertEquals(EchoDlnaRejectReason.UnsupportedFormat, EchoChromecastCastPolicy.rejectReason(flac))
    }
}
