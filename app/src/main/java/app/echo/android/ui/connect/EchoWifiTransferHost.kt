package app.echo.android.ui.connect

import android.content.Context
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.echo.android.WifiTransferSession
import app.echo.android.feature.connect.WifiTransferDialog

@Composable
internal fun EchoWifiTransferHost(scanning: Boolean, onScan: (Uri) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val currentScan by rememberUpdatedState(onScan)
    var pendingScan by remember { mutableStateOf<Uri?>(null) }
    val session = remember { WifiTransferSession(context.applicationContext) { pendingScan = it } }
    LaunchedEffect(scanning, pendingScan) {
        if (!scanning) pendingScan?.let { pendingScan = null; currentScan(it) }
    }
    val state by session.state.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(session::choose) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(session, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) session.stop() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); session.close() }
    }
    val activity = remember(context) {
        var candidate: Context? = context
        while (candidate is android.content.ContextWrapper && candidate !is android.app.Activity) candidate = candidate.baseContext
        candidate as? android.app.Activity
    }
    DisposableEffect(state.phase, activity) {
        val flag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        val wasSet = activity?.window?.attributes?.flags?.and(flag) != 0
        if (state.phase == "running") activity?.window?.addFlags(flag)
        onDispose { if (!wasSet) activity?.window?.clearFlags(flag) }
    }
    WifiTransferDialog(state, onChoose = { launcher.launch(null) }, onStart = session::start,
        onStop = session::stop, onScan = session::scan, scanning = scanning || pendingScan != null,
        onClose = { session.close(); onClose() })
}

@Composable
internal fun rememberWifiTransferAction(viewModel: app.echo.android.EchoAndroidViewModel,
    options: app.echo.android.model.library.LibraryScanOptions,
    access: app.echo.android.EchoLocalNetworkPermission): () -> Unit {
    var visible by remember { mutableStateOf(false) }
    if (visible) {
        val scan by viewModel.scanState.collectAsStateWithLifecycle()
        EchoWifiTransferHost(scan.isScanning, onScan = { viewModel.refreshReceivedMusicFolder(it, options) },
            onClose = { visible = false })
    }
    return { access.run { visible = true } }
}
