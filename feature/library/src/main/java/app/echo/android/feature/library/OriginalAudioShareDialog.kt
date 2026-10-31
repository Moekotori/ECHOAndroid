package app.echo.android.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.echo.android.model.library.EchoTrack

internal val LocalOriginalAudioShare = staticCompositionLocalOf<((List<EchoTrack>) -> Unit)?> { null }

@Composable
fun OriginalAudioShareDialog(
    count: Int,
    skipped: Int,
    wholeOriginal: Boolean,
    busy: Boolean,
    progress: Int,
    missingLyrics: Int,
    missingCovers: Int,
    prepared: Boolean,
    error: String?,
    onOptionsChanged: () -> Unit,
    onShare: (Boolean, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var lyrics by rememberSaveable { mutableStateOf(false) }
    var covers by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.audio_share_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.audio_share_count, count))
                Text(stringResource(R.string.audio_share_original_note), style = MaterialTheme.typography.bodySmall)
                if (skipped > 0) Text(stringResource(R.string.audio_share_skipped, skipped), color = MaterialTheme.colorScheme.error)
                if (wholeOriginal) Text(stringResource(R.string.audio_share_whole_original), style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                    value = lyrics, enabled = !busy && count > 0, role = Role.Checkbox,
                    onValueChange = { lyrics = it; onOptionsChanged() },
                ), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(lyrics, null, enabled = !busy && count > 0, modifier = Modifier.padding(end = 12.dp))
                    Text(stringResource(R.string.audio_share_lyrics))
                }
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                    value = covers, enabled = !busy && count > 0, role = Role.Checkbox,
                    onValueChange = { covers = it; onOptionsChanged() },
                ), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(covers, null, enabled = !busy && count > 0, modifier = Modifier.padding(end = 12.dp))
                    Text(stringResource(R.string.audio_share_cover))
                }
                if (lyrics) Text(stringResource(R.string.audio_share_lyrics_note), style = MaterialTheme.typography.bodySmall)
                if (busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.audio_share_progress, progress, count))
                }
                if (prepared && (missingLyrics > 0 || missingCovers > 0))
                    Text(stringResource(R.string.audio_share_missing, missingLyrics, missingCovers))
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = { onShare(lyrics, covers) }, enabled = !busy && count > 0) {
                Text(stringResource(if (prepared) R.string.audio_share_continue else R.string.audio_share_action))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.audio_share_cancel)) } },
    )
}
