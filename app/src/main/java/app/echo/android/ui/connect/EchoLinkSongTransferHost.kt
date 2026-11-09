package app.echo.android.ui.connect

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.echo.android.EchoAndroidViewModel
import app.echo.android.EchoApplication
import app.echo.android.connect.EchoRemoteClient
import app.echo.android.feature.connect.EchoLinkSongTransferDialog
import app.echo.android.model.connect.EchoRemoteConnectionState

@Composable
internal fun rememberEchoLinkSongTransferAction(viewModel: EchoAndroidViewModel, client: EchoRemoteClient): () -> Unit {
    val application = LocalContext.current.applicationContext as EchoApplication
    val session = application.echoLinkSession.songTransfer
    var visible by remember { mutableStateOf(false) }
    val setupConnection by session.directorySetupConnection.collectAsStateWithLifecycle(initialValue = null)
    LaunchedEffect(setupConnection) {
        val connection = setupConnection ?: return@LaunchedEffect
        if (session.claimDirectoryGuide(connection)) visible = true
    }
    if (visible) {
        val state by session.state.collectAsStateWithLifecycle()
        val scan by viewModel.scanState.collectAsStateWithLifecycle()
        val choose = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(session::chooseDirectory) }
        val send = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { session.send(it) }
        EchoLinkSongTransferDialog(state, scan.isScanning,
            onSend = { send.launch(arrayOf("audio/*", "application/octet-stream")) }, onChoose = { choose.launch(session.directoryUri) },
            onScan = { session.directoryUri?.let(viewModel::refreshReceivedMusicFolder) },
            onClose = { visible = false })
    }
    return { if (client.status.value.connectionState == EchoRemoteConnectionState.Connected) visible = true }
}
