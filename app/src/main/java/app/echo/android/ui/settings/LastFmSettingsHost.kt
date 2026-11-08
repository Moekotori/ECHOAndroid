package app.echo.android.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.echo.android.EchoAndroidViewModel
import app.echo.android.feature.settings.LastFmSettingsDetails
import app.echo.android.model.settings.LastFmOptions

/** Keep one-second listen progress scoped to this part of the settings page. */
@Composable
internal fun LastFmSettingsHost(viewModel: EchoAndroidViewModel, enabled: Boolean, connected: Boolean, options: LastFmOptions) {
    val activity by viewModel.lastFmActivity.collectAsStateWithLifecycle()
    val importStatus by viewModel.lastFmLibrary.importStatus.collectAsStateWithLifecycle()
    val loveStatus by viewModel.lastFmLibrary.loveStatus.collectAsStateWithLifecycle()
    LastFmSettingsDetails(enabled, connected, options, activity, importStatus, loveStatus,
        viewModel::setLastFmOptions, viewModel.lastFmLibrary::startImport, viewModel.lastFmLibrary::cancelImport, viewModel::refreshLastFm)
}
