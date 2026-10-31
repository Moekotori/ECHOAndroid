package app.echo.android.ui.connect

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.echo.android.EchoAndroidViewModel
import app.echo.android.EchoLinkSongTransferSession
import app.echo.android.connect.EchoRemoteClient
import app.echo.android.feature.connect.EchoLinkSongTransferDialog
import app.echo.android.model.connect.EchoRemoteConnectionState
import app.echo.android.model.library.LibraryScanOptions

@Composable
internal fun rememberEchoLinkSongTransferAction(viewModel: EchoAndroidViewModel, client: EchoRemoteClient,
    options: LibraryScanOptions): () -> Unit {
    var visible by remember { mutableStateOf(false) }
    if (visible) {
        val context = LocalContext.current
        val session = remember(client) { EchoLinkSongTransferSession(context.applicationContext, client) }
        val state by session.state.collectAsStateWithLifecycle()
        val status by client.status.collectAsStateWithLifecycle()
        val scan by viewModel.scanState.collectAsStateWithLifecycle()
        val choose = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(session::chooseDirectory) }
        val send = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { session.send(it) }
        DisposableEffect(session) { onDispose { session.close() } }
        LaunchedEffect(session) { session.start() }
        EchoLinkSongTransferDialog(state, status.connectionState == EchoRemoteConnectionState.Connected, scan.isScanning,
            onSend = { send.launch(arrayOf("audio/*", "application/octet-stream")) }, onChoose = { choose.launch(session.directoryUri) },
            onStart = session::start, onStop = { session.stop() },
            onScan = { session.directoryUri?.let { viewModel.refreshReceivedMusicFolder(it, options) } },
            onClose = { session.close(); visible = false })
    }
    return { if (client.status.value.connectionState == EchoRemoteConnectionState.Connected) visible = true }
}
