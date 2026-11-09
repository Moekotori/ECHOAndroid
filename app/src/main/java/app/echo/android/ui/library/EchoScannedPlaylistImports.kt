package app.echo.android.ui.library

import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import app.echo.android.EchoAndroidViewModel
import app.echo.android.data.WatchedLibraryTree
import app.echo.android.data.withFolderIgnoredPaths
import app.echo.android.feature.library.ScannedPlaylistsScreen
import app.echo.android.model.library.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun EchoScannedPlaylistImports(
    viewModel: EchoAndroidViewModel,
    trees: List<WatchedLibraryTree>,
    scan: LibraryScanProgress,
    options: LibraryScanOptions,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<EchoPlaylistDiscoveryResult?>(null) }
    var searching by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf(false) }
    var imported by remember { mutableStateOf(emptySet<String>()) }
    var chosenTree by rememberSaveable { mutableStateOf<String?>(null) }
    var chosenFolderName by rememberSaveable { mutableStateOf<String?>(null) }
    var request by remember { mutableIntStateOf(0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            chosenTree = uri.toString()
            chosenFolderName = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrDefault("")
                .substringAfter(':').trimEnd('/').substringAfterLast('/').takeIf(String::isNotBlank)
            request++
        }
    }
    val importPlaylist = rememberPlaylistImport(viewModel)
    val canSearch = chosenTree != null || trees.isNotEmpty()
    LaunchedEffect(chosenTree, canSearch, scan.isScanning, request) {
        result = null
        searchError = false
        importError = false
        imported = emptySet()
        if (scan.isScanning || !canSearch) return@LaunchedEffect
        searching = true
        try {
            val selected = chosenTree
            result = withContext(Dispatchers.IO) {
                viewModel.mediaInspection.playlists.discover(
                    if (selected == null) trees.map { it.uri } else listOf(selected),
                    options.withFolderIgnoredPaths(trees),
                )
            }
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { searchError = true }
        finally { searching = false }
    }
    ScannedPlaylistsScreen(
        result = result,
        searching = searching,
        waitingForScan = scan.isScanning,
        importing = importing,
        searchError = searchError,
        importError = importError,
        imported = imported,
        canSearch = canSearch,
        customFolder = chosenTree != null,
        folderName = chosenFolderName,
        hasSavedFolders = trees.isNotEmpty(),
        onBack = onBack,
        onRefresh = { request++ },
        onChooseFolder = { picker.launch(null) },
        onChooseFile = importPlaylist,
        onUseSavedFolders = {
            chosenTree = null
            chosenFolderName = null
        },
        onDismissImportError = { importError = false },
        onImport = { candidate ->
            if (!importing && !searching && !scan.isScanning) {
                importing = true
                importError = false
                scope.launch {
                    try { viewModel.mediaInspection.playlists.import(candidate); imported = imported + candidate.selectionKey }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { importError = true }
                    finally { importing = false }
                }
            }
        },
    )
}
