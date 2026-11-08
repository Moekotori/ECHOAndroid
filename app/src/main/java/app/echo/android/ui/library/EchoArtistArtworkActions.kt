package app.echo.android.ui.library

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import app.echo.android.EchoAndroidViewModel
import app.echo.android.EchoApplication
import app.echo.android.feature.library.ArtistArtworkActions
import app.echo.android.feature.library.ArtistArtworkFeedback
import kotlinx.coroutines.*

@Composable
internal fun rememberArtistArtworkActions(viewModel: EchoAndroidViewModel): ArtistArtworkActions {
    val context = LocalContext.current.applicationContext
    val store = (context as EchoApplication).artistArtwork
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    fun execute(work: suspend () -> Unit) {
        job?.cancel(); busy = true; failed = false
        job = scope.launch {
            try { work(); busy = false }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
        }
    }
    val image = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { val artist = name; execute { store.setManual(artist, uri) } }
    }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) { val artist = name; execute {
            withContext(Dispatchers.IO) { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            store.matchFolder(artist, uri)
        } }
    }
    if (busy) ArtistArtworkFeedback(failed) { job?.cancel(); busy = false }
    return remember(viewModel, store, image, folder) { ArtistArtworkActions(
        choose = { artist -> name = artist; image.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        clear = { artist -> execute { store.clearManual(artist); store.discoverLocal(artist) } },
        matchFolder = { artist -> name = artist; folder.launch(null) },
        discover = store::discoverLocal,
    ) }
}
