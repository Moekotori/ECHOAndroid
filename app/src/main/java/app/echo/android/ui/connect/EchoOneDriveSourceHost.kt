package app.echo.android.ui.connect

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.echo.android.data.OneDriveFolder
import app.echo.android.data.OneDriveFolders
import app.echo.android.data.OneDriveLibrary
import app.echo.android.feature.connect.OneDriveSource
import app.echo.android.feature.connect.OneDriveSourceUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun EchoOneDriveSourceHost(
    library: OneDriveLibrary,
    expanded: Boolean,
    onExpand: () -> Unit,
    scanBusy: Boolean,
    onSync: (OneDriveFolder) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connectedFolder by library.folder.collectAsStateWithLifecycle()
    var browse by remember { mutableStateOf<FolderBrowser?>(null) }
    var operation by remember { mutableStateOf<Job?>(null) }
    var failed by remember { mutableStateOf(false) }
    var canCancel by remember { mutableStateOf(false) }

    fun launch(cancellable: Boolean = true, action: suspend () -> Unit) {
        if (operation != null) return
        failed = false
        canCancel = cancellable
        val job = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                failed = true
            } finally {
                operation = null
            }
        }
        operation = job
        job.start()
    }

    fun list(path: List<OneDriveFolder>, cursors: List<String?> = listOf(null)) {
        launch {
            val page = library.folders(path.last(), cursors.last())
            browse = FolderBrowser(path, cursors, page)
        }
    }

    val browser = browse
    OneDriveSource(
        state = OneDriveSourceUiState(
            available = library.isAvailable,
            connected = connectedFolder != null,
            loading = operation != null,
            cancellable = canCancel,
            failed = failed,
            browsing = browser != null,
            currentFolder = browser?.path?.last()?.name,
            directories = browser?.page?.folders?.map { it.name }.orEmpty(),
            canGoUp = (browser?.path?.size ?: 0) > 1,
            hasPreviousPage = (browser?.cursors?.size ?: 0) > 1,
            hasNextPage = browser?.page?.next?.let { it !in browser.cursors && browser.cursors.size < 200 } == true,
        ),
        expanded = expanded,
        onExpand = onExpand,
        scanBusy = scanBusy,
        onConnect = {
            launch {
                library.connect { address -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(address))) }
                browse = null
            }
        },
        onCancel = { operation?.cancel() },
        onDisconnect = {
            launch(cancellable = false) {
                library.disconnect()
                browse = null
            }
        },
        onBrowse = {
            val root = OneDriveFolder(null, "OneDrive")
            val saved = connectedFolder
            list(if (saved?.id == null) listOf(root) else listOf(root, saved))
        },
        onSelectDirectory = { index ->
            if (browser != null && browser.path.size < 100) {
                browser.page.folders.getOrNull(index)?.let { list(browser.path + it) }
            }
        },
        onGoUp = { if (browser != null && browser.path.size > 1) list(browser.path.dropLast(1)) },
        onPreviousPage = { if (browser != null && browser.cursors.size > 1) list(browser.path, browser.cursors.dropLast(1)) },
        onNextPage = {
            browser?.page?.next?.let { if (it !in browser.cursors && browser.cursors.size < 200) list(browser.path, browser.cursors + it) }
        },
        onSync = { browser?.path?.lastOrNull()?.let(onSync) },
    )
}

private class FolderBrowser(val path: List<OneDriveFolder>, val cursors: List<String?>, val page: OneDriveFolders)
