package app.echo.android.connect

import app.echo.android.model.connect.*
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Prevent peer/companion route mixing, lost second pages and remote controls on a library-only host. */
class EchoBridgeConnectionTest {
    @Test fun discoveredBridgeKeepsDynamicPortAndDoesNotReusePcCredentials() {
        val device = EchoLinkDiscoveryPolicy.deviceFromResolved("bridge", "192.168.1.12", 49234,
            mapOf("deviceId" to "bridge-test", "direct" to "1", "library" to "1", "role" to "bridge",
                "remoteControl" to "0", "apiPath" to "/echo-link/peer/v1"))!!
        assertTrue(device.libraryOnly); assertFalse(device.requiresPairing); assertEquals(49234, device.port)
        assertEquals("", EchoLinkDiscoveryPolicy.tokenAfterSelecting(device, "192.168.1.12:49234", "old-pc-token"))
        assertFalse(EchoLinkDiscoveryPolicy.deviceFromResolved("pc", "192.168.1.12", 26789, mapOf("direct" to "1"))!!.libraryOnly)
    }

    @Test fun manualConnectionBrowsesBothPagesAndPhoneStreamWithoutRemoteCommands() = runBlocking {
        val requests = CopyOnWriteArrayList<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests += exchange.requestMethod + " " + exchange.requestURI.path
            assertEquals("1", exchange.requestHeaders.getFirst("X-ECHO-Link-Direct"))
            assertNull(exchange.requestHeaders.getFirst("Authorization"))
            val query = exchange.requestURI.rawQuery.orEmpty()
            val body = when (exchange.requestURI.path) {
                "/echo-link/peer/v1/device" -> """{"version":1,"device":{"name":"Test Bridge"},"capabilities":{"library":true,"playbackControl":false}}"""
                "/echo-link/peer/v1/catalog" -> {
                    val songs = query.contains("kind=songs")
                    val page = if (query.contains("page=2")) 2 else 1
                    val rows = JSONArray()
                    if (songs) for (i in (if (page == 1) 1..50 else 51..51)) rows.put(JSONObject()
                        .put("id", "track-$i").put("title", "Track $i").put("artist", "Fixture")
                        .put("durationMs", 20000).put("canPlay", i != 1))
                    JSONObject().put("catalog", JSONObject().put("pageSize", 50).put("page", page)
                        .put("total", if (songs) 51 else 0).put("entries", rows)).toString()
                }
                "/echo-link/peer/v1/stream" -> """{"track":{"id":"track-2","title":"Track 2","canPlay":true,"streamUrl":"http://127.0.0.1:${server.address.port}/echo-link/media/fixture"}}"""
                "/echo-link/peer/v1/lyrics" -> """{"lyrics":{"syncedText":"[00:01.00]Fixture"}}"""
                else -> "{}"
            }.toByteArray()
            exchange.sendResponseHeaders(if (exchange.requestURI.path == "/echo-link/v1/status") 404 else 200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }; exchange.close()
        }
        server.start()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val client = EchoRemoteClient(scope, statusPollIntervalMs = 10000)
        try {
            val endpoint = EchoPairingParser.parseManual("127.0.0.1:${server.address.port}")!!
            val transport = OkHttpEchoLinkTransport()
            val status = transport.fetchStatus(endpoint)
            assertTrue(status.libraryOnly); assertNull(status.playback)
            client.connect(endpoint)
            withTimeout(5000) { while (client.library.value.tracks.size != 50) delay(10) }
            assertTrue(client.status.value.endpoint!!.libraryOnly)
            assertFalse(client.library.value.tracks.first().canPlayOnPhone)
            client.loadMoreTracks()
            withTimeout(5000) { while (client.library.value.tracks.size != 51) delay(10) }
            assertEquals("track-51", client.library.value.tracks.last().id)
            val bridge = client.status.value.endpoint!!
            assertTrue(transport.resolveStream(bridge, "track-2").streamUrl.endsWith("/media/fixture"))
            assertEquals("[00:01.00]Fixture", transport.fetchLyrics(bridge, "track-2")!!.rawText)
            try { transport.sendCommand(bridge, EchoRemoteCommand.PlayPause); fail("Bridge must reject remote commands") }
            catch (error: EchoLinkHttpException) { assertEquals(405, error.statusCode) }
            assertFalse(requests.any { it.startsWith("POST") })
        } finally { client.disconnect(); scope.cancel(); server.stop(0) }
    }

    @Test fun rejectedDirectAuthorizationNeverProbesPeerApi() = runBlocking {
        val requests = CopyOnWriteArrayList<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests += exchange.requestURI.path
            exchange.sendResponseHeaders(401, -1); exchange.close()
        }
        server.start()
        try {
            val endpoint = EchoPairingParser.parseManual("127.0.0.1:${server.address.port}")!!
            try { OkHttpEchoLinkTransport().fetchStatus(endpoint); fail("401 must remain an authentication error") }
            catch (error: EchoLinkHttpException) { assertEquals(401, error.statusCode) }
            assertEquals(listOf("/echo-link/v1/status"), requests)
        } finally { server.stop(0) }
    }

    /** Optional interop fixture: a real native Bridge and synthetic SQLite/WAV, never user media. */
    @Test fun realAutomaticBridgeConnectsBrowsesPreparesPhonePlaybackAndRevokesOnStop() = runBlocking {
        val executable = System.getenv("ECHO_BRIDGE_TEST_EXE")
        org.junit.Assume.assumeTrue(executable != null)
        val evidence = java.io.File(System.getenv("ECHO_BRIDGE_TEST_EVIDENCE"))
        val process = ProcessBuilder(executable!!, "--sharing-smoke", System.getenv("ECHO_BRIDGE_TEST_DB"), evidence.path)
            .directory(java.io.File(System.getenv("ECHO_BRIDGE_TEST_WORKSPACE"))).redirectErrorStream(true)
            .redirectOutput(java.io.File(evidence, "native.log")).start()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val client = EchoRemoteClient(scope)
        try {
            val ready = java.io.File(evidence, "host-ready.json")
            withTimeout(8000) { while (!ready.isFile) delay(25) }
            val started = JSONObject(ready.readText())
            assertTrue(started.getBoolean("automaticSharing"))
            assertTrue(started.getBoolean("windowVisible"))
            val port = started.getInt("port")
            val endpoint = EchoPairingParser.parseManual("127.0.0.1:$port")!!
            val transport = OkHttpEchoLinkTransport()
            assertTrue(transport.fetchStatus(endpoint).libraryOnly)
            val bridge = endpoint.copy(libraryOnly = true)
            val tracks = transport.fetchTracks(bridge, "", 1, 500).tracks
            val track = tracks.first { it.canPlayOnPhone }
            assertTrue(EchoBridgeArtwork.handles(requireNotNull(track.artworkUrl)))
            for ((size, expected) in listOf(320 to 320, 512 to 768)) {
                val image = EchoBridgeArtwork.read(track.artworkUrl!!, size)!!
                val raster = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(image.bytes))
                assertEquals(expected, raster.width)
            }
            val lyrics = JSONObject(transport.fetchLyrics(bridge, track.id!!)!!.rawText)
            assertEquals(-120, lyrics.getInt("offsetMs"))
            assertEquals(1250.5, lyrics.getJSONArray("lines").getJSONObject(0).getDouble("timeMs"), 0.0)
            client.connect(endpoint)
            withTimeout(8000) { while (client.library.value.tracks.isEmpty()) delay(25) }
            assertEquals(EchoRemoteConnectionState.Connected, client.status.value.connectionState)
            assertTrue(client.status.value.endpoint!!.libraryOnly)
            if (System.getenv("ECHO_BRIDGE_TEST_PAGINATION") == "1") {
                client.refreshFolders("")
                withTimeout(8000) { while (client.library.value.loadingFolderPath != null) delay(10) }
                assertEquals(11, client.library.value.folderPageCount)
                assertEquals(50, client.library.value.folders.size)
                client.changeFolderPage(11)
                withTimeout(8000) { while (client.library.value.loadingFolderPath != null) delay(10) }
                assertEquals(21, client.library.value.folders.size)
                client.refreshFolders("root:" + System.getenv("ECHO_BRIDGE_TEST_ROOT_FOLDER"))
                withTimeout(8000) { while (client.library.value.loadingFolderPath != null) delay(10) }
                assertEquals(11, client.library.value.folderPageCount)
                client.changeFolderPage(11)
                withTimeout(8000) { while (client.library.value.loadingFolderPath != null) delay(10) }
                assertEquals(20, client.library.value.folders.size)
                assertEquals(21, client.library.value.folderTracks.size)
                assertEquals("page-track-0520", client.library.value.folderTracks.last().id)
            }
            val phoneQueue = CompletableDeferred<List<app.echo.android.model.library.EchoTrack>>()
            client.playTracksOnPhone(listOf(track), 0, onQueueReady = { queue, index ->
                assertEquals(0, index); phoneQueue.complete(queue)
            })
            val phoneTrack = withTimeout(8000) { phoneQueue.await().single() }
            assertTrue(app.echo.android.model.playback.EchoLinkPlaybackUri.isOneShotStreamUri(phoneTrack.uri))
            assertEquals(track.id, app.echo.android.model.playback.EchoLinkPlaybackUri.trackIdFromMediaId(phoneTrack.id))
            assertEquals(track.artworkUrl, phoneTrack.artworkUri)
            val stream = transport.resolveStream(bridge, track.id!!)
            OkHttpClient().newCall(Request.Builder().url(stream.streamUrl).header("Range", "bytes=0-43").build()).execute().use {
                assertEquals(206, it.code)
                val bytes = it.body!!.bytes()
                assertEquals(44, bytes.size); assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
            }
            java.io.File(evidence, "host-stop").writeText("stop")
            assertTrue(process.waitFor(8, java.util.concurrent.TimeUnit.SECONDS))
            val stopped = JSONObject(java.io.File(evidence, "host-stopped.json").readText())
            assertTrue(stopped.getBoolean("ok")); assertTrue(stopped.getBoolean("stoppedByButton"))
            assertTrue(stopped.getBoolean("closedPreferenceSaved"))
            try { transport.fetchTracks(bridge, "", 1, 50); fail("Catalog must close with sharing") }
            catch (_: java.io.IOException) { }
            try {
                OkHttpClient().newCall(Request.Builder().url(stream.streamUrl).build()).execute().use {
                    assertFalse("Media must close with sharing", it.isSuccessful)
                }
            } catch (_: java.io.IOException) { }
            val discovered = EchoLinkDiscoveryPolicy.deviceFromResolved("bridge-service", endpoint.host, port,
                mapOf("direct" to "1", "library" to "1", "role" to "bridge", "remoteControl" to "0", "apiPath" to "/echo-link/peer/v1"))!!
            val other = discovered.copy(id = "other", serviceName = "other-service", port = port + 1)
            assertEquals(listOf(other), EchoLinkDiscoveryPolicy.removeService(listOf(discovered, other), discovered.serviceName))
            java.io.File(evidence, "android-client.json").writeText(JSONObject().put("ok", true)
                .put("automaticWindowSharing", true).put("connected", true).put("browsed", true)
                .put("phoneQueuePrepared", true).put("originalRangeReadable", true).put("catalogRevoked", true)
                .put("artwork320And768", true).put("structuredLyrics", true)
                .put("streamRevoked", true).put("discoveryRemovalPolicy", true).toString())
        } finally {
            client.disconnect(); scope.cancel()
            java.io.File(evidence, "host-stop").writeText("stop")
            if (!process.waitFor(4, java.util.concurrent.TimeUnit.SECONDS)) process.destroyForcibly()
        }
    }
}
