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
        EchoRemotePlaybackAuthRegistry.replaceJellyfinCredentials(emptyList())
    }

    @After
    fun clearRegistry() {
        EchoRemotePlaybackAuthRegistry.replaceWebDavCredentials(emptyList())
        EchoRemotePlaybackAuthRegistry.replaceSubsonicCredentials(emptyList())
        EchoRemotePlaybackAuthRegistry.replaceJellyfinCredentials(emptyList())
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
    fun unsupportedFormatFallbackAddsMp3Once() {
        val original = "https://navidrome.example/rest/stream.view?id=s1"
        val fallback = subsonicUnsupportedFormatFallbackUrl(original)
        assertTrue(fallback!!.contains("format=mp3"))
        assertTrue(fallback.contains("maxBitRate=320"))
        assertEquals("s1", queryMap(fallback)["id"])
        assertEquals(null, subsonicUnsupportedFormatFallbackUrl(fallback))
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

    @Test
    fun resolveSignsJellyfinStreamAndArtworkWithApiKey() {
        EchoRemotePlaybackAuthRegistry.replaceJellyfinCredentials(
            listOf(
                EchoJellyfinPlaybackCredential(
                    baseUrl = "http://nas:8096",
                    accessToken = "tok-1",
                ),
            ),
        )
        val stream = "http://nas:8096/Audio/abc/stream?static=true"
        val artwork = "http://nas:8096/Items/abc/Images/Primary?maxWidth=600&tag=t1"
        assertEquals("tok-1", queryMap(EchoRemotePlaybackAuthRegistry.resolveJellyfinUrl(stream))["api_key"])
        assertEquals("tok-1", queryMap(EchoRemotePlaybackAuthRegistry.resolveJellyfinUrl(artwork))["api_key"])
        assertTrue(queueRequiresJellyfinAuth(listOf(stream)))
        assertTrue(EchoRemotePlaybackAuthRegistry.isJellyfinAuthReadyForUris(listOf(stream, artwork)))
        assertFalse(
            EchoRemotePlaybackAuthRegistry.isJellyfinAuthReadyForUris(
                listOf("http://other:8096/Audio/abc/stream?static=true"),
            ),
        )
    }

    @Test
    fun jellyfinCacheIdentityIgnoresRotatingApiKey() {
        EchoRemotePlaybackAuthRegistry.replaceJellyfinCredentials(
            listOf(
                EchoJellyfinPlaybackCredential(
                    baseUrl = "http://nas:8096",
                    accessToken = "tok-1",
                ),
            ),
        )
        val first = "http://nas:8096/Audio/abc/stream?static=true&api_key=tok-1"
        val second = "http://nas:8096/Audio/abc/stream?static=true&api_key=tok-2"
        assertEquals(
            EchoRemotePlaybackAuthRegistry.cacheIdentity(first, emptyMap()),
            EchoRemotePlaybackAuthRegistry.cacheIdentity(second, emptyMap()),
        )
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
