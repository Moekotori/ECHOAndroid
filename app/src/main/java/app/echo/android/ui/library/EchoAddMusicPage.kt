package app.echo.android.ui.library

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.echo.android.EchoAndroidViewModel
import app.echo.android.data.EchoAppSettings
import app.echo.android.feature.library.AddMusicFolder
import app.echo.android.feature.library.AddMusicScreen
import app.echo.android.model.library.LibraryScanOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import app.echo.android.feature.library.R as LibraryR

/** Wiring for local import only; the feature owns the page and the app owns system pickers. */
@Composable
internal fun EchoAddMusicPage(
    viewModel: EchoAndroidViewModel,
    settings: EchoAppSettings,
    onBack: () -> Unit,
    onScanFolder: (LibraryScanOptions) -> Unit,
    onScanAll: (LibraryScanOptions) -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val trees by viewModel.watchedLibraryTrees.collectAsStateWithLifecycle(initialValue = null)
    val scan by viewModel.scanState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var actionBusy by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    val folderRows = remember(trees, configuration) {
        val formatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, configuration.locales[0])
        trees.orEmpty().map { tree ->
            AddMusicFolder(tree.uri, tree.documentId,
                tree.lastScanEpochMs.takeIf { it > 0 }?.let { formatter.format(Date(it)) })
        }
    }
    fun updateSettings(failureMessage: Int = LibraryR.string.add_music_action_error, operation: suspend () -> Unit) {
        if (actionBusy) return
        actionBusy = true
        actionError = null
        scope.launch {
            try { operation() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { actionError = context.getString(failureMessage) }
            finally { actionBusy = false }
        }
    }
    val playlistPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) updateSettings(LibraryR.string.add_music_playlist_error) {
            val playlist = viewModel.importM3uPlaylistWithResult(uri)
            if (playlist == null) actionError = context.getString(LibraryR.string.add_music_playlist_no_match)
            else Toast.makeText(context, context.getString(LibraryR.string.add_music_playlist_imported, playlist.name),
                Toast.LENGTH_SHORT).show()
        }
    }
    AddMusicScreen(
        initialOptions = settings.libraryScanOptions,
        scanState = scan, folders = folderRows, foldersLoaded = trees != null,
        autoRescan = settings.watchedFolderRescanEnabled,
        actionBusy = actionBusy, actionError = actionError, onBack = onBack,
        onScanFolder = onScanFolder, onScanAll = onScanAll,
        onRescanFolder = { uri, options ->
            if (!viewModel.scanState.value.isScanning) viewModel.refreshLibraryFolder(Uri.parse(uri), options)
        },
        onRemoveFolder = { uri -> updateSettings {
            if (!viewModel.scanState.value.isScanning) viewModel.removeWatchedLibraryTree(uri)
        } },
        onAutoRescanChange = viewModel::setWatchedFolderRescanEnabled,
        onSaveOptions = { options -> updateSettings {
            viewModel.saveLibraryScanOptions(options)
            Toast.makeText(context, LibraryR.string.add_music_rules_saved, Toast.LENGTH_SHORT).show()
        } },
        onImportPlaylist = { playlistPicker.launch(arrayOf("audio/x-mpegurl", "audio/mpegurl",
            "application/vnd.apple.mpegurl", "application/x-mpegurl", "text/plain", "*/*")) },
        onCancelScan = viewModel::cancelScan,
    )
}
