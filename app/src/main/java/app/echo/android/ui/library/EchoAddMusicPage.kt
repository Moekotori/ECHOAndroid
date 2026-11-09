package app.echo.android.ui.library

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.echo.android.EchoAndroidViewModel
import app.echo.android.data.EchoAppSettings
import app.echo.android.data.LibraryFolderWatchPolicy
import app.echo.android.data.MediaStoreAudioFolder
import app.echo.android.feature.library.AddMusicFolder
import app.echo.android.feature.library.AddMusicScreen
import app.echo.android.feature.library.LibraryFolderScanDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import app.echo.android.feature.library.R as LibraryR

/** Wiring for local import only; the feature owns the page and the app owns system pickers. */
@Composable
internal fun EchoAddMusicPage(
    viewModel: EchoAndroidViewModel,
    settings: EchoAppSettings,
    onBack: () -> Unit,
    onScanAll: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    val trees by viewModel.watchedLibraryTrees.collectAsStateWithLifecycle(initialValue = null)
    val scanOptions by viewModel.libraryScanOptions.collectAsStateWithLifecycle(initialValue = null)
    val scan by viewModel.scanState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var actionBusy by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var selectedExcludedFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTree by rememberSaveable { mutableStateOf<String?>(null) }
    val chooseExcludedFolder = rememberExcludedFolderPicker { selectedExcludedFolder = it }
    val folderRows = remember(trees, configuration) {
        val formatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, configuration.locales[0])
        trees.orEmpty().map { tree ->
            AddMusicFolder(tree.uri, tree.documentId,
                tree.lastScanEpochMs.takeIf { it > 0 }?.let { formatter.format(Date(it)) },
                tree.rules.watch)
        }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            if (uri.authority == "com.android.externalstorage.documents" &&
                MediaStoreAudioFolder.fromTreeUri(uri)?.treeUri != null
            ) {
                selectedTree = uri.toString()
                actionError = null
            } else {
                actionError = resources.getString(LibraryR.string.scan_rules_folder_unsupported)
            }
        }
    }
    fun updateSettings(operation: suspend () -> Unit) {
        if (actionBusy) return
        actionBusy = true
        actionError = null
        scope.launch {
            try { operation() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { actionError = resources.getString(LibraryR.string.add_music_action_error) }
            finally { actionBusy = false }
        }
    }
    AddMusicScreen(
        initialOptions = scanOptions,
        scanState = scan, folders = folderRows, foldersLoaded = trees != null,
        autoRescan = settings.watchedFolderRescanEnabled,
        actionBusy = actionBusy, actionError = actionError, onBack = onBack,
        onScanFolder = {
            if (folderRows.size < LibraryFolderWatchPolicy.MaxTrees) folderPicker.launch(null)
            else actionError = resources.getString(LibraryR.string.scan_folder_limit, LibraryFolderWatchPolicy.MaxTrees)
        },
        onScanAll = onScanAll,
        onRescanFolder = { uri ->
            val current = viewModel.scanState.value
            if (!current.isScanning || current.isAutomatic) viewModel.refreshLibraryFolder(Uri.parse(uri))
        },
        onRemoveFolder = { uri -> updateSettings {
            val current = viewModel.scanState.value
            if (!current.isScanning || current.isAutomatic) viewModel.removeWatchedLibraryTree(uri)
        } },
        onEditFolder = { selectedTree = it; actionError = null },
        onSaveOptions = { options, onSaved -> updateSettings {
            viewModel.saveLibraryScanOptions(options)
            Toast.makeText(context, LibraryR.string.add_music_rules_saved, Toast.LENGTH_SHORT).show()
            onSaved()
        } },
        playlistDiscovery = { options, back ->
            EchoScannedPlaylistImports(viewModel, trees.orEmpty(), scan, options, back)
        },
        onCancelScan = viewModel::cancelScan,
        selectedExcludedFolder = selectedExcludedFolder,
        onChooseExcludedFolder = chooseExcludedFolder,
        onExcludedFolderConsumed = { selectedExcludedFolder = null },
    )
    selectedTree?.let { raw ->
        val uri = remember(raw) { Uri.parse(raw) }
        val folder = remember(uri) { MediaStoreAudioFolder.fromTreeUri(uri) }
        if (folder != null) {
            val existing = trees.orEmpty().firstOrNull {
                it.uri == raw || it.documentId == folder.documentId?.let(LibraryFolderWatchPolicy::normalizeDocumentId)
            }
            LibraryFolderScanDialog(
                path = folder.relativePathPrefix.trimEnd('/'),
                initialRules = existing?.rules,
                autoRescan = settings.watchedFolderRescanEnabled,
                enabled = !actionBusy && (!scan.isScanning || scan.isAutomatic),
                error = actionError,
                onChooseFolder = { folderPicker.launch(null) },
                onDismiss = { selectedTree = null },
                onSave = { rules ->
                    updateSettings {
                        if (existing == null) {
                            try {
                                withContext(Dispatchers.IO) {
                                    try {
                                        context.contentResolver.takePersistableUriPermission(
                                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                                        )
                                    } catch (_: SecurityException) {
                                        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                }
                            } catch (_: SecurityException) {
                                actionError = resources.getString(LibraryR.string.scan_folder_permission_error)
                                return@updateSettings
                            }
                        }
                        val savedUri = Uri.parse(existing?.uri ?: raw)
                        viewModel.saveLibraryFolderScanRules(savedUri, rules)
                        selectedTree = null
                        viewModel.refreshLibraryFolder(savedUri)
                    }
                },
            )
        }
    }
}
