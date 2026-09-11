package app.echo.android.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.echo.android.model.backup.EchoBackupNotice

@Composable
internal fun SettingsAboutContent(
    appVersionLabel: String,
    errorLogCount: Int = 0,
    onOpenErrorLog: () -> Unit = {},
    backupNotice: EchoBackupNotice? = null,
    onExportBackup: () -> Unit = {},
    onImportBackup: () -> Unit = {},
) {
    SettingsSectionCard(title = stringResource(R.string.settings_section_about)) {
        SettingsInfoRow(
            title = stringResource(R.string.settings_version),
            detail = appVersionLabel,
        )
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
