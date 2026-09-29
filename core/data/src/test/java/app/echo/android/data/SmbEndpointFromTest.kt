package app.echo.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmbEndpointFromTest {
    @Test
    fun splitsDomainFromUsername() {
        val endpoint = smbEndpointFrom("smb://nas/Music/Albums", "HOME\\moe", "secret")!!
        assertEquals("nas", endpoint.normalizedHost)
        assertEquals("Music", endpoint.normalizedShare)
        assertEquals("Albums", endpoint.normalizedBasePath)
        assertEquals("moe", endpoint.username)
        assertEquals("HOME", endpoint.domain)
        assertEquals("secret", endpoint.password)
    }

    @Test
    fun blankUserIsGuestAndDropsPassword() {
        val endpoint = smbEndpointFrom("\\\\nas\\Public", "", "ignored")!!
        assertTrue(endpoint.isGuest)
        assertEquals("", endpoint.password)
    }

    @Test
    fun invalidAddressIsNull() {
        assertNull(smbEndpointFrom("nas", "u", "p"))
        assertNull(smbEndpointFrom(null, "u", "p"))
    }

    @Test
    fun sourceIdIsStableAndPrefixed() {
        val a = smbEndpointFrom("smb://NAS/music", "moe", "x")!!
        val b = smbEndpointFrom("smb://nas/Music/sub", "moe", "y")!!
        assertEquals(a.sourceId, b.sourceId)
        assertTrue(a.sourceId.startsWith("smb:"))
    }
}
