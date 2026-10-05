package app.echo.android.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.echo.android.model.library.LibraryHealthStats
import app.echo.android.model.library.LibraryAudioProfile
import app.echo.android.model.library.LibraryLyricsInspection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@Composable
internal fun SettingsLibraryHealthContent(
    isActive: Boolean,
    isScanning: Boolean,
    trackCount: Int,
    durationMs: Long,
    sizeBytes: Long,
    onLoadHealth: suspend () -> LibraryHealthStats,
    onLoadAudioProfile: suspend () -> LibraryAudioProfile,
    onInspectLyrics: suspend ((LibraryLyricsInspection) -> Unit) -> Unit,
) {
    var health by remember { mutableStateOf<LibraryHealthStats?>(null) }
    var audio by remember { mutableStateOf<LibraryAudioProfile?>(null) }
    var audioFailed by remember { mutableStateOf(false) }
    var lyrics by remember { mutableStateOf<LibraryLyricsInspection?>(null) }
    var requested by remember { mutableStateOf(false) }
    var healthFailed by remember { mutableStateOf(false) }
    var lyricsFailed by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val load by rememberUpdatedState(onLoadHealth)
    val loadAudio by rememberUpdatedState(onLoadAudioProfile)
    val inspect by rememberUpdatedState(onInspectLyrics)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val visible = isActive && foreground

    // Only the visible category subscribes to work. Let an active scan finish first.
    LaunchedEffect(visible, isScanning, trackCount, durationMs, sizeBytes, refresh) {
        if (!visible || isScanning) return@LaunchedEffect
        delay(300L)
        healthFailed = false
        try { health = load() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { healthFailed = true }
        audioFailed = false
        try { audio = loadAudio() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { audioFailed = true }
    }
    LaunchedEffect(visible, isScanning, requested, trackCount, durationMs, sizeBytes) {
        if (!visible || isScanning || !requested) {
            requested = false
            return@LaunchedEffect
        }
        lyricsFailed = false
        lyrics = LibraryLyricsInspection(totalCount = trackCount)
        try { inspect { lyrics = it } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { lyricsFailed = true }
        finally { requested = false }
    }

    SettingsLibraryAudioProfile(audio, isScanning, audioFailed, onRefresh = { refresh++ })
    SettingsLibraryMetadataPanel(health, isScanning, healthFailed)
    SettingsLibraryLyricsPanel(lyrics, trackCount, requested, isScanning, lyricsFailed,
        onToggleInspection = { requested = !requested })
}
