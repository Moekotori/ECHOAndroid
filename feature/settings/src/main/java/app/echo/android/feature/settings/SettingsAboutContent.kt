package app.echo.android.feature.settings

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import app.echo.android.model.backup.EchoBackupNotice

@Composable
internal fun SettingsAboutContent(
    appVersionLabel: String,
    updateContent: @Composable () -> Unit = {},
    errorLogCount: Int = 0,
    onOpenErrorLog: () -> Unit = {},
    backupNotice: EchoBackupNotice? = null,
    onExportBackup: () -> Unit = {},
    onImportBackup: () -> Unit = {},
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val linkError = stringResource(R.string.settings_about_link_error)
    val openLink: (String) -> Unit = { url ->
        try {
            uriHandler.openUri(url)
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(context, linkError, Toast.LENGTH_SHORT).show()
        } catch (_: IllegalArgumentException) {
            Toast.makeText(context, linkError, Toast.LENGTH_SHORT).show()
        }
    }
    SettingsAboutIdentity(appVersionLabel)
    SettingsAboutLinks(openLink)
    SettingsAboutPanel {
        updateContent()
        SettingsActionRow(
            title = stringResource(R.string.settings_backup_export),
            detail = when (backupNotice) {
                EchoBackupNotice.Exported -> stringResource(R.string.settings_backup_exported)
                is EchoBackupNotice.Restored -> stringResource(
                    R.string.settings_backup_restored,
                    backupNotice.result.playlistsRestored,
                    backupNotice.result.favoritesRestored,
                    backupNotice.result.tracksMissing,
                )
                is EchoBackupNotice.Failed -> backupNotice.message
                null -> stringResource(R.string.settings_backup_export_detail)
            },
            onClick = onExportBackup,
        )
        SettingsActionRow(
            title = stringResource(R.string.settings_backup_restore),
            detail = stringResource(R.string.settings_backup_restore_detail),
            onClick = onImportBackup,
        )
        SettingsActionRow(
            title = stringResource(R.string.settings_error_log),
            detail = if (errorLogCount > 0) {
                stringResource(R.string.settings_error_log_detail_count, errorLogCount)
            } else {
                stringResource(R.string.settings_error_log_detail_empty)
            },
            onClick = onOpenErrorLog,
        )
    }
}
