package app.echo.android.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import app.echo.android.EchoAndroidViewModel
import app.echo.android.feature.library.LibraryExperienceActions
import app.echo.android.feature.library.LibraryRepairSheet
import kotlinx.coroutines.CompletableDeferred

/** App-owned document picker shared by playlist tools and the maintenance sheet. */
@Composable
internal fun rememberLibraryExperienceActions(
    viewModel: EchoAndroidViewModel,
    onScanFolder: () -> Unit,
): LibraryExperienceActions {
    var repairFileResult by remember { mutableStateOf<CompletableDeferred<Uri?>?>(null) }
    val repairFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        repairFileResult?.complete(uri)
    }
    val repairScanFolder = rememberUpdatedState(onScanFolder)
    return remember(viewModel, repairFilePicker) {
        val tools = viewModel.libraryExperience
        LibraryExperienceActions(
            preview = tools::preview, rule = tools::rule, save = tools::save,
            pin = tools::pin,
            inspect = tools::inspect, candidates = tools::candidates, relink = tools::relink,
            rescanFolder = { repairScanFolder.value() },
            chooseFile = {
                val result = CompletableDeferred<Uri?>()
                repairFileResult = result
                try {
                    repairFilePicker.launch(arrayOf("audio/*", "application/ogg", "application/flac"))
                    result.await()?.let { tools.readFile(it) }
                } finally { repairFileResult = null }
            },
        )
    }
}

@Composable
internal fun EchoLibraryRepairHost(
    viewModel: EchoAndroidViewModel,
    onScanFolder: () -> Unit,
    onDismiss: () -> Unit,
) {
    LibraryRepairSheet(rememberLibraryExperienceActions(viewModel, onScanFolder), onDismiss)
}
