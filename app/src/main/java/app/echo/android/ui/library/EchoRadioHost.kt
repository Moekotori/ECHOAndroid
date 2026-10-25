package app.echo.android.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.echo.android.EchoAndroidViewModel
import app.echo.android.EchoApplication
import app.echo.android.feature.radio.RadioActions
import app.echo.android.feature.radio.RadioScreen
import app.echo.android.model.playback.EchoPlaybackState
import app.echo.android.model.playback.PlaybackControlsState
import kotlinx.coroutines.*

@Composable
internal fun EchoRadioHost(viewModel: EchoAndroidViewModel, currentId: String?, streamTitle: String?, controls: PlaybackControlsState, onBack: () -> Unit, openUrl: (String) -> Unit) {
    val context = LocalContext.current
    val controller = viewModel.radioController
    val saved by controller.stations.collectAsStateWithLifecycle()
    val visible by controller.visibleStations.collectAsStateWithLifecycle()
    val recent by controller.recent.collectAsStateWithLifecycle()
    val discovery by controller.discovery.collectAsStateWithLifecycle()
    val failed by controller.loadFailed.collectAsStateWithLifecycle()
    val radioPending by viewModel.broadcastPlayback.radioPending.collectAsStateWithLifecycle()
    val radioFailed by viewModel.broadcastPlayback.radioFailed.collectAsStateWithLifecycle()
    var fileFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val session = (context.applicationContext as EchoApplication).radioSession
    val revision by session.revision.collectAsStateWithLifecycle()
    LaunchedEffect(revision) { controller.reload() }
    LifecycleStartEffect(controller) {
        controller.foreground(true)
        onStopOrDispose { controller.foreground(false) }
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= 4 * 1024 * 1024)
                            output.write(buffer, 0, count)
                        }
                        val bytes = output.toByteArray()
                        bytes.toString(Charsets.UTF_8)
                    }
                }
                controller.importText(text); fileFailed = false
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { fileFailed = true }
        }
    }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            try {
                val text = controller.exportText()
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray(Charsets.UTF_8)) } }
                fileFailed = false
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { fileFailed = true }
        }
    }
    val exportPlaylist = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        if (uri != null) scope.launch {
            try {
                val text = controller.exportPlaylist()
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray(Charsets.UTF_8)) } }
                fileFailed = false
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { fileFailed = true }
        }
    }
    val actions = RadioActions(controller::browse, controller::closeDirectory, viewModel.broadcastPlayback::playRadio,
        viewModel::pause, controller::saveStation, controller::delete, controller::favorite, controller::clear,
        controller::importStations,
        controller::probe, controller::filterSaved,
        importFile = { importFile.launch(arrayOf("application/json", "audio/x-mpegurl", "audio/x-scpls", "text/*", "*/*")) },
        exportFile = { exportFile.launch("echo-radio-stations.json") },
        exportPlaylist = { exportPlaylist.launch("echo-radio-stations.m3u") },
        retrySaved = { fileFailed = false; if (radioFailed) viewModel.broadcastPlayback.retryRadio() else controller.reload() }, openUrl = openUrl)
    RadioScreen(saved, visible, recent, discovery, failed || fileFailed || radioFailed,
        radioPending || controls.state in listOf(EchoPlaybackState.Loading, EchoPlaybackState.Buffering), actions, currentId,
        controls.isPlaying, onBack, streamTitle = streamTitle, videoSurface = { url -> RadioVideoStage(url) })
}
