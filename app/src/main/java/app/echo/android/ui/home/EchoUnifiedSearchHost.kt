package app.echo.android.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
    var category by rememberSaveable { mutableStateOf(UnifiedSearchCategory.All) }
    BackHandler(enabled = active && momentsVisible) { momentsVisible = false }
    if (momentsVisible) {
        val debounced by produceState(momentQuery, momentQuery) { delay(200); value = momentQuery }
        val moments = remember(debounced) { viewModel.libraryExperience.moments(debounced) }.collectAsLazyPagingItems()
        MomentsScreen(momentQuery, moments, { momentQuery = it }, onPlay = { onClose(); viewModel.playMoment(it) },
            onDelete = { id -> scope.launch { viewModel.libraryExperience.deleteBookmark(id) } }, onBack = { momentsVisible = false })
        return
    }
    val connected = pc.status.collectAsState().value.connectionState == EchoRemoteConnectionState.Connected
    // Replacing the state holder synchronously removes old, clickable results before effects run.
    var local by remember(query, active) { mutableStateOf(UnifiedSearchSourceState()) }
    var remote by remember(query, active, endpointKey, connected) {
        mutableStateOf(UnifiedSearchSourceState(if (connected) UnifiedSearchPhase.Idle else UnifiedSearchPhase.Disconnected))
    }
    var localRetry by remember { mutableIntStateOf(0) }
    var pcRetry by remember { mutableIntStateOf(0) }
    val localEnabled = active && category != UnifiedSearchCategory.Pc
    val pcEnabled = active && category != UnifiedSearchCategory.Library && connected
    LaunchedEffect(query, localEnabled, localRetry) {
        if (!localEnabled || query.isBlank()) return@LaunchedEffect
        local = UnifiedSearchSourceState(UnifiedSearchPhase.Loading)
        delay(200)
        val found = viewModel.unifiedSearch.searchLocal(query.trim())
        local = UnifiedSearchSourceState(if (found.localUnavailable) UnifiedSearchPhase.Failed else UnifiedSearchPhase.Ready, found)
    }
    LaunchedEffect(query, pcEnabled, endpointKey, pcRetry) {
        if (!pcEnabled || query.isBlank()) return@LaunchedEffect
        remote = UnifiedSearchSourceState(UnifiedSearchPhase.Loading)
        delay(200)
        remote = try {
            UnifiedSearchSourceState(UnifiedSearchPhase.Ready, EchoUnifiedSearch(pcTracks = pc.searchTracksSnapshot(query.trim())))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            UnifiedSearchSourceState(UnifiedSearchPhase.Failed)
        }
    }
    UnifiedSearchScreen(query, category, local, remote, onQuery, { category = it }, onClose,
        { momentsVisible = true }, onConnect, { localRetry++ }, { pcRetry++ },
        onTrack = { onClose(); viewModel.play(it) }, onAlbum, onArtist, onPlaylist,
        onMoment = { onClose(); viewModel.playMoment(it) }, onPcTrack = { track ->
            onClose()
            playbackRouter.play(listOf(track), 0,
                onPhoneQueueReady = viewModel::playQueue, onPhoneLyricsReady = viewModel::setEchoLinkLyrics)
        })
}
