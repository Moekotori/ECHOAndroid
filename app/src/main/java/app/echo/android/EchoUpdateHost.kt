package app.echo.android

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import app.echo.android.feature.settings.SettingsUpdateDialog

@Composable
internal fun EchoUpdateHost(viewModel: EchoUpdateViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
    fun install() {
        val apk = state.apk ?: return
        try {
            viewModel.installing()
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (_: Exception) { viewModel.installFailed() }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (context.packageManager.canRequestPackageInstalls()) install()
        else viewModel.installFailed(permission = true)
    }
    fun requestInstall() {
        try {
            if (context.packageManager.canRequestPackageInstalls()) install()
            else permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")))
        } catch (_: Exception) { viewModel.installFailed() }
    }
    LaunchedEffect(state.apk) {
        // Do not interrupt another page after the user dismissed the download dialog.
        if (state.apk != null) {
            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
            if (viewModel.consumeAutoInstall()) requestInstall()
        }
    }
    if (state.visible) SettingsUpdateDialog(
        currentVersion = BuildConfig.VERSION_NAME, sizeBytes = state.update?.size,
        version = state.update?.versionName, notes = state.update?.notes.orEmpty(),
        busy = state.busy, progress = state.progress, ready = state.apk != null, error = state.error,
        onDismiss = viewModel::dismiss, onCheck = { viewModel.check() },
        onCancel = viewModel::cancelDownload,
        onDownload = viewModel::download, onInstall = { requestInstall() },
    )
}
