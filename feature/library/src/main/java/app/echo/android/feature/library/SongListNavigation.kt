package app.echo.android.feature.library

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import app.echo.android.design.EchoIcon
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.LocalEchoPageInteractive
import app.echo.android.model.library.EchoTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Scroll state is read here so navigation does not recompose the song list on each pixel. */
@Composable
internal fun SongListNavigation(
    tracks: LazyPagingItems<EchoTrack>,
    listState: LazyListState,
    playingTrackId: String?,
    queryKey: Any,
    findIndex: (suspend (String) -> Int?)?,
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val lookup by rememberUpdatedState(findIndex)
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val interactive = LocalEchoPageInteractive.current
    val dragging by listState.interactionSource.collectIsDraggedAsState()
    val selecting = LocalLibraryBatchSelection.current?.active == true
    val refresh = tracks.loadState.refresh
    val showTop by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
    }
    var jumpJob by remember { mutableStateOf<Job?>(null) }
    var locating by remember { mutableStateOf(false) }
    val notFound = stringResource(R.string.library_playing_song_not_found)
    val failed = stringResource(R.string.library_locate_song_failed)
    // A locate can itself trigger a Paging refresh; that must not cancel its own animation.
    DisposableEffect(queryKey, playingTrackId, selecting, interactive) {
        onDispose { jumpJob?.cancel() }
    }
    LaunchedEffect(dragging) {
        if (dragging) jumpJob?.cancel()
    }

    Row(modifier) {
        // Keep the action in place across scrolling and Paging refreshes; resolve only on click.
        if (!selecting && playingTrackId != null && lookup != null) {
            SongNavigationButton(
                label = stringResource(R.string.library_locate_playing_song),
                onClick = locate@{
                    if (locating || !interactive) return@locate
                    jumpJob?.cancel()
                    locating = true
                    jumpJob = scope.launch {
                        var message: String? = null
                        try {
                            // The retained window is bounded to 600 rows. Reading it does not load pages.
                            val snapshot = tracks.itemSnapshotList
                            val loadedIndex = snapshot.items.indexOfFirst { it.id == playingTrackId }
                            val index = if (loadedIndex >= 0) snapshot.placeholdersBefore + loadedIndex
                                else lookup?.invoke(playingTrackId)
                            if (index == null || index !in 0 until tracks.itemCount) {
                                message = notFound
                            } else if (tracks.loadState.refresh !is LoadState.NotLoading) {
                                // An external refresh replaced the result while the lookup was running.
                                return@launch
                            } else {
                                // Trigger Paging access so unloaded placeholders request their page.
                                tracks[index]
                                listState.scrollToSong(index, lightweight)
                                // Keep feedback until the target arrives, without polling or an unbounded wait.
                                val ready = withTimeoutOrNull(3_000) {
                                    snapshotFlow {
                                        when {
                                            index in 0 until tracks.itemCount &&
                                                tracks.peek(index)?.id == playingTrackId -> true
                                            tracks.loadState.hasError -> false
                                            else -> null
                                        }
                                    }.first { it != null }
                                }
                                if (ready != true) message = failed
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            message = failed
                        } finally {
                            locating = false
                        }
                        message?.let { snackbar.showSnackbar(it) }
                    }
                },
                enabled = interactive && !locating && refresh is LoadState.NotLoading,
            ) {
                if (locating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp,
                    color = LocalContentColor.current)
                else EchoIcon(LibraryPlayingLocationIcon, null,
                    Modifier.size(18.dp), tint = LocalContentColor.current)
            }
        }
        if (!selecting && showTop) {
            SongNavigationButton(label = stringResource(R.string.library_back_to_top), enabled = interactive && !locating, onClick = {
                jumpJob?.cancel()
                jumpJob = scope.launch {
                    listState.scrollToSong(0, lightweight)
                }
            }) {
                EchoIcon(Icons.Rounded.VerticalAlignTop, null,
                    Modifier.size(18.dp), tint = LocalContentColor.current)
            }
        }
    }
}

@Composable
private fun SongNavigationButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    IconButton(onClick, enabled = enabled, modifier = Modifier.size(48.dp)
        .semantics { contentDescription = label }) {
        content()
    }
}
