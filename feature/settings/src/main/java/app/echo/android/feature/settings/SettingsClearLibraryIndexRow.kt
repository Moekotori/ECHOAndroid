package app.echo.android.feature.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch

@Composable
internal fun SettingsClearLibraryIndexRow(onClear: suspend () -> Boolean) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    var result by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()

    SettingsActionRow(
        title = stringResource(R.string.settings_clear_index_title),
        detail = stringResource(R.string.settings_clear_index_detail),
        enabled = !clearing,
        disabledLabel = stringResource(R.string.settings_clear_index_busy),
        onClick = { confirming = true },
    )
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.settings_clear_index_title)) },
            text = { Text(stringResource(R.string.settings_clear_index_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    clearing = true
                    scope.launch {
                        try {
                            result = onClear()
                        } finally {
                            clearing = false
                        }
                    }
                }) {
                    Text(stringResource(R.string.settings_clear_index_action), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
    result?.let { succeeded ->
        AlertDialog(
            onDismissRequest = { result = null },
            text = {
                Text(stringResource(if (succeeded) R.string.settings_clear_index_success else R.string.settings_clear_index_failure))
            },
            confirmButton = {
                TextButton(onClick = { result = null }) { Text(stringResource(android.R.string.ok)) }
            },
        )
    }
}

@Composable
internal fun SettingsLibraryCleanupRow(onCleanup: suspend () -> Pair<Int, Int>) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var result by rememberSaveable { mutableStateOf<Pair<Int, Int>?>(null) }
    val scope = rememberCoroutineScope()
    SettingsActionRow(
        title = stringResource(R.string.settings_library_cleanup),
        detail = stringResource(R.string.settings_library_cleanup_detail),
        enabled = !busy,
        disabledLabel = stringResource(R.string.settings_library_cleanup_busy),
        onClick = { confirming = true },
    )
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.settings_library_cleanup)) },
            text = { Text(stringResource(R.string.settings_library_cleanup_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    busy = true
                    scope.launch {
                        try {
                            result = onCleanup()
                        } finally {
                            busy = false
                        }
                    }
                }) { Text(stringResource(R.string.settings_library_cleanup_action)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
    result?.let { (missing, duplicates) ->
        AlertDialog(
            onDismissRequest = { result = null },
            text = {
                Text(stringResource(R.string.settings_library_cleanup_result, missing, duplicates))
            },
            confirmButton = {
                TextButton(onClick = { result = null }) { Text(stringResource(android.R.string.ok)) }
            },
        )
    }
}
