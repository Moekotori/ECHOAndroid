package app.echo.android.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import app.echo.android.EchoAndroidViewModel
import app.echo.android.feature.library.ScannedPlaylistsPanel
import app.echo.android.model.library.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun rememberScannedPlaylistImports(viewModel: EchoAndroidViewModel, trees: List<String>, scan: LibraryScanProgress): @Composable () -> Unit {
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<EchoPlaylistDiscoveryResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var imported by remember { mutableStateOf(emptySet<String>()) }
    var extraTree by remember { mutableStateOf<String?>(null) }
    var nonce by remember { mutableIntStateOf(0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) { extraTree = uri.toString(); nonce++ }
    }
    LaunchedEffect(trees, scan.isScanning, scan.phase, nonce) {
        if (scan.isScanning) { result = null; return@LaunchedEffect }
        if (nonce == 0 && scan.phase != LibraryScanPhase.Completed) return@LaunchedEffect
        busy = true; error = false; imported = emptySet()
        try { result = viewModel.mediaInspection.playlists.discover(trees + listOfNotNull(extraTree)) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = true }
        finally { busy = false }
    }
    return { ScannedPlaylistsPanel(result, busy || scan.isScanning, error, imported, onDiscover = { nonce++ },
        onChooseFolder = { picker.launch(null) }, onImport = { candidate ->
            if (!busy) {
                busy = true; error = false
                scope.launch {
                    try { viewModel.mediaInspection.playlists.import(candidate); imported = imported + candidate.uri }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { error = true }
                    finally { busy = false }
                }
            }
        }) }
}
