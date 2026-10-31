package app.echo.android.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon

enum class NfcMusicPhase { Waiting, Working, Overwrite, Verify, Success, Missing, Choosing, Bound, Error, Disabled, LaunchBlocked, Unsupported }
enum class NfcMusicProblem { ReadOnly, TooSmall, Unsupported, Changed, Disconnected, Storage, Load, Empty }
data class NfcMusicChoice(val mediaId: String, val label: String, val album: Boolean, val detail: String = "")
data class NfcMusicUiState(
    val phase: NfcMusicPhase,
    val label: String = "",
    val query: String = "",
    val choices: List<NfcMusicChoice> = emptyList(),
    val loading: Boolean = false,
    val problem: NfcMusicProblem? = null,
)

@Composable
fun NfcMusicDialog(
    state: NfcMusicUiState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onOverwrite: () -> Unit,
    onRebind: () -> Unit,
    onQuery: (String) -> Unit,
    onChoose: (NfcMusicChoice) -> Unit,
    onEnable: () -> Unit,
    onPlay: () -> Unit,
) {
    val working = state.phase == NfcMusicPhase.Working
    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        icon = { EchoIcon(Icons.Rounded.Nfc, null, tint = MaterialTheme.colorScheme.onSurface) },
        title = { Text(stringResource(when (state.phase) {
            NfcMusicPhase.Missing, NfcMusicPhase.Choosing -> R.string.nfc_music_rebind
            NfcMusicPhase.Success, NfcMusicPhase.Bound -> R.string.nfc_music_done
            else -> R.string.nfc_music_write
        })) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.label.isNotBlank()) Text(state.label, style = MaterialTheme.typography.titleSmall)
                Text(stringResource(when (state.phase) {
                    NfcMusicPhase.Waiting -> R.string.nfc_music_waiting
                    NfcMusicPhase.Working -> R.string.nfc_music_working
                    NfcMusicPhase.Overwrite -> R.string.nfc_music_overwrite_detail
                    NfcMusicPhase.Verify -> R.string.nfc_music_verify
                    NfcMusicPhase.Success -> R.string.nfc_music_success
                    NfcMusicPhase.Bound -> R.string.nfc_music_bound
                    NfcMusicPhase.Missing -> R.string.nfc_music_missing
                    NfcMusicPhase.Choosing -> R.string.nfc_music_choose
                    NfcMusicPhase.Disabled -> R.string.nfc_music_disabled
                    NfcMusicPhase.LaunchBlocked -> R.string.nfc_music_launch_blocked
                    NfcMusicPhase.Unsupported -> R.string.nfc_music_no_hardware
                    NfcMusicPhase.Error -> when (state.problem) {
                        NfcMusicProblem.ReadOnly -> R.string.nfc_music_read_only
                        NfcMusicProblem.TooSmall -> R.string.nfc_music_too_small
                        NfcMusicProblem.Unsupported -> R.string.nfc_music_unsupported_tag
                        NfcMusicProblem.Changed -> R.string.nfc_music_changed
                        NfcMusicProblem.Storage -> R.string.nfc_music_storage
                        NfcMusicProblem.Load -> R.string.nfc_music_load_error
                        NfcMusicProblem.Empty -> R.string.nfc_music_empty_collection
                        else -> R.string.nfc_music_disconnected
                    }
                }))
                if (working || state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.phase == NfcMusicPhase.Choosing) {
                    OutlinedTextField(value = state.query, onValueChange = onQuery,
                        label = { Text(stringResource(R.string.nfc_music_search)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (!state.loading && state.choices.isEmpty()) Text(stringResource(R.string.nfc_music_no_results))
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                        items(state.choices, key = { it.mediaId }) { choice ->
                            Column(Modifier.fillMaxWidth().clickable { onChoose(choice) }.padding(vertical = 10.dp)) {
                                Text(choice.label, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                                Text(listOf(stringResource(if (choice.album) R.string.nfc_music_album else R.string.nfc_music_playlist),
                                    choice.detail).filter { it.isNotBlank() }.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (state.phase) {
                NfcMusicPhase.Overwrite -> TextButton(onClick = onOverwrite) { Text(stringResource(R.string.nfc_music_overwrite)) }
                NfcMusicPhase.Missing -> TextButton(onClick = onRebind) { Text(stringResource(R.string.nfc_music_rebind)) }
                NfcMusicPhase.Error -> TextButton(onClick = onRetry) { Text(stringResource(R.string.nfc_music_retry)) }
                NfcMusicPhase.Disabled, NfcMusicPhase.LaunchBlocked -> TextButton(onClick = onEnable) { Text(stringResource(R.string.nfc_music_settings)) }
                NfcMusicPhase.Bound -> TextButton(onClick = onPlay) { Text(stringResource(R.string.nfc_music_play)) }
                NfcMusicPhase.Success, NfcMusicPhase.Unsupported -> TextButton(onClick = onDismiss) { Text(stringResource(R.string.nfc_music_close)) }
                else -> Unit
            }
        },
        dismissButton = {
            if (!working && state.phase != NfcMusicPhase.Success && state.phase != NfcMusicPhase.Unsupported)
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.nfc_music_cancel)) }
        },
    )
}
