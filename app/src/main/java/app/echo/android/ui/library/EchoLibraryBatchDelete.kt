package app.echo.android.ui.library

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.echo.android.EchoAndroidViewModel
import app.echo.android.feature.library.LibraryBatchDeleteResult
import app.echo.android.model.library.EchoTrack
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun rememberLibraryBatchDelete(
    viewModel: EchoAndroidViewModel,
): suspend (List<EchoTrack>, Boolean) -> LibraryBatchDeleteResult {
    var pending by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        pending?.complete(it.resultCode == Activity.RESULT_OK)
    }
    DisposableEffect(Unit) { onDispose { pending?.cancel() } }
    return remember(viewModel, launcher) {
        { tracks, deleteOriginals ->
            val result = viewModel.batchDeleteTracks(tracks, deleteOriginals) { sender ->
                withContext(Dispatchers.Main.immediate) {
                    check(pending == null)
                    val response = CompletableDeferred<Boolean>()
                    pending = response
                    try {
                        launcher.launch(IntentSenderRequest.Builder(sender).build())
                        response.await()
                    } finally {
                        if (pending === response) pending = null
                    }
                }
            }
            LibraryBatchDeleteResult(result.removedIds, result.cancelled)
        }
    }
}
