package app.echo.android.connect

import app.echo.android.model.connect.EchoLanRendererKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoLanRendererPolicyTest {
    @Test
    fun parseMediaRendererSearchReply() {
        val parsed = EchoLanRendererPolicy.parseResponse(
            """
            HTTP/1.1 200 OK
            CACHE-CONTROL: max-age=1800
            LOCATION: http://192.168.1.40:52323/dmr.xml
            ST: urn:schemas-upnp-org:device:MediaRenderer:1
            USN: uuid:tv-living::urn:schemas-upnp-org:device:MediaRenderer:1
            SERVER: Linux/DLNA
            """.trimIndent().replace("\n", "\r\n"),
        )
        checkNotNull(parsed)
        assertEquals("http://192.168.1.40:52323/dmr.xml", parsed.location)
        assertTrue(parsed.usn.orEmpty().startsWith("uuid:tv-living"))
    }

    @Test
    fun rejectMediaServerAndNonLan() {
        assertNull(
            EchoLanRendererPolicy.parseResponse(
                """
                HTTP/1.1 200 OK
                LOCATION: http://192.168.1.9:8200/rootDesc.xml
                ST: urn:schemas-upnp-org:device:MediaServer:1
                USN: uuid:nas
                """.trimIndent(),
            ),
        )
        assertNull(
            EchoLanRendererPolicy.parseResponse(
                """
                HTTP/1.1 200 OK
                LOCATION: https://example.com/dmr.xml
                ST: urn:schemas-upnp-org:device:MediaRenderer:1
                """.trimIndent(),
            ),
        )
        assertTrue(EchoLanRendererPolicy.isLanHttpUrl("http://192.168.1.40/dmr.xml"))
        assertTrue(EchoLanRendererPolicy.isLanHttpUrl("http://tv.local/dmr.xml"))
        assertFalse(EchoLanRendererPolicy.isLanHttpUrl("http://127.0.0.1/dmr.xml"))
        assertFalse(EchoLanRendererPolicy.isLanHttpUrl("https://example.com/dmr.xml"))
    }

    @Test
    fun descriptionBecomesRenderer() {
        val xml = """
            <?xml version="1.0"?>
            <root>
              <device>
                <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
                <friendlyName>客厅电视</friendlyName>
                <manufacturer>Samsung</manufacturer>
                <modelName>UN55</modelName>
              </device>
            </root>
        """.trimIndent()
        val renderer = EchoLanRendererPolicy.rendererFromDescription(
            location = "http://192.168.1.40:52323/dmr.xml",
            xml = xml,
            usn = "uuid:tv-living::urn:schemas-upnp-org:device:MediaRenderer:1",
        )
        checkNotNull(renderer)
        assertEquals("客厅电视", renderer.name)
        assertEquals("192.168.1.40", renderer.host)
        assertEquals(52323, renderer.port)
        assertEquals(EchoLanRendererKind.Dlna, renderer.kind)
        assertEquals("Samsung", renderer.manufacturer)
        assertEquals("uuid:tv-living", renderer.id)
        assertNull(renderer.avTransport)
    }

    @Test
    fun descriptionReadsAvTransportControlUrl() {
        val xml = """
            <?xml version="1.0"?>
            <root>
              <URLBase>http://192.168.1.40:52323/</URLBase>
              <device>
                <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
                <friendlyName>客厅电视</friendlyName>
                <serviceList>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                    <controlURL>/upnp/control/AVTransport</controlURL>
                  </service>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
                    <controlURL>/upnp/control/RenderingControl</controlURL>
                  </service>
                </serviceList>
              </device>
            </root>
        """.trimIndent()
        val renderer = EchoLanRendererPolicy.rendererFromDescription(
            location = "http://192.168.1.40:52323/dmr.xml",
            xml = xml,
            usn = "uuid:tv-living",
        )
        checkNotNull(renderer)
        assertEquals("http://192.168.1.40:52323/upnp/control/AVTransport", renderer.avTransport?.controlUrl)
        assertEquals("urn:schemas-upnp-org:service:AVTransport:1", renderer.avTransport?.serviceType)
        assertEquals("http://192.168.1.40:52323/upnp/control/RenderingControl", renderer.renderingControl?.controlUrl)
    }

    @Test
    fun publicControlUrlIsRejected() {
        assertNull(
            EchoLanRendererPolicy.absoluteUrl("/control", "https://example.com/dmr.xml"),
        )
        assertNull(
            EchoLanRendererPolicy.absoluteUrl("http://example.com/control", "http://192.168.1.40/dmr.xml"),
        )
    }

    @Test
    fun mediaServerXmlIsIgnored() {
        val xml = """
            <root><device>
              <deviceType>urn:schemas-upnp-org:device:MediaServer:1</deviceType>
              <friendlyName>NAS</friendlyName>
            </device></root>
        """.trimIndent()
        assertNull(EchoLanRendererPolicy.parseDescription(xml))
    }

    @Test
    fun chromecastUsesFriendlyNameFromTxt() {
        val renderer = EchoLanRendererPolicy.chromecastFromResolved(
            serviceName = "Chromecast-abc",
            host = "192.168.1.50",
            port = 8009,
            txt = mapOf("fn" to "Bedroom TV", "md" to "Chromecast", "id" to "cast-1"),
        )
        checkNotNull(renderer)
        assertEquals("Bedroom TV", renderer.name)
        assertEquals(EchoLanRendererKind.Chromecast, renderer.kind)
        assertEquals("cast-1", renderer.id)
    }

    @Test
    fun upsertReplacesSameId() {
        val first = EchoLanRendererPolicy.chromecastFromResolved("a", "10.0.0.2", 8009, mapOf("fn" to "A", "id" to "x"))!!
        val updated = first.copy(name = "A2")
        val merged = EchoLanRendererPolicy.upsert(listOf(first), updated)
        assertEquals(1, merged.size)
        assertEquals("A2", merged.single().name)
    }
}
