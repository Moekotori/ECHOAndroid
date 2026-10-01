package app.echo.android.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.backup.EchoBackupPreview

@Composable
fun BackupMigrationPreview(preview: EchoBackupPreview, busy: Boolean, onApply: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(stringResource(R.string.migration_title)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.migration_counts,preview.incomingPlaylists,preview.incomingFavorites,preview.incomingMoments))
            Text(stringResource(R.string.migration_history_lyrics,preview.incomingHistory,preview.incomingLyrics,preview.incomingAssets))
            Text(stringResource(R.string.migration_matches,preview.matchedTracks,preview.missingTracks))
            HorizontalDivider()
            Text(stringResource(R.string.migration_policy), style = MaterialTheme.typography.bodySmall)
            preview.changedSettings.forEach { key -> Text(stringResource(when(key) {
                "home" -> R.string.migration_home; "player" -> R.string.migration_player; "theme" -> R.string.migration_theme
                "language" -> R.string.migration_language; "background" -> R.string.migration_background; else -> R.string.migration_lyrics
            })) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }, confirmButton = { TextButton(onClick = onApply, enabled = !busy) { Text(stringResource(R.string.migration_apply)) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.migration_cancel)) } })
}
