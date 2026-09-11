package app.echo.android.connect

import app.echo.android.model.connect.EchoDlnaService
import app.echo.android.model.connect.EchoLanRenderer
import app.echo.android.model.connect.EchoLanRendererKind
import app.echo.android.model.connect.EchoRemoteAudioFormat
import app.echo.android.model.connect.EchoRemoteStreamItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoDlnaSoapTest {
    @Test
    fun envelopeEscapesMetadataAndSetsSoapAction() {
        val didl = EchoDlnaDidl.build(
            id = "t1",
            streamUrl = "http://192.168.1.20:26800/echo-link/cast/abcd",
            title = "A & B",
            artist = "Artist",
            album = "Album",
            mimeType = "audio/flac",
            durationMs = 240_000L,
        )
        val body = EchoDlnaSoap.envelope(
            "urn:schemas-upnp-org:service:AVTransport:1",
            "SetAVTransportURI",
            mapOf("InstanceID" to "0", "CurrentURI" to "http://192.168.1.20/a", "CurrentURIMetaData" to didl),
        )
        assertTrue(body.contains("xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\""))
        assertTrue(body.contains("&amp;"))
        assertTrue(didl.contains("duration=\"0:04:00\""))
        assertTrue(didl.contains("DLNA.ORG_PN=FLAC"))
        assertEquals(
            "\"urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI\"",
            EchoDlnaSoap.soapAction("urn:schemas-upnp-org:service:AVTransport:1", "SetAVTransportURI"),
        )
    }

    @Test
    fun parsesSinkMimeTypesAndClock() {
        val xml = """
            <u:GetProtocolInfoResponse>
              <Sink>http-get:*:audio/mpeg:*,http-get:*:audio/wav:DLNA.ORG_PN=WAV,http-get:*:audio/x-flac:*</Sink>
            </u:GetProtocolInfoResponse>
        """.trimIndent()
        assertEquals(listOf("audio/mpeg", "audio/wav", "audio/x-flac"), EchoDlnaSoap.parseSinkMimeTypes(xml))
        assertEquals(185_000L, EchoDlnaSoap.parseDlnaTimeMs("0:03:05"))
        assertEquals("0:03:05", EchoDlnaSoap.formatDlnaTime(185_000L))
        assertNull(EchoDlnaSoap.parseDlnaTimeMs("NOT_IMPLEMENTED"))
    }
}

class EchoDlnaCastPolicyTest {
    @Test
    fun tvsDefaultToMpegAndWav() {
        val tv = renderer(name = "客厅电视", manufacturer = "Samsung")
        assertTrue(EchoDlnaCastPolicy.looksLikeTv(tv))
        assertTrue(EchoDlnaCastPolicy.looksLikeTv(renderer(name = "客厅电视")))
        assertEquals(EchoDlnaCastPolicy.TvSinkMimeTypes, EchoDlnaCastPolicy.defaultSinkMimeTypes(tv))
        assertEquals(
            EchoDlnaRejectReason.UnsupportedFormat,
            EchoDlnaCastPolicy.rejectReason(tv, item(mime = "audio/flac", codec = "flac")),
        )
        assertNull(EchoDlnaCastPolicy.rejectReason(tv, item(mime = "audio/mpeg", codec = "mp3")))
        assertEquals(
            EchoDlnaRejectReason.DsdOnTv,
            EchoDlnaCastPolicy.rejectReason(tv, item(mime = "audio/x-dsf", codec = "dsd")),
        )
    }

    @Test
    fun streamersAllowFlacAndAdvertisedDsd() {
        val streamer = renderer(name = "Eversolo DMP-A6", manufacturer = "Eversolo")
        assertFalse(EchoDlnaCastPolicy.looksLikeTv(streamer))
        assertNull(EchoDlnaCastPolicy.rejectReason(streamer, item(mime = "audio/flac", codec = "flac")))
        val withDsd = streamer.copy(sinkMimeTypes = listOf("audio/x-dsf"))
        assertNull(EchoDlnaCastPolicy.rejectReason(withDsd, item(mime = "audio/x-dsf", codec = "dsd")))
    }

    @Test
    fun chromecastAndMissingControlAreRejected() {
        val cast = renderer(name = "Bedroom TV", kind = EchoLanRendererKind.Chromecast).copy(avTransport = null)
        assertEquals(EchoDlnaRejectReason.Chromecast, EchoDlnaCastPolicy.rejectReason(cast, item()))
        val broken = renderer(name = "Speaker").copy(avTransport = null)
        assertEquals(EchoDlnaRejectReason.NoAvTransport, EchoDlnaCastPolicy.rejectReason(broken, item()))
    }
}

class EchoDlnaClientTest {
    @Test
    fun playSendsSetUriThenPlayAndOptionalNext() {
        val calls = mutableListOf<String>()
        val client = EchoDlnaClient { _, _, action, args ->
            calls += action
            if (action == "SetAVTransportURI") {
                assertTrue(args.getValue("CurrentURI").contains("/echo-link/cast/a"))
                assertTrue(args.getValue("CurrentURIMetaData").contains("Song"))
            }
            ""
        }
        client.play(
            renderer = renderer(name = "Speaker"),
            item = item(id = "a", url = "http://192.168.1.20:1/echo-link/cast/a", mime = "audio/flac"),
            positionMs = 0L,
            next = item(id = "b", url = "http://192.168.1.20:1/echo-link/cast/b", mime = "audio/flac"),
        )
        assertEquals(listOf("SetAVTransportURI", "SetNextAVTransportURI", "Play"), calls)
    }

    @Test
    fun publicControlUrlNeverPosts() {
        val renderer = renderer(name = "Speaker").copy(
            avTransport = EchoDlnaService(
                serviceType = "urn:schemas-upnp-org:service:AVTransport:1",
                controlUrl = "http://example.com/control",
            ),
        )
        try {
            EchoDlnaHttpTransport().post(
                renderer.avTransport!!.controlUrl,
                renderer.avTransport!!.serviceType,
                "Play",
                mapOf("InstanceID" to "0"),
            )
            error("expected lan rejection")
        } catch (error: EchoDlnaException) {
            assertEquals("control_url_must_be_lan", error.message)
        }
    }
}

private fun renderer(
    name: String,
    manufacturer: String? = null,
    kind: EchoLanRendererKind = EchoLanRendererKind.Dlna,
): EchoLanRenderer = EchoLanRenderer(
    id = "id-$name",
    name = name,
    host = "192.168.1.40",
    port = 52323,
    kind = kind,
    manufacturer = manufacturer,
    avTransport = EchoDlnaService(
        serviceType = "urn:schemas-upnp-org:service:AVTransport:1",
        controlUrl = "http://192.168.1.40:52323/upnp/control/AVTransport",
    ),
)

private fun item(
    id: String = "t1",
    url: String = "http://192.168.1.20:26800/echo-link/cast/abcd",
    mime: String = "audio/mpeg",
    codec: String = "mp3",
): EchoRemoteStreamItem = EchoRemoteStreamItem(
    id = id,
    streamUrl = url,
    title = "Song",
    artist = "Artist",
    album = "Album",
    durationMs = 240_000L,
    audio = EchoRemoteAudioFormat(codec = codec, mimeType = mime, lossless = mime.contains("flac") || mime.contains("wav")),
)
