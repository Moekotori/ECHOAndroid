package app.echo.android.appearance

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import app.echo.android.data.DecodedAppearancePack
import app.echo.android.data.EchoAppearancePackStore
import app.echo.android.feature.player.AppearancePackControls
import app.echo.android.feature.player.AppearancePackNotice
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** Document picker and lifecycle wiring; presentation stays in feature:player. */
@Composable
internal fun EchoAppearancePacks(snackbar: SnackbarHostState) {
    val context = LocalContext.current.applicationContext
    val store = remember(context) { EchoAppearancePackStore(context) }
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<DecodedAppearancePack?>(null) }
    var busy by remember { mutableStateOf(false) }
    var canUndo by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<AppearancePackNotice?>(null) }
    var exportName by remember { mutableStateOf("ECHO") }
    LaunchedEffect(store) { canUndo = store.canUndo() }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true; notice = null
            try { preview = store.read(uri) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { notice = AppearancePackNotice.ReadFailed }
            finally { busy = false }
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            busy = true; notice = null
            try { store.export(uri, exportName); notice = AppearancePackNotice.Exported }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { notice = AppearancePackNotice.WriteFailed }
            finally { busy = false }
        }
    }
    AppearancePackControls(preview?.appearance, preview?.background, busy, canUndo, notice, snackbar,
        onImport = { importer.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")) },
        onExport = { name -> exportName = name; exporter.launch(name.replace(Regex("[^\\p{L}\\p{N} _-]"), "_") + ".echoappearance.zip") },
        onApply = { preview?.let { pack -> scope.launch {
            busy = true; notice = null
            try { store.apply(pack); preview = null; canUndo = true; notice = AppearancePackNotice.Applied }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { notice = AppearancePackNotice.ApplyFailed }
            finally { busy = false }
        } } },
        onDismiss = { preview = null },
        onUndo = { scope.launch {
            busy = true; notice = null
            try { if (store.undo()) notice = AppearancePackNotice.Restored; canUndo = store.canUndo() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { notice = AppearancePackNotice.ApplyFailed }
            finally { busy = false }
        } })
}
