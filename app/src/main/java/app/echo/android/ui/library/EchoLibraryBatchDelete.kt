package app.echo.android.ui.library

import android.app.Activity
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import app.echo.android.EchoAndroidViewModel
import app.echo.android.data.LibraryTrackDeletionPolicy
import app.echo.android.feature.library.LibraryBatchDeleteResult
import app.echo.android.model.library.EchoTrack
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun rememberLibraryBatchDelete(
    viewModel: EchoAndroidViewModel,
): suspend (List<EchoTrack>, Boolean) -> LibraryBatchDeleteResult {
    val context = LocalContext.current.applicationContext
    var pending by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
    var storageConsent by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        pending?.complete(it.resultCode == Activity.RESULT_OK)
    }
    val storageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        storageConsent?.complete(it)
    }
    DisposableEffect(Unit) { onDispose { pending?.cancel(); storageConsent?.cancel() } }
    return remember(viewModel, launcher, storageLauncher, context) {
        delete@{ tracks, deleteOriginals ->
            if (deleteOriginals && LibraryTrackDeletionPolicy.requiresLegacyStoragePermission(Build.VERSION.SDK_INT, tracks) &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                val granted = withContext(Dispatchers.Main.immediate) {
                    check(storageConsent == null && pending == null)
                    val response = CompletableDeferred<Boolean>()
                    storageConsent = response
                    try {
                        storageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        response.await()
                    } finally {
                        if (storageConsent === response) storageConsent = null
                    }
                }
                if (!granted) return@delete LibraryBatchDeleteResult(emptySet(), cancelled = true)
            }
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
