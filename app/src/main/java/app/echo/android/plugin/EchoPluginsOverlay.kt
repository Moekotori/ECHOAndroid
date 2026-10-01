package app.echo.android.plugin

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Velocity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.echo.android.EchoAndroidViewModel
import app.echo.android.design.EchoPageOverlay
import app.echo.android.feature.plugins.PluginsHost
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@Composable
internal fun EchoPluginsOverlay(
    visible: Boolean,
    playback: EchoAndroidViewModel,
    onVisibleChange: (Boolean) -> Unit,
) {
    val plugins = viewModel<EchoPluginViewModel>()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val main = remember { Handler(Looper.getMainLooper()) }
    var notice by remember { mutableStateOf<PluginInstallResult?>(null) }
    var focusPluginId by remember { mutableStateOf<String?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(playback, plugins) {
        plugins.bridge.snapshotProvider = { playback.pluginSnapshot() }
        plugins.bridge.transportHandler = { command ->
            main.post {
                when (command) {
                    TransportCommand.Play -> if (!playback.playbackStatus.value.isPlaying) playback.playPause()
                    TransportCommand.Pause -> playback.pause()
                    TransportCommand.Next -> playback.skipNext()
                    TransportCommand.Previous -> playback.skipPrevious()
                    is TransportCommand.Seek -> playback.seekTo(command.positionMs)
                }
            }
        }
        // The plugin thread calls this. Do not wait for the main thread from here.
        plugins.bridge.searchHandler = { query ->
            runBlocking {
                withTimeout(3_000) {
                    playback.searchLocalLibrary(query).tracks.take(20).map { track ->
                        LibraryTrackHit(
                            id = track.id,
                            title = track.title,
                            artist = track.artist,
                            album = track.album.orEmpty(),
                        )
                    }
                }
            }
        }
        onDispose {
            plugins.bridge.snapshotProvider = { PlaybackSnapshot() }
            plugins.bridge.transportHandler = {}
            plugins.bridge.searchHandler = { emptyList() }
        }
    }
    val wantsPlayback by plugins.wantsPlayback.collectAsStateWithLifecycle()
    LaunchedEffect(wantsPlayback, playback, lifecycle) {
        if (!wantsPlayback) return@LaunchedEffect
        val updates = launch {
            playback.playbackStatus.map { it.pluginIdentity() }.distinctUntilChanged().collect {
                plugins.dispatch(playback.pluginSnapshot())
            }
        }
        try {
            while (isActive) {
                delay(2_000)
                val status = playback.playbackStatus.value
                if (!status.isPlaying) continue
                if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) continue
                plugins.dispatch(playback.pluginSnapshot())
            }
        } finally {
            updates.cancel()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { readPluginZip(context, uri) }
            if (bytes == null) {
                notice = PluginInstallResult.Rejected(PluginRejectReason.TooLarge)
            } else {
                plugins.install(bytes) { result ->
                    notice = result
                    if (result is PluginInstallResult.Installed) focusPluginId = result.id
                }
            }
        }
    }
    EchoPageOverlay(
        visible = visible,
    ) {
        val snapshot by plugins.snapshot.collectAsStateWithLifecycle()
        val blockPager = remember {
            object : NestedScrollConnection {
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                    Offset(available.x, 0f)
                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                    Velocity(available.x, 0f)
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .nestedScroll(blockPager)
                .background(MaterialTheme.colorScheme.background)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .navigationBarsPadding(),
        ) {
            PluginsHost(
                isActive = visible,
                snapshot = snapshot,
                notice = notice,
                onImport = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                onInstallSample = {
                    plugins.installSample { result ->
                        notice = result
                        if (result is PluginInstallResult.Installed) focusPluginId = result.id
                    }
                },
                onBack = { onVisibleChange(false) },
                onEnable = plugins::setEnabled,
                onGrant = plugins::setGrant,
                onDelete = plugins::delete,
                onOpenPage = plugins::openPage,
                onAction = plugins::performAction,
                onDismissNotice = { notice = null },
                focusPluginId = focusPluginId,
                onFocusPluginConsumed = { focusPluginId = null },
            )
        }
    }
}

private fun app.echo.android.model.playback.EchoPlaybackStatus.pluginIdentity() =
    listOf(track?.title, track?.artist, track?.album, isPlaying, durationMs)

private fun EchoAndroidViewModel.pluginSnapshot(): PlaybackSnapshot {
    val status = playbackStatus.value
    return PlaybackSnapshot(
        title = status.track?.title.orEmpty(),
        artist = status.track?.artist.orEmpty(),
        album = status.track?.album.orEmpty(),
        playing = status.isPlaying,
        positionMs = playbackPosition.value.positionMs.coerceAtLeast(0L),
        durationMs = status.durationMs.coerceAtLeast(0L),
    )
}

private fun readPluginZip(context: Context, uri: Uri): ByteArray? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > PluginLimits.MaxZipBytes) return@use null
            output.write(buffer, 0, read)
        }
        output.toByteArray()
    } ?: ByteArray(0)
}.getOrDefault(ByteArray(0))
