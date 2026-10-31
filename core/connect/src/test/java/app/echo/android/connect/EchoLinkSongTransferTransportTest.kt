package app.echo.android.connect

import app.echo.android.model.connect.*
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayInputStream
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class EchoLinkSongTransferTransportTest {
    @Test fun sendsAndReceivesOriginalBytesWithTheCurrentConnectionHeaders() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val bytes = "original-song".toByteArray()
        val requests = java.util.concurrent.LinkedBlockingQueue<Pair<String?, ByteArray>>()
        server.createContext("/echo-link/v1/transfers/") { exchange ->
            val request = exchange.requestBody.use { it.readBytes() }
            requests.add(exchange.requestHeaders.getFirst("X-ECHO-Link-Transfer") to request)
            val body = if (exchange.requestURI.path.endsWith("/file")) bytes else """{"id":"session"}""".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }; exchange.close()
        }
        server.start()
        val endpoint = EchoRemoteEndpoint("pc", "PC", "127.0.0.1", server.address.port, "paired-token")
        val transport = EchoLinkSongTransferTransport(endpoint)
        try {
            val id = transport.start("Phone", true)
            assertEquals("session", id)
            assertNull(requests.poll(1, TimeUnit.SECONDS)!!.first)
            transport.upload(id, "Song.mp3", bytes.size.toLong(), { ByteArrayInputStream(bytes) }, {})
            val upload = requests.poll(1, TimeUnit.SECONDS)!!
            assertEquals(id, upload.first); assertArrayEquals(bytes, upload.second)
            val item = EchoSongTransferItem("offer", "Song.flac", bytes.size.toLong(), direction = "to-phone")
            val name = transport.download(id, item) { fileName, size, input, check ->
                check(); assertEquals(bytes.size.toLong(), size); assertArrayEquals(bytes, input.readBytes()); fileName
            }
            assertEquals("Song.flac", name)
            assertEquals(id, requests.poll(1, TimeUnit.SECONDS)!!.first)
        } finally { transport.close(); server.stop(0) }
    }

    @Test fun cancellationClosesTheDownloadBeforeTheSaverCanPublish() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val release = CountDownLatch(1)
        server.createContext("/echo-link/v1/transfers/file") { exchange ->
            runCatching {
                exchange.sendResponseHeaders(200, 8)
                exchange.responseBody.use { output ->
                    output.write(byteArrayOf(1)); output.flush()
                    release.await(1, TimeUnit.SECONDS)
                    output.write(ByteArray(7))
                }
            }
            exchange.close()
        }
        server.start()
        val transport = EchoLinkSongTransferTransport(EchoRemoteEndpoint("pc", "PC", "127.0.0.1", server.address.port, "token"))
        val entered = CompletableDeferred<Unit>(); val published = AtomicBoolean()
        try {
            val job = async {
                transport.download("session", EchoSongTransferItem("offer", "Song.mp3", 8, direction = "to-phone")) { _, _, input, check ->
                    input.read(); entered.complete(Unit); input.readBytes(); check(); published.set(true); "Song.mp3"
                }
            }
            withTimeout(2000) { entered.await() }
            job.cancelAndJoin(); release.countDown()
            assertFalse(published.get())
        } finally { release.countDown(); transport.close(); server.stop(0) }
    }

    @Test fun reconnectingToTheSamePcChangesTheConnectionIdentity() = runBlocking {
        val client = EchoRemoteClient(this, FakeEchoLinkTransport())
        val endpoint = EchoRemoteEndpoint("pc", "PC", "127.0.0.1", 26789, "token")
        suspend fun connected() { withTimeout(1000) { while (client.status.value.connectionState != EchoRemoteConnectionState.Connected) delay(5) } }
        try {
            client.connect(endpoint, false); connected(); val first = client.status.value.connectionId
            client.disconnect(); client.connect(endpoint, false); connected()
            assertTrue(client.status.value.connectionId > first)
        } finally { client.disconnect() }
    }
}
