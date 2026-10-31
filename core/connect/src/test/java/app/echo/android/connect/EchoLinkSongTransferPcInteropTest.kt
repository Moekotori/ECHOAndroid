package app.echo.android.connect

import app.echo.android.model.connect.EchoRemoteEndpoint
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in developer check against the real PC service, without depending on a PC checkout path. */
class EchoLinkSongTransferPcInteropTest {
    @Test fun realPcServerExchangesOriginalFilesAndRejectsTheClosedSession() = runBlocking {
        val address = System.getenv("ECHO_SONG_TRANSFER_PC_TEST")
        assumeTrue("Requires a local PC interop server", !address.isNullOrBlank())
        val endpoint = EchoPairingParser.parseManual(address!!, "interop-token")!!
        val transport = EchoLinkSongTransferTransport(endpoint)
        val http = OkHttpClient()
        fun proof(): JSONObject = http.newCall(Request.Builder().url("http://$address/proof").build()).execute().use { JSONObject(it.body!!.string()) }
        fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        try {
            val id = transport.start("Android JVM interop", true)
            var offered = transport.poll(id, true).firstOrNull { it.direction == "to-phone" }
            withTimeout(3000) { while (offered == null) { delay(20); offered = transport.poll(id, true).firstOrNull { it.direction == "to-phone" } } }
            var original = byteArrayOf()
            transport.download(id, offered!!) { name, size, input, check ->
                assertEquals("电脑原曲.wav", name); check(); original = input.readBytes(); assertEquals(size, original.size.toLong()); name
            }
            assertEquals(proof().getString("sourceHash"), hash(original))
            transport.acknowledge(id, offered!!.id, true)
            transport.upload(id, "手机原曲.wav", original.size.toLong(), { ByteArrayInputStream(original) }, {})
            assertEquals(hash(original), proof().getString("uploadedHash"))
            transport.end(id)
            try {
                transport.upload(id, "拒绝.wav", original.size.toLong(), { ByteArrayInputStream(original) }, {})
                fail("Closed sessions must reject file writes")
            } catch (error: EchoSongTransferException) { assertEquals(410, error.status) }
            assertEquals(0, proof().getInt("devices"))
        } finally { transport.close(); http.connectionPool.evictAll(); http.dispatcher.executorService.shutdown() }
    }
}
