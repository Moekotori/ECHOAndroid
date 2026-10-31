package app.echo.android

import app.echo.android.model.settings.EchoBackgroundStyle
import app.echo.android.model.settings.EchoCustomColors
import app.echo.android.model.settings.EchoSavedColorThemeResult

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
import app.echo.android.data.createOrderedLocalPlaylist
import app.echo.android.data.toEchoTrack
import app.echo.android.data.EchoLibraryDatabase
import app.echo.android.data.EchoLibraryRepository
import app.echo.android.data.batchTags
import app.echo.android.data.deleteSelectedTracks
import app.echo.android.data.libraryHealthStats
import app.echo.android.data.libraryAudioProfile
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
import app.echo.android.data.decodeLegacyTagText
import app.echo.android.data.DocumentTreeTrackScanner
import app.echo.android.data.EmbeddedTagWriteResult
import app.echo.android.data.EmbeddedTagWriter
import app.echo.android.data.MediaStoreTrackScanner
import app.echo.android.data.LocalLibrarySearchResults
import app.echo.android.data.OpraHeadphoneCorrectionRepository
import app.echo.android.data.JellyfinEndpoint
import app.echo.android.data.SubsonicEndpoint
import app.echo.android.data.trackVersions
import app.echo.android.data.setPreferredTrackVersion
import app.echo.android.lyrics.EchoLrcFormatter
import android.content.IntentSender
import java.util.concurrent.atomic.AtomicLong

import app.echo.android.data.WebDavEndpoint
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
import app.echo.android.model.playback.EchoEqualizerShareCodec
import app.echo.android.model.playback.EchoEqualizerUserPresets
import app.echo.android.model.playback.PlaybackControlsState
import app.echo.android.model.playback.PlaybackDiagnosticsState
import app.echo.android.model.playback.OpraHeadphoneCorrectionState
import app.echo.android.model.playback.ListeningStats
import app.echo.android.model.playback.ListeningStatsRange
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
import java.util.UUID
import app.echo.android.data.observeHiddenTrackCount
import app.echo.android.data.pagedHiddenTracks
import app.echo.android.data.hideTracks
import app.echo.android.data.restoreHiddenTracks
import app.echo.android.data.restoreAllHiddenTracks
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@androidx.annotation.OptIn(UnstableApi::class)
@Suppress("SpellCheckingInspection", "unused")
class EchoAndroidViewModel(application: Application) : AndroidViewModel(application) {
    val radioController = app.echo.android.radio.EchoRadioController(
        (application as EchoApplication).radioSession.store,
        app.echo.android.data.RadioBrowserClient(BuildConfig.VERSION_NAME,
            discoverMirrors = app.echo.android.data.RadioBrowserMirrorDiscovery::discover),
        viewModelScope,
    )
    internal val podcasts = app.echo.android.podcasts.PodcastController((application as EchoApplication).podcasts, viewModelScope)
    private val database = EchoLibraryDatabase.create(application)
    internal val nfcMusic = app.echo.android.nfc.NfcMusicController(application, viewModelScope)
    private val repository = EchoLibraryRepository(
        database = database,
        scanner = MediaStoreTrackScanner(application),
        documentTreeScanner = DocumentTreeTrackScanner(application.contentResolver),
        tagWriter = EmbeddedTagWriter(application),
        appContext = application,
    )
    private val errorLog = EchoErrorLogRepository.create(application)
    internal val remoteLibraryTools = RemoteLibraryWritebackController(repository) { EchoSubsonicEndpointRef.get() }
    internal val musicRelations by lazy { repository.musicRelations() }
    internal fun playMusicRelationTrack(track: EchoTrack) {
        preferredPlayback.invalidate()
        playbackController.play(track)
    }
    internal fun playMusicRelationTracks(tracks: List<EchoTrack>) {
        if (tracks.isEmpty()) return
        preferredPlayback.invalidate()
        playbackController.playQueue(tracks.take(40), 0)
    }
    internal fun playMusicRelationNext(track: EchoTrack) = playbackController.playNext(track)
    internal fun enqueueMusicRelation(track: EchoTrack) = playbackController.enqueue(track)
    internal val navidromeTools by lazy {
        NavidromeController(getApplication(), repository, { EchoSubsonicEndpointRef.get() },
            currentQueue = { playbackQueue.value to currentPlaybackPositionMs() },
            play = { track, position -> preferredPlayback.invalidate(); playbackController.playAt(track, position) },
            restore = { tracks, index, position -> preferredPlayback.invalidate()
                playbackController.playQueue(tracks, index, startPositionMs = position) })
    }
    internal val mediaInspection by lazy { MediaInspectionController(getApplication(), repository) }
    internal val libraryExperience = LibraryExperienceController(repository, application, remoteLibraryTools::setFavorite)
    internal val playlistTools = PlaylistManagementController(application, repository, viewModelScope, remoteLibraryTools::setFavorite)
    internal val unifiedSearch = UnifiedSearchController(repository, viewModelScope)
    internal val librarySync = LibrarySyncController(repository)
    internal val personalStateSync = PersonalStateSyncController(application, repository)
    internal fun resumePersonalTrack(track: EchoTrack, positionMs: Long) {
        preferredPlayback.launch { playbackController.playAt(preferredPlayback.resolve(listOf(track)).single(), positionMs) }
    }
    internal fun reconcile(session: app.echo.android.connect.EchoLibrarySyncSession,peerId: String) = LibraryReconcileController(getApplication<Application>(),repository,session,peerId)
    private val settingsStore = EchoSettingsStore(application)
    private val echoLinkLanBrowser = EchoLinkLanBrowser(application)
    private val lanRendererBrowser = EchoLanRendererBrowser(application)
    private val opraRepository = OpraHeadphoneCorrectionRepository(application)
    val initialAppSettings: EchoAppSettings = settingsStore.startupAppSettingsSnapshot()

    // 远程播放凭据由 Application 在进程内常驻收集,ViewModel 再应用一次以便 UI 会话
    // 立刻刷新歌词/听歌记录端点,并门控 MediaController 的恢复播放。不在构造期 runBlocking。

    private val libraryController = LibraryController(
        repository = repository,
        scope = viewModelScope,
        settingsStore = settingsStore,
        resolver = application.contentResolver,
        appContext = application,
    )
    private val lyricsController = (application as EchoApplication).lyricsSession.controller
    internal val libraryMigration = LibraryMigrationController(application, repository, settingsStore, lyricsController)
    private val playbackController = PlaybackController(
        application = application,
        settingsStore = settingsStore,
        scope = viewModelScope,
        onTrackChanged = lyricsController::updateLyricsForTrack,
        onTrackActivated = ::recordRecentPlayback,
    )
    private val preferredPlayback = PreferredPlaybackSelection(viewModelScope, repository,
        readiness = {
            val settings = settingsStore.appSettings.first()
            trackVersionSourceReadiness(settings, (getApplication<Application>() as EchoApplication)
                .echoLinkSession.client.status.value.connectionState == app.echo.android.model.connect.EchoRemoteConnectionState.Connected)
        },
        onFailure = { error ->
            EchoErrorLog.record(EchoErrorSource.Library, "Song version selection failed", throwable = error)
            viewModelScope.launch {
                android.widget.Toast.makeText(application, app.echo.android.feature.library.R.string.track_versions_play_error,
                    android.widget.Toast.LENGTH_LONG).show()
            }
        })
    internal val broadcastPlayback by lazy { BroadcastPlaybackController(application as EchoApplication, playbackController, preferredPlayback, radioController) }
    internal fun cancelPendingPlaybackSelection() = preferredPlayback.invalidate()
    internal fun observeHiddenTracksCount() = repository.observeHiddenTrackCount()
    internal fun hiddenTracks() = repository.pagedHiddenTracks()
    internal suspend fun hideLibraryTracks(ids: List<String>) {
        // ViewModel ownership completes the queue edit even if the confirmation sheet leaves composition.
        viewModelScope.async {
            val hidden = repository.hideTracks(ids)
            require(hidden.isNotEmpty())
            playbackController.removeHiddenTracks(hidden)
        }.await()
    }
    internal suspend fun restoreLibraryTracks(ids: List<String>) = repository.restoreHiddenTracks(ids)
    internal suspend fun restoreAllLibraryTracks() = repository.restoreAllHiddenTracks()

    internal suspend fun trackVersions(track: EchoTrack) = repository.trackVersions(track)
    internal val duplicateLibrary by lazy { LibraryDuplicatesController(application, viewModelScope) { !scanState.value.isScanning } }
    internal suspend fun setPreferredTrackVersion(track: EchoTrack, id: String?) = repository.setPreferredTrackVersion(track, id)
    private suspend fun playPreferredQueue(
        queue: List<EchoTrack>, startIndex: Int,
        intent: PlaybackQueueReplaceIntent = PlaybackQueueReplaceIntent.PlayAll,
        source: String? = queue.firstOrNull()?.album?.takeIf { album -> queue.all { it.album == album } },
    ) = playbackController.playQueue(preferredPlayback.resolve(queue), startIndex, intent, source)
    private suspend fun playPreferredAlbumQueue(
        queue: List<EchoTrack>, startIndex: Int,
        intent: PlaybackQueueReplaceIntent = PlaybackQueueReplaceIntent.PlayAll,
    ) = playbackController.playAlbumQueue(preferredPlayback.resolve(queue), startIndex, intent)

    init {
        libraryController.setPlaybackOccupiesStorage {
            playbackController.playbackControls.value.isPlaying
        }
        libraryController.startWatchingMediaStore(application.contentResolver)
        viewModelScope.launch {
            playbackController.playbackControls.map { it.isPlaying }.distinctUntilChanged().collect { playing ->
                if (!playing) libraryController.onPlaybackStorageIdle()
            }
        }
    }
    internal val diagnosticCapture get() = (getApplication<android.app.Application>() as EchoApplication).diagnosticCapture
    private val lastFmClient = LastFmClient()
    private val lastFmController = (application as EchoApplication).scrobbleSession.lastFm
    private val listenBrainzClient = ListenBrainzClient()
    private val listenBrainzController = (application as EchoApplication).scrobbleSession.listenBrainz

    private var pendingLastFmAuthToken: String? = null
    private var usbStartupPolicyApplied = false
    private var effectivePerformanceMode: EchoEffectivePerformanceMode = EchoEffectivePerformanceMode.Balanced
    private var playbackProgressUiVisibility: PlaybackProgressUiVisibility = PlaybackProgressUiVisibility.MiniPlayer
    private var selectedLibrarySource: String = initialAppSettings.librarySelectedSource

    val libraryQuery: StateFlow<String> = libraryController.libraryQuery
    val libraryTrackFilter = libraryController.trackFilter
    fun updateLibraryTrackFilter(filter: app.echo.android.model.library.LibraryTrackFilter) = libraryController.updateTrackFilter(filter)
    val libraryTrackSortMode: StateFlow<LibraryTrackSortMode> = libraryController.trackSortMode
    val libraryAlbumSortMode: StateFlow<AlbumSortMode> = libraryController.albumSortMode
    val libraryArtistSortMode: StateFlow<ArtistSortMode> = libraryController.artistSortMode
    val libraryFolderSortMode: StateFlow<FolderSortMode> = libraryController.folderSortMode
    val tracks: Flow<PagingData<EchoTrack>> = libraryController.tracks
    val albums: Flow<PagingData<AlbumSummary>> = libraryController.albums
    val remoteAlbums: Flow<PagingData<AlbumSummary>> = libraryController.remoteAlbums
    val artists: Flow<PagingData<ArtistSummary>> = libraryController.artists
    val genres: Flow<PagingData<app.echo.android.model.library.GenreSummary>> = libraryController.genres
    val composers: Flow<PagingData<app.echo.android.model.library.GenreSummary>> = libraryController.composers
    val folders: Flow<PagingData<FolderSummary>> = libraryController.folders
    val localPlaylists: StateFlow<List<EchoPlaylist>> = libraryController.localPlaylists
    val favoriteTrackIds: StateFlow<Set<String>> = libraryController.favoriteTrackIds
    val favoriteAlbums: StateFlow<List<AlbumSummary>> = libraryController.favoriteAlbums
    val libraryStats: StateFlow<LibraryStats> = libraryController.libraryStats
    internal suspend fun libraryHealthStats() = repository.libraryHealthStats()
    internal suspend fun libraryAudioProfile() = repository.libraryAudioProfile()

    private val libraryLyricsInspector by lazy {
        app.echo.android.lyrics.LibraryLyricsInspector(
            getApplication(), repository,
            isPlaying = { playbackController.playbackControls.value.isPlaying },
            awaitPlaybackIdle = { playbackController.playbackControls.first { !it.isPlaying } },
        )
    }

    private val lyricsIndexController = LibraryLyricsIndexController(viewModelScope,
        readSummary = { app.echo.android.data.LibraryLyricsIndex(database).summary() }) { report -> libraryLyricsInspector.inspect(report) }
    val libraryLyricsIndexState = lyricsIndexController.state
    fun startLibraryLyricsInspection() = lyricsIndexController.start()
    fun cancelLibraryLyricsInspection() = lyricsIndexController.cancel()
    internal suspend fun inspectLibraryLyrics(report: (app.echo.android.model.library.LibraryLyricsInspection) -> Unit) =
        lyricsIndexController.inspect(report)
    val recommendedTracks: StateFlow<List<EchoTrack>> = libraryController.recommendedTracks
    val recentlyAddedAlbums: StateFlow<List<AlbumSummary>> = libraryController.recentlyAddedAlbums
    val recentlyPlayedTracks: StateFlow<List<EchoTrack>> = libraryController.recentlyPlayedTracks
    val recommendedAlbums: StateFlow<List<AlbumSummary>> = libraryController.recommendedAlbums
    val rediscoveredAlbums: StateFlow<List<AlbumSummary>> = libraryController.rediscoveredAlbums
    val scanState: StateFlow<LibraryScanProgress> = libraryController.scanState
    internal val watchedLibraryTrees = settingsStore.watchedLibraryTreesFlow

    internal suspend fun saveLibraryScanOptions(options: LibraryScanOptions) =
        settingsStore.setLibraryScanOptions(options)

    internal suspend fun removeWatchedLibraryTree(uri: String) =
        settingsStore.removeWatchedLibraryTree(uri)
    val remoteScanState: StateFlow<LibraryScanProgress> = libraryController.remoteScanState
    val echoLinkDiscoveryState = echoLinkLanBrowser.state
    val echoLinkLanDevices = echoLinkLanBrowser.devices
    val lanRendererDiscoveryState = lanRendererBrowser.state
    val lanRenderers = lanRendererBrowser.devices

    val playbackStatus: StateFlow<EchoPlaybackStatus> = playbackController.playbackStatus
    val playbackMetadata: StateFlow<PlaybackMetadataState> = playbackController.playbackMetadata
    val playbackPosition: StateFlow<PlaybackPositionState> = playbackController.playbackPosition
    val abLoop = EchoPlaybackProcessRuntime.abLoop
    fun currentPlaybackPositionMs(): Long = playbackController.currentPositionMs()
    internal suspend fun saveEditedLyrics(trackId: String, lyrics: EchoLyrics) {
        val track = playbackStatus.value.track?.takeIf { it.id == trackId }
        val enriched = if (track == null) lyrics else lyrics.copy(metadata = lyrics.metadata + mapOf(
            "echo_title" to track.title, "echo_artist" to track.artist, "echo_album" to track.album.orEmpty(), "echo_duration_ms" to track.durationMs.toString()))
        lyricsController.saveEditedLyrics(trackId, enriched)
    }
    suspend fun playSavedClip(clip: app.echo.android.model.library.EchoSavedTrackClip) =
        playbackController.setAbLoop(clip.trackId, clip.startMs, clip.endMs, startPlayback = true)

    suspend fun setAbLoop(trackId: String?, startMs: Long, endMs: Long) = playbackController.setAbLoop(trackId, startMs, endMs)
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
    private val tagWriteAuthorizations = PendingTagWriteQueue<PendingEmbeddedTagWrite> { it.requestId }
    private val embeddedTagWriteMutex = kotlinx.coroutines.sync.Mutex()
    val pendingEmbeddedTagWrite: StateFlow<PendingEmbeddedTagWrite?> = tagWriteAuthorizations.pending
    private val _embeddedTagWriteMessage = MutableStateFlow<EmbeddedTagWriteUserMessage?>(null)
    val embeddedTagWriteMessage: StateFlow<EmbeddedTagWriteUserMessage?> = _embeddedTagWriteMessage.asStateFlow()

    fun searchLyrics() = lyricsController.refreshLyrics(playbackController.currentTrackId)
    fun cancelLyricsSearch() = lyricsController.cancelSearch()
    fun selectLyricsCandidate(id: String) = lyricsController.selectCandidate(id, playbackController.currentTrackId)
    internal suspend fun selectPlayerLyrics(id: String, trackId: String): Boolean =
        lyricsController.selectCandidateAndAwait(id, trackId)
    fun removeLyricsSelection() = lyricsController.removeSelection(playbackController.currentTrackId)

    internal fun msebPresets(presets: List<app.echo.android.model.playback.EchoMsebUserPreset>) = msebPresetActions(settingsStore, presets)

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
    private val listeningHistory = (application as EchoApplication).listeningHistory
    internal val playbackHistory = PlaybackHistoryController(viewModelScope, listeningHistory, repository, ::play)
    val recentPlaybackHeatmap: StateFlow<List<PlaybackHeatmapDay>> = listeningHistory
        .observeHeatmap(HOME_HEATMAP_VISIBLE_DAYS)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())
    private val _listeningStats = MutableStateFlow<ListeningStats?>(null)
    val listeningStats: StateFlow<ListeningStats?> = _listeningStats.asStateFlow()
    private var listeningStatsJob: Job? = null

    private val _monthlyRecap = MutableStateFlow<app.echo.android.model.playback.EchoMonthlyRecap?>(null)
    val monthlyRecap = _monthlyRecap.asStateFlow()
    private val _monthlyRecapError = MutableStateFlow(false)
    val monthlyRecapError = _monthlyRecapError.asStateFlow()
    private var monthlyRecapJob: Job? = null
    fun loadMonthlyRecap(year: Int, month: Int) {
        monthlyRecapJob?.cancel()
        _monthlyRecapError.value = false
        // Reopening the same month refreshes in place, without flashing an empty loading page.
        _monthlyRecap.value = _monthlyRecap.value?.takeIf { it.year == year && it.month == month }
        monthlyRecapJob = viewModelScope.launch {
            try { _monthlyRecap.value = listeningHistory.monthlyRecap(year, month) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { _monthlyRecapError.value = true }
        }
    }

    fun loadListeningStats(range: ListeningStatsRange) {
        listeningStatsJob?.cancel()
        listeningStatsJob = viewModelScope.launch {
            val stats = withContext(Dispatchers.IO) { listeningHistory.stats(range) }
            _listeningStats.value = stats
        }
    }
    private val _usbExclusiveTestResult = MutableStateFlow(
        application.getString(R.string.usb_test_idle),
    )
    val usbExclusiveTestResult: StateFlow<String> = _usbExclusiveTestResult.asStateFlow()
    private val opraSearch = OpraSearchController(viewModelScope, opraRepository, application)
    val opraState: StateFlow<OpraHeadphoneCorrectionState> = opraSearch.state

    private val albumPlaybackCounts = mutableMapOf<String, Int>()
    private val artistPlaybackCounts = mutableMapOf<String, Int>()
    init {
        EchoSubsonicListen.startFromPlayback(
            playbackStatus = playbackController.playbackStatus,
            playbackPosition = playbackController.playbackPosition,
            settingsReady = settingsStore.appSettings,
        )
        viewModelScope.launch {
            var lastChannelBalance = EchoChannelBalanceState()
            settingsStore.appSettings.collect { settings ->
                selectedLibrarySource = settings.librarySelectedSource
                withContext(Dispatchers.IO) {
                    settingsStore.cacheStartupThemeSnapshot(settings)
                }
                lyricsController.setOnlineLyricsEnabled(settings.onlineLyricsEnabled, playbackController.currentTrackId)
                val firstSettingsEmission = !usbStartupPolicyApplied
                usbStartupPolicyApplied = true
                if (firstSettingsEmission) {
                    opraSearch.restoreQuery(settings.opraLastQuery)
                }
                val usbAlreadyActive = playbackController.isUsbExclusiveEnabled()
                val shouldEnableUsbExclusive = if (firstSettingsEmission && !usbAlreadyActive) {
                    settings.usbExclusiveEnabled && settings.usbExclusiveAutoRequestOnStartup
                } else {
                    settings.usbExclusiveEnabled
                }
                playbackController.setUsbOutputOptions(settings.usbOutputOptions)
                playbackController.setUsbOutputMode(shouldEnableUsbExclusive, settings.usbBitPerfectEnabled)
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

    fun artistAlbumPaging(artistKey: String): Flow<PagingData<AlbumSummary>> =
        libraryController.artistAlbumPaging(artistKey)

    fun observeArtistSummary(artistKey: String): Flow<ArtistSummary?> =
        libraryController.observeArtistSummary(artistKey)

    fun artistTrackPaging(artistKey: String, query: String? = null, sort: LibraryTrackSortMode = LibraryTrackSortMode.Album): Flow<PagingData<EchoTrack>> =
        libraryController.artistTrackPaging(artistKey, query, sort)

    fun genreTrackPaging(genreKey: String): Flow<PagingData<EchoTrack>> =
        libraryController.genreTrackPaging(genreKey)

    fun folderTrackPaging(folderKey: String): Flow<PagingData<EchoTrack>> =
        libraryController.folderTrackPaging(folderKey)

    fun playlistTrackPaging(playlistId: String): Flow<PagingData<EchoTrack>> =
        libraryController.playlistTrackPaging(playlistId)

    fun artistNavigationTarget(name: String, artworkUri: String? = null): ArtistSummary? =
        libraryController.artistNavigationTarget(name, artworkUri)

    suspend fun albumForTrack(trackId: String): AlbumSummary? =
        libraryController.albumSummaryForTrack(trackId)

    internal suspend fun playerLibraryTrack(trackId: String): EchoTrack? = withContext(Dispatchers.IO) {
        repository.trackById(trackId)?.toEchoTrack()
    }

    internal suspend fun playerTrackFolder(trackId: String): app.echo.android.model.library.FolderSummary? = withContext(Dispatchers.IO) {
        val track = repository.trackById(trackId) ?: return@withContext null
        if (track.source != "mediastore" && track.source != "saf") return@withContext null
        database.trackDao().getFolderSummary(track.relativePath.orEmpty())
    }

    suspend fun artistForTrack(trackId: String): ArtistSummary? =
        libraryController.artistSummaryForTrack(trackId)

    suspend fun contributingArtists(trackId: String, name: String, artworkUri: String?): List<ArtistSummary> =
        libraryController.contributingArtists(trackId, name, artworkUri)

    suspend fun clearLocalLibraryIndex(): Boolean = libraryController.clearLocalLibraryIndex()

    suspend fun cleanupLocalLibrary(): app.echo.android.data.LibraryHygieneResult =
        libraryController.cleanupLocalLibrary()

    fun refreshLibrary(options: LibraryScanOptions? = null) {
        libraryController.refreshLibrary(options)
    }

    fun refreshLibraryIfEmpty() {
        libraryController.refreshLibraryIfEmpty()
    }

    internal fun refreshReceivedMusicFolder(treeUri: Uri, options: LibraryScanOptions) = libraryController.refreshReceivedMusicFolder(treeUri, options)

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

    suspend fun localTitleIndex(letter: Char): Int = libraryController.localTitleIndex(letter)

    suspend fun albumTitleIndex(letter: Char, remote: Boolean): Int = repository.albumTitleIndex(letter, remote)

    suspend fun localTrackIndex(trackId: String, query: String, sort: LibraryTrackSortMode,
        filter: app.echo.android.model.library.LibraryTrackFilter): Int? =
        repository.localTrackIndex(trackId, query, sort, filter)

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
        if (track.source.id == "radio") {
            preferredPlayback.invalidate()
            playbackController.play(track)
        } else preferredPlayback.launch { playbackController.play(preferredPlayback.resolve(listOf(track)).single()) }
    }

    fun playIncomingAudio(uris: List<String>) {
        val parsed = uris.mapNotNull { raw -> raw.trim().takeIf { it.isNotBlank() }?.let(Uri::parse) }
        if (parsed.isEmpty()) return
        preferredPlayback.launch {
            val tracks = withContext(Dispatchers.IO) {
                parsed.mapNotNull { uri ->
                    tryTakePersistableReadPermission(getApplication(), uri)
                    resolveIncomingAudioTrack(getApplication(), repository, uri)
                }
            }
            when {
                tracks.size == 1 -> playbackController.play(preferredPlayback.resolve(tracks).single())
                tracks.isNotEmpty() -> playPreferredQueue(tracks, 0)
            }
        }
    }

    fun playQueue(queue: List<EchoTrack>, startIndex: Int) {
        preferredPlayback.launch { playPreferredQueue(queue, startIndex) }
    }

    fun playFromLibrary(track: EchoTrack, origin: LibraryPlaybackOrigin) {
        val source = selectedLibrarySource.ifBlank { EchoLibrarySelectedSource.Local }
        val query = libraryController.songQuerySnapshot()
        preferredPlayback.launch {
            if (LibraryPlaybackQueuePolicy.usesCollectionQueue(origin)) {
                val queue = libraryController.collectionTracksForPlayback(origin, track.id)
                if (queue.isEmpty()) return@launch
                val startIndex = LibraryPlaybackQueuePolicy.startIndex(queue.map { it.id }, track.id)
                if (startIndex < 0) return@launch
                if (origin is LibraryPlaybackOrigin.Album) {
                    playPreferredAlbumQueue(queue, startIndex)
                    return@launch
                }
                playPreferredQueue(queue, startIndex, source = when (origin) {
                    is LibraryPlaybackOrigin.Album -> track.album
                    is LibraryPlaybackOrigin.Artist -> track.artist
                    is LibraryPlaybackOrigin.Playlist -> libraryController.localPlaylists.value.firstOrNull { it.id == origin.playlistId }?.name
                    is LibraryPlaybackOrigin.Folder -> origin.folderKey.substringAfterLast('/')
                    LibraryPlaybackOrigin.Songs -> null
                })
                return@launch
            }
            val queue = libraryController.queueAroundTrack(track.id, source, query)
            if (queue.isEmpty()) return@launch
            val startIndex = LibraryPlaybackQueuePolicy.startIndex(queue.map { it.id }, track.id)
            if (startIndex >= 0) playPreferredQueue(queue, startIndex)
        }
    }

    fun playTrackFromLibrary(trackId: String) {
        preferredPlayback.launch {
            // A listening-history entry is an explicit track request, independent of song-page filters.
            val track = repository.trackById(trackId) ?: return@launch
            val source = if (LibraryPlaybackQueuePolicy.isLocalTrackSource(track.source))
                EchoLibrarySelectedSource.Local else EchoLibrarySelectedSource.Cloud
            val queue = libraryController.queueAroundTrack(trackId, source, LibrarySongQuery())
            val startIndex = LibraryPlaybackQueuePolicy.startIndex(queue.map { it.id }, trackId)
            if (queue.isNotEmpty() && startIndex >= 0) playPreferredQueue(queue, startIndex)
        }
    }

    suspend fun searchLocalLibrary(query: String): LocalLibrarySearchResults =
        libraryController.searchLocalLibrary(query)

    suspend fun updateTrackMetadata(update: EchoTrackMetadataUpdate): Boolean {
        val version = update.lyrics?.let { lyricsController.beginSelectionChange(update.trackId) }
        val written = try {
            writeTagsWithAuthorization(
                write = {
                    var result: app.echo.android.data.TrackMetadataUpdateResult? = null
                    val write: suspend () -> Unit = {
                        withReleasedPlaybackFile(update.trackId) {
                            if (version == null || lyricsController.isCurrentSelection(update.trackId, version)) {
                                result = libraryController.updateTrackMetadata(update)
                            }
                        }
                    }
                    if (version == null) write() else lyricsController.withCurrentSelection(update.trackId, version, write)
                    result ?: app.echo.android.data.TrackMetadataUpdateResult(false, EmbeddedTagWriteResult.Failed)
                },
                authorize = { authorizeTagWrite(update.trackId, it) },
            )
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _embeddedTagWriteMessage.value = EmbeddedTagWriteUserMessage.Failed
            EchoErrorLog.record(EchoErrorSource.Library, "Unable to save audio tag draft.", detail = "${update.trackId}: ${error.message}")
            return false
        }
        if (version != null && !lyricsController.isCurrentSelection(update.trackId, version)) return false
        val applied = handleEmbeddedTagWriteResult(
            trackId = update.trackId,
            result = written.fileWrite,
            lyricsText = update.lyrics,
            artworkUri = update.artworkUri,
            syncLyricsSelection = true,
            lyricsSelectionVersion = version,
            composerModified = update.composer != null,
            genreModified = update.genre != null,
            allowAuthorization = false,
        )
        return written.indexUpdated && applied
    }

    internal suspend fun batchTags(ids: List<String>, patch: app.echo.android.model.library.EchoBatchTagPatch, progress: (Int) -> Unit) =
        repository.batchTags(ids, patch, progress) { update ->
            withContext(Dispatchers.Main.immediate) {
                writeTagsWithAuthorization(
                    write = {
                        var result: app.echo.android.data.TrackMetadataUpdateResult? = null
                        withReleasedPlaybackFile(update.trackId) { result = libraryController.updateTrackMetadata(update) }
                        checkNotNull(result)
                    },
                    authorize = { result -> authorizeTagWrite(update.trackId, result) },
                )
            }
        }

    private suspend fun authorizeTagWrite(trackId: String, result: EmbeddedTagWriteResult): Boolean {
        val response = kotlinx.coroutines.CompletableDeferred<Boolean>()
        val requestId = tagWriteRequestIds.getAndIncrement()
        val request = when (result) {
            is EmbeddedTagWriteResult.NeedsMediaStoreConsent -> PendingEmbeddedTagWrite(
                requestId, trackId, result.uriString, intentSender = result.intentSender, completion = response,
            )
            EmbeddedTagWriteResult.NeedsStoragePermission -> PendingEmbeddedTagWrite(
                requestId, trackId, "", needsStoragePermission = true, completion = response,
            )
            else -> return false
        }
        if (!enqueueTagWriteAuthorization(request)) return false
        return try {
            response.await()
        } finally {
            tagWriteAuthorizations.remove(requestId)
            response.cancel()
        }
    }

    internal suspend fun batchQueue(tracks: List<EchoTrack>, mode: Int) {
        require(tracks.size <= 200)
        val token = preferredPlayback.generationToken()
        val resolved = preferredPlayback.resolve(tracks)
        if (!preferredPlayback.isCurrent(token)) return
        val ordered = if (mode == 1) resolved.asReversed() else resolved
        for (track in ordered) {
            when (mode) { 1 -> playbackController.playNext(track); 2 -> playbackController.addNextUp(track); else -> playbackController.enqueue(track) }
            kotlinx.coroutines.yield()
        }
    }

    internal suspend fun batchDeleteTracks(
        tracks: List<EchoTrack>, deleteOriginals: Boolean,
        authorize: suspend (IntentSender) -> Boolean,
    ) = repository.deleteSelectedTracks(tracks, deleteOriginals, authorize) { deletedUris ->
        withContext(Dispatchers.Main.immediate) {
            playbackController.removeDeletedFiles(deletedUris)
        }
    }

    internal fun playMoment(moment: app.echo.android.model.library.EchoSavedMoment) {
        preferredPlayback.launch {
            playbackController.playAt(preferredPlayback.resolve(listOf(moment.track)).single(), moment.bookmark.positionMs)
        }
    }

    fun onEmbeddedTagWriteAccessResult(requestId: Long, granted: Boolean) {
        val pending = tagWriteAuthorizations.take(requestId) ?: return
        pending.completion?.let { response ->
            response.complete(granted)
            return
        }
        if (!granted) {
            _embeddedTagWriteMessage.value = EmbeddedTagWriteUserMessage.IndexOnly
            return
        }
        viewModelScope.launch {
            val version = pending.lyricsSelectionVersion
            var result: EmbeddedTagWriteResult? = null
            val write: suspend () -> Unit = {
                withReleasedPlaybackFile(pending.trackId) {
                    if (version == null || lyricsController.isCurrentSelection(pending.trackId, version)) {
                        result = libraryController.writeEmbeddedTagsForTrack(
                            trackId = pending.trackId,
                            lyricsText = pending.lyricsText,
                            artworkUri = pending.artworkUri,
                            composerModified = pending.composerModified,
                            genreModified = pending.genreModified,
                        )
                    }
                }
            }
            if (version == null) write() else lyricsController.withCurrentSelection(pending.trackId, version, write)
            val written = result ?: return@launch
            if (version == null || lyricsController.isCurrentSelection(pending.trackId, version)) {
                handleEmbeddedTagWriteResult(
                    trackId = pending.trackId,
                    result = written,
                    lyricsText = pending.lyricsText,
                    artworkUri = pending.artworkUri,
                    syncLyricsSelection = pending.syncLyricsSelection,
                    lyricsSelectionVersion = version,
                    composerModified = pending.composerModified,
                    genreModified = pending.genreModified,
                    allowAuthorization = false,
                )
            }
        }
    }

    fun consumeEmbeddedTagWriteMessage() {
        _embeddedTagWriteMessage.value = null
    }

    private suspend fun withReleasedPlaybackFile(trackId: String, block: suspend () -> Unit) = embeddedTagWriteMutex.withLock {
        val writable = withContext(Dispatchers.IO) { repository.embeddedTagWritePreflight(trackId) == null }
        val released = writable && playbackController.releaseCurrentItemForFileWrite(trackId)
        try {
            block()
        } finally {
            if (released) playbackController.prepareAfterFileWrite(trackId)
        }
    }

    private suspend fun handleEmbeddedTagWriteResult(
        trackId: String,
        result: EmbeddedTagWriteResult,
        lyricsText: String? = null,
        artworkUri: String? = null,
        syncLyricsSelection: Boolean = false,
        lyricsSelectionVersion: Long? = null,
        composerModified: Boolean = false,
        genreModified: Boolean = false,
        allowAuthorization: Boolean = true,
    ): Boolean {
        if (lyricsSelectionVersion != null && !lyricsController.isCurrentSelection(trackId, lyricsSelectionVersion)) return false
        if (!allowAuthorization && (result is EmbeddedTagWriteResult.NeedsMediaStoreConsent ||
                result == EmbeddedTagWriteResult.NeedsStoragePermission)) {
            _embeddedTagWriteMessage.value = EmbeddedTagWriteUserMessage.Failed
            return false
        }
        return when (result) {
            is EmbeddedTagWriteResult.Written -> {
                if (syncLyricsSelection && lyricsText != null) {
                    try {
                        lyricsController.applyEmbeddedLyricsEdit(
                            trackId, lyricsText, lyricsSelectionVersion ?: lyricsController.beginSelectionChange(trackId),
                        )
                        if (lyricsSelectionVersion != null && !lyricsController.isCurrentSelection(trackId, lyricsSelectionVersion)) return false
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        _embeddedTagWriteMessage.value = EmbeddedTagWriteUserMessage.Failed
                        EchoErrorLog.record(
                            EchoErrorSource.Library,
                            "Audio tags were written, but the selected lyrics copy could not be updated.",
                            detail = "$trackId: ${error.message}",
                        )
                        return false
                    }
                } else if (lyricsController.currentTrackId == trackId) {
                    lyricsController.updateLyricsForTrack(trackId, force = true)
                }
                _embeddedTagWriteMessage.value = EmbeddedTagWriteUserMessage.Written
                true
            }
            EmbeddedTagWriteResult.UnsupportedFormat -> {
                _embeddedTagWriteMessage.value = EmbeddedTagWriteUserMessage.UnsupportedFormat
                EchoErrorLog.record(
                    EchoErrorSource.Library,
                    "Embedded tag write is unsupported for this file format.",
                    detail = trackId,
                )
                false
            }
            EmbeddedTagWriteResult.Failed -> {
                _embeddedTagWriteMessage.value = EmbeddedTagWriteUserMessage.Failed
                EchoErrorLog.record(
                    EchoErrorSource.Library,
                    "Embedded tag write failed.",
                    detail = trackId,
                )
                false
            }
            EmbeddedTagWriteResult.NotLocal -> {
                _embeddedTagWriteMessage.value = EmbeddedTagWriteUserMessage.IndexOnly
                true
            }
            is EmbeddedTagWriteResult.NeedsMediaStoreConsent ->
                enqueueTagWriteAuthorization(PendingEmbeddedTagWrite(
                    requestId = tagWriteRequestIds.getAndIncrement(),
                    trackId = trackId,
                    contentUri = result.uriString,
                    intentSender = result.intentSender,
                    lyricsText = lyricsText,
                    artworkUri = artworkUri,
                    syncLyricsSelection = syncLyricsSelection,
                    lyricsSelectionVersion = lyricsSelectionVersion ?: lyricsText?.let { lyricsController.selectionVersion(trackId) },
                    composerModified = composerModified,
                    genreModified = genreModified,
                ))
            EmbeddedTagWriteResult.NeedsStoragePermission ->
                enqueueTagWriteAuthorization(PendingEmbeddedTagWrite(
                    requestId = tagWriteRequestIds.getAndIncrement(),
                    trackId = trackId,
                    contentUri = "",
                    needsStoragePermission = true,
                    lyricsText = lyricsText,
                    artworkUri = artworkUri,
                    syncLyricsSelection = syncLyricsSelection,
                    lyricsSelectionVersion = lyricsSelectionVersion ?: lyricsText?.let { lyricsController.selectionVersion(trackId) },
                    composerModified = composerModified,
                    genreModified = genreModified,
                ))
        }
    }

    private fun enqueueTagWriteAuthorization(request: PendingEmbeddedTagWrite): Boolean {
        val queued = tagWriteAuthorizations.enqueue(request)
        if (!queued) {
            _embeddedTagWriteMessage.value = EmbeddedTagWriteUserMessage.Failed
            EchoErrorLog.record(EchoErrorSource.Library, "Too many pending audio tag write authorizations.")
        }
        return queued
    }

    internal suspend fun savePlayerArtwork(trackId: String, artworkUri: Uri): app.echo.android.data.TrackMetadataUpdateResult {
        val result = writeTagsWithAuthorization(
            write = {
                var written: app.echo.android.data.TrackMetadataUpdateResult? = null
                withReleasedPlaybackFile(trackId) { written = libraryController.updateTrackArtwork(trackId, artworkUri) }
                checkNotNull(written)
            },
            authorize = { authorizeTagWrite(trackId, it) },
        )
        handleEmbeddedTagWriteResult(trackId, result.fileWrite, artworkUri = artworkUri.toString(), allowAuthorization = false)
        if (result.indexUpdated) EchoPlaybackProcessRuntime.updateTrackArtwork(trackId, artworkUri.toString())
        return result
    }

    internal suspend fun createPlayerPlaylist(name: String, trackId: String): Boolean =
        repository.createOrderedLocalPlaylist(name, listOf(trackId)) != null

    internal suspend fun addPlayerSongToPlaylist(playlistId: String, trackId: String): Boolean {
        if (playlistId == EchoPlaylist.LikedSongsId && trackId.startsWith("subsonic:"))
            return remoteLibraryTools.setFavorite(trackId, true)
        if (libraryController.addTrackToLocalPlaylist(playlistId, trackId)) return true
        // Repeating an add is successful when the song is already present.
        return withContext(Dispatchers.IO) {
            if (playlistId == EchoPlaylist.LikedSongsId) database.playlistDao().isFavorite(trackId)
            else database.playlistDao().membership(playlistId, trackId) != null
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

    private var voiceSearchJob: Job? = null
    fun playVoiceSearch(request: app.echo.android.model.playback.EchoMediaSearchRequest) {
        voiceSearchJob?.cancel()
        voiceSearchJob = preferredPlayback.launch {
            val (queue, start) = app.echo.android.library.resolveVoiceLaunchTracks(database, request)
            if (queue.isEmpty()) android.widget.Toast.makeText(getApplication(),
                app.echo.android.playback.R.string.voice_search_no_result, android.widget.Toast.LENGTH_SHORT).show()
            else playPreferredQueue(queue, start)
        }
    }

    suspend fun libraryShortcutTarget(mediaId: String): Pair<AlbumSummary?, EchoPlaylist?>? = withContext(Dispatchers.IO) {
        app.echo.android.playback.EchoPlaybackLibraryIds.albumKey(mediaId)?.let { key ->
            return@withContext database.trackDao().getAlbumSummary(key)?.let { it to null }
        }
        app.echo.android.playback.EchoPlaybackLibraryIds.playlistId(mediaId)?.let { id ->
            val item = EchoPlaybackProcessRuntime.catalog().item(mediaId) ?: return@withContext null
            return@withContext null to EchoPlaylist(id = id, name = item.title, artworkUri = item.artworkUri)
        }
        null
    }

    fun playAlbum(albumKey: String) {
        preferredPlayback.launch {
            val queue = libraryController.albumTracksForPlayback(albumKey)
            if (queue.isNotEmpty()) {
                playPreferredAlbumQueue(queue, 0)
                app.echo.android.library.EchoLibraryShortcuts.rememberCollection(getApplication(),
                    app.echo.android.playback.EchoPlaybackLibraryIds.album(albumKey))
            }
        }
    }

    fun shuffleAlbum(albumKey: String) {
        preferredPlayback.launch {
            val queue = libraryController.albumTracksForPlayback(albumKey)
            if (queue.isNotEmpty()) {
                playPreferredAlbumQueue(
                    queue = queue,
                    startIndex = queue.indices.random(),
                    intent = PlaybackQueueReplaceIntent.Shuffle,
                )
            }
        }
    }

    fun playArtist(artistKey: String) {
        preferredPlayback.launch {
            val queue = libraryController.artistTracksForPlayback(artistKey)
            if (queue.isNotEmpty()) playPreferredQueue(queue, 0, source = queue.firstOrNull()?.artist)
        }
    }

    fun playGenre(genreKey: String) {
        preferredPlayback.launch {
            val queue = libraryController.genreTracksForPlayback(genreKey)
            if (queue.isNotEmpty()) playPreferredQueue(queue, 0)
        }
    }

    fun playComposer(composerKey: String) {
        preferredPlayback.launch {
            val queue = libraryController.composerTracksForPlayback(composerKey)
            if (queue.isNotEmpty()) playPreferredQueue(queue, 0)
        }
    }

    fun composerTrackPaging(composerKey: String): Flow<PagingData<EchoTrack>> =
        libraryController.composerTrackPaging(composerKey)

    fun playFolder(folderKey: String) {
        preferredPlayback.launch {
            val queue = libraryController.folderTracksForPlayback(folderKey)
            if (queue.isNotEmpty()) playPreferredQueue(queue, 0, source = folderKey.substringAfterLast('/'))
        }
    }

    fun shuffleFolder(folderKey: String) {
        preferredPlayback.launch {
            val queue = libraryController.folderTracksForPlayback(folderKey)
            if (queue.isNotEmpty()) {
                playPreferredQueue(
                    queue = queue,
                    startIndex = queue.indices.random(),
                    intent = PlaybackQueueReplaceIntent.Shuffle,
                )
            }
        }
    }

    fun playPlaylist(playlistId: String) {
        preferredPlayback.launch {
            val queue = libraryController.playlistTracksForPlayback(playlistId)
            if (queue.isNotEmpty()) {
                playPreferredQueue(queue, 0, source = libraryController.localPlaylists.value.firstOrNull { it.id == playlistId }?.name)
                app.echo.android.library.EchoLibraryShortcuts.rememberCollection(getApplication(),
                    app.echo.android.playback.EchoPlaybackLibraryIds.playlist(playlistId))
            }
        }
    }

    fun shufflePlaylist(playlistId: String) {
        preferredPlayback.launch {
            val queue = libraryController.playlistTracksForPlayback(playlistId)
            if (queue.isNotEmpty()) {
                playPreferredQueue(
                    queue = queue,
                    startIndex = queue.indices.random(),
                    intent = PlaybackQueueReplaceIntent.Shuffle,
                    source = libraryController.localPlaylists.value.firstOrNull { it.id == playlistId }?.name,
                )
            }
        }
    }

    fun toggleFavorite(trackId: String? = playbackController.currentTrackId) {
        val id = trackId?.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch {
            if (id.startsWith("subsonic:")) remoteLibraryTools.toggleFavorite(id)
            else libraryController.toggleFavorite(id)
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
            if (playlistId == EchoPlaylist.LikedSongsId && trackId.startsWith("subsonic:"))
                remoteLibraryTools.setFavorite(trackId, true)
            else libraryController.addTrackToLocalPlaylist(playlistId, trackId)
        }
    }

    fun removeTrackFromLocalPlaylist(playlistId: String, trackId: String) {
        viewModelScope.launch {
            if (playlistId == EchoPlaylist.LikedSongsId && trackId.startsWith("subsonic:"))
                remoteLibraryTools.setFavorite(trackId, false)
            else libraryController.removeTrackFromLocalPlaylist(playlistId, trackId)
        }
    }

    fun importM3uPlaylist(uri: Uri) {
        playlistTools.prepare(uri)
    }

    internal suspend fun importM3uPlaylistWithResult(uri: Uri): EchoPlaylist? = playlistTools.importImmediately(uri)

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            try { libraryMigration.export(uri); _backupNotice.value = app.echo.android.model.backup.EchoBackupNotice.Exported }
            catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (error: Exception) { _backupNotice.value = app.echo.android.model.backup.EchoBackupNotice.Failed(error.message ?: "Backup failed") }
        }
    }
    fun importBackup(uri: Uri) {
        tryTakePersistableReadPermission(getApplication<Application>(), uri)
        viewModelScope.launch {
            try { libraryMigration.prepare(uri) }
            catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (error: Exception) { _backupNotice.value = app.echo.android.model.backup.EchoBackupNotice.Failed(error.message ?: "Backup failed") }
        }
    }
    fun applyMigrationBackup() {
        viewModelScope.launch {
            try { _backupNotice.value = app.echo.android.model.backup.EchoBackupNotice.Restored(libraryMigration.apply()) }
            catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (error: Exception) { _backupNotice.value = app.echo.android.model.backup.EchoBackupNotice.Failed(error.message ?: "Restore failed") }
        }
    }

    fun exportM3uPlaylist(playlistId: String, uri: Uri) = playlistTools.export(playlistId, uri)

    fun reorderLocalPlaylistTracks(playlistId: String, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            libraryController.reorderLocalPlaylistTracks(playlistId, fromIndex, toIndex)
        }
    }

    fun reorderAlbumTracks(albumKey: String, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            libraryController.reorderAlbumTracks(albumKey, fromIndex, toIndex)
        }
    }

    fun shuffleArtist(artistKey: String) {
        preferredPlayback.launch {
            val queue = libraryController.artistTracksForPlayback(artistKey)
            if (queue.isNotEmpty()) {
                playPreferredQueue(
                    queue = queue,
                    startIndex = queue.indices.random(),
                    intent = PlaybackQueueReplaceIntent.Shuffle,
                )
            }
        }
    }

    fun pause() {
        preferredPlayback.invalidate()
        playbackController.pause()
    }

    fun usbExclusiveOutputActive(): Boolean = playbackController.isUsbExclusiveEnabled()

    fun playPause() {
        preferredPlayback.invalidate()
        playbackController.playPause()
    }

    fun playLastSavedSession() {
        preferredPlayback.invalidate()
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
        preferredPlayback.invalidate()
        playbackController.skipNext()
    }

    fun skipPrevious() {
        preferredPlayback.invalidate()
        playbackController.skipPrevious()
    }

    fun playQueueItem(index: Int) {
        preferredPlayback.invalidate()
        playbackController.playQueueItem(index)
    }

    fun removeQueueItem(index: Int) {
        playbackController.removeQueueItem(index)
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        playbackController.moveQueueItem(fromIndex, toIndex)
    }

    fun clearQueue() {
        preferredPlayback.invalidate()
        playbackController.clearQueue()
    }

    fun setRepeatMode(mode: app.echo.android.model.playback.EchoRepeatMode) {
        playbackController.setRepeatMode(mode)
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
        preferredPlayback.append({ listOf(track) }) { it.forEach(playbackController::playNext) }
    }

    fun addNextUp(track: EchoTrack) {
        preferredPlayback.append({ listOf(track) }) { tracks -> tracks.forEach { playbackController.addNextUp(it) } }
    }

    fun clearNextUp() = playbackController.clearNextUp()

    fun addNextUpByTrackId(trackId: String) {
        preferredPlayback.append({ listOfNotNull(libraryController.trackById(trackId)) }) { tracks -> tracks.forEach { playbackController.addNextUp(it) } }
    }

    fun enqueue(track: EchoTrack) {
        preferredPlayback.append({ listOf(track) }) { it.forEach(playbackController::enqueue) }
    }

    fun playNextByTrackId(trackId: String) {
        preferredPlayback.append({ listOfNotNull(libraryController.trackById(trackId)) }) { it.forEach(playbackController::playNext) }
    }

    fun enqueueByTrackId(trackId: String) {
        preferredPlayback.append({ listOfNotNull(libraryController.trackById(trackId)) }) { it.forEach(playbackController::enqueue) }
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

    fun cyclePlayMode() {
        playbackController.cyclePlayMode()
    }

    internal suspend fun previewEncodedLyrics(uri: String, charset: String?): String =
        lyricsController.previewEncodedLyrics(Uri.parse(uri), charset).lines.take(16).joinToString("\n") { it.text }

    internal suspend fun importEncodedLyrics(trackId: String, uri: String, charset: String?): Boolean =
        lyricsController.importEncodedLyrics(Uri.parse(uri), trackId, charset)

    fun importLyrics(uri: Uri) {
        val trackId = playbackController.currentTrackId
        lyricsController.importLyrics(uri, trackId) { lyrics, version ->
            trackId?.let { writeImportedLyricsToFile(it, lyrics, version) }
        }
    }

    fun importLyricsForTrack(trackId: String, uri: Uri) {
        lyricsController.importLyrics(uri, trackId) { lyrics, version ->
            writeImportedLyricsToFile(trackId, lyrics, version)
        }
    }

    private fun writeImportedLyricsToFile(trackId: String, lyrics: EchoLyrics, version: Long) {
        viewModelScope.launch {
            val track = libraryController.trackById(trackId) ?: return@launch
            if (!app.echo.android.lyrics.LyricsClipPolicy.canMirrorToWholeFile(trackId, track.clipStartMs, track.clipEndMs)) return@launch
            // The full imported selection is already durable. Only mirror it to audio tags
            // when ordinary LRC can retain its timing, auxiliary text and vocal fields.
            val text = withContext(Dispatchers.Default) {
                EchoLrcFormatter.formatForEmbeddedWriteOrNull(lyrics)
            } ?: return@launch
            var result: EmbeddedTagWriteResult? = null
            lyricsController.withCurrentSelection(trackId, version) {
                withReleasedPlaybackFile(trackId) {
                    if (lyricsController.isCurrentSelection(trackId, version)) {
                        result = libraryController.writeEmbeddedLyrics(trackId, text).fileWrite
                    }
                }
            }
            val written = result ?: return@launch
            if (lyricsController.isCurrentSelection(trackId, version)) {
                handleEmbeddedTagWriteResult(
                    trackId = trackId,
                    result = written,
                    lyricsText = text,
                    lyricsSelectionVersion = version,
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

    fun setPlayerAppearance(style: String, textScale: Float, artworkScale: Float) {
        updateSettings { setPlayerAppearance(style, textScale, artworkScale) }
    }

    fun setOnlineLyricsEnabled(enabled: Boolean) {
        lyricsController.setOnlineLyricsEnabled(enabled, playbackController.currentTrackId)
        updateSettings {
            setOnlineLyricsEnabled(enabled)
        }
    }

    fun setLockScreenLyricsEnabled(enabled: Boolean) {
        updateSettings {
            setLockScreenLyricsEnabled(enabled)
        }
    }

    fun setLyricsOptions(value: app.echo.android.model.settings.EchoLyricsOptions) {
        updateSettings { setLyricsOptions(value) }
    }

    fun setFloatingLyrics(value: app.echo.android.model.settings.EchoFloatingLyricsSettings) {
        updateSettings {
            setFloatingLyrics(value)
        }
    }

    fun setSetlistFmApiKey(apiKey: String?) {
        updateSettings {
            setSetlistFmApiKey(apiKey)
        }
    }

    suspend fun matchSetlist(setlist: app.echo.android.model.library.ArtistSetlist) =
        libraryController.matchSetlist(setlist)

    fun playSetlistTracks(trackIds: List<String>) {
        preferredPlayback.launch {
            val tracks = libraryController.tracksByIds(trackIds)
            if (tracks.isNotEmpty()) playPreferredQueue(tracks, 0)
        }
    }

    suspend fun loadEmbeddedLyrics(trackId: String): String? =
        libraryController.readEmbeddedLyrics(trackId)

    fun setUsbExclusiveEnabled(enabled: Boolean) {
        playbackController.setUsbExclusiveEnabled(enabled)
        updateSettings {
            setUsbExclusiveEnabled(enabled)
        }
    }

    fun setShuffleOptions(options: app.echo.android.model.playback.EchoShuffleOptions) {
        updateSettings { setShuffleOptions(options) }
    }

    fun setTrackTransitions(options: app.echo.android.model.playback.EchoTrackTransitionOptions) {
        updateSettings { setTrackTransitions(options) }
    }

    fun setAudioFocusEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setAudioFocusEnabled(enabled) }
    }

    fun resetAudioOutputSettings() {
        viewModelScope.launch { settingsStore.resetAudioOutputSettings() }
    }

    fun setAudioInterruptionPolicy(value: String) {
        viewModelScope.launch { settingsStore.setAudioInterruptionPolicy(value) }
    }

    fun setStopOnTaskRemoved(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setStopOnTaskRemoved(enabled) }
    }

    fun setSleepTimerFinishTrack(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setSleepTimerFinishTrack(enabled) }
    }

    fun setPauseOnAudioDisconnect(enabled: Boolean) {
        EchoPlaybackProcessRuntime.setAudioRoutePlaybackPolicy(
            enabled,
            EchoPlaybackProcessRuntime.resumeOnAudioReconnect,
        )
        updateSettings { setPauseOnAudioDisconnect(enabled) }
    }

    fun setResumeOnAudioReconnect(enabled: Boolean) {
        EchoPlaybackProcessRuntime.setAudioRoutePlaybackPolicy(
            EchoPlaybackProcessRuntime.pauseOnAudioDisconnect,
            enabled,
        )
        updateSettings { setResumeOnAudioReconnect(enabled) }
    }

    fun setUsbBitPerfectEnabled(enabled: Boolean) {
        updateSettings { setUsbBitPerfectEnabled(enabled) }
    }

    fun setUsbOutputOptions(options: app.echo.android.model.playback.EchoUsbOutputOptions) {
        updateSettings { setUsbOutputOptions(options) }
    }

    fun setUsbExclusiveAutoRequestOnStartup(enabled: Boolean) {
        updateSettings {
            setUsbExclusiveAutoRequestOnStartup(enabled)
        }
    }

    val volumeSettings = getApplication<EchoApplication>().appVolumeController.state

    fun setVolumeSettings(value: app.echo.android.model.playback.EchoVolumeSettings) {
        getApplication<EchoApplication>().appVolumeController.set(value)
    }

    fun setDspSettings(value: app.echo.android.model.playback.EchoDspSettings) {
        EchoPlaybackProcessRuntime.setDspSettings(value)
        updateSettings { setDspSettings(value) }
    }

    fun setParametricFilters(filters: List<app.echo.android.model.playback.OpraEqBand>) {
        EchoPlaybackProcessRuntime.equalizerController().setParametricFilters(filters)
        val selected = playbackController.equalizerState.value
        updateSettings {
            setEqualizerParametricConfig(selected.gainsDb, selected.preampDb, selected.filters, selected.sourceLabel, enabled = selected.enabled)
        }
    }

    fun previewParametricFilters(filters: List<app.echo.android.model.playback.OpraEqBand>) {
        if (EchoPlaybackProcessRuntime.usbBitPerfectEnabled) return
        EchoPlaybackProcessRuntime.equalizerController().setParametricFilters(filters)
    }

    fun previewMseb(state: app.echo.android.model.playback.EchoMsebState) {
        if (EchoPlaybackProcessRuntime.usbBitPerfectEnabled) return
        EchoPlaybackProcessRuntime.equalizerController().setMseb(state)
    }

    fun setEqualizerPreamp(gainDb: Float) {
        playbackController.setEqualizerPreamp(gainDb)
        updateSettings { setEqualizerPreamp(gainDb) }
    }

    fun setMseb(state: app.echo.android.model.playback.EchoMsebState) {
        val normalized = state.normalized
        EchoPlaybackProcessRuntime.equalizerController().setMseb(normalized)
        updateSettings { setMseb(normalized) }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        playbackController.setEqualizerEnabled(enabled)
        updateSettings {
            setEqualizerEnabled(enabled)
        }
    }

    fun setEqualizerPreset(presetId: String) {
        playbackController.setEqualizerPreset(presetId)
        val selected = playbackController.equalizerState.value
        updateSettings {
            setEqualizerPreset(selected.presetId, selected.preampDb)
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
        val selected = playbackController.equalizerState.value
        updateSettings {
            if (selected.parametric) {
                setEqualizerParametricConfig(selected.gainsDb, selected.preampDb, selected.filters,
                    selected.sourceLabel, enabled = selected.enabled)
                setEqualizerActiveUserPresetId(null)
            } else resetEqualizer()
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

    fun browseOpraBrand(brandId: String?) = opraSearch.browse(brandId)

    fun updateOpraQuery(query: String) = opraSearch.setQuery(query)

    fun searchOpraHeadphoneCorrections(refresh: Boolean = false) {
        opraSearch.search(refresh)
        val query = opraState.value.query.trim()
        if (query.isNotBlank()) updateSettings { setOpraLastQuery(query) }
    }

    fun selectOpraPreset(eqId: String) = opraSearch.select(eqId)

    fun applySelectedOpraPreset() {
        val preset = opraState.value.selectedPreset ?: return
        if (opraState.value.loading) return
        playbackController.applyOpraPreset(preset)
        val equalizer = playbackController.equalizerState.value
        val query = opraState.value.query.trim()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                settingsStore.setEqualizerParametricConfig(
                    equalizer.gainsDb,
                    equalizer.preampDb,
                    equalizer.filters,
                    equalizer.sourceLabel,
                )
                if (query.isNotBlank()) settingsStore.setOpraLastQuery(query)
                val starredId = settingsStore.appSettings.first()
                    .equalizerUserPresets
                    .firstOrNull { it.opraEqId == preset.eqId }
                    ?.id
                settingsStore.setEqualizerActiveUserPresetId(starredId)
            }
        }
        opraSearch.message(
            if (EchoPlaybackProcessRuntime.usbBitPerfectEnabled) {
                getApplication<Application>().getString(R.string.opra_saved_bypassed)
            } else {
                getApplication<Application>().getString(R.string.opra_saved_eq, preset.displayName)
            },
        )
    }

    fun saveCurrentEqualizerPreset(name: String) {
        val captured = EchoEqualizerUserPresets.capture(
            id = UUID.randomUUID().toString(),
            name = name,
            state = playbackController.equalizerState.value,
            updatedAtEpochMs = System.currentTimeMillis(),
        ) ?: return
        updateSettings {
            if (upsertEqualizerUserPreset(captured)) {
                setEqualizerActiveUserPresetId(captured.id)
            }
        }
    }

    fun updateCurrentEqualizerUserPreset() {
        viewModelScope.launch {
            val settings = withContext(Dispatchers.IO) { settingsStore.appSettings.first() }
            val activeId = settings.equalizerActiveUserPresetId ?: return@launch
            val existing = settings.equalizerUserPresets.firstOrNull { it.id == activeId } ?: return@launch
            val captured = EchoEqualizerUserPresets.capture(
                id = existing.id,
                name = existing.name,
                state = playbackController.equalizerState.value,
                updatedAtEpochMs = System.currentTimeMillis(),
                opraEqId = existing.opraEqId,
            ) ?: return@launch
            withContext(Dispatchers.IO) {
                if (settingsStore.upsertEqualizerUserPreset(captured)) {
                    settingsStore.setEqualizerActiveUserPresetId(captured.id)
                }
            }
        }
    }

    fun currentOutputProfileKey(): String {
        val context = getApplication<Application>()
        val route = EchoPlaybackProcessRuntime.outputRouteMonitor(context).route.value
        val usbName = EchoPlaybackProcessRuntime.usbAudioMonitor(context).status.value.deviceName
        return app.echo.android.model.playback.EchoOutputDspPolicy.deviceKey(
            app.echo.android.model.playback.EchoOutputDeviceKind.fromId(route.kind),
            if (route.kind == app.echo.android.model.playback.EchoOutputDeviceKind.Usb.id) usbName ?: route.deviceName else route.deviceName)
    }
    fun currentOutputProfileLabel(): String {
        val context = getApplication<Application>()
        val route = EchoPlaybackProcessRuntime.outputRouteMonitor(context).route.value
        return if (route.kind == app.echo.android.model.playback.EchoOutputDeviceKind.Usb.id)
            EchoPlaybackProcessRuntime.usbAudioMonitor(context).status.value.deviceName ?: route.deviceName ?: context.getString(R.string.output_profile_current_output)
        else route.deviceName ?: context.getString(R.string.output_profile_current_output)
    }
    fun saveOutputAudioProfile() { val key = currentOutputProfileKey(); updateSettings { saveOutputAudioProfile(key) } }
    fun removeOutputAudioProfile() { val key = currentOutputProfileKey(); updateSettings { removeOutputAudioProfile(key) } }


    fun bindActiveEqualizerPresetToOutput() {
        viewModelScope.launch {
            val presetId = withContext(Dispatchers.IO) {
                settingsStore.appSettings.first().equalizerActiveUserPresetId
            } ?: return@launch
            val key = currentOutputProfileKey()
            updateSettings { bindEqualizerPresetToDevice(key, presetId) }
        }
    }

    fun unbindEqualizerPresetFromOutput() {
        val key = currentOutputProfileKey()
        updateSettings { unbindEqualizerPresetFromDevice(key) }
    }

    fun applyEqualizerUserPreset(id: String) {
        viewModelScope.launch {
            val preset = withContext(Dispatchers.IO) {
                settingsStore.appSettings.first().equalizerUserPresets.firstOrNull { it.id == id }
            } ?: return@launch
            if (!playbackController.applyEqualizerUserPreset(preset)) return@launch
            withContext(Dispatchers.IO) { settingsStore.applyEqualizerUserPreset(preset) }
        }
    }

    fun renameEqualizerUserPreset(id: String, name: String) {
        updateSettings { renameEqualizerUserPreset(id, name) }
    }

    fun deleteEqualizerUserPreset(id: String) {
        updateSettings { deleteEqualizerUserPreset(id) }
    }

    internal suspend fun importEqualizerBundle(presets: List<app.echo.android.model.playback.EchoEqualizerUserPreset>, conflict: app.echo.android.model.playback.EchoEqImportConflict) =
        withContext(Dispatchers.IO) { settingsStore.importEqualizerBundle(presets, conflict) }

    fun importEqualizerShareCode(code: String) {
        val parsed = EchoEqualizerShareCodec.decode(code, UUID.randomUUID().toString()) ?: return
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { settingsStore.upsertEqualizerUserPreset(parsed) }
            if (!saved) return@launch
            if (!playbackController.applyEqualizerUserPreset(parsed)) return@launch
            withContext(Dispatchers.IO) { settingsStore.applyEqualizerUserPreset(parsed) }
        }
    }

    fun toggleStarredOpraPreset() {
        val selected = opraState.value.selectedPreset ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val settings = settingsStore.appSettings.first()
                val existing = settings.equalizerUserPresets.firstOrNull { it.opraEqId == selected.eqId }
                if (existing != null) {
                    settingsStore.deleteEqualizerUserPreset(existing.id)
                    return@withContext
                }
                val captured = EchoEqualizerUserPresets.captureOpra(
                    id = UUID.randomUUID().toString(),
                    name = selected.displayName,
                    preset = selected,
                    updatedAtEpochMs = System.currentTimeMillis(),
                ) ?: return@withContext
                settingsStore.upsertEqualizerUserPreset(captured)
            }
        }
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

    fun setStartupBackground(uri: Uri?) {
        updateSettings { setStartupBackgroundUri(uri?.toString()) }
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

    fun setLibraryLayout(value: app.echo.android.model.settings.EchoLibraryLayout) {
        updateSettings { setLibraryLayout(value) }
    }

    fun setHomeLayout(value: app.echo.android.model.settings.EchoHomeLayout) {
        updateSettings { setHomeLayout(value) }
    }

    fun setUiDensityScale(value: Float) {
        updateSettings {
            setUiDensityScale(value)
        }
    }

    fun setLyricsPageStyle(value: String) {
        updateSettings { setLyricsPageStyle(value) }
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

    fun setLyricsBackdrop(value: String) {
        updateSettings {
            setLyricsBackdrop(value)
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

    fun setLyricsEstimatedWordHighlightEnabled(enabled: Boolean) {
        updateSettings {
            setLyricsEstimatedWordHighlightEnabled(enabled)
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

    fun setLyricsWordHighlightStyle(value: String) {
        updateSettings {
            setLyricsWordHighlightStyle(value)
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

    fun setCustomColors(colors: EchoCustomColors) {
        updateSettings {
            setCustomColors(colors)
        }
    }

    fun saveCustomColorTheme(name: String, onResult: (EchoSavedColorThemeResult) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                settingsStore.saveCustomColorTheme(name)
            }
            onResult(result)
        }
    }

    fun applySavedColorTheme(id: String) {
        updateSettings {
            applySavedColorTheme(id)
        }
    }

    fun deleteSavedColorTheme(id: String) {
        updateSettings {
            deleteSavedColorTheme(id)
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
        libraryController.refreshSubsonic(endpoint) { resolved ->
            saveSubsonicCredentials(resolved.normalizedBaseUrl, resolved.username, resolved.password)
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

    fun saveSmbCredentials(
        serverUrl: String,
        username: String,
        password: String,
    ) {
        updateSettings {
            setSmbCredentials(serverUrl, username, password)
        }
    }

    fun clearSmbCredentials() {
        updateSettings {
            clearSmbCredentials()
        }
    }

    fun syncSmbLibrary(
        serverUrl: String,
        username: String,
        password: String,
    ) {
        val endpoint = app.echo.android.data.smbEndpointFrom(serverUrl, username, password) ?: return
        // 播放要用同一份凭据，先登记再扫描，扫完就能直接播。
        app.echo.android.smb.EchoSmbConnections.replaceCredentials(listOf(endpoint))
        libraryController.refreshSmb(endpoint)
        saveSmbCredentials(serverUrl, username, password)
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
        // The Application owns loading; only dismiss this UI session's search.
        lyricsController.cancelSearch()
        playbackController.clear()
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
    val syncLyricsSelection: Boolean = false,
    val lyricsSelectionVersion: Long? = null,
    val composerModified: Boolean = false,
    val genreModified: Boolean = false,
    val completion: kotlinx.coroutines.CompletableDeferred<Boolean>? = null,
)

enum class EmbeddedTagWriteUserMessage {
    Written,
    IndexOnly,
    UnsupportedFormat,
    Failed,
}
