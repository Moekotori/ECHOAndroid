package app.echo.android

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.paging.PagingData
import app.echo.android.data.EchoLibraryDatabase
import app.echo.android.data.EchoLibraryRepository
import app.echo.android.data.EchoAppSettings
import app.echo.android.data.EchoSettingsStore
import app.echo.android.data.DocumentTreeTrackScanner
import app.echo.android.data.MediaStoreTrackScanner
import app.echo.android.data.NeteaseSourceId
import app.echo.android.data.OpraHeadphoneCorrectionRepository
import app.echo.android.data.parseNeteaseSongId
import app.echo.android.data.SubsonicEndpoint
import app.echo.android.data.WebDavEndpoint
import app.echo.android.lyrics.ImportedLyricsStore
import app.echo.android.lyrics.LocalLyricsResolver
import app.echo.android.lyrics.OnlineLyricsResolver
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.ArtistSummary
import app.echo.android.model.library.EchoPlaylist
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.EchoTrackMetadataUpdate
import app.echo.android.model.library.FolderSummary
import app.echo.android.model.library.LibraryScanProgress
import app.echo.android.model.library.LibraryStats
import app.echo.android.model.library.LibraryTrackSortMode
import app.echo.android.model.library.NeteaseAccountState
import app.echo.android.model.library.NeteaseAudioQuality
import app.echo.android.model.library.NeteaseImportState
import app.echo.android.model.lyrics.EchoLyricsLoadState
import app.echo.android.model.connect.EchoMobileDiscordPresenceSnapshot
import app.echo.android.model.connect.EchoRemoteLyrics
import app.echo.android.model.connect.EchoRemotePlaybackState
import app.echo.android.model.connect.EchoRemoteTrack
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoPlaybackState
import app.echo.android.model.playback.EchoEqualizerState
import app.echo.android.model.playback.PlaybackControlsState
import app.echo.android.model.playback.PlaybackDiagnosticsState
import app.echo.android.model.playback.OpraHeadphoneCorrectionState
import app.echo.android.model.playback.PlaybackHeatmapDay
import app.echo.android.model.playback.PlaybackMetadataState
import app.echo.android.model.playback.PlaybackPositionState
import app.echo.android.model.playback.PlaybackQueueState
import app.echo.android.model.settings.EchoEffectivePerformanceMode
import app.echo.android.playback.EchoRemotePlaybackAuthRegistry
import app.echo.android.playback.EchoPlaybackCachePolicy
import app.echo.android.playback.EchoWebDavPlaybackCredential
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
class EchoAndroidViewModel(application: Application) : AndroidViewModel(application) {
    private val database = EchoLibraryDatabase.create(application)
    private val repository = EchoLibraryRepository(
        database = database,
        scanner = MediaStoreTrackScanner(application.contentResolver),
        documentTreeScanner = DocumentTreeTrackScanner(application.contentResolver),
    )
    private val settingsStore = EchoSettingsStore(application)
    private val opraRepository = OpraHeadphoneCorrectionRepository(application)

    private val libraryController = LibraryController(
        repository = repository,
        scope = viewModelScope,
    )
    private val lyricsController = LyricsController(
        repository = repository,
        lyricsResolver = LocalLyricsResolver(application.contentResolver),
        onlineLyricsResolver = OnlineLyricsResolver(),
        importedLyricsStore = ImportedLyricsStore(application),
        scope = viewModelScope,
    )
    private val playbackController = PlaybackController(
        application = application,
        scope = viewModelScope,
        onTrackChanged = lyricsController::updateLyricsForTrack,
        onTrackActivated = ::recordRecentPlayback,
    )
    private val neteaseController = NeteaseController(
        repository = repository,
        settingsStore = settingsStore,
        scope = viewModelScope,
    )
    private val lastFmClient = LastFmClient()
    private val lastFmController = LastFmScrobbleController(
        scope = viewModelScope,
        client = lastFmClient,
    )
    private var pendingLastFmAuthToken: String? = null
    private var usbStartupPolicyApplied = false
    private var effectivePerformanceMode: EchoEffectivePerformanceMode = EchoEffectivePerformanceMode.Balanced
    private var playbackProgressUiVisibility: PlaybackProgressUiVisibility = PlaybackProgressUiVisibility.MiniPlayer

    val libraryQuery: StateFlow<String> = libraryController.libraryQuery
    val libraryTrackSortMode: StateFlow<LibraryTrackSortMode> = libraryController.trackSortMode
    val tracks: Flow<PagingData<EchoTrack>> = libraryController.tracks
    val albums: Flow<PagingData<AlbumSummary>> = libraryController.albums
    val remoteAlbums: Flow<PagingData<AlbumSummary>> = libraryController.remoteAlbums
    val artists: Flow<PagingData<ArtistSummary>> = libraryController.artists
    val folders: Flow<PagingData<FolderSummary>> = libraryController.folders
    val neteaseImportedPlaylists: Flow<List<EchoPlaylist>> = libraryController.neteasePlaylists
    val localPlaylists: Flow<List<EchoPlaylist>> = libraryController.localPlaylists
    val libraryStats: Flow<LibraryStats> = libraryController.libraryStats
    val recommendedTracks: Flow<List<EchoTrack>> = libraryController.recommendedTracks
    val recentlyAddedAlbums: Flow<List<AlbumSummary>> = libraryController.recentlyAddedAlbums
    val scanState: StateFlow<LibraryScanProgress> = libraryController.scanState
    val remoteScanState: StateFlow<LibraryScanProgress> = libraryController.remoteScanState

    val playbackStatus: StateFlow<EchoPlaybackStatus> = playbackController.playbackStatus
    val playbackMetadata: StateFlow<PlaybackMetadataState> = playbackController.playbackMetadata
    val playbackPosition: StateFlow<PlaybackPositionState> = playbackController.playbackPosition
    val playbackControls: StateFlow<PlaybackControlsState> = playbackController.playbackControls
    val playbackQueue: StateFlow<PlaybackQueueState> = playbackController.playbackQueue
    val playbackDiagnostics: StateFlow<PlaybackDiagnosticsState> = playbackController.playbackDiagnostics
    val equalizerState: StateFlow<EchoEqualizerState> = playbackController.equalizerState
    val lyricsState: StateFlow<EchoLyricsLoadState> = lyricsController.lyricsState
    val initialAppSettings: EchoAppSettings = settingsStore.startupAppSettingsSnapshot()
    val appSettings: Flow<EchoAppSettings> = settingsStore.appSettings
    val lastFmState: StateFlow<LastFmUiState> = lastFmController.uiState
    val neteaseAccountState: StateFlow<NeteaseAccountState> = neteaseController.accountState
    val neteaseImportState: StateFlow<NeteaseImportState> = neteaseController.importState
    val discordPresenceSnapshot: Flow<EchoMobileDiscordPresenceSnapshot?> =
        combine(
            settingsStore.appSettings,
            playbackController.playbackStatus,
            playbackController.playbackPosition,
        ) { settings, status, position ->
            if (!settings.discordPresenceViaPcEnabled) {
                null
            } else {
                status.toMobileDiscordPresence(position)
            }
        }

    private val _recentPlaybackAlbums = MutableStateFlow<List<AlbumSummary>>(emptyList())
    val recentPlaybackAlbums: StateFlow<List<AlbumSummary>> = _recentPlaybackAlbums.asStateFlow()
    private val _recentPlaybackArtists = MutableStateFlow<List<ArtistSummary>>(emptyList())
    val recentPlaybackArtists: StateFlow<List<ArtistSummary>> = _recentPlaybackArtists.asStateFlow()
    private val _recentPlaybackHeatmap = MutableStateFlow<List<PlaybackHeatmapDay>>(emptyList())
    val recentPlaybackHeatmap: StateFlow<List<PlaybackHeatmapDay>> = _recentPlaybackHeatmap.asStateFlow()
    private val _usbExclusiveTestResult = MutableStateFlow("尚未测试")
    val usbExclusiveTestResult: StateFlow<String> = _usbExclusiveTestResult.asStateFlow()
    private val _opraState = MutableStateFlow(OpraHeadphoneCorrectionState())
    val opraState: StateFlow<OpraHeadphoneCorrectionState> = _opraState.asStateFlow()

    private val albumPlaybackCounts = mutableMapOf<String, Int>()
    private val artistPlaybackCounts = mutableMapOf<String, Int>()
    private val playbackHeatmapCounts = mutableMapOf<Long, Int>()
    private var latestNeteaseQuality: NeteaseAudioQuality = NeteaseAudioQuality.Default

    init {
        lastFmController.start(
            settingsFlow = settingsStore.appSettings,
            playbackStatus = playbackController.playbackStatus,
            playbackPosition = playbackController.playbackPosition,
        )
        viewModelScope.launch {
            settingsStore.appSettings.collect { settings ->
                withContext(Dispatchers.IO) {
                    settingsStore.cacheStartupThemeSnapshot(settings)
                }
                latestNeteaseQuality = NeteaseAudioQuality.fromId(settings.neteaseAudioQuality)
                neteaseController.restore(settings)
                lyricsController.setOnlineLyricsEnabled(settings.onlineLyricsEnabled, playbackController.currentTrackId)
                val firstSettingsEmission = !usbStartupPolicyApplied
                usbStartupPolicyApplied = true
                val shouldEnableUsbExclusive = if (firstSettingsEmission) {
                    settings.usbExclusiveEnabled && settings.usbExclusiveAutoRequestOnStartup
                } else {
                    settings.usbExclusiveEnabled
                }
                playbackController.setUsbExclusiveEnabled(shouldEnableUsbExclusive)
                playbackController.setEqualizerConfig(
                    enabled = settings.equalizerEnabled,
                    presetId = settings.equalizerPreset,
                    gainsDb = settings.equalizerBandGains,
                )
                if (firstSettingsEmission &&
                    settings.usbExclusiveEnabled &&
                    !settings.usbExclusiveAutoRequestOnStartup
                ) {
                    withContext(Dispatchers.IO) {
                        settingsStore.setUsbExclusiveEnabled(false)
                    }
                }
                if (settings.lastFmEnabled && !settings.lastFmUsername.isNullOrBlank()) {
                    lastFmController.setConnected(settings.lastFmUsername.orEmpty())
                }
                EchoRemotePlaybackAuthRegistry.replaceWebDavCredentials(
                    listOfNotNull(webDavPlaybackCredential(settings)),
                )
            }
        }
    }

    fun albumTrackPaging(albumKey: String): Flow<PagingData<EchoTrack>> =
        libraryController.albumTrackPaging(albumKey)

    fun artistTrackPaging(artistKey: String): Flow<PagingData<EchoTrack>> =
        libraryController.artistTrackPaging(artistKey)

    fun folderTrackPaging(folderKey: String): Flow<PagingData<EchoTrack>> =
        libraryController.folderTrackPaging(folderKey)

    fun playlistTrackPaging(playlistId: String): Flow<PagingData<EchoTrack>> =
        libraryController.playlistTrackPaging(playlistId)

    fun refreshLibrary() {
        libraryController.refreshLibrary()
    }

    fun refreshLibraryFolder(treeUri: Uri) {
        libraryController.refreshLibraryFolder(treeUri)
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

    fun play(track: EchoTrack) {
        if (!track.isNeteaseTrack()) {
            playbackController.play(track)
            return
        }
        viewModelScope.launch {
            resolveNeteasePlayback(listOf(track))
                .firstOrNull()
                ?.let(playbackController::play)
        }
    }

    fun playQueue(queue: List<EchoTrack>, startIndex: Int) {
        if (queue.none { it.isNeteaseTrack() }) {
            playbackController.playQueue(queue, startIndex)
            return
        }
        viewModelScope.launch {
            val startTrackId = queue.getOrNull(startIndex)?.id
            val resolvedQueue = resolveNeteasePlayback(queue)
            val resolvedStartIndex = startTrackId
                ?.let { id -> resolvedQueue.indexOfFirst { it.id == id } }
                ?.takeIf { it >= 0 }
                ?: startIndex.coerceIn(0, (resolvedQueue.size - 1).coerceAtLeast(0))
            if (resolvedQueue.isNotEmpty()) {
                playbackController.playQueue(resolvedQueue, resolvedStartIndex)
            }
        }
    }

    fun playTrackFromLibrary(trackId: String) {
        viewModelScope.launch {
            val queue = libraryController.queueAroundTrack(trackId)
            val startIndex = queue.indexOfFirst { it.id == trackId }.takeIf { it >= 0 } ?: 0
            if (queue.isNotEmpty()) playQueue(queue, startIndex)
        }
    }

    fun updateTrackMetadata(update: EchoTrackMetadataUpdate) {
        viewModelScope.launch {
            libraryController.updateTrackMetadata(update)
        }
    }

    fun updateTrackArtwork(trackId: String, artworkUri: Uri) {
        viewModelScope.launch {
            libraryController.updateTrackArtwork(trackId, artworkUri)
        }
    }

    fun applyBestNeteaseMetadata(trackId: String) {
        viewModelScope.launch {
            libraryController.applyBestNeteaseMetadata(trackId)
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
            if (queue.isNotEmpty()) playQueue(queue, 0)
        }
    }

    fun shuffleAlbum(albumKey: String) {
        viewModelScope.launch {
            val queue = libraryController.albumTracksForPlayback(albumKey).shuffled()
            if (queue.isNotEmpty()) {
                playQueue(queue, 0)
                playbackController.enableShuffle()
            }
        }
    }

    fun playArtist(artistKey: String) {
        viewModelScope.launch {
            val queue = libraryController.artistTracksForPlayback(artistKey)
            if (queue.isNotEmpty()) playQueue(queue, 0)
        }
    }

    fun playFolder(folderKey: String) {
        viewModelScope.launch {
            val queue = libraryController.folderTracksForPlayback(folderKey)
            if (queue.isNotEmpty()) playQueue(queue, 0)
        }
    }

    fun playPlaylist(playlistId: String) {
        viewModelScope.launch {
            val queue = libraryController.playlistTracksForPlayback(playlistId)
            if (queue.isNotEmpty()) playQueue(queue, 0)
        }
    }

    fun shuffleArtist(artistKey: String) {
        viewModelScope.launch {
            val queue = libraryController.artistTracksForPlayback(artistKey).shuffled()
            if (queue.isNotEmpty()) {
                playQueue(queue, 0)
                playbackController.enableShuffle()
            }
        }
    }

    fun playPause() {
        playbackController.playPause()
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

    fun cancelSleepTimer() {
        playbackController.cancelSleepTimer()
    }

    fun setReplayGain(enabled: Boolean, preampDb: Float) {
        playbackController.setReplayGain(enabled, preampDb)
    }

    fun adjustReplayGainPreamp(deltaDb: Float) {
        playbackController.adjustReplayGainPreamp(deltaDb)
    }

    fun setSkipSilenceEnabled(enabled: Boolean) {
        playbackController.setSkipSilenceEnabled(enabled)
    }

    fun cyclePlayMode() {
        playbackController.cyclePlayMode()
    }

    fun importLyrics(uri: Uri) {
        lyricsController.importLyrics(uri, playbackController.currentTrackId)
    }

    fun importLyricsForTrack(trackId: String, uri: Uri) {
        lyricsController.importLyrics(uri, trackId)
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

    fun setPerformanceMode(value: String) {
        updateSettings {
            setPerformanceMode(value)
        }
    }

    fun setEffectivePerformanceMode(mode: EchoEffectivePerformanceMode) {
        if (effectivePerformanceMode == mode) return
        effectivePerformanceMode = mode
        libraryController.setEffectivePerformanceMode(mode)
        EchoPlaybackCachePolicy.setEffectivePerformanceMode(mode)
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

    fun setUsbExclusiveAutoRequestOnStartup(enabled: Boolean) {
        updateSettings {
            setUsbExclusiveAutoRequestOnStartup(enabled)
        }
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

    fun updateOpraQuery(query: String) {
        _opraState.update { it.copy(query = query) }
    }

    fun searchOpraHeadphoneCorrections(refresh: Boolean = false) {
        val query = _opraState.value.query.trim()
        if (query.isBlank()) {
            _opraState.update { it.copy(message = "输入耳机型号后再搜索") }
            return
        }
        _opraState.update { it.copy(loading = true, message = null) }
        viewModelScope.launch {
            val result = opraRepository.search(query = query, refresh = refresh)
            result
                .onSuccess { searchResult ->
                    _opraState.update {
                        it.copy(
                            loading = false,
                            results = searchResult.products,
                            status = searchResult.status,
                            selectedEqId = searchResult.products.firstOrNull()?.presets?.firstOrNull()?.eqId,
                            message = if (searchResult.products.isEmpty()) "OPRA 未找到匹配型号" else null,
                        )
                    }
                }
                .onFailure { error ->
                    _opraState.update {
                        it.copy(
                            loading = false,
                            message = error.message ?: "OPRA 搜索失败",
                        )
                    }
                }
        }
    }

    fun selectOpraPreset(eqId: String) {
        _opraState.update { it.copy(selectedEqId = eqId) }
    }

    fun applySelectedOpraPreset() {
        val preset = _opraState.value.selectedPreset
        if (preset == null) {
            _opraState.update { it.copy(message = "先选择一个 OPRA preset") }
            return
        }
        val gainsDb = playbackController.applyOpraPreset(preset)
        updateSettings {
            setEqualizerEnabled(true)
            setEqualizerBandGains(gainsDb)
        }
        _opraState.update {
            it.copy(message = "已近似应用 ${preset.vendorName} ${preset.productName}")
        }
    }

    fun testUsbExclusiveDriver() {
        _usbExclusiveTestResult.value = "正在测试 USB 独占驱动..."
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                playbackController.testUsbExclusiveDriver()
            }
            _usbExclusiveTestResult.value = result
        }
    }

    fun setCustomBackground(mode: String, uri: Uri?) {
        updateSettings {
            setCustomBackground(mode, uri?.toString())
        }
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

    fun setDiscordPresenceViaPcEnabled(enabled: Boolean) {
        updateSettings {
            setDiscordPresenceViaPcEnabled(enabled)
        }
    }

    fun saveEchoLinkPcEndpoint(address: String, token: String) {
        updateSettings {
            setEchoLinkPcEndpoint(address, token)
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
        updateSettings {
            clearSubsonicCredentials()
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
        libraryController.refreshSubsonic(endpoint)
        saveSubsonicCredentials(serverUrl, username, password)
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

    fun loginNeteaseByPhone(phone: String, password: String) {
        neteaseController.loginByPhone(phone, password)
    }

    fun loginNeteaseWithCookie(cookie: String) {
        neteaseController.loginWithCookie(cookie)
    }

    fun refreshNeteasePlaylists() {
        neteaseController.refreshRemotePlaylists()
    }

    fun importNeteasePlaylist(playlistId: Long) {
        neteaseController.importPlaylist(playlistId)
    }

    fun logoutNetease() {
        neteaseController.logout()
    }

    fun setNeteaseAudioQuality(qualityId: String) {
        latestNeteaseQuality = NeteaseAudioQuality.fromId(qualityId)
        updateSettings {
            setNeteaseAudioQuality(qualityId)
        }
    }

    fun connectLastFm(
        apiKey: String,
        sharedSecret: String,
        username: String,
        password: String,
    ) {
        viewModelScope.launch {
            val resolvedApiKey = apiKey.ifBlank { LastFmApiConfig.apiKey }
            val resolvedSharedSecret = sharedSecret.ifBlank { LastFmApiConfig.sharedSecret }
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
            val resolvedApiKey = LastFmApiConfig.apiKey
            val resolvedSharedSecret = LastFmApiConfig.sharedSecret
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
            val resolvedApiKey = LastFmApiConfig.apiKey
            val resolvedSharedSecret = LastFmApiConfig.sharedSecret
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

    private suspend fun resolveNeteasePlayback(queue: List<EchoTrack>): List<EchoTrack> =
        withContext(Dispatchers.IO) {
            val ids = queue.mapNotNull { track -> parseNeteaseSongId(track.id) }.distinct()
            if (ids.isEmpty()) return@withContext queue
            val urls = repository.resolveNeteasePlaybackUrls(
                sessionCookie = neteaseController.currentCookie(),
                songIds = ids,
                quality = latestNeteaseQuality,
            )
            queue.mapNotNull { track ->
                val songId = parseNeteaseSongId(track.id)
                if (songId == null) {
                    track
                } else {
                    urls[songId]?.let { url ->
                        track.copy(
                            uri = url,
                            mimeType = if (latestNeteaseQuality == NeteaseAudioQuality.Standard) {
                                "audio/mpeg"
                            } else {
                                "audio/flac"
                            },
                        )
                    }
                }
            }
        }

    override fun onCleared() {
        libraryController.clear()
        lyricsController.clear()
        playbackController.clear()
        neteaseController.clear()
        lastFmController.clear()
        database.close()
        super.onCleared()
    }

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
        val firstVisibleDay = today - HomeHeatmapVisibleDays + 1
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

    private fun EchoPlaybackStatus.toMobileDiscordPresence(
        position: PlaybackPositionState,
    ): EchoMobileDiscordPresenceSnapshot {
        val currentTrack = track
        return EchoMobileDiscordPresenceSnapshot(
            enabled = true,
            state = state.toRemotePlaybackState(),
            track = currentTrack?.let {
                EchoRemoteTrack(
                    id = it.id,
                    title = it.title,
                    artist = it.artist,
                    album = it.album,
                    artworkUrl = it.artworkUri,
                    durationMs = maxOf(it.durationMs, durationMs, position.durationMs),
                )
            },
            positionMs = position.positionMs,
            durationMs = maxOf(durationMs, position.durationMs, currentTrack?.durationMs ?: 0L),
            deviceName = "ECHOAndroid",
            updatedAtEpochMs = System.currentTimeMillis(),
        )
    }

    private fun EchoPlaybackState.toRemotePlaybackState(): EchoRemotePlaybackState =
        when (this) {
            EchoPlaybackState.Playing -> EchoRemotePlaybackState.Playing
            EchoPlaybackState.Paused -> EchoRemotePlaybackState.Paused
            EchoPlaybackState.Stopped -> EchoRemotePlaybackState.Stopped
            EchoPlaybackState.Ended -> EchoRemotePlaybackState.Stopped
            EchoPlaybackState.Idle -> EchoRemotePlaybackState.Idle
            EchoPlaybackState.Buffering,
            EchoPlaybackState.Loading,
            EchoPlaybackState.Seeking,
            -> EchoRemotePlaybackState.Loading
            EchoPlaybackState.Error -> EchoRemotePlaybackState.Error
        }

    private companion object {
        const val HomeHeatmapVisibleDays = 84L
    }
}

private fun EchoTrack.isNeteaseTrack(): Boolean =
    source.id == NeteaseSourceId || parseNeteaseSongId(id) != null

private fun webDavPlaybackCredential(settings: EchoAppSettings): EchoWebDavPlaybackCredential? {
    val serverUrl = settings.webDavServerUrl?.takeIf { it.isNotBlank() } ?: return null
    val username = settings.webDavUsername?.takeIf { it.isNotBlank() } ?: return null
    val password = settings.webDavPassword?.takeIf { it.isNotBlank() } ?: return null
    return EchoWebDavPlaybackCredential(
        baseUrl = serverUrl,
        username = username,
        password = password,
    )
}
