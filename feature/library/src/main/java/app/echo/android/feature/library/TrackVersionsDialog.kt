package app.echo.android.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.design.*
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.EchoTrackVersions

val LocalTrackVersionsAction = staticCompositionLocalOf<((EchoTrack) -> Unit)?> { null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackVersionsDialog(versions: EchoTrackVersions, saving: Boolean = false, error: Boolean = false,
    onChoose: (String?) -> Unit, onDismiss: () -> Unit, onRetry: (() -> Unit)? = null) {
    val height = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val currentSaving = rememberUpdatedState(saving)
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !currentSaving.value })
    EchoModalSheet(onDismissRequest = { if (!saving) onDismiss() }, sheetState = sheet, sheetGesturesEnabled = !saving) {
        Column(Modifier.fillMaxWidth().heightIn(max = if (height > 0.dp) minOf(650.dp, height * 0.88f) else 650.dp)) {
            EchoSheetHeader(stringResource(R.string.track_versions_title), subtitle = versions.anchor.title, trailing = {
                IconButton(onClick = onDismiss, enabled = !saving) { EchoIcon(Icons.Rounded.Close, stringResource(R.string.track_versions_close)) }
            })
            if (saving) Text(stringResource(R.string.duplicates_saving), Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (error) Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.track_versions_error), Modifier.weight(1f), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
                if (onRetry != null) TextButton(onClick = onRetry, enabled = !saving) { Text(stringResource(R.string.duplicates_reload_versions)) }
            }
            LazyColumn(Modifier.weight(1f, fill = false), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item(key = "automatic") {
                    TrackVersionAutomaticRow(versions.preferredTrackId == null, versions.preferHighestQuality, !saving) { onChoose(null) }
                }
                items(versions.tracks, key = { it.id }) { track ->
                    TrackVersionChoiceRow(track, versions.quality[track.id], versions.locations[track.id],
                        selected = versions.preferredTrackId == track.id, recommended = versions.recommendedTrackId == track.id,
                        enabled = !saving, onClick = { onChoose(track.id) })
                }
                if (versions.tracks.size < 2) item(key = "empty") {
                    Text(stringResource(R.string.track_versions_empty), Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
