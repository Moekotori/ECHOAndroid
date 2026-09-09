package app.echo.android.data

import app.echo.android.model.connect.EchoSavedPcEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EchoSavedPcCodecTest {
    @Test
    fun upsertReplacesSameAddressAndCapsList() {
        val first = EchoSavedPcEndpoint("http://192.168.1.8:26789", "Studio")
        val updated = EchoSavedPcEndpoint("http://192.168.1.8:26789", "Living room", supportsV2Events = true)
        val encoded = EchoSavedPcCodec.encode(EchoSavedPcCodec.upsert(listOf(first), updated))
        val decoded = EchoSavedPcCodec.decode(encoded)
        assertEquals(1, decoded.size)
        assertEquals("Living room", decoded.single().name)
        assertEquals(true, decoded.single().supportsV2Events)
    }

    @Test
    fun encodeOmitsTokenAndCapsList() {
        val encoded = EchoSavedPcCodec.encode(
            listOf(
                EchoSavedPcEndpoint("http://192.168.1.8:26789", "Studio", token = "secret-token"),
            ),
        )
        assertFalse(encoded.contains("secret-token"))
        val many = (1..10).map { index ->
            EchoSavedPcEndpoint("http://192.168.1.$index:26789", "PC $index")
        }
        val capped = EchoSavedPcCodec.upsert(many.take(8), EchoSavedPcEndpoint("http://192.168.1.99:26789", "New"))
        assertEquals(8, capped.size)
        assertEquals("http://192.168.1.99:26789", capped.last().address)
    }
}
