package app.echo.android.connect

import app.echo.android.model.connect.EchoRemoteAlbum
import app.echo.android.model.connect.EchoRemoteCommand
import app.echo.android.model.connect.EchoRemoteConnectionState
import app.echo.android.model.connect.EchoRemoteEndpoint
import app.echo.android.model.connect.EchoRemoteFolder
import app.echo.android.model.connect.EchoRemoteLyrics
import app.echo.android.model.connect.EchoRemotePlaybackSnapshot
import app.echo.android.model.connect.EchoRemotePlaylist
import app.echo.android.model.connect.EchoRemoteStreamItem
import app.echo.android.model.connect.EchoRemoteTrack
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EchoRemoteClientTest {
    private lateinit var originalLocale: Locale

    @Before
    fun pinEnglishLocale() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    private val endpoint = EchoRemoteEndpoint(
        id = "192.168.1.20:26789",
        name = "PC ECHO",
        host = "192.168.1.20",
        port = 26789,
        token = "abcdefghijklmnop",
    )

    @Test
    fun failedSelectedTrackDoesNotPlayAnotherTrackOrPublishLyrics() = runBlocking {
        val transport = FakeEchoLinkTransport(failedStreamIds = setOf("selected"))
        val client = EchoRemoteClient(this, transport)
        client.connect(endpoint, false)
        delay(20)
        var played = false
        var lyrics = false
        client.playTracksOnPhone(listOf(remoteTrack("other"), remoteTrack("selected")), 1,
            onQueueReady = { _, _ -> played = true }, onLyricsReady = { _, _ -> lyrics = true })
        delay(20)
        assertFalse(played)
        assertFalse(lyrics)
        assertEquals(1, transport.streamCalls)
        assertTrue(client.library.value.error != null)
        client.disconnect()
    }

    @Test
    fun selectedTrackStartsWithoutResolvingTheRestOfTheQueue() = runBlocking {
        val transport = FakeEchoLinkTransport()
        val client = EchoRemoteClient(this, transport)
        client.connect(endpoint, false)
        delay(20)
        var uris = emptyList<String>()
        var start = -1
        client.playTracksOnPhone((0..599).map { remoteTrack("$it") }, 300,
            onQueueReady = { queue, index -> uris = queue.map { it.uri }; start = index })
        delay(20)
        assertEquals(300, start)
        assertEquals(600, uris.size)
        assertEquals(1, transport.streamCalls)
        assertTrue(uris[0].startsWith("echo-link://track/"))
        assertTrue(uris[300].startsWith("http://"))
        client.disconnect()
    }

    @Test
    fun replacingPhonePlaybackCancelsOldStreamRequest() = runBlocking {
        val blocker = CompletableDeferred<Unit>()
        val transport = FakeEchoLinkTransport(streamBlocker = blocker)
        val client = EchoRemoteClient(this, transport)
        client.connect(endpoint, false)
        delay(20)
        var played = ""
        client.playTrackOnPhone(remoteTrack("old"), { played = it.id })
        delay(10)
        client.playTrackOnPhone(remoteTrack("new"), { played = it.id })
        delay(10)
        assertEquals(1, transport.cancelledStreams)
        blocker.complete(Unit)
        delay(20)
        assertEquals("echo-link:new", played)
        client.disconnect()
    }

    @Test
    fun playlistLoadsAllPagesAndRefreshInvalidatesSameSizeCache() = runBlocking {
        val tracks = (0..600).map { remoteTrack("$it") }.toMutableList()
        val transport = FakeEchoLinkTransport(playlistTracks = mapOf("pl" to tracks))
        val client = EchoRemoteClient(this, transport)
        client.connect(endpoint, false)
        delay(20)
        val playlist = EchoRemotePlaylist(id = "pl", name = "Playlist", trackCount = 601)
        client.refreshPlaylistTracks(playlist)
        delay(20)
        assertEquals(601, client.library.value.playlistTracks["pl"]?.size)
        assertEquals(2, transport.playlistTrackCalls)
        tracks[0] = remoteTrack("replacement")
        client.refreshLibrary()
        delay(20)
        client.refreshPlaylistTracks(playlist)
        delay(20)
        assertEquals("replacement", client.library.value.playlistTracks["pl"]?.first()?.id)
        client.disconnect()
    }

    @Test
    fun backgroundStopsStatusPollingUntilVisible() = runBlocking {
        val transport = FakeEchoLinkTransport()
        val client = EchoRemoteClient(this, transport, statusPollIntervalMs = 5)
        client.connect(endpoint, false)
        delay(20)
        client.setForeground(false)
        val calls = transport.statusCalls
        delay(25)
        assertEquals(calls, transport.statusCalls)
        client.setForeground(true)
        delay(20)
        assertTrue(transport.statusCalls > calls)
        client.disconnect()
    }

    @Test
    fun rejectedAuthorizationStopsRetries() = runBlocking {
        val transport = FakeEchoLinkTransport(statusError = EchoLinkHttpException("revoked", 401))
        val client = EchoRemoteClient(this, transport, statusPollIntervalMs = 5)
        client.connect(endpoint, false)
        delay(30)
        assertEquals(1, transport.statusCalls)
        assertEquals(EchoRemoteConnectionState.Error, client.status.value.connectionState)
        client.setForeground(true)
        delay(15)
        assertEquals(1, transport.statusCalls)
        client.disconnect()
    }

    @Test
    fun playlistListDoesNotBlockFirstTracksAndSwitchingPcClearsOldLibrary() = runBlocking {
        val blocker = CompletableDeferred<Unit>()
        val transport = FakeEchoLinkTransport(libraryTotalCount = 2, playlistListBlocker = blocker)
        val client = EchoRemoteClient(this, transport)
        client.connect(endpoint, true)
        delay(30)
        assertEquals(2, client.library.value.tracks.size)
        assertFalse(client.library.value.isLoading)
        client.connect(endpoint.copy(id = "other", host = "192.168.1.21"), false)
        assertTrue(client.library.value.tracks.isEmpty())
        delay(20)
        blocker.complete(Unit)
        client.disconnect()
    }

    @Test
    fun firstFailedConnectIsRetriedUntilStatusSucceeds() = runBlocking {
        val transport = FakeEchoLinkTransport(failStatusTimes = 1)
        val client = EchoRemoteClient(this, transport, connectRetryDelayMs = 0)
        client.connect(endpoint, refreshLibraryOnConnect = false)
        delay(50)
        assertEquals(EchoRemoteConnectionState.Connected, client.status.value.connectionState)
        assertTrue(transport.statusCalls >= 2)
        client.disconnect()
    }

    @Test
    fun disconnectInvalidatesAnInFlightConnect() = runBlocking {
        val pairingBlocker = CompletableDeferred<Unit>()
        val transport = FakeEchoLinkTransport(pairingBlocker = pairingBlocker)
        val client = EchoRemoteClient(this, transport, connectRetryDelayMs = 0)

        client.connect(endpoint, refreshLibraryOnConnect = false)
        delay(10)
        client.disconnect()
        pairingBlocker.complete(Unit)
        delay(20)

        assertEquals(EchoRemoteConnectionState.Disconnected, client.status.value.connectionState)
        assertNull(client.status.value.endpoint)
    }

    @Test
    fun cancelledLibraryRefreshCannotOverwriteTheNewQuery() = runBlocking {
        val oldQueryBlocker = CompletableDeferred<Unit>()
        val transport = FakeEchoLinkTransport(
            libraryTotalCount = 1,
            trackBlockers = mapOf("old" to oldQueryBlocker),
        )
        val client = EchoRemoteClient(this, transport, connectRetryDelayMs = 0)
        client.connect(endpoint, refreshLibraryOnConnect = false)
        delay(20)

        client.refreshLibrary("old")
        delay(10)
        client.refreshLibrary("new")
        delay(20)

        assertEquals("new", client.library.value.query)
        assertEquals(false, client.library.value.isLoading)
        assertNull(client.library.value.error)
        client.disconnect()
    }

    @Test
    fun statusPollingWaitsForThePreviousRequest() = runBlocking {
        val transport = FakeEchoLinkTransport(statusDelayAfterFirstMs = 30)
        val client = EchoRemoteClient(
            scope = this,
            transport = transport,
            connectRetryDelayMs = 0,
            statusPollIntervalMs = 5,
        )

        client.connect(endpoint, refreshLibraryOnConnect = false)
        delay(100)

        assertTrue(transport.statusCalls >= 3)
        assertEquals(1, transport.maxConcurrentStatusCalls)
        client.disconnect()
    }

    @Test
    fun oldPlaylistRequestCannotOverwriteTheSelectedPlaylist() = runBlocking {
        val oldPlaylistBlocker = CompletableDeferred<Unit>()
        val transport = FakeEchoLinkTransport(
            playlistBlockers = mapOf("old" to oldPlaylistBlocker),
            playlistTracks = mapOf(
                "old" to listOf(remoteTrack("old-track")),
                "new" to listOf(remoteTrack("new-track")),
            ),
        )
        val client = EchoRemoteClient(this, transport, connectRetryDelayMs = 0)
        client.connect(endpoint, refreshLibraryOnConnect = false)
        delay(20)

        client.refreshPlaylistTracks(EchoRemotePlaylist("old", "Old", null, 1))
        delay(10)
        client.refreshPlaylistTracks(EchoRemotePlaylist("new", "New", null, 1))
        delay(20)
        oldPlaylistBlocker.complete(Unit)
        delay(20)

        assertNull(client.library.value.loadingPlaylistId)
        assertEquals(listOf("new-track"), client.library.value.playlistTracks["new"]?.map { it.id })
        assertTrue(client.library.value.playlistTracks["old"].isNullOrEmpty())
        assertNull(client.library.value.error)
        client.disconnect()
    }

    @Test
    fun disconnectedPhoneStreamFailureCannotRestoreAnOldError() = runBlocking {
        val streamBlocker = CompletableDeferred<Unit>()
        val transport = FakeEchoLinkTransport(
            streamBlocker = streamBlocker,
            failStream = true,
        )
        val client = EchoRemoteClient(this, transport, connectRetryDelayMs = 0)
        client.connect(endpoint, refreshLibraryOnConnect = false)
        delay(20)

        client.playTrackOnPhone(
            track = remoteTrack("phone-track"),
            onTrackReady = { error("failed stream must not become playable") },
        )
        delay(10)
        client.disconnect()
        streamBlocker.complete(Unit)
        delay(20)

        assertNull(client.library.value.error)
        assertEquals(EchoRemoteConnectionState.Disconnected, client.status.value.connectionState)
    }

    @Test
    fun statusRetryDoesNotConsumePairingSecretTwice() = runBlocking {
        val pairingEndpoint = endpoint.copy(
            token = "one-time-secret",
            protocolVersion = app.echo.android.model.connect.EchoProtocolVersion(2, 0),
            pairingId = "pair-1",
            pairingSecret = "one-time-secret",
        )
        val transport = FakeEchoLinkTransport(failStatusTimes = 1)
        val client = EchoRemoteClient(this, transport, connectRetryDelayMs = 0)
        client.connect(pairingEndpoint, refreshLibraryOnConnect = false)
        delay(50)
        assertEquals(EchoRemoteConnectionState.Connected, client.status.value.connectionState)
        assertEquals("access-token", client.status.value.endpoint?.token)
        assertEquals(1, transport.pairingCalls)
        assertEquals(2, transport.statusCalls)
        client.disconnect()
    }

    @Test
    fun exhaustedPairingStopsInError() = runBlocking {
        val pairingEndpoint = endpoint.copy(
            token = "one-time-secret",
            protocolVersion = app.echo.android.model.connect.EchoProtocolVersion(2, 0),
            pairingId = "pair-1",
            pairingSecret = "one-time-secret",
        )
        val transport = FakeEchoLinkTransport(failPairing = true)
        val client = EchoRemoteClient(this, transport, connectRetryDelayMs = 0)
        client.connect(pairingEndpoint, refreshLibraryOnConnect = false)
        delay(50)
        assertEquals(EchoRemoteConnectionState.Error, client.status.value.connectionState)
        assertEquals(2, transport.pairingCalls)
        client.disconnect()
    }

    @Test
    fun playTracksOnPhonePreservesTheQueueAndResolvesOnlyTheSelectedTrack() = runBlocking {
        val transport = FakeEchoLinkTransport()
        val client = EchoRemoteClient(this, transport, connectRetryDelayMs = 0)
        client.connect(endpoint, refreshLibraryOnConnect = false)
        delay(20)
        var received: List<String> = emptyList()
        var start = -1
        client.playTracksOnPhone(
            tracks = listOf(remoteTrack("a"), remoteTrack("b"), remoteTrack("c")),
            startIndex = 1,
            onQueueReady = { queue, index ->
                received = queue.map { it.id }
                start = index
            },
        )
        delay(50)
        assertEquals(1, transport.streamCalls)
        assertEquals(listOf("echo-link:a", "echo-link:b", "echo-link:c"), received)
        assertEquals(1, start)
        client.disconnect()
    }

    @Test
    fun playlistTracks404DoesNotLoadTheGeneralLibrary() = runBlocking {
        val transport = FakeEchoLinkTransport()
        val client = EchoRemoteClient(this, transport, connectRetryDelayMs = 0)
        client.connect(endpoint, refreshLibraryOnConnect = false)
        delay(50)
        client.refreshPlaylistTracks(
            EchoRemotePlaylist(id = "missing", name = "Missing", artworkUrl = null, trackCount = 0),
        )
        delay(50)
        assertEquals(1, transport.playlistTrackCalls)
        assertEquals(0, transport.libraryTrackCalls)
        assertTrue(client.library.value.error?.contains("404") == true)
        assertTrue(client.library.value.playlistTracks["missing"].isNullOrEmpty())
        client.disconnect()
    }

    @Test
    fun pagesBeyondTheFirstLibraryPage() = runBlocking {
        val transport = FakeEchoLinkTransport(libraryPageSize = 2, libraryTotalCount = 5)
        val client = EchoRemoteClient(this, transport, connectRetryDelayMs = 0)
        client.connect(endpoint, refreshLibraryOnConnect = true)
        delay(80)
        assertEquals(5, client.library.value.tracks.size)
        assertEquals(5, client.library.value.totalCount)
        assertEquals(3, transport.libraryTrackCalls)
        // 流式拉取完成后必须清掉"继续加载中"标记
        assertFalse(client.library.value.isLoadingMore)
        client.disconnect()
    }

    @Test
    fun playlistTracksLoadedDuringStreamingRefreshSurviveLaterPublishes() = runBlocking {
        val pageBlocker = CompletableDeferred<Unit>()
        val transport = FakeEchoLinkTransport(
            libraryPageSize = 2,
            libraryTotalCount = 8,
            trackPageBlockers = mapOf(2 to pageBlocker),
            playlistTracks = mapOf("pl" to listOf(remoteTrack("pl-track"))),
        )
        val client = EchoRemoteClient(this, transport, connectRetryDelayMs = 0)
        client.connect(endpoint, refreshLibraryOnConnect = false)
        delay(20)

        // 首页发布后第 2 页被卡住:模拟流式刷新进行中用户点开歌单
        client.refreshLibrary()
        delay(20)
        client.refreshPlaylistTracks(EchoRemotePlaylist("pl", "PL", null, 1))
        delay(20)
        assertEquals(listOf("pl-track"), client.library.value.playlistTracks["pl"]?.map { it.id })

        // 流式刷新的后续发布不得清掉已加载的歌单曲目
        pageBlocker.complete(Unit)
        delay(40)
        assertFalse(client.library.value.isLoadingMore)
        assertEquals(8, client.library.value.tracks.size)
        assertEquals(listOf("pl-track"), client.library.value.playlistTracks["pl"]?.map { it.id })
        client.disconnect()
    }

    @Test
    fun streamingRefreshKeepsTheLoadingPlaylistIndicator() = runBlocking {
        val pageBlocker = CompletableDeferred<Unit>()
        val playlistBlocker = CompletableDeferred<Unit>()
        val transport = FakeEchoLinkTransport(
            libraryPageSize = 2,
            libraryTotalCount = 4,
            trackPageBlockers = mapOf(2 to pageBlocker),
            playlistBlockers = mapOf("pl" to playlistBlocker),
            playlistTracks = mapOf("pl" to listOf(remoteTrack("pl-track"))),
        )
        val client = EchoRemoteClient(this, transport, connectRetryDelayMs = 0)
        client.connect(endpoint, refreshLibraryOnConnect = false)
        delay(20)

        client.refreshLibrary()
        delay(20)
        client.refreshPlaylistTracks(EchoRemotePlaylist("pl", "PL", null, 1))
        delay(20)
        assertEquals("pl", client.library.value.loadingPlaylistId)

        // 歌单仍在加载时流式刷新完成:加载指示器不得凭空消失
        pageBlocker.complete(Unit)
        delay(40)
        assertFalse(client.library.value.isLoadingMore)
        assertEquals("pl", client.library.value.loadingPlaylistId)

        playlistBlocker.complete(Unit)
        delay(20)
        assertNull(client.library.value.loadingPlaylistId)
        assertEquals(listOf("pl-track"), client.library.value.playlistTracks["pl"]?.map { it.id })
        client.disconnect()
    }

    @Test
    fun handoffAcknowledgementSurvivesStatusPolling() = runBlocking {
        val blocker = CompletableDeferred<Unit>()
        val transport = FakeEchoLinkTransport(commandBlocker = blocker)
        val client = EchoRemoteClient(this, transport, statusPollIntervalMs = 5)
        client.connect(endpoint, false)
        delay(20)
        var acknowledged = false
        client.handoffToPc(remoteTrack("song"), 1200) { acknowledged = true }
        delay(25)
        assertFalse(acknowledged)
        blocker.complete(Unit)
        delay(20)
        assertTrue(acknowledged)
        client.disconnect()
    }

    @Test
    fun failedHandoffDoesNotAcknowledgeOrPauseThePhone() = runBlocking {
        val client = EchoRemoteClient(this, FakeEchoLinkTransport(failCommand = true))
        client.connect(endpoint, false)
        delay(20)
        var acknowledged = false
        client.handoffToPc(remoteTrack("song"), 1200) { acknowledged = true }
        delay(20)
        assertFalse(acknowledged)
        assertEquals(EchoRemoteConnectionState.Connected, client.status.value.connectionState)
        client.disconnect()
    }

    @Test
    fun failedCommandKeepsTheSessionConnected() = runBlocking {
        val client = EchoRemoteClient(this, FakeEchoLinkTransport(failCommand = true))
        client.connect(endpoint, false)
        delay(20)
        client.send(EchoRemoteCommand.SeekTo(12_000))
        delay(20)
        assertEquals(EchoRemoteConnectionState.Connected, client.status.value.connectionState)
        client.disconnect()
    }

    @Test
    fun commandAuthRejectionStopsRetries() = runBlocking {
        val transport = FakeEchoLinkTransport(
            failCommand = true,
            commandErrorCode = 401,
        )
        val client = EchoRemoteClient(this, transport, statusPollIntervalMs = 60_000)
        client.connect(endpoint, false)
        delay(20)
        assertEquals(1, transport.statusCalls)
        client.send(EchoRemoteCommand.PlayPause)
        delay(30)
        assertEquals(EchoRemoteConnectionState.Error, client.status.value.connectionState)
        client.setForeground(true)
        delay(15)
        assertEquals(1, transport.statusCalls)
        client.disconnect()
    }

    @Test
    fun disconnectedHandoffDoesNotAcknowledge() = runBlocking {
        val blocker = CompletableDeferred<Unit>()
        val client = EchoRemoteClient(this, FakeEchoLinkTransport(commandBlocker = blocker))
        client.connect(endpoint, false)
        delay(20)
        var acknowledged = false
        client.handoffToPc(remoteTrack("song"), 1200) { acknowledged = true }
        delay(10)
        client.disconnect()
        blocker.complete(Unit)
        delay(20)
        assertFalse(acknowledged)
    }

    @Test
    fun canSendQueueReplaceForALinkedQueue() = runBlocking {
        val transport = FakeEchoLinkTransport()
        val client = EchoRemoteClient(this, transport, connectRetryDelayMs = 0)
        client.connect(endpoint, refreshLibraryOnConnect = false)
        delay(50)
        client.playQueueOnPc(listOf(remoteTrack("a"), remoteTrack("b"), remoteTrack("c")), startIndex = 1)
        delay(50)
        val replace = transport.commands.filterIsInstance<EchoRemoteCommand.QueueReplace>().single()
        assertEquals(listOf("a", "b", "c"), replace.trackIds)
        assertEquals("b", replace.startTrackId)
        client.disconnect()
    }

    @Test
    fun multiTrackHandoffSendsQueueReplaceThenHandoff() = runBlocking {
        val transport = FakeEchoLinkTransport()
        val client = EchoRemoteClient(this, transport, connectRetryDelayMs = 0)
        client.connect(endpoint, refreshLibraryOnConnect = false)
        delay(20)
        var acknowledged = false
        client.handoffPhoneQueueToPc(
            tracks = listOf(remoteTrack("a"), remoteTrack("b"), remoteTrack("c")),
            startIndex = 1,
            positionMs = 4_200,
        ) { acknowledged = true }
        delay(50)
        assertEquals(
            listOf("queueReplace", "handoff"),
            transport.commands.map {
                when (it) {
                    is EchoRemoteCommand.QueueReplace -> "queueReplace"
                    is EchoRemoteCommand.HandoffToPc -> "handoff"
                    else -> it::class.simpleName
                }
            },
        )
        val replace = transport.commands.filterIsInstance<EchoRemoteCommand.QueueReplace>().single()
        assertEquals("b", replace.startTrackId)
        assertEquals(4_200L, (transport.commands.last() as EchoRemoteCommand.HandoffToPc).positionMs)
        assertTrue(acknowledged)
        client.disconnect()
    }

    @Test
    fun failedQueueReplaceDoesNotSendHandoff() = runBlocking {
        val transport = FakeEchoLinkTransport(failCommand = true)
        val client = EchoRemoteClient(this, transport, connectRetryDelayMs = 0)
        client.connect(endpoint, refreshLibraryOnConnect = false)
        delay(20)
        var acknowledged = false
        client.handoffPhoneQueueToPc(
            tracks = listOf(remoteTrack("a"), remoteTrack("b")),
            startIndex = 0,
            positionMs = 900,
        ) { acknowledged = true }
        delay(40)
        assertEquals(1, transport.commands.size)
        assertTrue(transport.commands.single() is EchoRemoteCommand.QueueReplace)
        assertFalse(acknowledged)
        assertEquals(EchoRemoteConnectionState.Connected, client.status.value.connectionState)
        client.disconnect()
    }

    @Test
    fun missingAlbumEndpointDoesNotFailTheTrackLibrary() = runBlocking {
        val transport = FakeEchoLinkTransport(failAlbums = true, libraryTotalCount = 2)
        val client = EchoRemoteClient(this, transport, connectRetryDelayMs = 0)
        client.connect(endpoint, refreshLibraryOnConnect = true)
        delay(80)
        assertEquals(2, client.library.value.tracks.size)
        assertTrue(client.library.value.albumsUnavailable)
        assertNull(client.library.value.error)
        client.disconnect()
    }

    @Test
    fun albumTrack404DoesNotDisableAlbumBrowsing() = runBlocking {
        val album = EchoRemoteAlbum(id = "album-1", title = "Album", artist = "Artist", trackCount = 4)
        val transport = FakeEchoLinkTransport(
            libraryTotalCount = 3,
            albums = listOf(album),
            failAlbumTrackIds = setOf("album-1"),
        )
        val client = EchoRemoteClient(this, transport, connectRetryDelayMs = 0)
        client.connect(endpoint, refreshLibraryOnConnect = true)
        delay(80)
        client.refreshAlbumTracks(album)
        delay(40)
        assertFalse(client.library.value.albumsUnavailable)
        assertEquals(3, client.library.value.tracks.size)
        assertTrue(client.library.value.error?.contains("404") == true)
        client.disconnect()
    }

    @Test
    fun nestedFolder404DoesNotDisableFolderBrowsing() = runBlocking {
        val transport = FakeEchoLinkTransport(
            libraryTotalCount = 2,
            folderPages = mapOf(
                "" to EchoLinkFolderPage(
                    path = "",
                    folders = listOf(EchoRemoteFolder(path = "Music", name = "Music")),
                    tracks = emptyList(),
                ),
            ),
        )
        val client = EchoRemoteClient(this, transport, connectRetryDelayMs = 0)
        client.connect(endpoint, refreshLibraryOnConnect = false)
        delay(20)
        client.refreshFolders("")
        delay(20)
        assertFalse(client.library.value.foldersUnavailable)
        client.refreshFolders("Music/Missing")
        delay(20)
        assertFalse(client.library.value.foldersUnavailable)
        assertEquals("Music", client.library.value.folders.single().name)
        client.disconnect()
    }

    @Test
    fun playOnPcFailureKeepsLoadedTracks() = runBlocking {
        val transport = FakeEchoLinkTransport(failCommand = true, libraryTotalCount = 4)
        val client = EchoRemoteClient(this, transport, connectRetryDelayMs = 0)
        client.connect(endpoint, refreshLibraryOnConnect = true)
        delay(80)
        assertEquals(4, client.library.value.tracks.size)
        client.playQueueOnPc(listOf(remoteTrack("a"), remoteTrack("b")), startIndex = 0)
        delay(30)
        assertEquals(4, client.library.value.tracks.size)
        assertEquals(EchoRemoteConnectionState.Connected, client.status.value.connectionState)
        client.disconnect()
    }

    @Test
    fun unstreamablePhonePlayKeepsLoadedTracks() = runBlocking {
        val transport = FakeEchoLinkTransport(libraryTotalCount = 5)
        val client = EchoRemoteClient(this, transport, connectRetryDelayMs = 0)
        client.connect(endpoint, refreshLibraryOnConnect = true)
        delay(80)
        client.playTrackOnPhone(
            track = EchoRemoteTrack(
                id = "dsd",
                title = "DSD",
                artist = "Artist",
                album = null,
                artworkUrl = null,
                durationMs = 1_000,
                canPlayOnPhone = false,
            ),
            onTrackReady = { error("should not play") },
        )
        delay(20)
        assertEquals(5, client.library.value.tracks.size)
        client.disconnect()
    }

    @Test
    fun emptyLatinSearchKeepsPreviouslyLoadedTracks() = runBlocking {
        val transport = FakeEchoLinkTransport(
            libraryTotalCount = 6,
            emptyRemoteQueries = setOf("radiohead"),
        )
        val client = EchoRemoteClient(this, transport, connectRetryDelayMs = 0)
        client.connect(endpoint, refreshLibraryOnConnect = true)
        delay(80)
        assertEquals(6, client.library.value.tracks.size)
        client.refreshLibrary("radiohead")
        delay(50)
        assertEquals(6, client.library.value.tracks.size)
        assertEquals("radiohead", client.library.value.query)
        client.disconnect()
    }

    @Test
    fun canSendPlayOnPcAndHandoffCommands() = runBlocking {
        val transport = FakeEchoLinkTransport()
        val client = EchoRemoteClient(this, transport, connectRetryDelayMs = 0)
        client.connect(endpoint, refreshLibraryOnConnect = false)
        delay(50)
        val track = EchoRemoteTrack(
            id = "track-1",
            title = "Song",
            artist = "Artist",
            album = "Album",
            artworkUrl = null,
            durationMs = 240_000,
            canPlayOnPhone = true,
        )
        client.playTrackOnPc(track)
        client.handoffToPc(track, 12_000)
        delay(50)
        assertTrue(transport.commands.any { it is EchoRemoteCommand.PlayTrackOnPc && it.trackId == "track-1" })
        assertTrue(transport.commands.any { it is EchoRemoteCommand.HandoffToPc && it.positionMs == 12_000L })
        client.disconnect()
    }

    @Test
    fun multiTrackCastSendsQueueReplaceRemoteThenPlayRemoteStream() = runBlocking {
        val transport = FakeEchoLinkTransport()
        val client = EchoRemoteClient(this, transport, connectRetryDelayMs = 0)
        client.connect(endpoint, refreshLibraryOnConnect = false)
        delay(20)
        var acknowledged = false
        client.castRemoteQueueToPc(
            items = listOf(
                EchoRemoteStreamItem("a", "http://phone/echo-link/cast/a", "A", "Artist"),
                EchoRemoteStreamItem("b", "http://phone/echo-link/cast/b", "B", "Artist"),
            ),
            startIndex = 1,
            positionMs = 3_000,
        ) { acknowledged = true }
        delay(50)
        assertEquals(
            listOf("queueReplaceRemote", "playRemoteStream"),
            transport.commands.map {
                when (it) {
                    is EchoRemoteCommand.QueueReplaceRemote -> "queueReplaceRemote"
                    is EchoRemoteCommand.PlayRemoteStream -> "playRemoteStream"
                    else -> it::class.simpleName
                }
            },
        )
        assertEquals("b", (transport.commands.first() as EchoRemoteCommand.QueueReplaceRemote).startTrackId)
        assertEquals(3_000L, (transport.commands.last() as EchoRemoteCommand.PlayRemoteStream).positionMs)
        assertTrue(acknowledged)
        client.disconnect()
    }

    @Test
    fun unsupportedCastCommandSurfacesUpgradeError() = runBlocking {
        val transport = FakeEchoLinkTransport(failCommand = true, commandErrorCode = 400)
        val client = EchoRemoteClient(this, transport, connectRetryDelayMs = 0)
        client.connect(endpoint, refreshLibraryOnConnect = false)
        delay(20)
        var failed = false
        client.castRemoteQueueToPc(
            items = listOf(EchoRemoteStreamItem("a", "http://phone/echo-link/cast/a", "A", "Artist")),
            startIndex = 0,
            positionMs = 0,
            onFailure = { failed = true },
            onSuccess = {},
        )
        delay(40)
        assertTrue(failed)
        assertTrue(client.status.value.error.orEmpty().contains("ECHOSteam"))
        assertEquals(EchoRemoteConnectionState.Connected, client.status.value.connectionState)
        client.disconnect()
    }

    @Test
    fun refusesUnstreamablePhonePlayback() = runBlocking {
        val transport = FakeEchoLinkTransport()
        val client = EchoRemoteClient(this, transport, connectRetryDelayMs = 0)
        client.connect(endpoint, refreshLibraryOnConnect = false)
        delay(50)
        client.playTrackOnPhone(
            track = EchoRemoteTrack(
                id = "track-dsd",
                title = "DSD",
                artist = "Artist",
                album = null,
                artworkUrl = null,
                durationMs = 240_000,
                canPlayOnPhone = false,
            ),
            onTrackReady = { error("should not start phone playback") },
        )
        delay(20)
        assertEquals(0, transport.streamCalls)
        assertTrue(client.library.value.error?.contains("stream", ignoreCase = true) == true)
        client.disconnect()
    }

    @Test
    fun v2EventsUpdatePlaybackWithoutWaitingForPoll() = runBlocking {
        val transport = FakeEchoLinkTransport()
        val client = EchoRemoteClient(this, transport, connectRetryDelayMs = 0, statusPollIntervalMs = 5_000)
        client.connect(endpoint.copy(supportsV2Events = true), refreshLibraryOnConnect = false)
        delay(40)
        assertEquals(1, transport.eventTicketCalls)
        assertEquals(1, transport.eventSubscriptions)
        transport.emitSnapshot(
            EchoRemotePlaybackSnapshot(
                state = app.echo.android.model.connect.EchoRemotePlaybackState.Playing,
                positionMs = 42L,
                queue = app.echo.android.model.connect.EchoRemotePlaybackQueue(
                    currentTrackId = "a",
                    items = listOf(remoteTrack("a"), remoteTrack("b")),
                ),
            ),
        )
        delay(10)
        assertEquals(42L, client.status.value.playback.positionMs)
        assertEquals(2, client.status.value.playback.queue.items.size)
        client.disconnect()
    }

    @Test
    fun phonePlaybackTrackPersistsStableIdNotOneShotStream() {
        val phoneTrack = remoteTrack("pc-42").toPhonePlaybackTrack(
            "http://192.168.1.20:26789/echo-link/media/token",
        )
        val persistUri = app.echo.android.model.playback.EchoLinkPlaybackUri.persistableUri(
            phoneTrack.id,
            phoneTrack.uri,
        )
        assertEquals("echo-link:pc-42", phoneTrack.id)
        assertTrue(app.echo.android.model.playback.EchoLinkPlaybackUri.isOneShotStreamUri(phoneTrack.uri))
        assertEquals("echo-link://track/pc-42", persistUri)
        assertTrue(
            app.echo.android.model.playback.EchoLinkPlaybackUri.requiresStreamResolve(
                phoneTrack.id,
                persistUri,
            ),
        )
        assertFalse(app.echo.android.model.playback.EchoLinkPlaybackUri.isOneShotStreamUri(persistUri))
    }
}

private fun remoteTrack(id: String): EchoRemoteTrack =
    EchoRemoteTrack(
        id = id,
        title = "Song $id",
        artist = "Artist",
        album = "Album",
        artworkUrl = null,
        durationMs = 1_000,
        canPlayOnPhone = true,
    )

private class FakeEchoLinkTransport(
    private val failPairing: Boolean = false,
    private val statusError: Throwable? = null,
    private val playlistListBlocker: CompletableDeferred<Unit>? = null,
    private val commandBlocker: CompletableDeferred<Unit>? = null,
    private val failCommand: Boolean = false,
    private val commandErrorCode: Int? = null,
    private val failStatusTimes: Int = 0,
    private val libraryPageSize: Int = 500,
    private val libraryTotalCount: Int = 0,
    private val pairingBlocker: CompletableDeferred<Unit>? = null,
    private val trackBlockers: Map<String, CompletableDeferred<Unit>> = emptyMap(),
    private val trackPageBlockers: Map<Int, CompletableDeferred<Unit>> = emptyMap(),
    private val statusDelayAfterFirstMs: Long = 0L,
    private val playlistBlockers: Map<String, CompletableDeferred<Unit>> = emptyMap(),
    private val playlistTracks: Map<String, List<EchoRemoteTrack>> = emptyMap(),
    private val streamBlocker: CompletableDeferred<Unit>? = null,
    private val failStream: Boolean = false,
    private val failedStreamIds: Set<String> = emptySet(),
    private val failAlbums: Boolean = false,
    private val albums: List<EchoRemoteAlbum> = emptyList(),
    private val failAlbumTrackIds: Set<String> = emptySet(),
    private val folderPages: Map<String, EchoLinkFolderPage> = emptyMap(),
    private val emptyRemoteQueries: Set<String> = emptySet(),
) : EchoLinkTransport {
    var statusCalls = 0
    var maxConcurrentStatusCalls = 0
    private var activeStatusCalls = 0
    var pairingCalls = 0
    var libraryTrackCalls = 0
    var playlistTrackCalls = 0
    var streamCalls = 0
    var cancelledStreams = 0
    val commands = mutableListOf<EchoRemoteCommand>()

    override suspend fun completePairing(endpoint: EchoRemoteEndpoint): EchoRemoteEndpoint {
        pairingBlocker?.await()
        if (!endpoint.needsV2PairExchange) return endpoint
        pairingCalls += 1
        if (failPairing || pairingCalls > 1) {
            throw EchoLinkHttpException("PC ECHO request failed (401): invalid_or_expired_pairing")
        }
        return endpoint.copy(
            token = "access-token",
            pairingId = null,
            pairingSecret = null,
            protocolVersion = app.echo.android.model.connect.EchoProtocolVersion.Current,
            supportsV2Events = true,
        )
    }

    var eventTicketCalls = 0
    var eventSubscriptions = 0
    var failEventTicket = false
    private var eventListener: ((app.echo.android.model.connect.EchoRemoteMessage) -> Unit)? = null

    override suspend fun createEventTicket(endpoint: EchoRemoteEndpoint): EchoLinkEventTicket {
        eventTicketCalls += 1
        if (failEventTicket) throw EchoLinkHttpException("events unavailable", 404)
        return EchoLinkEventTicket(
            ticket = "ticket-1",
            eventsUrl = okhttp3.HttpUrl.Builder()
                .scheme(endpoint.scheme)
                .host(endpoint.host)
                .port(endpoint.port)
                .addPathSegment("echo-link")
                .addPathSegment("v2")
                .addPathSegment("events")
                .addQueryParameter("ticket", "ticket-1")
                .build(),
        )
    }

    override fun subscribeEvents(
        endpoint: EchoRemoteEndpoint,
        ticket: EchoLinkEventTicket,
        onEvent: (app.echo.android.model.connect.EchoRemoteMessage) -> Unit,
        onClosed: (Throwable?) -> Unit,
    ): EchoLinkEventSubscription {
        eventSubscriptions += 1
        eventListener = onEvent
        return EchoLinkEventSubscription { eventListener = null }
    }

    fun emitSnapshot(snapshot: EchoRemotePlaybackSnapshot) {
        eventListener?.invoke(app.echo.android.model.connect.EchoRemoteMessage.StatusSnapshot(snapshot))
    }

    override suspend fun fetchStatus(endpoint: EchoRemoteEndpoint): EchoLinkStatusResponse {
        statusCalls += 1
        statusError?.let { throw it }
        activeStatusCalls += 1
        maxConcurrentStatusCalls = maxOf(maxConcurrentStatusCalls, activeStatusCalls)
        try {
            if (statusCalls > 1 && statusDelayAfterFirstMs > 0L) {
                delay(statusDelayAfterFirstMs)
            }
            if (statusCalls <= failStatusTimes) {
                throw EchoLinkHttpException("PC ECHO request failed (503): starting")
            }
            return EchoLinkStatusResponse(
                deviceName = endpoint.name,
                playback = EchoRemotePlaybackSnapshot(),
            )
        } finally {
            activeStatusCalls -= 1
        }
    }

    override suspend fun sendCommand(
        endpoint: EchoRemoteEndpoint,
        command: EchoRemoteCommand,
    ): EchoLinkStatusResponse? {
        commands += command
        commandBlocker?.await()
        if (failCommand) throw EchoLinkHttpException("command failed", commandErrorCode)
        return EchoLinkStatusResponse(deviceName = endpoint.name, playback = EchoRemotePlaybackSnapshot())
    }

    override suspend fun fetchTracks(
        endpoint: EchoRemoteEndpoint,
        query: String,
        page: Int,
        pageSize: Int,
    ): EchoLinkTrackPage {
        trackBlockers[query]?.await()
        trackPageBlockers[page]?.await()
        libraryTrackCalls += 1
        if (query in emptyRemoteQueries) {
            return EchoLinkTrackPage(tracks = emptyList(), totalCount = 0)
        }
        val start = (page - 1) * libraryPageSize
        if (start >= libraryTotalCount) {
            return EchoLinkTrackPage(tracks = emptyList(), totalCount = libraryTotalCount)
        }
        val end = minOf(libraryTotalCount, start + libraryPageSize)
        val tracks = (start until end).map { index ->
            EchoRemoteTrack(
                id = "track-$index",
                title = "Song $index",
                artist = "Artist",
                album = "Album",
                artworkUrl = null,
                durationMs = 1_000,
            )
        }
        return EchoLinkTrackPage(tracks = tracks, totalCount = libraryTotalCount)
    }

    override suspend fun fetchPlaylists(
        endpoint: EchoRemoteEndpoint,
        query: String,
        page: Int,
        pageSize: Int,
    ): EchoLinkPlaylistPage {
        playlistListBlocker?.await()
        return EchoLinkPlaylistPage(playlists = emptyList(), totalCount = 0)
    }

    override suspend fun fetchAlbums(
        endpoint: EchoRemoteEndpoint,
        query: String,
        page: Int,
        pageSize: Int,
    ): EchoLinkAlbumPage {
        if (failAlbums) throw EchoLinkHttpException("PC ECHO request failed (404): albums_not_found", 404)
        return EchoLinkAlbumPage(albums = albums, totalCount = albums.size)
    }

    override suspend fun fetchAlbumTracks(
        endpoint: EchoRemoteEndpoint,
        albumId: String,
        page: Int,
        pageSize: Int,
    ): EchoLinkTrackPage {
        if (albumId in failAlbumTrackIds || albums.none { it.id == albumId }) {
            throw EchoLinkHttpException("PC ECHO request failed (404): album_not_found", 404)
        }
        return EchoLinkTrackPage(tracks = emptyList(), totalCount = 0)
    }

    override suspend fun fetchFolders(
        endpoint: EchoRemoteEndpoint,
        path: String,
    ): EchoLinkFolderPage {
        folderPages[path]?.let { return it }
        throw EchoLinkHttpException("PC ECHO request failed (404): folders_not_found", 404)
    }

    override suspend fun fetchPlaylistTracks(
        endpoint: EchoRemoteEndpoint,
        playlistId: String,
        page: Int,
        pageSize: Int,
    ): EchoLinkTrackPage {
        playlistTrackCalls += 1
        playlistBlockers[playlistId]?.await()
        playlistTracks[playlistId]?.let { tracks ->
            return EchoLinkTrackPage(tracks = tracks.drop((page - 1) * pageSize).take(pageSize), totalCount = tracks.size)
        }
        throw EchoLinkHttpException("PC ECHO request failed (404): playlist_not_found")
    }

    override suspend fun resolveStream(endpoint: EchoRemoteEndpoint, trackId: String): EchoLinkStreamResponse {
        streamCalls += 1
        try { streamBlocker?.await() } catch (cancelled: kotlinx.coroutines.CancellationException) {
            cancelledStreams += 1
            throw cancelled
        }
        if (failStream || trackId in failedStreamIds) {
            throw EchoLinkHttpException("PC ECHO request failed (503): stream_unavailable")
        }
        return EchoLinkStreamResponse(streamUrl = "http://192.168.1.20:26789/echo-link/media/token", track = null)
    }

    override suspend fun fetchLyrics(endpoint: EchoRemoteEndpoint, trackId: String): EchoRemoteLyrics? = null
}
