package app.echo.android.feature.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoArtworkImage
import app.echo.android.design.EchoArtworkSize
import app.echo.android.design.EchoModalSheet
import app.echo.android.design.EchoSheetHeader
import app.echo.android.design.EchoSheetRow
import app.echo.android.model.playback.PlaybackHistoryEntry
import kotlinx.coroutines.launch

/** Everything a history row can do. Delete is offered on the timeline only, where a row is one event. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaybackHistoryActionSheet(
    entry: PlaybackHistoryEntry,
    subtitle: String,
    canDelete: Boolean,
    actions: PlaybackHistoryActions,
    onRequestDelete: (PlaybackHistoryEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close(then: () -> Unit) {
        scope.launch { state.hide() }.invokeOnCompletion { onDismiss(); then() }
    }
    EchoModalSheet(onDismissRequest = onDismiss, sheetState = state) {
        EchoSheetHeader(
            title = entry.title,
            subtitle = subtitle.ifBlank { null },
            leading = {
                EchoArtworkImage(
                    entry.artworkUri, null, Modifier.size(52.dp),
                    shape = RoundedCornerShape(14.dp), sizeClass = EchoArtworkSize.Thumbnail,
                )
            },
        )
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            EchoSheetRow(
                stringResource(R.string.playback_history_play), Icons.Rounded.PlayArrow,
                onClick = { close { actions.onPlay(entry) } }, enabled = entry.canReplay, chevron = false,
            )
            EchoSheetRow(
                stringResource(R.string.playback_history_play_next), Icons.AutoMirrored.Rounded.PlaylistAdd,
                onClick = { close { actions.onPlayNext(entry) } }, enabled = entry.canReplay, chevron = false,
            )
            EchoSheetRow(
                stringResource(R.string.playback_history_add_queue), Icons.AutoMirrored.Rounded.QueueMusic,
                onClick = { close { actions.onEnqueue(entry) } }, enabled = entry.canReplay, chevron = false,
            )
            EchoSheetRow(
                stringResource(R.string.playback_history_copy), Icons.Rounded.ContentCopy,
                onClick = { close { actions.onCopy(entry) } }, chevron = false,
            )
            if (canDelete) EchoSheetRow(
                stringResource(R.string.playback_history_delete), Icons.Rounded.DeleteOutline,
                onClick = { close { onRequestDelete(entry) } }, danger = true, chevron = false,
            )
        }
    }
}
