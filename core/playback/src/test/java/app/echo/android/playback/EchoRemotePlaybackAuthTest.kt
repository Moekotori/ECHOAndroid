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
    @Test
    fun echoLinkCacheIdentitySeparatesServersEvenWithTheSameTrackAndToken() {
        val path = "/echo-link/media/token"
        val first = EchoRemotePlaybackAuthRegistry.cacheIdentity("http://192.168.1.20:26789$path", emptyMap())
        val otherHost = EchoRemotePlaybackAuthRegistry.cacheIdentity("http://192.168.1.21:26789$path", emptyMap())
        val otherPort = EchoRemotePlaybackAuthRegistry.cacheIdentity("http://192.168.1.20:26790$path", emptyMap())
        val otherScheme = EchoRemotePlaybackAuthRegistry.cacheIdentity("https://192.168.1.20:26789$path", emptyMap())
        assertNotEquals(first, otherHost)
        assertNotEquals(first, otherPort)
        assertNotEquals(first, otherScheme)
    }

    @Test
    fun echoLinkCacheIdentityRemainsStableWhenStreamTokenPathRotates() {
        assertEquals(
            EchoRemotePlaybackAuthRegistry.cacheIdentity("http://pc:26789/echo-link/media/first", emptyMap()),
            EchoRemotePlaybackAuthRegistry.cacheIdentity("http://pc:26789/echo-link/media/second", emptyMap()),
        )
        assertEquals(
            EchoRemotePlaybackAuthRegistry.cacheIdentity("https://PC/echo-link/media/first", emptyMap()),
            EchoRemotePlaybackAuthRegistry.cacheIdentity("https://pc:443/echo-link/media/second", emptyMap()),
        )
    }

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
        assertEquals("raw", query["format"])
        assertEquals("alice", query["u"])
        assertFalse(query["s"].isNullOrBlank())
        assertEquals(md5Hex("secret${query["s"]}"), query["t"])
        assertNull(queryMap(unsigned)["t"])
        assertNull(queryMap(unsigned)["s"])
    }

    @Test
    fun playbackRequestSignsSubsonicWithoutLeakingPasswordHeader() {
        EchoRemotePlaybackAuthRegistry.replaceSubsonicCredentials(
            listOf(
                EchoSubsonicPlaybackCredential(
                    baseUrl = "https://navidrome.example",
                    username = "alice",
                    password = "secret",
                ),
            ),
        )
        val request = EchoRemotePlaybackAuthRegistry.playbackRequest(
            "https://navidrome.example/rest/stream.view?id=s1",
        )
        val query = queryMap(request.url)
        assertEquals("alice", query["u"])
        assertFalse(query["t"].isNullOrBlank())
        assertTrue(request.headers.isEmpty())
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
    fun streamSigningDefaultsToRawButPreservesExplicitTranscoding() {
        val credential = EchoSubsonicPlaybackCredential("https://navidrome.example", "alice", "secret")
        val base = "https://navidrome.example/music/rest/stream.view?id=s1"
        assertEquals("raw", queryMap(applySubsonicTokenAuth("$base&format=", credential, "salt"))["format"])
        val fallback = requireNotNull(subsonicUnsupportedFormatFallbackUrl(base))
        val signedFallback = queryMap(applySubsonicTokenAuth(fallback, credential, "salt"))
        assertEquals("mp3", signedFallback["format"])
        assertEquals("320", signedFallback["maxBitRate"])
        assertEquals("opus", queryMap(applySubsonicTokenAuth("$base&format=opus", credential, "salt"))["format"])
        val extensionless = "https://navidrome.example/rest/stream?id=s1"
        assertEquals("raw", queryMap(applySubsonicTokenAuth(extensionless, credential, "salt"))["format"])
        assertNull(queryMap(applySubsonicTokenAuth("https://navidrome.example/rest/getCoverArt.view?id=c1", credential, "salt"))["format"])
        assertNull(subsonicUnsupportedFormatFallbackUrl("https://navidrome.example/rest/streamSomething.view?id=s1"))
    }

    @Test
    fun cacheNamespaceSeparatesOriginalAndTranscodedStreams() {
        EchoRemotePlaybackAuthRegistry.replaceSubsonicCredentials(
            listOf(EchoSubsonicPlaybackCredential("https://navidrome.example", "alice", "secret")),
        )
        val base = "https://navidrome.example/rest/stream.view?id=s1"
        val original = EchoRemotePlaybackAuthRegistry.cacheIdentity(base, emptyMap())
        assertEquals(original, EchoRemotePlaybackAuthRegistry.cacheIdentity("$base&format=raw", emptyMap()))
        val fallback = requireNotNull(subsonicUnsupportedFormatFallbackUrl(base))
        val mp3 = EchoRemotePlaybackAuthRegistry.cacheIdentity(fallback, emptyMap())
        assertNotEquals(original, mp3)
        assertEquals(
            mp3,
            EchoRemotePlaybackAuthRegistry.cacheIdentity(
                EchoRemotePlaybackAuthRegistry.resolveSubsonicUrl(fallback), emptyMap(),
            ),
        )
        assertNotEquals(mp3, EchoRemotePlaybackAuthRegistry.cacheIdentity("$base&format=mp3&maxBitRate=128", emptyMap()))
        assertNotEquals(original, EchoRemotePlaybackAuthRegistry.cacheIdentity("$base&format=raw&timeOffset=20", emptyMap()))
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
