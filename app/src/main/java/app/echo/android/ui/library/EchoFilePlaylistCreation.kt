package app.echo.android.ui.library

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import app.echo.android.EchoAndroidViewModel
import app.echo.android.feature.library.FilePlaylistProgressDialog
import app.echo.android.model.library.EchoPlaylist
import kotlinx.coroutines.*

internal class FilePlaylistDocumentActions(val create: (String, Boolean) -> Unit,
    val append: (EchoPlaylist, Boolean) -> Unit)

@Composable
internal fun rememberFilePlaylistCreation(viewModel: EchoAndroidViewModel, onOpen: (EchoPlaylist) -> Unit): FilePlaylistDocumentActions {
    val context = LocalContext.current.applicationContext
    val resolver = context.contentResolver
    val scope = rememberCoroutineScope()
    val open by rememberUpdatedState(onOpen)
    var name by rememberSaveable { mutableStateOf("") }
    var targetId by rememberSaveable { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var error by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    fun grant(uri: Uri) { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    fun create(uris: List<Uri>, tree: Uri? = null) {
        if (uris.isEmpty() && tree == null) return
        val target = targetId
        val title = name
        running = true; error = false; progress = 0 to uris.size
        job = scope.launch {
            try {
                val sources = withContext(Dispatchers.IO) {
                    require(uris.size <= app.echo.android.data.FilePlaylistStore.MaxEntries)
                    requireFilePlaylistGrantBudget(resolver.persistedUriPermissions.mapTo(hashSetOf()) { it.uri.toString() },
                        if (tree != null) listOf(tree.toString()) else uris.map(Uri::toString))
                    if (tree != null) viewModel.filePlaylists.folderUris(tree) else uris.distinct()
                }
                require(sources.isNotEmpty()) { "No audio files selected" }
                val pending = withContext(Dispatchers.IO) {
                    val existing = target?.let { viewModel.filePlaylists.read(it).map { row -> row.contentUri } }.orEmpty()
                    app.echo.android.data.newFilePlaylistUris(existing, sources.map(Uri::toString)).map(Uri::parse)
                }
                val rows = if (pending.isEmpty()) emptyList() else viewModel.filePlaylists.readFiles(pending) { done, total ->
                    if (done == total || done % 10 == 0) scope.launch { progress = done to total }
                }
                currentCoroutineContext().ensureActive()
                withContext(Dispatchers.IO) {
                    val pendingUris = pending.toSet()
                    val readable = rows.mapTo(hashSetOf()) { it.contentUri }
                    val grants = if (tree != null) listOf(tree) else sources.filter { it !in pendingUris || it.toString() in readable }.distinct()
                    requireFilePlaylistGrantBudget(resolver.persistedUriPermissions.mapTo(hashSetOf()) { it.uri.toString() }, grants.map(Uri::toString))
                    grants.forEach(::grant)
                }
                if (target == null) {
                    val playlist = viewModel.filePlaylists.createFromTracks(title, rows)
                    running = false; open(playlist)
                } else {
                    val count = viewModel.filePlaylists.add(target, rows)
                    running = false
                    android.widget.Toast.makeText(context, context.resources.getQuantityString(
                        app.echo.android.feature.library.R.plurals.playlist_added, count, count), android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
        }
    }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { create(it) }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let { tree -> create(emptyList(), tree) } }
    if (running) FilePlaylistProgressDialog(progress, error, onDismiss = { job?.cancel(); running = false }, adding = targetId != null)
    return remember(files, folder) { FilePlaylistDocumentActions(create = { title, fromFolder ->
        if (!running) {
            name = title; targetId = null
            if (fromFolder) folder.launch(null) else files.launch(arrayOf("audio/*", "application/ogg", "application/octet-stream"))
        }
    }, append = { playlist, fromFolder ->
        if (!running && playlist.isFilePlaylist) {
            name = playlist.name; targetId = playlist.id
            if (fromFolder) folder.launch(null) else files.launch(arrayOf("audio/*", "application/ogg", "application/octet-stream"))
        }
    }) }
}
