package app.echo.android.ui.home

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.paging.compose.collectAsLazyPagingItems
import app.echo.android.EchoAndroidViewModel
import app.echo.android.connect.EchoRemoteClient
import app.echo.android.feature.home.*
import app.echo.android.model.library.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun EchoUnifiedSearchHost(viewModel: EchoAndroidViewModel, pc: EchoRemoteClient,
    playbackRouter: app.echo.android.EchoLinkPlaybackRouter,
    query: String, active: Boolean, endpointKey: String, onQuery: (String) -> Unit, onClose: () -> Unit,
    onAlbum: (AlbumSummary) -> Unit, onArtist: (ArtistSummary) -> Unit, onPlaylist: (EchoPlaylist) -> Unit) {
    var momentsVisible by rememberSaveable { mutableStateOf(false) }
    var momentQuery by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    if (momentsVisible) {
        val debounced by produceState(momentQuery, momentQuery) { delay(200); value = momentQuery }
        val moments = remember(debounced) { viewModel.libraryExperience.moments(debounced) }.collectAsLazyPagingItems()
        MomentsScreen(momentQuery, moments, { momentQuery = it }, onPlay = { onClose(); viewModel.playMoment(it) },
            onDelete = { id -> scope.launch { viewModel.libraryExperience.deleteBookmark(id) } }, onBack = { momentsVisible = false })
        return
    }
    val state by produceState(false to EchoUnifiedSearch(), query, active, endpointKey) {
        if (!active) return@produceState
        if (query.isBlank()) { value = false to EchoUnifiedSearch(); return@produceState }
        value = true to value.second
        delay(200)
        value = false to viewModel.unifiedSearch.search(query.trim(), pc)
    }
    UnifiedSearchScreen(query, state.first, state.second, onQuery, onClose, { momentsVisible = true },
        onTrack = { onClose(); viewModel.play(it) }, onAlbum, onArtist, onPlaylist,
        onMoment = { onClose(); viewModel.playMoment(it) }, onPcTrack = { track ->
            onClose()
            playbackRouter.play(listOf(track), 0,
                onPhoneQueueReady = viewModel::playQueue, onPhoneLyricsReady = viewModel::setEchoLinkLyrics)
        })
}
