package app.echo.android.connect

import java.io.ByteArrayInputStream
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test

class EchoPhoneArtworkResponseTest {
    @Test
    fun sharedArtworkUsesThumbnailBodyMimeAndRangeInsteadOfRawImageFile() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val server = EchoLinkCastServer(
            openBody = { _, _ -> error("must not export the original cover") },
            bindHost = "127.0.0.1",
            openArtwork = { publication, start ->
                assertEquals("file:/original-cover.png", publication.artworkUrl)
                EchoLinkCastBody(ByteArrayInputStream(bytes, start.toInt(), bytes.size - start.toInt()),
                    bytes.size.toLong(), "image/jpeg")
            },
        )
        try {
            val port = server.start()
            val url = server.publish(listOf(EchoLinkCastPublication(
                EchoLinkCastPolicy.newToken(), "track", "file:/audio.flac", title = "Song", artist = "Artist",
                artworkUrl = "file:/original-cover.png", artworkToken = EchoLinkCastPolicy.newToken(),
            )), "127.0.0.1", port).single().artworkUrl!!
            val client = OkHttpClient()
            client.newCall(Request.Builder().url(url).build()).execute().use {
                assertEquals(200, it.code)
                assertEquals("image/jpeg", it.header("Content-Type"))
                assertArrayEquals(bytes, it.body!!.bytes())
            }
            client.newCall(Request.Builder().url(url).header("Range", "bytes=1-2").build()).execute().use {
                assertEquals(206, it.code)
                assertEquals("bytes 1-2/4", it.header("Content-Range"))
                assertArrayEquals(byteArrayOf(2, 3), it.body!!.bytes())
            }
        } finally {
            server.stop()
        }
    }
}
