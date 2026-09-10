package app.echo.android.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

@Composable
internal fun SettingsAboutContent(
    appVersionLabel: String,
    errorLogCount: Int = 0,
    onOpenErrorLog: () -> Unit = {},
) {
    SettingsSectionCard(title = stringResource(R.string.settings_section_about)) {
        SettingsActionRow(
            title = stringResource(R.string.settings_error_log),
            detail = if (errorLogCount > 0) {
                stringResource(R.string.settings_error_log_detail_count, errorLogCount)
            } else {
                stringResource(R.string.settings_error_log_detail_empty)
            },
            onClick = onOpenErrorLog,
        )
        SettingsInfoRow(
            title = stringResource(R.string.settings_version),
            detail = appVersionLabel,
        )
    }
}
