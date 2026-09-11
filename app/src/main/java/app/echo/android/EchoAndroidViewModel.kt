package app.echo.android

import app.echo.android.model.settings.EchoBackgroundStyle

import app.echo.android.model.library.LibraryScanOptions
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.paging.PagingData
import app.echo.android.connect.EchoLanRendererBrowser
import app.echo.android.connect.EchoLinkLanBrowser
import app.echo.android.data.EchoErrorLogRepository
import app.echo.android.data.EchoLibraryDatabase
import app.echo.android.data.EchoLibraryRepository
import app.echo.android.model.error.EchoErrorLog
import app.echo.android.model.error.EchoErrorRecord
import app.echo.android.model.error.EchoErrorSource
import app.echo.android.data.EchoAppSettings
import app.echo.android.data.EchoLibrarySelectedSource
import app.echo.android.data.EchoBackupCodec
import app.echo.android.data.EchoSettingsStore
import app.echo.android.data.toBackupSettings
import app.echo.android.model.backup.EchoBackupDocument
import app.echo.android.model.backup.EchoBackupException
import app.echo.android.data.LibraryPlaybackQueuePolicy
import app.echo.android.data.DocumentTreeTrackScanner
import app.echo.android.data.EmbeddedTagWriteResult
import app.echo.android.data.EmbeddedTagWriter
import app.echo.android.data.MediaStoreTrackScanner
import app.echo.android.data.LocalLibrarySearchResults
import app.echo.android.data.OpraHeadphoneCorrectionRepository
import app.echo.android.data.JellyfinEndpoint
import app.echo.android.data.SubsonicEndpoint
import app.echo.android.data.fetchSubsonicLyricsText
import app.echo.android.data.subsonicSongIdFromTrack
import app.echo.android.lyrics.EchoLyricsParser
import app.echo.android.lyrics.EchoLrcFormatter
import android.content.IntentSender
import java.util.concurrent.atomic.AtomicLong

import app.echo.android.data.WebDavEndpoint
import app.echo.android.lyrics.ImportedLyricsStore
import app.echo.android.lyrics.LocalLyricsResolver
import app.echo.android.lyrics.OnlineLyricsResolver
import app.echo.android.model.i18n.echoText
import app.echo.android.model.library.AlbumSortMode
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.ArtistSortMode
import app.echo.android.model.library.ArtistSummary
import app.echo.android.model.library.FolderSortMode
import app.echo.android.model.library.EchoPlaylist
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.EchoTrackMetadataUpdate
import app.echo.android.model.library.LibraryPlaybackOrigin
import app.echo.android.model.library.FolderSummary
import app.echo.android.model.library.LibraryScanProgress
import app.echo.android.model.library.LibrarySource
import app.echo.android.model.library.LibraryStats
import app.echo.android.model.library.LibraryTrackSortMode
import app.echo.android.model.lyrics.EchoLyrics
import app.echo.android.model.lyrics.EchoLyricsLoadState
import app.echo.android.model.connect.EchoRemoteLyrics
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoTrackRef
import app.echo.android.model.playback.EchoChannelBalanceState
import app.echo.android.model.playback.EchoEqualizerState
import app.echo.android.model.playback.PlaybackControlsState
import app.echo.android.model.playback.PlaybackDiagnosticsState
import app.echo.android.model.playback.OpraHeadphoneCorrectionState
import app.echo.android.model.playback.PlaybackHeatmapDay
import app.echo.android.model.playback.PlaybackMetadataState
import app.echo.android.model.playback.PlaybackPositionState
import app.echo.android.model.playback.PlaybackQueueState
import app.echo.android.i18n.applyEchoAppLocale
import app.echo.android.model.settings.EchoAppLanguage
import app.echo.android.model.settings.EchoEffectivePerformanceMode
import app.echo.android.playback.PlaybackQueueReplaceIntent
import app.echo.android.design.EchoArtworkImageLoader
import app.echo.android.playback.EchoPlaybackCachePolicy
import app.echo.android.playback.EchoPlaybackProcessRuntime
import app.echo.android.playback.EchoReplayGainScanner
import app.echo.android.model.playback.EchoReplayGainScanFailure
import app.echo.android.model.playback.EchoReplayGainScanState
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

@androidx.annotation.OptIn(UnstableApi::class)
@Suppress("SpellCheckingInspection", "ConstPropertyName", "unused")
class EchoAndroidViewModel(application: Application) : AndroidViewModel(application) {
    private val database = EchoLibraryDatabase.create(application)
    private val repository = EchoLibraryRepository(
        database = database,
        scanner = MediaStoreTrackScanner(application),
        documentTreeScanner = DocumentTreeTrackScanner(application.contentResolver),
        tagWriter = EmbeddedTagWriter(application),
    )
    private val errorLog = EchoErrorLogRepository.create(application)
    private val settingsStore = EchoSettingsStore(application)
    private val echoLinkLanBrowser = EchoLinkLanBrowser(application)
    private val lanRendererBrowser = EchoLanRendererBrowser(application)
    private val opraRepository = OpraHeadphoneCorrectionRepository(application)
    private val subsonicEndpointRef = EchoSubsonicEndpointRef
    val initialAppSettings: EchoAppSettings = settingsStore.startupAppSettingsSnapshot()

    // 远程播放凭据由 Application 在进程内常驻收集,ViewModel 再应用一次以便 UI 会话
    // 立刻刷新歌词/听歌记录端点,并门控 MediaController 的恢复播放。不在构造期 runBlocking。

    private val libraryController = LibraryController(
        repository = repository,
        scope = viewModelScope,
        settingsStore = settingsStore,
        resolver = application.contentResolver,
    )
    private val lyricsController = LyricsController(
        repository = repository,
        lyricsResolver = LocalLyricsResolver(application.contentResolver),
        onlineLyricsResolver = OnlineLyricsResolver(),
        importedLyricsStore = ImportedLyricsStore(application),
        scope = viewModelScope,
        subsonicLyricsLoader = { track ->
            val endpoint = subsonicEndpointRef.get() ?: return@LyricsController null
            val songId = subsonicSongIdFromTrack(track.id, track.source) ?: return@LyricsController null
            val text = try {
                fetchSubsonicLyricsText(endpoint, songId, track.artist, track.title)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                null
            }?.takeIf { it.isNotBlank() } ?: return@LyricsController null
            EchoLyricsParser.parse(text, sourceLabel = "Navidrome").takeIf { it.lines.isNotEmpty() }
        },
    )
    private val playbackController = PlaybackController(
        application = application,
        settingsStore = settingsStore,
        scope = viewModelScope,
        onTrackChanged = lyricsController::updateLyricsForTrack,
        onTrackActivated = ::recordRecentPlayback,
    )
    init {
        libraryController.setPlaybackOccupiesStorage {
            playbackController.playbackControls.value.isPlaying
        }
        libraryController.startWatchingMediaStore(application.contentResolver)
    }
    private val lastFmClient = LastFmClient()
    private val lastFmController = LastFmScrobbleController(
        scope = EchoPlaybackProcessRuntime.scope,
        client = lastFmClient,
    )
    private val listenBrainzClient = ListenBrainzClient()
    private val listenBrainzController = ListenBrainzScrobbleController(
        scope = EchoPlaybackProcessRuntime.scope,
        client = listenBrainzClient,
    )

    private var pendingLastFmAuthToken: String? = null
    private var usbStartupPolicyApplied = false
    private var effectivePerformanceMode: EchoEffectivePerformanceMode = EchoEffectivePerformanceMode.Balanced
    private var playbackProgressUiVisibility: PlaybackProgressUiVisibility = PlaybackProgressUiVisibility.MiniPlayer
    private var selectedLibrarySource: String = initialAppSettings.librarySelectedSource

    val libraryQuery: StateFlow<String> = libraryController.libraryQuery
    val libraryTrackSortMode: StateFlow<LibraryTrackSortMode> = libraryController.trackSortMode
    val libraryAlbumSortMode: StateFlow<AlbumSortMode> = libraryController.albumSortMode
    val libraryArtistSortMode: StateFlow<ArtistSortMode> = libraryController.artistSortMode
    val libraryFolderSortMode: StateFlow<FolderSortMode> = libraryController.folderSortMode
    val tracks: Flow<PagingData<EchoTrack>> = libraryController.tracks
    val albums: Flow<PagingData<AlbumSummary>> = libraryController.albums
    val remoteAlbums: Flow<PagingData<AlbumSummary>> = libraryController.remoteAlbums
    val artists: Flow<PagingData<ArtistSummary>> = libraryController.artists
    val genres: Flow<PagingData<app.echo.android.model.library.GenreSummary>> = libraryController.genres
    val folders: Flow<PagingData<FolderSummary>> = libraryController.folders
    val localPlaylists: StateFlow<List<EchoPlaylist>> = libraryController.localPlaylists
    val favoriteTrackIds: StateFlow<Set<String>> = libraryController.favoriteTrackIds
    val favoriteAlbums: StateFlow<List<AlbumSummary>> = libraryController.favoriteAlbums
    val libraryStats: StateFlow<LibraryStats> = libraryController.libraryStats
    val recommendedTracks: StateFlow<List<EchoTrack>> = libraryController.recommendedTracks
    val recentlyAddedAlbums: StateFlow<List<AlbumSummary>> = libraryController.recentlyAddedAlbums
    val recommendedAlbums: StateFlow<List<AlbumSummary>> = libraryController.recommendedAlbums
    val scanState: StateFlow<LibraryScanProgress> = libraryController.scanState
    val remoteScanState: StateFlow<LibraryScanProgress> = libraryController.remoteScanState
    val echoLinkDiscoveryState = echoLinkLanBrowser.state
    val echoLinkLanDevices = echoLinkLanBrowser.devices
    val lanRendererDiscoveryState = lanRendererBrowser.state
    val lanRenderers = lanRendererBrowser.devices

    val playbackStatus: StateFlow<EchoPlaybackStatus> = playbackController.playbackStatus
    val playbackMetadata: StateFlow<PlaybackMetadataState> = playbackController.playbackMetadata
    val playbackPosition: StateFlow<PlaybackPositionState> = playbackController.playbackPosition
    val playbackControls: StateFlow<PlaybackControlsState> = playbackController.playbackControls
    val playbackQueue: StateFlow<PlaybackQueueState> = playbackController.playbackQueue
    val playbackDiagnostics: StateFlow<PlaybackDiagnosticsState> = playbackController.playbackDiagnostics
    val equalizerState: StateFlow<EchoEqualizerState> = playbackController.equalizerState
    val channelBalanceState: StateFlow<EchoChannelBalanceState> = playbackController.channelBalanceState
    private val replayGainScanner by lazy { EchoReplayGainScanner(getApplication()) }
    private var replayGainScanJob: Job? = null
    private val _replayGainScanState = MutableStateFlow<EchoReplayGainScanState>(EchoReplayGainScanState.Idle)
    val replayGainScanState: StateFlow<EchoReplayGainScanState> = _replayGainScanState.asStateFlow()
    val lyricsState: StateFlow<EchoLyricsLoadState> = lyricsController.lyricsState
    val lyricsCandidates = lyricsController.candidates
    val lyricsSearching = lyricsController.searching
    val lyricsManagementError = lyricsController.managementError
    private val tagWriteRequestIds = AtomicLong(1L)
    private val _pendingEmbeddedTagWrite = MutableStateFlow<PendingEmbeddedTagWrite?>(null)
    val pendingEmbeddedTagWrite: StateFlow<PendingEmbeddedTagWrite?> = _pendingEmbeddedTagWrite.asStateFlow()
    private val _embeddedTagWriteMessage = MutableStateFlow<EmbeddedTagWriteUserMessage?>(null)
    val embeddedTagWriteMessage: StateFlow<EmbeddedTagWriteUserMessage?> = _embeddedTagWriteMessage.asStateFlow()

    fun searchLyrics() = lyricsController.refreshLyrics(playbackController.currentTrackId)
    fun cancelLyricsSearch() = lyricsController.cancelSearch()
    fun selectLyricsCandidate(id: String) = lyricsController.selectCandidate(id, playbackController.currentTrackId)
    fun removeLyricsSelection() = lyricsController.removeSelection(playbackController.currentTrackId)

    val appSettings: Flow<EchoAppSettings> = settingsStore.appSettings
    private val _backupNotice = MutableStateFlow<app.echo.android.model.backup.EchoBackupNotice?>(null)
    val backupNotice: StateFlow<app.echo.android.model.backup.EchoBackupNotice?> = _backupNotice.asStateFlow()
    val lastFmState: StateFlow<LastFmUiState> = lastFmController.uiState
    val listenBrainzState: StateFlow<ListenBrainzUiState> = listenBrainzController.uiState
    val errorLogRecords: Flow<List<EchoErrorRecord>> = errorLog.records
    val errorLogCount: Flow<Int> = errorLog.count

    fun clearErrorLog() {
        viewModelScope.launch { errorLog.clear() }
    }

    fun deleteErrorLog(id: Long) {
        viewModelScope.launch { errorLog.delete(id) }
    }

    private val _recentPlaybackAlbums = MutableStateFlow<List<AlbumSummary>>(emptyList())
    val recentPlaybackAlbums: StateFlow<List<AlbumSummary>> = _recentPlaybackAlbums.asStateFlow()
    private val _recentPlaybackArtists = MutableStateFlow<List<ArtistSummary>>(emptyList())
    val recentPlaybackArtists: StateFlow<List<ArtistSummary>> = _recentPlaybackArtists.asStateFlow()
    private val _recentPlaybackHeatmap = MutableStateFlow<List<PlaybackHeatmapDay>>(emptyList())
    val recentPlaybackHeatmap: StateFlow<List<PlaybackHeatmapDay>> = _recentPlaybackHeatmap.asStateFlow()
    private val _usbExclusiveTestResult = MutableStateFlow(
        application.getString(R.string.usb_test_idle),
    )
    val usbExclusiveTestResult: StateFlow<String> = _usbExclusiveTestResult.asStateFlow()
    private val opraSearch = OpraSearchController(viewModelScope, opraRepository) { en, zh, ja -> echoText(en = en, zh = zh, ja = ja) }
    val opraState: StateFlow<OpraHeadphoneCorrectionState> = opraSearch.state

    private val albumPlaybackCounts = mutableMapOf<String, Int>()
    private val artistPlaybackCounts = mutableMapOf<String, Int>()
    private val playbackHeatmapCounts = mutableMapOf<Long, Int>()
    init {
        lastFmController.start(
            settingsFlow = settingsStore.appSettings,
            playbackStatus = playbackController.playbackStatus,
            playbackPosition = playbackController.playbackPosition,
        )
        listenBrainzController.start(
            settingsFlow = settingsStore.appSettings,
            playbackStatus = playbackController.playbackStatus,
            playbackPosition = playbackController.playbackPosition,
        )
        EchoSubsonicListen.startFromPlayback(
            playbackStatus = playbackController.playbackStatus,
            playbackPosition = playbackController.playbackPosition,
            settingsReady = settingsStore.appSettings,
        )
        viewModelScope.launch {
            var lastEqualizerSignature: String? = null
            var lastChannelBalance = EchoChannelBalanceState()
            settingsStore.appSettings.collect { settings ->
                selectedLibrarySource = settings.librarySelectedSource
                withContext(Dispatchers.IO) {
                    settingsStore.cacheStartupThemeSnapshot(settings)
                }
                lyricsController.setOnlineLyricsEnabled(settings.onlineLyricsEnabled, playbackController.currentTrackId)
                val firstSettingsEmission = !usbStartupPolicyApplied
                usbStartupPolicyApplied = true
                val usbAlreadyActive = playbackController.isUsbExclusiveEnabled()
                val shouldEnableUsbExclusive = if (firstSettingsEmission && !usbAlreadyActive) {
                    settings.usbExclusiveEnabled && settings.usbExclusiveAutoRequestOnStartup
                } else {
                    settings.usbExclusiveEnabled
                }
                playbackController.setUsbOutputMode(shouldEnableUsbExclusive, settings.usbBitPerfectEnabled)
                val equalizerSignature =
                    "${settings.equalizerEnabled}|${settings.equalizerPreset}|${settings.equalizerBandGains}|" +
                        "${settings.equalizerPreampDb}|${settings.equalizerParametric}|${settings.equalizerSourceLabel}|" +
                        settings.equalizerFilters
                if (equalizerSignature != lastEqualizerSignature) {
                    lastEqualizerSignature = equalizerSignature
                    playbackController.setEqualizerConfig(
                        enabled = settings.equalizerEnabled,
                        presetId = settings.equalizerPreset,
                        gainsDb = settings.equalizerBandGains,
                        preampDb = settings.equalizerPreampDb,
                        filters = if (settings.equalizerParametric) settings.equalizerFilters else emptyList(),
                        sourceLabel = settings.equalizerSourceLabel,
                    )
                }
                if (settings.channelBalance != lastChannelBalance) {
                    lastChannelBalance = settings.channelBalance
                    playbackController.setChannelBalance(settings.channelBalance)
                }
                if (firstSettingsEmission &&
                    settings.usbExclusiveEnabled &&
                    !settings.usbExclusiveAutoRequestOnStartup &&
                    !usbAlreadyActive
                ) {
                    playbackController.setUsbExclusiveEnabled(false)
                }
                if (settings.lastFmEnabled && !settings.lastFmUsername.isNullOrBlank()) {
                    lastFmController.setConnected(settings.lastFmUsername.orEmpty())
                }
                if (settings.listenBrainzEnabled && !settings.listenBrainzToken.isNullOrBlank()) {
                    listenBrainzController.setConnected(listenBrainzController.uiState.value.userName)
                }
                playbackController.setReplayGain(settings.replayGainEnabled, settings.replayGainPreampDb)
                playbackController.setReplayGainMode(
                    app.echo.android.model.playback.EchoReplayGainMode.fromId(settings.replayGainMode),
                )
                applyRemotePlaybackCredentials(settings, allowClearIfEmpty = true)
                playbackController.notifyRemotePlaybackAuthReady()
            }
        }
    }

    fun albumTrackPaging(albumKey: String): Flow<PagingData<EchoTrack>> =
        libraryController.albumTrackPaging(albumKey)

    fun artistTrackPaging(artistKey: String): Flow<PagingData<EchoTrack>> =
        libraryController.artistTrackPaging(artistKey)

    fun genreTrackPaging(genreKey: String): Flow<PagingData<EchoTrack>> =
        libraryController.genreTrackPaging(genreKey)

    fun folderTrackPaging(folderKey: String): Flow<PagingData<EchoTrack>> =
        libraryController.folderTrackPaging(folderKey)

    fun playlistTrackPaging(playlistId: String): Flow<PagingData<EchoTrack>> =
        libraryController.playlistTrackPaging(playlistId)

    fun refreshLibrary(options: LibraryScanOptions = LibraryScanOptions()) {
        libraryController.refreshLibrary(options)
    }

    fun refreshLibraryIfEmpty() {
        libraryController.refreshLibraryIfEmpty()
    }

    fun refreshLibraryFolder(treeUri: Uri, options: LibraryScanOptions = LibraryScanOptions()) {
        libraryController.refreshLibraryFolder(treeUri, options)
    }

    fun onLibraryForeground() {
        libraryController.onForeground()
    }

    fun cancelScan() {
        libraryController.cancelScan()
    }

    fun cancelRemoteSync() {
        libraryController.cancelRemoteScan()
    }

    fun updateLibraryQuery(query: String) {
        libraryController.updateLibraryQuery(query)
    }

    fun updateLibraryTrackSortMode(sortMode: LibraryTrackSortMode) {
        libraryController.updateTrackSortMode(sortMode)
    }

    fun updateLibraryAlbumSortMode(sortMode: AlbumSortMode) {
        libraryController.updateAlbumSortMode(sortMode)
    }

    fun updateLibraryArtistSortMode(sortMode: ArtistSortMode) {
        libraryController.updateArtistSortMode(sortMode)
    }

    fun updateLibraryFolderSortMode(sortMode: FolderSortMode) {
        libraryController.updateFolderSortMode(sortMode)
    }

    fun setEchoLinkPlaybackResolver(resolver: suspend (EchoTrackRef) -> EchoTrackRef) {
        playbackController.setEchoLinkPlaybackResolver(resolver)
    }

    fun play(track: EchoTrack) {
        playbackController.play(track)
    }

    fun playIncomingAudio(uris: List<String>) {
        val parsed = uris.mapNotNull { raw -> raw.trim().takeIf { it.isNotBlank() }?.let(Uri::parse) }
        if (parsed.isEmpty()) return
        viewModelScope.launch {
            val tracks = withContext(Dispatchers.IO) {
                parsed.mapNotNull { uri ->
                    tryTakePersistableReadPermission(getApplication(), uri)
                    resolveIncomingAudioTrack(getApplication(), repository, uri)
                }
            }
            when {
                tracks.size == 1 -> playbackController.play(tracks.single())
                tracks.isNotEmpty() -> playbackController.playQueue(tracks, 0)
            }
        }
    }

    fun playQueue(queue: List<EchoTrack>, startIndex: Int) {
        playbackController.playQueue(queue, startIndex)
    }

    fun playFromLibrary(track: EchoTrack, origin: LibraryPlaybackOrigin) {
        viewModelScope.launch {
            val source = selectedLibrarySource.ifBlank { EchoLibrarySelectedSource.Local }
            if (LibraryPlaybackQueuePolicy.usesCollectionQueue(origin)) {
                val collectionKey = LibraryPlaybackQueuePolicy.collectionKey(origin) ?: return@launch
                val queue = when (origin) {
                    is LibraryPlaybackOrigin.Album -> libraryController.albumTracksForPlayback(collectionKey)
                    is LibraryPlaybackOrigin.Artist -> libraryController.artistTracksForPlayback(collectionKey)
                    is LibraryPlaybackOrigin.Folder -> libraryController.folderTracksForPlayback(collectionKey)
                    is LibraryPlaybackOrigin.Playlist -> libraryController.playlistTracksForPlayback(collectionKey)
                    LibraryPlaybackOrigin.Songs -> emptyList()
                }
                if (queue.isEmpty()) return@launch
                playQueue(queue, LibraryPlaybackQueuePolicy.startIndex(queue.map { it.id }, track.id))
                return@launch
            }
            val queue = libraryController.queueAroundTrack(track.id, source)
            if (queue.isEmpty()) return@launch
            playQueue(queue, LibraryPlaybackQueuePolicy.startIndex(queue.map { it.id }, track.id))
        }
    }

    fun playTrackFromLibrary(trackId: String) {
        viewModelScope.launch {
            val source = selectedLibrarySource.ifBlank { EchoLibrarySelectedSource.Local }
            val queue = libraryController.queueAroundTrack(trackId, source)
            val startIndex = LibraryPlaybackQueuePolicy.startIndex(queue.map { it.id }, trackId)
            if (queue.isNotEmpty()) playQueue(queue, startIndex)
        }
    }

    suspend fun searchLocalLibrary(query: String): LocalLibrarySearchResults =
        libraryController.searchLocalLibrary(query)

    suspend fun updateTrackMetadata(update: EchoTrackMetadataUpdate) {
        withReleasedPlaybackFile(update.trackId) {
            handleEmbeddedTagWriteResult(
                trackId = update.trackId,
                result = libraryController.updateTrackMetadata(update).fileWrite,
            )
        }
    }

    fun onEmbeddedTagWriteAccessResult(granted: Boolean) {
        val pending = _pendingEmbeddedTagWrite.value ?: return
        _pendingEmbeddedTagWrite.value = null
        if (!granted) {
            _embeddedTagWriteMessage.value = EmbeddedTagWriteUserMessage.IndexOnly
            return
        }
        viewModelScope.launch {
            withReleasedPlaybackFile(pending.trackId) {
                handleEmbeddedTagWriteResult(
                    trackId = pending.trackId,
                    result = libraryController.writeEmbeddedTagsForTrack(
                        trackId = pending.trackId,
                        lyricsText = pending.lyricsText,
                        artworkUri = pending.artworkUri,
                    ),
                    lyricsText = pending.lyricsText,
                    artworkUri = pending.artworkUri,
                )
            }
        }
    }

    fun consumeEmbeddedTagWriteMessage() {
        _embeddedTagWriteMessage.value = null
    }

    private suspend fun withReleasedPlaybackFile(trackId: String, block: suspend () -> Unit) {
        val released = playbackController.releaseCurrentItemForFileWrite(trackId)
        try {
            block()
        } finally {
            if (released) playbackController.prepareAfterFileWrite(trackId)
        }
    }

    private fun handleEmbeddedTagWriteResult(
        trackId: String,
        result: EmbeddedTagWriteResult,
        lyricsText: String? = null,
        artworkUri: String? = null,
    ) {
        when (result) {
            is EmbeddedTagWriteResult.Written ->
                _embeddedTagWriteMessage.value = EmbeddedTagWriteUserMessage.Written
            EmbeddedTagWriteResult.UnsupportedFormat -> {
                _embeddedTagWriteMessage.value = EmbeddedTagWriteUserMessage.UnsupportedFormat
                EchoErrorLog.record(
                    EchoErrorSource.Library,
                    "Embedded tag write is unsupported for this file format.",
                    detail = trackId,
                )
            }
            EmbeddedTagWriteResult.Failed -> {
                _embeddedTagWriteMessage.value = EmbeddedTagWriteUserMessage.Failed
                EchoErrorLog.record(
                    EchoErrorSource.Library,
                    "Embedded tag write failed.",
                    detail = trackId,
                )
            }
            EmbeddedTagWriteResult.NotLocal ->
                _embeddedTagWriteMessage.value = EmbeddedTagWriteUserMessage.IndexOnly
            is EmbeddedTagWriteResult.NeedsMediaStoreConsent ->
                _pendingEmbeddedTagWrite.value = PendingEmbeddedTagWrite(
                    requestId = tagWriteRequestIds.getAndIncrement(),
                    trackId = trackId,
                    contentUri = result.uriString,
                    intentSender = result.intentSender,
                    lyricsText = lyricsText,
                    artworkUri = artworkUri,
                )
            EmbeddedTagWriteResult.NeedsStoragePermission ->
                _pendingEmbeddedTagWrite.value = PendingEmbeddedTagWrite(
                    requestId = tagWriteRequestIds.getAndIncrement(),
                    trackId = trackId,
                    contentUri = "",
                    needsStoragePermission = true,
                    lyricsText = lyricsText,
                    artworkUri = artworkUri,
                )
        }
    }

    fun updateTrackArtwork(trackId: String, artworkUri: Uri) {
        viewModelScope.launch {
            withReleasedPlaybackFile(trackId) {
                handleEmbeddedTagWriteResult(
                    trackId = trackId,
                    result = libraryController.updateTrackArtwork(trackId, artworkUri).fileWrite,
                    artworkUri = artworkUri.toString(),
                )
            }
        }
    }

    fun openCurrentPlaybackAlbum(onFound: (AlbumSummary) -> Unit) {
        val trackId = playbackController.currentTrackId ?: return
        viewModelScope.launch {
            libraryController.albumSummaryForTrack(trackId)?.let(onFound)
        }
    }

    fun openCurrentPlaybackArtist(onFound: (ArtistSummary) -> Unit) {
        val trackId = playbackController.currentTrackId ?: return
        viewModelScope.launch {
            libraryController.artistSummaryForTrack(trackId)?.let(onFound)
        }
    }

    fun playAlbum(albumKey: String) {
        viewModelScope.launch {
            val queue = libraryController.albumTracksForPlayback(albumKey)
            if (queue.isNotEmpty()) playbackController.playQueue(queue, 0)
        }
    }

    fun shuffleAlbum(albumKey: String) {
        viewModelScope.launch {
            val queue = libraryController.albumTracksForPlayback(albumKey)
            if (queue.isNotEmpty()) {
                playbackController.playQueue(
                    queue = queue,
                    startIndex = queue.indices.random(),
                    intent = PlaybackQueueReplaceIntent.Shuffle,
                )
            }
        }
    }

    fun playArtist(artistKey: String) {
        viewModelScope.launch {
            val queue = libraryController.artistTracksForPlayback(artistKey)
            if (queue.isNotEmpty()) playbackController.playQueue(queue, 0)
        }
    }

    fun playGenre(genreKey: String) {
        viewModelScope.launch {
            val queue = libraryController.genreTracksForPlayback(genreKey)
            if (queue.isNotEmpty()) playbackController.playQueue(queue, 0)
        }
    }

    fun playFolder(folderKey: String) {
        viewModelScope.launch {
            val queue = libraryController.folderTracksForPlayback(folderKey)
            if (queue.isNotEmpty()) playbackController.playQueue(queue, 0)
        }
    }

    fun shuffleFolder(folderKey: String) {
        viewModelScope.launch {
            val queue = libraryController.folderTracksForPlayback(folderKey)
            if (queue.isNotEmpty()) {
                playbackController.playQueue(
                    queue = queue,
                    startIndex = queue.indices.random(),
                    intent = PlaybackQueueReplaceIntent.Shuffle,
                )
            }
        }
    }

    fun playPlaylist(playlistId: String) {
        viewModelScope.launch {
            val queue = libraryController.playlistTracksForPlayback(playlistId)
            if (queue.isNotEmpty()) playbackController.playQueue(queue, 0)
        }
    }

    fun shufflePlaylist(playlistId: String) {
        viewModelScope.launch {
            val queue = libraryController.playlistTracksForPlayback(playlistId)
            if (queue.isNotEmpty()) {
                playbackController.playQueue(
                    queue = queue,
                    startIndex = queue.indices.random(),
                    intent = PlaybackQueueReplaceIntent.Shuffle,
                )
            }
        }
    }

    fun toggleFavorite(trackId: String? = playbackController.currentTrackId) {
        val id = trackId?.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch {
            libraryController.toggleFavorite(id)
        }
    }

    fun createLocalPlaylist(name: String, addTrackId: String? = null) {
        viewModelScope.launch {
            val created = libraryController.createLocalPlaylist(name) ?: return@launch
            val trackId = addTrackId?.takeIf { it.isNotBlank() } ?: return@launch
            libraryController.addTrackToLocalPlaylist(created.id, trackId)
        }
    }

    fun renameLocalPlaylist(playlistId: String, name: String) {
        viewModelScope.launch {
            libraryController.renameLocalPlaylist(playlistId, name)
        }
    }

    fun deleteLocalPlaylist(playlistId: String) {
        viewModelScope.launch {
            libraryController.deleteLocalPlaylist(playlistId)
        }
    }

    fun addTrackToLocalPlaylist(playlistId: String, trackId: String) {
        viewModelScope.launch {
            libraryController.addTrackToLocalPlaylist(playlistId, trackId)
        }
    }

    fun removeTrackFromLocalPlaylist(playlistId: String, trackId: String) {
        viewModelScope.launch {
            libraryController.removeTrackFromLocalPlaylist(playlistId, trackId)
        }
    }

    fun importM3uPlaylist(uri: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)
                    ?.bufferedReader(java.nio.charset.StandardCharsets.UTF_8)
                    ?.use { it.readText() }
            } ?: return@launch
            val name = uri.lastPathSegment
                ?.substringAfterLast('/')
                ?.substringBeforeLast('.')
                ?.replace('+', ' ')
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: "M3U"
            libraryController.importM3uPlaylist(name, text)
        }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val document = EchoBackupDocument(
                    version = EchoBackupDocument.CurrentVersion,
                    exportedAtEpochMs = System.currentTimeMillis(),
                    settings = settingsStore.appSettings.first().toBackupSettings(),
                    playlists = libraryController.exportBackupPlaylists(),
                    favorites = libraryController.exportBackupFavorites(),
                )
                val text = EchoBackupCodec.encode(document)
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(text.toByteArray(java.nio.charset.StandardCharsets.UTF_8))
                    } ?: error("Could not write backup")
                }
                _backupNotice.value = app.echo.android.model.backup.EchoBackupNotice.Exported
            }.onFailure { error ->
                _backupNotice.value = app.echo.android.model.backup.EchoBackupNotice.Failed(
                    error.message ?: "Backup failed",
                )
            }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                } ?: error("Could not read backup")
                val document = EchoBackupCodec.decode(text)
                settingsStore.applyBackupSettings(document.settings)
                val restored = libraryController.restoreBackupCatalog(document.playlists, document.favorites)
                _backupNotice.value = app.echo.android.model.backup.EchoBackupNotice.Restored(restored)
            }.onFailure { error ->
                _backupNotice.value = app.echo.android.model.backup.EchoBackupNotice.Failed(
                    (error as? EchoBackupException)?.message ?: error.message ?: "Restore failed",
                )
            }
        }
    }

    fun exportM3uPlaylist(playlistId: String, uri: Uri) {
        viewModelScope.launch {
            val text = libraryController.exportM3uPlaylist(playlistId) ?: return@launch
            withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(text.toByteArray(java.nio.charset.StandardCharsets.UTF_8))
                }
            }
        }
    }

    fun reorderLocalPlaylistTracks(playlistId: String, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            libraryController.reorderLocalPlaylistTracks(playlistId, fromIndex, toIndex)
        }
    }

    fun shuffleArtist(artistKey: String) {
        viewModelScope.launch {
            val queue = libraryController.artistTracksForPlayback(artistKey)
            if (queue.isNotEmpty()) {
                playbackController.playQueue(
                    queue = queue,
                    startIndex = queue.indices.random(),
                    intent = PlaybackQueueReplaceIntent.Shuffle,
                )
            }
        }
    }

    fun pause() {
        playbackController.pause()
    }

    fun playPause() {
        playbackController.playPause()
    }

    fun playLastSavedSession() {
        playbackController.playWhenReadyAfterRestore()
    }

    fun notifyEchoLinkConnected() {
        playbackController.notifyEchoLinkEndpointReady()
    }

    fun setEchoLinkLyricsFetcher(fetcher: suspend (String) -> EchoRemoteLyrics?) {
        lyricsController.setEchoLinkLyricsFetcher(fetcher)
    }

    fun seekTo(positionMs: Long) {
        playbackController.seekTo(positionMs)
    }

    fun skipNext() {
        playbackController.skipNext()
    }

    fun skipPrevious() {
        playbackController.skipPrevious()
    }

    fun playQueueItem(index: Int) {
        playbackController.playQueueItem(index)
    }

    fun removeQueueItem(index: Int) {
        playbackController.removeQueueItem(index)
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        playbackController.moveQueueItem(fromIndex, toIndex)
    }

    fun clearQueue() {
        playbackController.clearQueue()
    }

    fun cycleRepeatMode() {
        playbackController.cycleRepeatMode()
    }

    fun toggleShuffle() {
        playbackController.toggleShuffle()
    }

    fun setPlaybackSpeed(speed: Float, nightcore: Boolean) {
        playbackController.setPlaybackSpeed(speed, nightcore)
    }

    fun setSleepTimer(minutes: Int) {
        playbackController.setSleepTimer(minutes)
    }

    fun setSleepTimerEndOfTrack() {
        playbackController.setSleepTimerEndOfTrack()
    }

    fun cancelSleepTimer() {
        playbackController.cancelSleepTimer()
    }

    fun playNext(track: EchoTrack) {
        playbackController.playNext(track)
    }

    fun enqueue(track: EchoTrack) {
        playbackController.enqueue(track)
    }

    fun playNextByTrackId(trackId: String) {
        viewModelScope.launch {
            val track = libraryController.trackById(trackId) ?: return@launch
            playbackController.playNext(track)
        }
    }

    fun enqueueByTrackId(trackId: String) {
        viewModelScope.launch {
            val track = libraryController.trackById(trackId) ?: return@launch
            playbackController.enqueue(track)
        }
    }

    fun refreshHomeRecommendations() {
        libraryController.refreshHomeRecommendations()
    }

    fun startEchoLinkDiscovery() {
        echoLinkLanBrowser.start()
        lanRendererBrowser.start()
    }

    fun refreshEchoLinkDiscovery() {
        echoLinkLanBrowser.restart()
        lanRendererBrowser.restart()
    }

    fun stopEchoLinkDiscovery() {
        echoLinkLanBrowser.stop()
        lanRendererBrowser.stop()
    }

    fun scanReplayGainForCurrentTrack() {
        val track = playbackController.playbackStatus.value.track ?: return
        replayGainScanJob?.cancel()
        replayGainScanJob = viewModelScope.launch {
            _replayGainScanState.value = EchoReplayGainScanState.Scanning
            val next = withContext(Dispatchers.IO) {
                val local = libraryController.trackById(track.id)
                val source = LibrarySource(track.sourceId ?: local?.source?.id.orEmpty())
                if (!source.isLocalAudioFile) {
                    return@withContext EchoReplayGainScanState.Failed(EchoReplayGainScanFailure.NotLocal)
                }
                val uri = local?.uri ?: track.uri
                val gain = replayGainScanner.scanTrackGainDb(uri, local?.mimeType)
                    ?: return@withContext EchoReplayGainScanState.Failed(EchoReplayGainScanFailure.DecodeFailed)
                when (libraryController.writeReplayGainTrackGain(track.id, gain)) {
                    is EmbeddedTagWriteResult.Written -> {
                        playbackController.invalidateReplayGain(track.id)
                        EchoReplayGainScanState.Written(gain)
                    }
                    EmbeddedTagWriteResult.UnsupportedFormat ->
                        EchoReplayGainScanState.Failed(EchoReplayGainScanFailure.Unsupported)
                    EmbeddedTagWriteResult.NotLocal ->
                        EchoReplayGainScanState.Failed(EchoReplayGainScanFailure.NotLocal)
                    else -> EchoReplayGainScanState.Failed(EchoReplayGainScanFailure.WriteFailed)
                }
            }
            _replayGainScanState.value = next
        }
    }

    fun setReplayGain(enabled: Boolean, preampDb: Float) {
        playbackController.setReplayGain(enabled, preampDb)
        viewModelScope.launch { settingsStore.setReplayGain(enabled, preampDb) }
    }

    fun setReplayGainMode(mode: app.echo.android.model.playback.EchoReplayGainMode) {
        playbackController.setReplayGainMode(mode)
        viewModelScope.launch { settingsStore.setReplayGainMode(mode.id) }
    }

    fun adjustReplayGainPreamp(deltaDb: Float) {
        playbackController.adjustReplayGainPreamp(deltaDb)
        val status = playbackController.playbackStatus.value
        viewModelScope.launch { settingsStore.setReplayGain(enabled = true, preampDb = status.replayGainPreampDb) }
    }

    fun setSkipSilenceEnabled(enabled: Boolean) {
        playbackController.setSkipSilenceEnabled(enabled)
    }

    fun cyclePlayMode() {
        playbackController.cyclePlayMode()
    }

    fun importLyrics(uri: Uri) {
        val trackId = playbackController.currentTrackId
        lyricsController.importLyrics(uri, trackId) { lyrics ->
            trackId?.let { writeImportedLyricsToFile(it, lyrics) }
        }
    }

    fun importLyricsForTrack(trackId: String, uri: Uri) {
        lyricsController.importLyrics(uri, trackId) { lyrics ->
            writeImportedLyricsToFile(trackId, lyrics)
        }
    }

    private fun writeImportedLyricsToFile(trackId: String, lyrics: EchoLyrics) {
        viewModelScope.launch {
            val text = EchoLrcFormatter.format(lyrics)
            if (text.isBlank()) return@launch
            withReleasedPlaybackFile(trackId) {
                handleEmbeddedTagWriteResult(
                    trackId = trackId,
                    result = libraryController.writeEmbeddedLyrics(trackId, text).fileWrite,
                    lyricsText = text,
                )
            }
        }
    }

    fun setEchoLinkLyrics(trackId: String, lyrics: EchoRemoteLyrics) {
        lyricsController.setEchoLinkLyrics(
            trackId = trackId,
            rawText = lyrics.rawText,
            sourceLabel = lyrics.sourceLabel,
        )
    }

    fun adjustLyricsOffset(deltaMs: Long) {
        lyricsController.adjustLyricsOffset(deltaMs, playbackController.currentTrackId)
    }

    fun resetLyricsOffset() {
        lyricsController.resetLyricsOffset(playbackController.currentTrackId)
    }

    fun setDynamicArtworkEnabled(enabled: Boolean) {
        updateSettings {
            setDynamicArtworkEnabled(enabled)
        }
    }

    fun setCompactModeEnabled(enabled: Boolean) {
        updateSettings {
            setCompactModeEnabled(enabled)
        }
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        updateSettings {
            setDynamicColorEnabled(enabled)
        }
    }

    fun setPlaybackHapticsEnabled(enabled: Boolean) {
        updateSettings {
            setPlaybackHapticsEnabled(enabled)
        }
    }

    fun setPerformanceMode(value: String) {
        updateSettings {
            setPerformanceMode(value)
        }
    }

    fun setEffectivePerformanceMode(mode: EchoEffectivePerformanceMode) {
        if (effectivePerformanceMode == mode) return
        val previous = effectivePerformanceMode
        effectivePerformanceMode = mode
        libraryController.setEffectivePerformanceMode(mode)
        EchoPlaybackCachePolicy.bindDeviceConstraints(getApplication())
        EchoPlaybackCachePolicy.setEffectivePerformanceMode(mode)
        EchoArtworkImageLoader.setEffectivePerformanceMode(getApplication(), previous, mode)
        playbackController.setProgressUpdatePolicy(mode, playbackProgressUiVisibility)
    }

    internal fun setPlaybackProgressUiVisibility(visibility: PlaybackProgressUiVisibility) {
        if (playbackProgressUiVisibility == visibility) return
        playbackProgressUiVisibility = visibility
        playbackController.setProgressUpdatePolicy(effectivePerformanceMode, visibility)
    }

    fun setTrackAudioInfoTagsVisible(visible: Boolean) {
        updateSettings {
            setTrackAudioInfoTagsVisible(visible)
        }
    }

    fun setWatchedFolderRescanEnabled(enabled: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                settingsStore.setWatchedFolderRescanEnabled(enabled)
            }
            if (enabled) {
                libraryController.onForeground()
            } else {
                libraryController.cancelWatchedFolderRescan()
            }
        }
    }

    fun setPcHandoffEnabled(enabled: Boolean) {
        updateSettings {
            setPcHandoffEnabled(enabled)
        }
    }

    fun setShowLyricsControlDeck(enabled: Boolean) {
        updateSettings {
            setShowLyricsControlDeck(enabled)
        }
    }

    fun setOnlineLyricsEnabled(enabled: Boolean) {
        lyricsController.setOnlineLyricsEnabled(enabled, playbackController.currentTrackId)
        updateSettings {
            setOnlineLyricsEnabled(enabled)
        }
    }

    fun setUsbExclusiveEnabled(enabled: Boolean) {
        playbackController.setUsbExclusiveEnabled(enabled)
        updateSettings {
            setUsbExclusiveEnabled(enabled)
        }
    }

    fun setTrackTransitions(options: app.echo.android.model.playback.EchoTrackTransitionOptions) {
        updateSettings { setTrackTransitions(options) }
    }

    fun setUsbBitPerfectEnabled(enabled: Boolean) {
        updateSettings { setUsbBitPerfectEnabled(enabled) }
    }

    fun setUsbExclusiveAutoRequestOnStartup(enabled: Boolean) {
        updateSettings {
            setUsbExclusiveAutoRequestOnStartup(enabled)
        }
    }

    fun setEqualizerPreamp(gainDb: Float) {
        playbackController.setEqualizerPreamp(gainDb)
        updateSettings { setEqualizerPreamp(gainDb) }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        playbackController.setEqualizerEnabled(enabled)
        updateSettings {
            setEqualizerEnabled(enabled)
        }
    }

    fun setEqualizerPreset(presetId: String) {
        playbackController.setEqualizerPreset(presetId)
        updateSettings {
            setEqualizerPreset(presetId)
        }
    }

    fun setEqualizerBandGain(index: Int, gainDb: Float) {
        if (playbackController.equalizerState.value.parametric) return
        playbackController.setEqualizerBandGain(index, gainDb)
        val gainsDb = playbackController.equalizerState.value.gainsDb
        updateSettings {
            setEqualizerBandGains(gainsDb)
        }
    }

    fun resetEqualizer() {
        playbackController.resetEqualizer()
        updateSettings {
            resetEqualizer()
        }
    }

    fun setChannelBalance(state: EchoChannelBalanceState) {
        playbackController.setChannelBalance(state)
        updateSettings { setChannelBalance(state) }
    }

    fun resetChannelBalance() {
        playbackController.resetChannelBalance()
        updateSettings { setChannelBalance(EchoChannelBalanceState()) }
    }

    fun refreshOutputRoute() {
        playbackController.refreshOutputRoute()
    }

    fun updateOpraQuery(query: String) = opraSearch.setQuery(query)

    fun searchOpraHeadphoneCorrections(refresh: Boolean = false) = opraSearch.search(refresh)

    fun selectOpraPreset(eqId: String) = opraSearch.select(eqId)

    fun applySelectedOpraPreset() {
        val preset = opraState.value.selectedPreset ?: return
        if (opraState.value.loading) return
        playbackController.applyOpraPreset(preset)
        val equalizer = playbackController.equalizerState.value
        updateSettings {
            setEqualizerParametricConfig(equalizer.gainsDb, equalizer.preampDb, equalizer.filters, equalizer.sourceLabel)
        }
        opraSearch.message(if (EchoPlaybackProcessRuntime.usbBitPerfectEnabled) echoText(
            en = "Saved. Bit-perfect mode currently bypasses EQ.",
            zh = "已保存；bit-perfect 模式当前旁路 EQ。", ja = "保存しました。bit-perfect モードでは EQ はバイパスされます。",
        ) else echoText(en = "Saved ${preset.displayName}. EQ enabled.",
            zh = "已保存 ${preset.displayName}，均衡器已启用。", ja = "${preset.displayName} を保存し、EQ を有効にしました。"))
    }

    fun testUsbExclusiveDriver() {
        _usbExclusiveTestResult.value = getApplication<Application>().getString(R.string.usb_test_running)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                playbackController.testUsbExclusiveDriver()
            }
            _usbExclusiveTestResult.value = result
            if (isUsbExclusiveTestFailure(result)) {
                EchoErrorLog.record(EchoErrorSource.Usb, result)
            }
        }
    }

    fun setCustomBackground(mode: String, uri: Uri?) {
        updateSettings {
            setCustomBackground(mode, uri?.toString())
        }
    }

    fun setCustomBackgroundStyle(style: EchoBackgroundStyle) {
        updateSettings { setCustomBackgroundStyle(style) }
    }

    fun setCustomBackgroundBlur(value: Float) {
        updateSettings {
            setCustomBackgroundBlur(value)
        }
    }

    fun setCustomBackgroundBrightness(value: Float) {
        updateSettings {
            setCustomBackgroundBrightness(value)
        }
    }

    fun setCustomBackgroundGlass(value: Float) {
        updateSettings {
            setCustomBackgroundGlass(value)
        }
    }

    fun setCustomBackgroundScale(value: Float) {
        updateSettings {
            setCustomBackgroundScale(value)
        }
    }

    fun setUiFontFamily(value: String) {
        updateSettings {
            setUiFontFamily(value)
        }
    }

    fun setUiFontScale(value: Float) {
        updateSettings {
            setUiFontScale(value)
        }
    }

    fun setUiDensityScale(value: Float) {
        updateSettings {
            setUiDensityScale(value)
        }
    }

    fun setLyricsFontFamily(value: String) {
        updateSettings {
            setLyricsFontFamily(value)
        }
    }

    fun setLyricsFontScale(value: Float) {
        updateSettings {
            setLyricsFontScale(value)
        }
    }

    fun setLyricsColorMode(value: String) {
        updateSettings {
            setLyricsColorMode(value)
        }
    }

    fun setLyricsAlignment(value: String) {
        updateSettings {
            setLyricsAlignment(value)
        }
    }

    fun setLyricsLineSpacing(value: Float) {
        updateSettings {
            setLyricsLineSpacing(value)
        }
    }

    fun setLyricsBackgroundDim(value: Float) {
        updateSettings {
            setLyricsBackgroundDim(value)
        }
    }

    fun setLyricsWordHighlightEnabled(enabled: Boolean) {
        updateSettings {
            setLyricsWordHighlightEnabled(enabled)
        }
    }

    fun setLyricsWordHighlightIntensity(value: Float) {
        updateSettings {
            setLyricsWordHighlightIntensity(value)
        }
    }

    fun setLyricsImmersiveModeEnabled(enabled: Boolean) {
        updateSettings {
            setLyricsImmersiveModeEnabled(enabled)
        }
    }

    fun setLyricsMotionMode(value: String) {
        updateSettings {
            setLyricsMotionMode(value)
        }
    }

    fun setLyricsShowTranslation(enabled: Boolean) {
        updateSettings {
            setLyricsShowTranslation(enabled)
        }
    }

    fun setLyricsShowRomanization(enabled: Boolean) {
        updateSettings {
            setLyricsShowRomanization(enabled)
        }
    }

    fun setLyricsFocusGlowEnabled(enabled: Boolean) {
        updateSettings {
            setLyricsFocusGlowEnabled(enabled)
        }
    }

    fun setImportedFontUri(uri: Uri?) {
        updateSettings {
            setImportedFontUri(uri?.toString())
        }
    }

    fun setThemeMode(value: String) {
        updateSettings {
            setThemeMode(value)
        }
    }

    fun setColorTheme(value: String) {
        updateSettings {
            setColorTheme(value)
        }
    }

    fun setAppLanguage(value: String) {
        val language = EchoAppLanguage.fromId(value)
        settingsStore.persistAppLanguageSnapshot(language)
        getApplication<Application>().applyEchoAppLocale(language)
        updateSettings {
            setAppLanguage(language)
        }
    }

    fun setScheduledDarkModeEnabled(enabled: Boolean) {
        updateSettings {
            setScheduledDarkModeEnabled(enabled)
        }
    }

    fun setScheduledDarkStartMinute(value: Int) {
        updateSettings {
            setScheduledDarkStartMinute(value)
        }
    }

    fun setScheduledDarkEndMinute(value: Int) {
        updateSettings {
            setScheduledDarkEndMinute(value)
        }
    }

    fun setLastFmEnabled(enabled: Boolean) {
        updateSettings {
            setLastFmEnabled(enabled)
        }
    }

    fun saveEchoLinkPcEndpoint(address: String, token: String) {
        updateSettings {
            setEchoLinkPcEndpoint(address, token)
        }
    }

    fun forgetSavedEchoLinkPc(address: String) {
        updateSettings {
            forgetSavedEchoLinkPc(address)
        }
    }

    fun setEchoLinkAutoReconnectEnabled(enabled: Boolean) {
        updateSettings {
            setEchoLinkAutoReconnectEnabled(enabled)
        }
    }

    fun setEchoLinkPreferLinkedLibrary(enabled: Boolean) {
        updateSettings {
            setEchoLinkPreferLinkedLibrary(enabled)
        }
    }

    fun setLibrarySelectedSource(source: String) {
        updateSettings {
            setLibrarySelectedSource(source)
        }
    }

    fun clearEchoLinkPcEndpoint() {
        updateSettings {
            clearEchoLinkPcEndpoint()
        }
    }

    fun saveSubsonicCredentials(
        serverUrl: String,
        username: String,
        password: String,
    ) {
        updateSettings {
            setSubsonicCredentials(serverUrl, username, password)
        }
    }

    fun clearSubsonicCredentials() {
        viewModelScope.launch {
            val settings = withContext(Dispatchers.IO) {
                settingsStore.appSettings.first()
            }
            val sourceId = subsonicEndpointFrom(settings)?.sourceId
            withContext(Dispatchers.IO) {
                settingsStore.clearSubsonicCredentials()
            }
            if (sourceId != null) {
                libraryController.deleteRemoteSource(sourceId)
                if (selectedLibrarySource == sourceId) {
                    setLibrarySelectedSource(EchoLibrarySelectedSource.Local)
                }
            }
        }
    }

    fun syncSubsonicLibrary(
        serverUrl: String,
        username: String,
        password: String,
    ) {
        val endpoint = SubsonicEndpoint(
            baseUrl = serverUrl,
            username = username,
            password = password,
        )
        libraryController.refreshSubsonic(endpoint) {
            saveSubsonicCredentials(serverUrl, username, password)
        }
    }

    fun saveWebDavCredentials(
        serverUrl: String,
        username: String,
        password: String,
    ) {
        updateSettings {
            setWebDavCredentials(serverUrl, username, password)
        }
    }

    fun clearWebDavCredentials() {
        updateSettings {
            clearWebDavCredentials()
        }
    }

    fun syncWebDavLibrary(
        serverUrl: String,
        username: String,
        password: String,
    ) {
        val endpoint = WebDavEndpoint(
            baseUrl = serverUrl,
            username = username,
            password = password,
        )
        libraryController.refreshWebDav(endpoint)
        saveWebDavCredentials(serverUrl, username, password)
    }

    fun saveJellyfinCredentials(
        serverUrl: String,
        username: String,
        password: String,
        accessToken: String? = null,
        userId: String? = null,
    ) {
        viewModelScope.launch {
            var token = accessToken
            var jellyfinUserId = userId
            if (token.isNullOrBlank() || jellyfinUserId.isNullOrBlank()) {
                val authed = runCatching {
                    libraryController.authenticateJellyfin(
                        JellyfinEndpoint(serverUrl, username, password),
                    )
                }.getOrNull()
                token = authed?.accessToken ?: token
                jellyfinUserId = authed?.userId ?: jellyfinUserId
            }
            withContext(Dispatchers.IO) {
                settingsStore.setJellyfinCredentials(
                    serverUrl = serverUrl,
                    username = username,
                    password = password,
                    accessToken = token,
                    userId = jellyfinUserId,
                )
            }
        }
    }

    fun clearJellyfinCredentials() {
        viewModelScope.launch {
            val settings = withContext(Dispatchers.IO) {
                settingsStore.appSettings.first()
            }
            val sourceId = jellyfinEndpointFrom(settings)?.sourceId
            withContext(Dispatchers.IO) {
                settingsStore.clearJellyfinCredentials()
            }
            if (sourceId != null) {
                libraryController.deleteRemoteSource(sourceId)
                if (selectedLibrarySource == sourceId) {
                    setLibrarySelectedSource(EchoLibrarySelectedSource.Local)
                }
            }
        }
    }

    fun syncJellyfinLibrary(
        serverUrl: String,
        username: String,
        password: String,
    ) {
        val endpoint = JellyfinEndpoint(
            baseUrl = serverUrl,
            username = username,
            password = password,
        )
        libraryController.refreshJellyfin(endpoint) { token, userId ->
            saveJellyfinCredentials(
                serverUrl = serverUrl,
                username = username,
                password = password,
                accessToken = token,
                userId = userId,
            )
        }
    }

    fun setListenBrainzEnabled(enabled: Boolean) {
        updateSettings {
            setListenBrainzEnabled(enabled)
        }
    }

    fun saveListenBrainzToken(token: String) {
        viewModelScope.launch {
            val trimmed = token.trim()
            if (trimmed.isBlank()) {
                listenBrainzController.setError("Missing ListenBrainz token")
                return@launch
            }
            listenBrainzController.setConnecting()
            val result = withContext(Dispatchers.IO) {
                listenBrainzClient.validateToken(trimmed)
            }
            result
                .onSuccess { userName ->
                    withContext(Dispatchers.IO) {
                        settingsStore.setListenBrainzToken(trimmed)
                    }
                    listenBrainzController.setConnected(userName)
                }
                .onFailure { error ->
                    listenBrainzController.setError(error.message ?: "ListenBrainz token is not valid")
                }
        }
    }

    fun disconnectListenBrainz() {
        listenBrainzController.setDisconnected()
        updateSettings {
            clearListenBrainzToken()
        }
    }

    fun connectLastFm(
        apiKey: String,
        sharedSecret: String,
        username: String,
        password: String,
    ) {
        viewModelScope.launch {
            val resolvedApiKey = apiKey.ifBlank { LastFmApiConfig.API_KEY }
            val resolvedSharedSecret = sharedSecret.ifBlank { LastFmApiConfig.SHARED_SECRET }
            if (resolvedApiKey.isBlank()) {
                lastFmController.setError("Missing Last.fm API key")
                return@launch
            }
            if (resolvedSharedSecret.isBlank()) {
                lastFmController.setError("Missing Last.fm shared secret")
                return@launch
            }
            lastFmController.setConnecting()
            val result = withContext(Dispatchers.IO) {
                lastFmClient.authenticate(
                    apiKey = resolvedApiKey,
                    sharedSecret = resolvedSharedSecret,
                    username = username,
                    password = password,
                )
            }
            result
                .onSuccess { session ->
                    withContext(Dispatchers.IO) {
                        settingsStore.setLastFmCredentials(
                            apiKey = resolvedApiKey,
                            sharedSecret = resolvedSharedSecret,
                            username = session.username,
                            sessionKey = session.sessionKey,
                        )
                    }
                    lastFmController.setConnected(session.username)
                }
                .onFailure { error ->
                    lastFmController.setError(error.message ?: "Unknown Last.fm auth error")
                }
        }
    }

    fun startLastFmWebAuth(onOpenAuthUrl: (String) -> Unit) {
        viewModelScope.launch {
            val resolvedApiKey = LastFmApiConfig.API_KEY
            val resolvedSharedSecret = LastFmApiConfig.SHARED_SECRET
            if (resolvedApiKey.isBlank()) {
                lastFmController.setError("Missing Last.fm API key")
                return@launch
            }
            if (resolvedSharedSecret.isBlank()) {
                lastFmController.setError("Missing Last.fm shared secret")
                return@launch
            }
            lastFmController.setConnecting()
            val result = withContext(Dispatchers.IO) {
                lastFmClient.createWebAuthToken(
                    apiKey = resolvedApiKey,
                    sharedSecret = resolvedSharedSecret,
                )
            }
            result
                .onSuccess { auth ->
                    pendingLastFmAuthToken = auth.token
                    lastFmController.setWebAuthPending()
                    onOpenAuthUrl(auth.url)
                }
                .onFailure { error ->
                    lastFmController.setError(error.message ?: "Unable to start Last.fm web auth")
                }
        }
    }

    fun completeLastFmWebAuth() {
        viewModelScope.launch {
            val token = pendingLastFmAuthToken
            if (token.isNullOrBlank()) {
                lastFmController.setError("Open the Last.fm authorization page first")
                return@launch
            }
            val resolvedApiKey = LastFmApiConfig.API_KEY
            val resolvedSharedSecret = LastFmApiConfig.SHARED_SECRET
            if (resolvedApiKey.isBlank() || resolvedSharedSecret.isBlank()) {
                lastFmController.setError("Missing Last.fm app credentials")
                return@launch
            }
            lastFmController.setConnecting()
            val result = withContext(Dispatchers.IO) {
                lastFmClient.completeWebAuth(
                    apiKey = resolvedApiKey,
                    sharedSecret = resolvedSharedSecret,
                    token = token,
                )
            }
            result
                .onSuccess { session ->
                    pendingLastFmAuthToken = null
                    withContext(Dispatchers.IO) {
                        settingsStore.setLastFmCredentials(
                            apiKey = resolvedApiKey,
                            sharedSecret = resolvedSharedSecret,
                            username = session.username,
                            sessionKey = session.sessionKey,
                        )
                    }
                    lastFmController.setConnected(session.username)
                }
                .onFailure { error ->
                    lastFmController.setWebAuthError(error.message ?: "Last.fm authorization has not been approved yet")
                }
        }
    }

    fun disconnectLastFm() {
        pendingLastFmAuthToken = null
        lastFmController.setDisconnected()
        updateSettings {
            clearLastFmCredentials()
        }
    }

    private fun updateSettings(block: suspend EchoSettingsStore.() -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                settingsStore.block()
            }
        }
    }

    override fun onCleared() {
        libraryController.clear()
        lyricsController.clear()
        playbackController.clear()
        lastFmController.clear()
        listenBrainzController.clear()
        echoLinkLanBrowser.stop()
        lanRendererBrowser.stop()
        EchoSubsonicListen.startFromSurface()
        super.onCleared()
    }

    private fun isUsbExclusiveTestFailure(result: String): Boolean =
        result.startsWith("Permission denied") ||
            result.startsWith("No USB") ||
            result.startsWith("Format unavailable") ||
            result.startsWith("Open failed") ||
            result.startsWith("Unsupported transport")

    private fun recordRecentPlayback(trackId: String) {
        viewModelScope.launch {
            recordPlaybackHeatmapTick()
            val (album, artist) = withContext(Dispatchers.IO) {
                repository.recordPlayback(trackId)
                repository.albumSummaryForTrack(trackId) to repository.artistSummaryForTrack(trackId)
            }
            album?.let { summary ->
                albumPlaybackCounts[summary.albumKey] = (albumPlaybackCounts[summary.albumKey] ?: 0) + 1
                _recentPlaybackAlbums.value = (listOf(summary) + _recentPlaybackAlbums.value)
                    .distinctBy { it.albumKey }
                    .sortedByDescending { albumPlaybackCounts[it.albumKey] ?: 0 }
                    .take(12)
            }
            artist?.let { summary ->
                artistPlaybackCounts[summary.artistKey] = (artistPlaybackCounts[summary.artistKey] ?: 0) + 1
                _recentPlaybackArtists.value = (listOf(summary) + _recentPlaybackArtists.value)
                    .distinctBy { it.artistKey }
                    .sortedByDescending { artistPlaybackCounts[it.artistKey] ?: 0 }
                    .take(8)
            }
        }
    }

    private fun recordPlaybackHeatmapTick() {
        val today = LocalDate.now().toEpochDay()
        val firstVisibleDay = today - HOME_HEATMAP_VISIBLE_DAYS + 1
        playbackHeatmapCounts[today] = (playbackHeatmapCounts[today] ?: 0) + 1
        playbackHeatmapCounts.keys.removeAll { it < firstVisibleDay }
        _recentPlaybackHeatmap.value = playbackHeatmapCounts
            .toSortedMap()
            .map { (epochDay, count) ->
                PlaybackHeatmapDay(
                    epochDay = epochDay,
                    playCount = count,
                )
            }
    }

    private companion object {
        const val HOME_HEATMAP_VISIBLE_DAYS = 84L
    }
}

data class PendingEmbeddedTagWrite(
    val requestId: Long,
    val trackId: String,
    val contentUri: String,
    val intentSender: IntentSender? = null,
    val needsStoragePermission: Boolean = false,
    val lyricsText: String? = null,
    val artworkUri: String? = null,
)

enum class EmbeddedTagWriteUserMessage {
    Written,
    IndexOnly,
    UnsupportedFormat,
    Failed,
}
