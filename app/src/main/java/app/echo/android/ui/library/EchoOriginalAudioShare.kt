package app.echo.android.ui.library

import android.content.Intent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.echo.android.data.*
import app.echo.android.feature.library.OriginalAudioShareDialog
import app.echo.android.lyrics.*
import app.echo.android.model.library.CueSheetPolicy
import app.echo.android.model.library.EchoTrack
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicReference
import app.echo.android.feature.library.R as LibraryR

private data class AudioShareRequest(val tracks: List<EchoTrack>, val skipped: Int, val wholeOriginal: Boolean)

/** Each surface owns its dialog/job; leaving the surface cancels unfinished preparation. */
@Composable
internal fun rememberOriginalAudioShare(): (List<EchoTrack>) -> Unit {
    val context = LocalContext.current
    val app = context.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val preparer = remember(app) { OriginalAudioSharePreparer(app) }
    var request by remember { mutableStateOf<AudioShareRequest?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<PreparedOriginalAudioShare?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun discardPrepared() {
        val old = result
        result = null
        if (old != null) scope.launch { old.discard() }
    }
    fun launchShare(prepared: PreparedOriginalAudioShare) {
        if (prepared.expired) {
            discardPrepared()
            error = context.getString(LibraryR.string.audio_share_expired)
            return
        }
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            error = context.getString(LibraryR.string.audio_share_return)
            return
        }
        try {
            context.startActivity(Intent.createChooser(prepared.intent(), context.getString(LibraryR.string.audio_share_title)))
            result = null // Keep granted attachments alive for the receiving app.
            request = null
        } catch (_: Exception) { error = context.getString(LibraryR.string.audio_share_launch_failed) }
    }
    request?.let { selected ->
        OriginalAudioShareDialog(
            count = selected.tracks.size, skipped = selected.skipped, wholeOriginal = selected.wholeOriginal,
            busy = busy, progress = progress, missingLyrics = result?.missingLyrics ?: 0,
            missingCovers = result?.missingCovers ?: 0, prepared = result != null, error = error,
            onOptionsChanged = { discardPrepared(); error = null },
            onDismiss = { job?.cancel(); discardPrepared(); request = null },
            onShare = { lyrics, covers ->
                val existing = result
                if (existing != null) launchShare(existing)
                else if (!busy) {
                    busy = true; progress = 0; error = null
                    job = scope.launch {
                        val pending = AtomicReference<PreparedOriginalAudioShare?>()
                        try {
                            val prepared = withContext(Dispatchers.IO) {
                                val sourceOrder = if (lyrics) EchoSettingsStore(app).appSettings.first().lyricsOptions.sourceOrder else emptyList()
                                val loader = OriginalAudioShareLyricsLoader(app, sourceOrder)
                                preparer.prepare(selected.tracks, lyrics, covers, loadLyrics = loader::load,
                                    onProgress = { done -> withContext(Dispatchers.Main.immediate) { progress = done } })
                                    .also(pending::set)
                            }
                            result = prepared
                            pending.set(null)
                            if (prepared.missingLyrics == 0 && prepared.missingCovers == 0) launchShare(prepared)
                        } catch (cancelled: CancellationException) {
                            withContext(NonCancellable) { pending.getAndSet(null)?.discard() }
                            throw cancelled
                        }
                        catch (failure: OriginalAudioShareReadException) { error = context.getString(LibraryR.string.audio_share_read_failed, failure.trackTitle) }
                        catch (_: OriginalAudioShareCacheFullException) { error = context.getString(LibraryR.string.audio_share_cache_full) }
                        catch (_: Exception) { error = context.getString(LibraryR.string.audio_share_failed) }
                        finally { busy = false }
                    }
                }
            },
        )
    }
    return remember {
        { tracks ->
            if (request == null && !busy) {
                val selected = tracks.distinctBy(EchoTrack::id)
                if (selected.isNotEmpty()) {
                    val local = selected.filter(OriginalAudioSharePolicy::supports).take(OriginalAudioSharePolicy.MaxTracks)
                    error = null; progress = 0
                    request = AudioShareRequest(local, selected.size - local.size,
                        local.any { CueSheetPolicy.isCueTrackId(it.id) || it.clipStartMs > 0 || it.clipEndMs > 0 })
                }
            }
        }
    }
}
