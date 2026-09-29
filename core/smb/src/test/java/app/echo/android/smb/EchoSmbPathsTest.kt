package app.echo.android.smb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EchoSmbPathsTest {
    @Test
    fun roundTripsPathWithSpacesAndCjk() {
        val uri = EchoSmbPaths.uri("nas.local", 445, "Music", "周杰伦/七里香 (2004)/01 我的地盘.flac")
        val parsed = EchoSmbPaths.parse(uri)!!
        assertEquals("nas.local", parsed.host)
        assertEquals(445, parsed.port)
        assertEquals("Music", parsed.share)
        assertEquals("周杰伦/七里香 (2004)/01 我的地盘.flac", parsed.path)
    }

    @Test
    fun keepsCustomPort() {
        val uri = EchoSmbPaths.uri("192.168.1.9", 1445, "share", "a.mp3")
        assertEquals("smb://192.168.1.9:1445/share/a.mp3", uri)
        assertEquals(1445, EchoSmbPaths.parse(uri)!!.port)
    }

    @Test
    fun rejectsUriWithoutShareOrScheme() {
        assertNull(EchoSmbPaths.parse("smb://host/"))
        assertNull(EchoSmbPaths.parse("https://host/share/a.mp3"))
    }

    @Test
    fun parsesUserAddressForms() {
        assertEquals(
            EchoSmbAddress("nas", 445, "Music", "Albums/Jazz"),
            EchoSmbEndpoint.parseAddress("smb://nas/Music/Albums/Jazz/"),
        )
        assertEquals(
            EchoSmbAddress("nas", 445, "Music", ""),
            EchoSmbEndpoint.parseAddress("\\\\nas\\Music"),
        )
        assertEquals(
            EchoSmbAddress("10.0.0.2", 1445, "m", ""),
            EchoSmbEndpoint.parseAddress("10.0.0.2:1445/m"),
        )
        assertNull(EchoSmbEndpoint.parseAddress("nas"))
    }

    @Test
    fun shareKeyIgnoresCase() {
        val endpoint = EchoSmbEndpoint(host = "NAS.local", share = "Music")
        assertEquals(EchoSmbPaths.parse("smb://nas.local/music/a.flac")!!.shareKey, endpoint.shareKey)
    }
}
