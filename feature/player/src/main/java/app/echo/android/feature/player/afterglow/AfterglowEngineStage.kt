package app.echo.android.feature.player.afterglow

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** Lets the shared scene controls reach the engine without making the stage recompose. */
internal class AfterglowEngineController {
    internal var host: AfterglowEngineHost? = null
    fun palette() = host?.deliver("{\"type\":\"echo:jizura:palette\"}")
    fun randomize() = host?.deliver("{\"type\":\"echo:jizura:randomize\"}")
}

private const val ReadyTimeoutMs = 12_000L
private const val HeartbeatMs = 500L
private const val JumpMs = 320L

/**
 * Draws the lyrics with echosteam's Jizura engine in an offline WebView. The engine owns planning and drawing;
 * this only hands it text, a presentation clock and render settings, and reports failure so the caller can
 * fall back to the native stage. The playback host remains the source of truth for position.
 */
@Composable
internal fun AfterglowEngineStage(
    plan: AfterglowPlan, look: String, title: String, artist: String, durationMs: Long, seed: Long,
    hostPosition: State<Long>, offsetMs: Long, playing: Boolean, speed: Float, active: Boolean,
    budget: AfterglowBudget, motion: Boolean, wordHighlight: Boolean, estimatedHighlight: Boolean,
    background: Int, description: String, controller: AfterglowEngineController,
    onFailed: () -> Unit, onSeed: (Long) -> Unit, modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var ready by remember { mutableStateOf(false) }
    val failed by rememberUpdatedState(onFailed)
    val seedChanged by rememberUpdatedState(onSeed)
    val host = remember(context) {
        AfterglowEngineHost(context, background or 0xFF000000.toInt()) { type, data ->
            when (type) {
                "echo:jizura:ready" -> ready = true
                "echo:jizura:error" -> failed()
                "echo:jizura:composition" -> data.optLong("seed", -1L).takeIf { it >= 0 }?.let { seedChanged(it) }
            }
        }
    }
    DisposableEffect(host) {
        controller.host = host
        onDispose { if (controller.host === host) controller.host = null; host.dispose() }
    }
    LaunchedEffect(host) {
        if (host.view == null) failed() else { delay(ReadyTimeoutMs); if (!ready) failed() }
    }
    val project by produceState<String?>(null, plan, look, title, artist, durationMs, seed, wordHighlight, estimatedHighlight) {
        value = withContext(Dispatchers.Default) {
            AfterglowEngineProject.build(plan, durationMs, title, artist, seed, look, wordHighlight, estimatedHighlight) { ensureActive() }
        }
        if (value == null) failed()
    }
    LaunchedEffect(ready, budget.framesPerSecond, motion) {
        if (ready) host.deliver(AfterglowEngineProject.renderSettings(budget.framesPerSecond, stepped = !motion, pressureReduced = false))
    }
    LaunchedEffect(ready, project) {
        val json = project
        if (ready && json != null) host.deliver(AfterglowEngineProject.projectMessage(json))
    }
    LaunchedEffect(host, active) { if (active) host.resume() else host.pause() }
    LaunchedEffect(ready, active, playing, speed, offsetMs, budget.framesPerSecond, motion) {
        if (!ready) return@LaunchedEffect
        val interval = if (active) 1000 / budget.framesPerSecond.coerceAtLeast(1) else null
        fun send(position: Long) = host.deliver(AfterglowEngineProject.clock(position, System.currentTimeMillis(), speed,
            playing && active, offsetMs, interval, motion))
        send(hostPosition.value)
        if (!active) return@LaunchedEffect
        launch {
            var previous = hostPosition.value
            var previousAt = SystemClock.elapsedRealtime()
            snapshotFlow { hostPosition.value }.collect { position ->
                val now = SystemClock.elapsedRealtime()
                val expected = previous + if (playing) ((now - previousAt) * speed).toLong() else 0L
                // A seek or a stall shows up as a jump between consecutive host samples.
                if (!playing || abs(position - expected) > JumpMs) send(position)
                previous = position; previousAt = now
            }
        }
        // The engine extrapolates between packets; the heartbeat only re-anchors it to the host.
        while (isActive && playing) { delay(HeartbeatMs); send(hostPosition.value) }
    }
    Box(modifier.semantics { contentDescription = description }) {
        host.view?.let { view -> AndroidView(factory = { view }, modifier = Modifier.fillMaxSize()) }
    }
}
