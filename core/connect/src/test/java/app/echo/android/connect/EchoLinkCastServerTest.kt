package app.echo.android.connect

import java.io.File
import java.io.FileInputStream
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoLinkCastServerTest {
    @Test
    fun servesFullBodyRangeAndRejectsUnknownToken() {
        val payload = ByteArray(256) { index -> index.toByte() }
        val file = File.createTempFile("echo-cast", ".bin")
        file.writeBytes(payload)
        val server = EchoLinkCastServer(
            openBody = fileOpener(file),
            bindHost = "127.0.0.1",
        )
        try {
            val port = server.start()
            val token = EchoLinkCastPolicy.newToken()
            val items = server.publish(
                listOf(
                    EchoLinkCastPublication(
                        token = token,
                        trackId = "track-1",
                        uri = file.toURI().toString(),
                        title = "Song",
                        artist = "Artist",
                    ),
                ),
                host = "127.0.0.1",
                boundPort = port,
            )
            assertEquals(1, items.size)
            val client = OkHttpClient()
            val url = items.single().streamUrl
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                assertEquals(200, response.code)
                assertEquals("bytes", response.header("Accept-Ranges"))
                assertTrue(response.body!!.bytes().contentEquals(payload))
            }
            client.newCall(
                Request.Builder().url(url).header("Range", "bytes=10-19").build(),
            ).execute().use { response ->
                assertEquals(206, response.code)
                assertEquals("bytes 10-19/256", response.header("Content-Range"))
                assertTrue(response.body!!.bytes().contentEquals(payload.copyOfRange(10, 20)))
            }
            client.newCall(Request.Builder().url(url + "nope").build()).execute().use { response ->
                assertEquals(404, response.code)
            }
        } finally {
            server.stop()
            file.delete()
        }
    }

    @Test
    fun rejectsDisallowedPeer() {
        val file = File.createTempFile("echo-cast-peer", ".bin")
        file.writeBytes(byteArrayOf(1, 2, 3, 4))
        val server = EchoLinkCastServer(
            openBody = fileOpener(file),
            allowedPeerHost = { "203.0.113.9" },
            bindHost = "127.0.0.1",
        )
        try {
            val port = server.start()
            val token = EchoLinkCastPolicy.newToken()
            val items = server.publish(
                listOf(
                    EchoLinkCastPublication(
                        token = token,
                        trackId = "track-1",
                        uri = file.toURI().toString(),
                        title = "Song",
                        artist = "Artist",
                    ),
                ),
                host = "127.0.0.1",
                boundPort = port,
            )
            OkHttpClient().newCall(Request.Builder().url(items.single().streamUrl).build()).execute().use { response ->
                assertEquals(403, response.code)
            }
        } finally {
            server.stop()
            file.delete()
        }
    }

    @Test
    fun advertisesSniffedFlacOnOriginalStream() {
        val payload = ByteArray(64)
        "fLaC".encodeToByteArray().copyInto(payload)
        payload[7] = 34
        val packedRate = 96_000
        payload[18] = (packedRate shr 12).toByte()
        payload[19] = ((packedRate shr 4) and 0xFF).toByte()
        payload[20] = (((packedRate and 0x0F) shl 4) or (1 shl 1) or (23 shr 4)).toByte()
        payload[21] = ((23 and 0x0F) shl 4).toByte()
        val file = File.createTempFile("echo-cast-flac", ".bin")
        file.writeBytes(payload)
        val server = EchoLinkCastServer(openBody = fileOpener(file), bindHost = "127.0.0.1")
        try {
            val port = server.start()
            val token = EchoLinkCastPolicy.newToken()
            val items = server.publish(
                listOf(
                    EchoLinkCastPublication(
                        token = token,
                        trackId = "hires",
                        uri = file.toURI().toString(),
                        title = "Song",
                        artist = "Artist",
                    ),
                ),
                host = "127.0.0.1",
                boundPort = port,
            )
            OkHttpClient().newCall(Request.Builder().url(items.single().streamUrl).build()).execute().use { response ->
                assertEquals(200, response.code)
                assertEquals("audio/flac", response.header("Content-Type"))
                assertEquals("flac", response.header("X-ECHO-Link-Codec"))
                assertEquals("96000", response.header("X-ECHO-Link-Sample-Rate"))
                assertEquals("24", response.header("X-ECHO-Link-Bit-Depth"))
                assertEquals("2", response.header("X-ECHO-Link-Channels"))
                assertEquals("1", response.header("X-ECHO-Link-Lossless"))
            }
        } finally {
            server.stop()
            file.delete()
        }
    }

    @Test
    fun servesLocalArtworkUrl() {
        val audio = File.createTempFile("echo-cast-audio", ".bin")
        val art = File.createTempFile("echo-cast-art", ".jpg")
        audio.writeBytes(byteArrayOf(1, 2, 3, 4))
        art.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
        val server = EchoLinkCastServer(openBody = { uri, start ->
            val file = if (uri == art.toURI().toString()) art else audio
            val stream = java.io.FileInputStream(file)
            if (start > 0L) stream.channel.position(start)
            EchoLinkCastBody(stream, file.length(), "application/octet-stream")
        }, bindHost = "127.0.0.1")
        try {
            val port = server.start()
            val token = EchoLinkCastPolicy.newToken()
            val artToken = EchoLinkCastPolicy.newToken()
            val items = server.publish(
                listOf(
                    EchoLinkCastPublication(
                        token = token,
                        trackId = "t",
                        uri = audio.toURI().toString(),
                        title = "Song",
                        artist = "Artist",
                        artworkUrl = art.toURI().toString(),
                        artworkToken = artToken,
                    ),
                ),
                host = "127.0.0.1",
                boundPort = port,
            )
            val artworkUrl = items.single().artworkUrl
            checkNotNull(artworkUrl)
            assertTrue(artworkUrl.contains("/echo-link/cast-art/"))
            OkHttpClient().newCall(Request.Builder().url(artworkUrl).build()).execute().use { response ->
                assertEquals(200, response.code)
                assertEquals("image/jpeg", response.header("Content-Type"))
                assertEquals(3, response.body!!.bytes().size)
            }
        } finally {
            server.stop()
            audio.delete()
            art.delete()
        }
    }

    @Test
    fun hostnameAllowListDoesNotBlockLanIpPeer() {
        val file = File.createTempFile("echo-cast-host", ".bin")
        file.writeBytes(byteArrayOf(9, 8, 7, 6))
        val server = EchoLinkCastServer(
            openBody = fileOpener(file),
            allowedPeerHost = { "pc.local" },
            bindHost = "127.0.0.1",
        )
        try {
            val port = server.start()
            val token = EchoLinkCastPolicy.newToken()
            val items = server.publish(
                listOf(
                    EchoLinkCastPublication(
                        token = token,
                        trackId = "track-1",
                        uri = file.toURI().toString(),
                        title = "Song",
                        artist = "Artist",
                    ),
                ),
                host = "127.0.0.1",
                boundPort = port,
            )
            OkHttpClient().newCall(Request.Builder().url(items.single().streamUrl).build()).execute().use { response ->
                assertEquals(200, response.code)
            }
            assertFalse(server.isIdle(System.currentTimeMillis(), timeoutMs = 60_000))
            assertTrue(server.isIdle(System.currentTimeMillis() + 2_000, timeoutMs = 1_000))
        } finally {
            server.stop()
            file.delete()
        }
    }

    private fun fileOpener(file: File) = EchoLinkCastBodyFactory { _, startByte ->
        val stream = FileInputStream(file)
        if (startByte > 0L) stream.channel.position(startByte)
        EchoLinkCastBody(
            stream = stream,
            totalLength = file.length(),
            mimeType = "application/octet-stream",
        )
    }
}
