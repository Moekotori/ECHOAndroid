package app.echo.android.playback

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EchoRemotePlaybackAuthTest {
    @Before
    fun resetRegistry() {
        EchoRemotePlaybackAuthRegistry.replaceWebDavCredentials(emptyList())
        EchoRemotePlaybackAuthRegistry.replaceSubsonicCredentials(emptyList())
    }

    @After
    fun clearRegistry() {
        EchoRemotePlaybackAuthRegistry.replaceWebDavCredentials(emptyList())
        EchoRemotePlaybackAuthRegistry.replaceSubsonicCredentials(emptyList())
    }

    @Test
    fun resolveSignsUnsignedStreamWithCurrentCredentials() {
        EchoRemotePlaybackAuthRegistry.replaceSubsonicCredentials(
            listOf(
                EchoSubsonicPlaybackCredential(
                    baseUrl = "https://navidrome.example",
                    username = "alice",
                    password = "secret",
                ),
            ),
        )
        val unsigned = "https://navidrome.example/rest/stream.view?id=s1"
        val resolved = EchoRemotePlaybackAuthRegistry.resolveSubsonicUrl(unsigned)
        val query = queryMap(resolved)

        assertEquals("s1", query["id"])
        assertEquals("alice", query["u"])
        assertFalse(query["s"].isNullOrBlank())
        assertEquals(md5Hex("secret${query["s"]}"), query["t"])
        assertNull(queryMap(unsigned)["t"])
        assertNull(queryMap(unsigned)["s"])
    }

    @Test
    fun resolveLeavesUnsignedStreamWhenCredentialsCleared() {
        val unsigned = "https://navidrome.example/rest/stream.view?id=s1"
        val resolved = EchoRemotePlaybackAuthRegistry.resolveSubsonicUrl(unsigned)
        assertEquals(unsigned, resolved)
        val query = queryMap(resolved)
        assertNull(query["t"])
        assertNull(query["s"])
        assertNull(query["u"])
    }

    @Test
    fun resolveSignsUnsignedCoverArtWithCurrentCredentials() {
        EchoRemotePlaybackAuthRegistry.replaceSubsonicCredentials(
            listOf(
                EchoSubsonicPlaybackCredential(
                    baseUrl = "https://navidrome.example",
                    username = "alice",
                    password = "secret",
                ),
            ),
        )
        val unsigned = "https://navidrome.example/rest/getCoverArt.view?id=cover-1"
        val resolved = EchoRemotePlaybackAuthRegistry.resolveSubsonicUrl(unsigned)
        val query = queryMap(resolved)
        assertEquals("cover-1", query["id"])
        assertEquals("alice", query["u"])
        assertEquals(md5Hex("secret${query["s"]}"), query["t"])
    }

    @Test
    fun subsonicAuthReadyRequiresMatchingBaseUrl() {
        val queue = listOf("https://navidrome.example/rest/stream.view?id=s1")
        assertTrue(queueRequiresSubsonicAuth(queue))
        assertFalse(EchoRemotePlaybackAuthRegistry.isSubsonicAuthReadyForUris(queue))
        EchoRemotePlaybackAuthRegistry.replaceSubsonicCredentials(
            listOf(
                EchoSubsonicPlaybackCredential(
                    baseUrl = "https://navidrome.example",
                    username = "alice",
                    password = "secret",
                ),
            ),
        )
        assertTrue(EchoRemotePlaybackAuthRegistry.isSubsonicAuthReadyForUris(queue))
        assertFalse(
            EchoRemotePlaybackAuthRegistry.isSubsonicAuthReadyForUris(
                listOf("https://other.example/rest/stream.view?id=s1"),
            ),
        )
    }

    @Test
    fun cacheIdentityIgnoresRotatingSubsonicTokensForSameUser() {
        EchoRemotePlaybackAuthRegistry.replaceSubsonicCredentials(
            listOf(
                EchoSubsonicPlaybackCredential(
                    baseUrl = "https://navidrome.example",
                    username = "alice",
                    password = "secret",
                ),
            ),
        )
        val first = "https://navidrome.example/rest/stream.view?id=s1&u=alice&t=aaa&s=salt-1"
        val second = "https://navidrome.example/rest/stream.view?id=s1&u=alice&t=bbb&s=salt-2"
        assertEquals(
            EchoRemotePlaybackAuthRegistry.cacheIdentity(first, emptyMap()),
            EchoRemotePlaybackAuthRegistry.cacheIdentity(second, emptyMap()),
        )
    }

    @Test
    fun cacheIdentityDiffersAcrossSubsonicUsers() {
        val url = "https://navidrome.example/rest/stream.view?id=s1"
        EchoRemotePlaybackAuthRegistry.replaceSubsonicCredentials(
            listOf(
                EchoSubsonicPlaybackCredential(
                    baseUrl = "https://navidrome.example",
                    username = "alice",
                    password = "secret",
                ),
            ),
        )
        val aliceIdentity = EchoRemotePlaybackAuthRegistry.cacheIdentity(url, emptyMap())
        EchoRemotePlaybackAuthRegistry.replaceSubsonicCredentials(
            listOf(
                EchoSubsonicPlaybackCredential(
                    baseUrl = "https://navidrome.example",
                    username = "bob",
                    password = "other",
                ),
            ),
        )
        val bobIdentity = EchoRemotePlaybackAuthRegistry.cacheIdentity(url, emptyMap())
        assertNotEquals(aliceIdentity, bobIdentity)
    }
}

private fun queryMap(url: String): Map<String, String> {
    val query = url.substringAfter('?', missingDelimiterValue = "")
    if (query.isEmpty()) return emptyMap()
    return query.split('&').mapNotNull { part ->
        val separator = part.indexOf('=')
        if (separator <= 0) {
            null
        } else {
            part.substring(0, separator) to part.substring(separator + 1)
        }
    }.toMap()
}

private fun md5Hex(value: String): String {
    val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray(StandardCharsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
