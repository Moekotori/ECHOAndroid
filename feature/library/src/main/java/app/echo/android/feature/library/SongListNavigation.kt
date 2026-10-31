package app.echo.android.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import app.echo.android.design.EchoIcon
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.model.library.EchoTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Scroll state is read here so navigation does not recompose the song list on each pixel. */
@Composable
internal fun SongListNavigation(
    tracks: LazyPagingItems<EchoTrack>,
    listState: LazyListState,
    playingTrackId: String?,
    queryKey: Any,
    findIndex: (suspend (String) -> Int?)?,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val lookup by rememberUpdatedState(findIndex)
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val selecting = LocalLibraryBatchSelection.current?.active == true
    val refresh = tracks.loadState.refresh
    val currentIndex by produceState<Int?>(null, playingTrackId, queryKey, refresh, tracks.itemCount) {
        value = null
        if (playingTrackId != null && refresh is LoadState.NotLoading && lookup != null) {
            // Match the library search debounce; a changed query cancels the old lookup.
            delay(350)
            try {
                value = lookup?.invoke(playingTrackId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                value = null
            }
        }
    }
    val showTop by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
    }
    val currentVisible by remember(listState, playingTrackId) {
        derivedStateOf {
            val layout = listState.layoutInfo
            layout.visibleItemsInfo.any { item ->
                item.key == playingTrackId && item.offset >= layout.viewportStartOffset &&
                    item.offset + item.size <= layout.viewportEndOffset - layout.afterContentPadding
            }
        }
    }
    var jumpJob by remember { mutableStateOf<Job?>(null) }
    var locating by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val notFound = stringResource(R.string.library_playing_song_not_found)
    val failed = stringResource(R.string.library_locate_song_failed)
    DisposableEffect(queryKey, playingTrackId, refresh) {
        onDispose { jumpJob?.cancel() }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(0.dp), horizontalAlignment = Alignment.End) {
        SnackbarHost(snackbar)
        if (!selecting && currentIndex != null && !currentVisible) {
            SongNavigationButton(
                label = stringResource(R.string.library_locate_playing_song),
                onClick = {
                    jumpJob?.cancel()
                    jumpJob = scope.launch {
                        locating = true
                        try {
                            // Resolve again at click time: scans or metadata edits may have moved it.
                            val index = playingTrackId?.let { lookup?.invoke(it) }
                            if (index == null || index !in 0 until tracks.itemCount) {
                                snackbar.showSnackbar(notFound)
                            } else {
                                // Trigger Paging access so unloaded placeholders request their page.
                                tracks[index]
                                val offset = -(listState.layoutInfo.viewportSize.height / 3)
                                if (lightweight || abs(index - listState.firstVisibleItemIndex) > 20) {
                                    listState.scrollToItem(index, offset)
                                } else {
                                    listState.animateScrollToItem(index, offset)
                                }
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            snackbar.showSnackbar(failed)
                        } finally {
                            locating = false
                        }
                    }
                },
                enabled = !locating && refresh is LoadState.NotLoading,
            ) {
                if (locating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp,
                    color = LocalContentColor.current)
                else EchoIcon(LibraryPlayingLocationIcon, null,
                    Modifier.size(18.dp), tint = LocalContentColor.current)
            }
        }
        if (!selecting && showTop) {
            SongNavigationButton(label = stringResource(R.string.library_back_to_top), onClick = {
                jumpJob?.cancel()
                jumpJob = scope.launch {
                    if (lightweight || listState.firstVisibleItemIndex > 20) listState.scrollToItem(0)
                    else listState.animateScrollToItem(0)
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
    // Keep a full touch target while the visible control stays clear of the song metadata.
    IconButton(onClick, enabled = enabled, modifier = Modifier.size(48.dp)
        .semantics { contentDescription = label }) {
        Surface(
            modifier = Modifier.size(32.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
        ) {
            CompositionLocalProvider(LocalContentColor provides
                MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.56f else 0.3f)) {
                Box(contentAlignment = Alignment.Center) { content() }
            }
        }
    }
}
