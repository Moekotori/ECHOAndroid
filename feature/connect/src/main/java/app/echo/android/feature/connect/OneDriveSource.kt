package app.echo.android.feature.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon

data class OneDriveSourceUiState(
    val available: Boolean,
    val connected: Boolean,
    val loading: Boolean,
    val cancellable: Boolean,
    val failed: Boolean,
    val browsing: Boolean,
    val currentFolder: String?,
    val directories: List<String>,
    val canGoUp: Boolean,
    val hasPreviousPage: Boolean,
    val hasNextPage: Boolean,
)

@Composable
fun OneDriveSource(
    state: OneDriveSourceUiState,
    expanded: Boolean,
    onExpand: () -> Unit,
    scanBusy: Boolean,
    onConnect: () -> Unit,
    onCancel: () -> Unit,
    onDisconnect: () -> Unit,
    onBrowse: () -> Unit,
    onSelectDirectory: (Int) -> Unit,
    onGoUp: () -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onSync: () -> Unit,
) {
    var confirmingRemoval by remember { mutableStateOf(false) }
    LibrarySourceSection(
        title = "OneDrive · Beta",
        icon = Icons.Rounded.Cloud,
        description = stringResource(R.string.connect_onedrive_description),
        expanded = expanded,
        onExpand = onExpand,
        status = {
            if (state.connected) ConnectStatus(stringResource(R.string.feature_connect_configuration_saved_a7628f), ConnectTone.Positive)
            else ConnectStatus(stringResource(R.string.feature_connect_not_configured_29b98a), ConnectTone.Neutral)
        },
    ) {
        when {
            !state.available -> ConnectNote(stringResource(R.string.connect_onedrive_unavailable))
            state.loading -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.cancellable) TextButton(onClick = onCancel) { Text(stringResource(android.R.string.cancel)) }
            }
            else -> {
                if (state.failed) ConnectNote(stringResource(R.string.connect_onedrive_failed), error = true)
                if (state.connected) {
                    if (state.browsing) {
                        state.currentFolder?.let { ConnectNote(it) }
                        if (state.canGoUp) TextButton(onClick = onGoUp) { Text(stringResource(R.string.connect_onedrive_parent)) }
                        state.directories.forEachIndexed { index, folder ->
                            TextButton(onClick = { onSelectDirectory(index) }, modifier = Modifier.fillMaxWidth()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    EchoIcon(Icons.Rounded.Folder, contentDescription = null)
                                    Text(folder, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        if (state.hasPreviousPage || state.hasNextPage) Row {
                            TextButton(onClick = onPreviousPage, enabled = state.hasPreviousPage) {
                                Text(stringResource(R.string.connect_onedrive_previous_page))
                            }
                            TextButton(onClick = onNextPage, enabled = state.hasNextPage) {
                                Text(stringResource(R.string.connect_onedrive_next_page))
                            }
                        }
                        Button(onClick = onSync, enabled = !scanBusy && !state.failed, modifier = Modifier.fillMaxWidth(), shape = ConnectControlShape) {
                            Text(stringResource(R.string.connect_onedrive_sync_folder))
                        }
                        if (state.failed) TextButton(onClick = onBrowse) { Text(stringResource(R.string.connect_onedrive_browse)) }
                    } else {
                        Button(onClick = onBrowse, enabled = !scanBusy, modifier = Modifier.fillMaxWidth(), shape = ConnectControlShape) {
                            Text(stringResource(R.string.connect_onedrive_browse))
                        }
                    }
                    Row {
                        TextButton(onClick = onConnect, enabled = !scanBusy) { Text(stringResource(R.string.connect_onedrive_sign_in_again)) }
                        TextButton(onClick = { confirmingRemoval = true }, enabled = !scanBusy) {
                            Text(stringResource(R.string.feature_connect_remove_configuration_26bffc))
                        }
                    }
                } else {
                    Button(onClick = onConnect, enabled = !scanBusy, modifier = Modifier.fillMaxWidth(), shape = ConnectControlShape) {
                        Text(stringResource(R.string.connect_onedrive_sign_in))
                    }
                }
            }
        }
    }
    if (confirmingRemoval) ForgetConnectionDialog(
        title = stringResource(R.string.feature_connect_remove_title_323067, "OneDrive"),
        onDismiss = { confirmingRemoval = false },
        onConfirm = { confirmingRemoval = false; onDisconnect() },
    )
}
