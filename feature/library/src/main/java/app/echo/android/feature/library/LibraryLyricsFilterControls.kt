package app.echo.android.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.library.LibraryLyricsIndexState
import app.echo.android.model.library.LibraryPresenceFilter
import app.echo.android.model.library.LibraryTrackFilter
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LibraryLyricsFilterControls(
    selected: LibraryPresenceFilter,
    onSelect: (LibraryPresenceFilter) -> Unit,
    indexState: StateFlow<LibraryLyricsIndexState>?,
    onInspect: () -> Unit,
    onCancel: () -> Unit,
) {
    Text(stringResource(R.string.library_filter_lyrics), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LibraryPresenceFilter.entries.forEach { value ->
            FilterChip(selected = selected == value, onClick = { onSelect(value) }, label = {
                Text(stringResource(when (value) {
                    LibraryPresenceFilter.Any -> R.string.library_filter_any
                    LibraryPresenceFilter.Present -> R.string.library_filter_lyrics_yes
                    LibraryPresenceFilter.Missing -> R.string.library_filter_lyrics_no
                }))
            })
        }
    }
    Text(stringResource(R.string.library_filter_lyrics_hint), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (indexState == null) return
    // Progress never becomes a high-frequency input of the library page or song list.
    val state by indexState.collectAsState()
    if (state.failed) Text(stringResource(R.string.library_filter_lyrics_failed),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        val progress = state.progress
        Text(if (progress.waitingForPlayback && state.isRunning)
            stringResource(R.string.library_filter_lyrics_waiting)
        else if (progress.totalCount == 0) stringResource(R.string.library_filter_lyrics_not_checked)
        else stringResource(R.string.library_filter_lyrics_progress, progress.checkedCount, progress.totalCount),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f))
        TextButton(onClick = if (state.isRunning) onCancel else onInspect) {
            Text(stringResource(if (state.isRunning) R.string.library_filter_lyrics_stop else R.string.library_filter_lyrics_inspect))
        }
    }
}

@Composable
internal fun LibraryFilteredEmptyState(
    filter: LibraryTrackFilter,
    indexState: StateFlow<LibraryLyricsIndexState>?,
    onInspect: () -> Unit,
    onCancel: () -> Unit,
    onClear: () -> Unit,
) {
    if (filter.lyrics == LibraryPresenceFilter.Any || indexState == null) {
        LibraryCollectionEmpty(stringResource(R.string.library_filter_empty),
            stringResource(R.string.library_filter_empty_hint), stringResource(R.string.library_filter_clear), onClear)
        return
    }
    val state by indexState.collectAsState()
    val needsInspection = state.progress.checkedAtEpochMs == 0L || state.progress.pendingCount > 0 || state.failed
    LibraryCollectionEmpty(
        title = stringResource(if (state.isRunning) R.string.library_filter_lyrics_checking else R.string.library_filter_empty),
        detail = when {
            state.failed -> stringResource(R.string.library_filter_lyrics_failed)
            state.isRunning && state.progress.waitingForPlayback -> stringResource(R.string.library_filter_lyrics_waiting)
            state.isRunning -> stringResource(R.string.library_filter_lyrics_progress, state.progress.checkedCount, state.progress.totalCount)
            needsInspection -> stringResource(R.string.library_filter_lyrics_hint)
            else -> stringResource(R.string.library_filter_empty_hint)
        },
        actionLabel = stringResource(when {
            state.isRunning -> R.string.library_filter_lyrics_stop
            needsInspection -> R.string.library_filter_lyrics_inspect
            else -> R.string.library_filter_clear
        }),
        onAction = when { state.isRunning -> onCancel; needsInspection -> onInspect; else -> onClear },
    )
}
