package app.echo.android.playback

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EchoOfflinePlaybackIndexTest {
    @Test
    fun missingAndEmptyFilesAreIgnored() {
        EchoOfflinePlaybackIndex.replace(mapOf("t1" to "/no/such/file"))
        assertNull(EchoOfflinePlaybackIndex.uriFor("t1"))
        assertNull(EchoOfflinePlaybackIndex.uriFor("missing"))
    }

    @Test
    fun readyFileBecomesAFileUri() {
        val file = File.createTempFile("echo-offline", ".bin")
        file.writeBytes(byteArrayOf(1, 2, 3))
        try {
            EchoOfflinePlaybackIndex.replace(mapOf("t2" to file.absolutePath))
            val uri = EchoOfflinePlaybackIndex.uriFor("t2")
            assertTrue(uri!!.startsWith("file:"))
            assertTrue(uri.contains(file.name))
        } finally {
            file.delete()
            EchoOfflinePlaybackIndex.replace(emptyMap())
        }
    }
}
