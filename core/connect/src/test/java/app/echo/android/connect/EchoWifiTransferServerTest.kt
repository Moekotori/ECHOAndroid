package app.echo.android.connect

import app.echo.android.model.connect.EchoWifiTransferPolicy
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference

class EchoWifiTransferServerTest {
    @Test fun stoppingAPartialUploadUnblocksTheReceiverAndRunsItsCleanup() {
        val entered = java.util.concurrent.CountDownLatch(1)
        val cleaned = java.util.concurrent.CountDownLatch(1)
        val server = EchoWifiTransferServer("127.0.0.1", byteArrayOf(), { name, length, input ->
            entered.countDown()
            try {
                repeat(length.toInt()) { if (input.read() < 0) throw java.io.EOFException() }
                name
            } finally { cleaned.countDown() }
        })
        val address = java.net.URI(server.start())
        try {
            val paired = Socket(address.host, address.port).use { socket ->
                socket.soTimeout = 2000
                socket.getOutputStream().write(("POST /pair HTTP/1.1\r\nHost: ${address.host}:${address.port}\r\n" +
                    "Content-Length: 0\r\nX-Echo-Code: ${server.pairingCode}\r\n\r\n").toByteArray())
                socket.getInputStream().readBytes().toString(Charsets.UTF_8)
            }
            val token = JSONObject(paired.substringAfter("\r\n\r\n")).getString("token")
            Socket(address.host, address.port).use { socket ->
                socket.getOutputStream().apply {
                    write(("POST /upload?name=track.flac HTTP/1.1\r\nHost: ${address.host}:${address.port}\r\n" +
                        "Content-Length: 10000\r\nX-Echo-Token: $token\r\n\r\n").toByteArray())
                    write(byteArrayOf(1, 2, 3)); flush()
                }
                assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS))
                server.close()
                assertTrue("closing must promptly release the receiver into its finally block",
                    cleaned.await(2, java.util.concurrent.TimeUnit.SECONDS))
            }
        } finally { server.close() }
    }

    @Test fun pairingAndUploadAreBoundedAndUnrelatedRoutesStayPrivate() {
        val received = AtomicReference<ByteArray>()
        val server = EchoWifiTransferServer("127.0.0.1", "test-page".toByteArray(), { name, length, input ->
            require(name == "Album/track.flac")
            val data = ByteArray(length.toInt())
            var read = 0
            while (read < data.size) { val count = input.read(data, read, data.size - read); check(count > 0); read += count }
            received.set(data); name
        })
        server.use {
            val address = java.net.URI(server.start())
            fun request(method: String, path: String, extra: String = "", body: ByteArray = byteArrayOf()): String = Socket(address.host, address.port).use { socket ->
                socket.soTimeout = 3000
                val head = "$method $path HTTP/1.1\r\nHost: ${address.host}:${address.port}\r\nContent-Length: ${body.size}\r\n$extra\r\n"
                socket.getOutputStream().apply { write(head.toByteArray()); write(body); flush() }
                socket.getInputStream().readBytes().toString(Charsets.UTF_8)
            }
            assertTrue(request("POST", "/upload?name=track.flac").startsWith("HTTP/1.1 403"))
            assertTrue(request("POST", "/pair", "X-Echo-Code: wrong\r\n").startsWith("HTTP/1.1 403"))
            val paired = request("POST", "/pair", "X-Echo-Code: ${server.pairingCode}\r\n")
            val token = JSONObject(paired.substringAfter("\r\n\r\n")).getString("token")
            val auth = "X-Echo-Token: $token\r\n"
            assertTrue(request("POST", "/upload?name=Album%2Ftrack.flac", auth, byteArrayOf(1, 2, 3)).startsWith("HTTP/1.1 200"))
            assertArrayEquals(byteArrayOf(1, 2, 3), received.get())
            assertTrue(request("POST", "/upload?name=..%2Ftrack.flac", auth, byteArrayOf(1)).startsWith("HTTP/1.1 400"))
            assertTrue(request("GET", "/echo-link/phone/v1/library", auth).startsWith("HTTP/1.1 404"))
            assertTrue(request("POST", "/finish", auth + "Origin: http://elsewhere.invalid\r\n").startsWith("HTTP/1.1 403"))
        }
    }

    @Test fun pathValidationRejectsEscapesAbsoluteAndDeepPaths() {
        listOf("../x.mp3", "/x.mp3", "a\\x.mp3", "a//x.mp3", "a/./x.mp3", "a/../../x.mp3", "a/".repeat(10) + "x.mp3").forEach {
            assertTrue(it, runCatching { EchoWifiTransferPolicy.pathSegments(it) }.isFailure)
        }
        assertEquals(listOf("专辑", "曲目.flac"), EchoWifiTransferPolicy.pathSegments("专辑/曲目.flac"))
    }
}
