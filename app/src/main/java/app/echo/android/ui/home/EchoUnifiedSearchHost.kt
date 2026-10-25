package app.echo.android.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import app.echo.android.feature.home.R as HomeR
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.paging.compose.collectAsLazyPagingItems
import app.echo.android.EchoAndroidViewModel
import app.echo.android.connect.EchoRemoteClient
import app.echo.android.feature.home.*
import app.echo.android.model.library.*
import app.echo.android.model.connect.EchoRemoteConnectionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun EchoUnifiedSearchHost(viewModel: EchoAndroidViewModel, pc: EchoRemoteClient,
    playbackRouter: app.echo.android.EchoLinkPlaybackRouter,
    query: String, active: Boolean, endpointKey: String, onQuery: (String) -> Unit, onClose: () -> Unit,
    onAlbum: (AlbumSummary) -> Unit, onArtist: (ArtistSummary) -> Unit, onPlaylist: (EchoPlaylist) -> Unit,
    onConnect: () -> Unit) {
    var momentsVisible by rememberSaveable { mutableStateOf(false) }
    var momentQuery by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val snackbar = remember { SnackbarHostState() }
    val failedMessage = stringResource(HomeR.string.search_action_failed)
    val addedMessage = stringResource(HomeR.string.search_action_added)
    var category by rememberSaveable { mutableStateOf(UnifiedSearchCategory.All) }
    var section by rememberSaveable { mutableStateOf<UnifiedSearchSection?>(null) }
    val sectionStates = rememberSaveableStateHolder()
    var focusedOnce by rememberSaveable { mutableStateOf(false) }
    var playlistTrack by remember { mutableStateOf<EchoTrack?>(null) }
    var playlistBusy by remember { mutableStateOf(false) }
    var playlistFailed by remember { mutableStateOf(false) }
    var pendingPlay by remember { mutableStateOf<UnifiedSearchItem?>(null) }
    val playlists by viewModel.localPlaylists.collectAsState()
    fun play(item: UnifiedSearchItem) {
        onClose()
        when (item) {
            is UnifiedSearchItem.Track -> { playbackRouter.selectRemoteMode(false); viewModel.play(item.value) }
            is UnifiedSearchItem.PcTrack -> playbackRouter.play(listOf(item.value), 0,
                onPhoneQueueReady = viewModel::playQueue, onPhoneLyricsReady = viewModel::setEchoLinkLyrics)
            else -> Unit
        }
    }
    fun open(item: UnifiedSearchItem) {
        focus.clearFocus(); keyboard?.hide()
        when (item) {
            is UnifiedSearchItem.Track, is UnifiedSearchItem.PcTrack -> {
                val hasQueue = if (item is UnifiedSearchItem.PcTrack && playbackRouter.remoteMode.value)
                    pc.status.value.playback.queue.totalCount > 0 else viewModel.playbackQueue.value.items.isNotEmpty()
                if (hasQueue) pendingPlay = item else play(item)
            }
            is UnifiedSearchItem.Album -> onAlbum(item.value)
            is UnifiedSearchItem.Artist -> onArtist(item.value)
            is UnifiedSearchItem.Playlist -> onPlaylist(item.value)
            is UnifiedSearchItem.Moment -> { playbackRouter.selectRemoteMode(false); onClose(); viewModel.playMoment(item.value) }
        }
    }
    val trackAction: (EchoTrack, SearchTrackAction) -> Unit = { track, action ->
        focus.clearFocus(); keyboard?.hide()
        if (action == SearchTrackAction.AddToPlaylist) { playlistFailed = false; playlistTrack = track }
        else scope.launch {
            try {
                playbackRouter.selectRemoteMode(false)
                viewModel.batchQueue(listOf(track), when (action) {
                    SearchTrackAction.PlayNext -> 1; SearchTrackAction.AddNextUp -> 2; else -> 3
                })
                snackbar.showSnackbar(addedMessage)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { snackbar.showSnackbar(failedMessage) }
        }
    }
    val pcAction: (app.echo.android.model.connect.EchoRemoteTrack, SearchTrackAction) -> Unit = { track, action ->
        focus.clearFocus(); keyboard?.hide()
        scope.launch {
            try {
                val resolved = pc.resolveSearchTrackOnPhone(track)
                playbackRouter.selectRemoteMode(false)
                viewModel.batchQueue(listOf(resolved), when (action) {
                    SearchTrackAction.PlayNext -> 1; SearchTrackAction.AddNextUp -> 2; else -> 3
                })
                snackbar.showSnackbar(addedMessage)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { snackbar.showSnackbar(failedMessage) }
        }
    }
    pendingPlay?.let { item -> AlertDialog(onDismissRequest = { pendingPlay = null },
        title = { Text(stringResource(HomeR.string.search_replace_queue_title)) },
        text = { Text(stringResource(HomeR.string.search_replace_queue_body)) },
        confirmButton = { TextButton(onClick = { pendingPlay = null; play(item) }) { Text(stringResource(HomeR.string.search_play_now)) } },
        dismissButton = { TextButton(onClick = { pendingPlay = null }) { Text(stringResource(HomeR.string.search_cancel)) } }) }
    playlistTrack?.let { track -> SearchPlaylistSheet(playlists, playlistBusy, playlistFailed,
        onSelect = { playlist ->
            playlistBusy = true; playlistFailed = false
            scope.launch {
                try { viewModel.playlistTools.add(playlist.id, listOf(track.id)); playlistTrack = null; snackbar.showSnackbar(addedMessage) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { playlistFailed = true }
                finally { playlistBusy = false }
            }
        }, onDismiss = { playlistTrack = null }) }
    BackHandler(enabled = active && section != null && pendingPlay == null && playlistTrack == null) { section = null }
    section?.let { selected ->
        sectionStates.SaveableStateProvider(selected.name) {
            val pages = remember(selected, query, endpointKey) { viewModel.unifiedSearch.pages(selected, query, pc, endpointKey) }
            Box(Modifier.fillMaxSize()) {
                UnifiedSearchResults(selected, query, pages.collectAsLazyPagingItems(), { section = null }, ::open, trackAction, pcAction)
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
            }
        }
        return
    }
    BackHandler(enabled = active && momentsVisible) { momentsVisible = false }
    if (momentsVisible) {
        val debounced by produceState(momentQuery, momentQuery) { delay(200); value = momentQuery }
        val moments = remember(debounced) { viewModel.libraryExperience.moments(debounced) }.collectAsLazyPagingItems()
        Box(Modifier.fillMaxSize()) {
        MomentsScreen(momentQuery, moments, { momentQuery = it }, onPlay = { onClose(); viewModel.playMoment(it) },
            onDelete = { id -> scope.launch {
                try { viewModel.libraryExperience.deleteBookmark(id) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { snackbar.showSnackbar(failedMessage) }
            } }, onBack = { momentsVisible = false })
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }
        return
    }
    val connected = pc.status.collectAsState().value.connectionState == EchoRemoteConnectionState.Connected
    // Replacing the state holder synchronously removes old, clickable results before effects run.
    var local by remember(query) { mutableStateOf(viewModel.unifiedSearch.cachedPreview(query)?.let {
        UnifiedSearchSourceState(if (it.localUnavailable) UnifiedSearchPhase.Failed else UnifiedSearchPhase.Ready, it)
    } ?: UnifiedSearchSourceState()) }
    var remote by remember(query, endpointKey, connected) {
        mutableStateOf(if (connected) viewModel.unifiedSearch.cachedPcPreview(query, endpointKey)?.let {
            UnifiedSearchSourceState(UnifiedSearchPhase.Ready, it)
        } ?: UnifiedSearchSourceState() else UnifiedSearchSourceState(UnifiedSearchPhase.Disconnected))
    }
    var localRetry by remember { mutableIntStateOf(0) }
    var pcRetry by remember { mutableIntStateOf(0) }
    val localEnabled = active && category != UnifiedSearchCategory.Pc
    val pcEnabled = active && category != UnifiedSearchCategory.Library && connected
    LaunchedEffect(query, localEnabled, localRetry) {
        if (!localEnabled || query.isBlank()) return@LaunchedEffect
        if (local.phase != UnifiedSearchPhase.Ready) local = local.copy(phase = UnifiedSearchPhase.Loading)
        delay(200)
        val found = viewModel.unifiedSearch.searchLocal(query.trim())
        local = UnifiedSearchSourceState(if (found.localUnavailable) UnifiedSearchPhase.Failed else UnifiedSearchPhase.Ready, found)
    }
    LaunchedEffect(query, pcEnabled, endpointKey, pcRetry) {
        if (!pcEnabled || query.isBlank()) return@LaunchedEffect
        if (remote.phase != UnifiedSearchPhase.Ready) remote = remote.copy(phase = UnifiedSearchPhase.Loading)
        delay(200)
        remote = try {
            val result = EchoUnifiedSearch(pcTracks = pc.searchTracksSnapshot(query.trim()))
            viewModel.unifiedSearch.cachePcPreview(query, endpointKey, result)
            UnifiedSearchSourceState(UnifiedSearchPhase.Ready, result)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            UnifiedSearchSourceState(UnifiedSearchPhase.Failed)
        }
    }
    sectionStates.SaveableStateProvider("preview") {
    Box(Modifier.fillMaxSize()) {
    UnifiedSearchScreen(query, category, local, remote, onQuery, { category = it }, onClose,
        { momentsVisible = true }, onConnect, { localRetry++ }, { pcRetry++ },
        onTrack = { open(UnifiedSearchItem.Track(it)) }, onAlbum, onArtist, onPlaylist,
        onMoment = { open(UnifiedSearchItem.Moment(it)) }, onPcTrack = { open(UnifiedSearchItem.PcTrack(it)) },
        onViewAll = { focus.clearFocus(); keyboard?.hide(); section = it },
        onTrackAction = trackAction, onPcAction = pcAction, requestFocus = active && !focusedOnce,
        onFocusRequested = { focusedOnce = true })
    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
    }
}
