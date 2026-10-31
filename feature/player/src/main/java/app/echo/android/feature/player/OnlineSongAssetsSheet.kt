package app.echo.android.feature.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.echo.android.design.ArtworkTile
import app.echo.android.design.EchoModalSheet
import app.echo.android.design.EchoSheetHeader
import app.echo.android.model.library.EchoTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class CoverSaveOutcome { Saved, LibraryOnly, Failed }

private enum class CoverOperation { Idle, Searching, Applying }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineSongAssetsSheet(
    track: EchoTrack,
    searchCover: suspend () -> String?,
    applyCover: suspend (String) -> CoverSaveOutcome,
    onLyrics: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sizing = playerDialogWindowSizing()
    var operation by remember(track.id) { mutableStateOf(CoverOperation.Idle) }
    var job by remember { mutableStateOf<Job?>(null) }
    var closing by remember { mutableStateOf(false) }
    var searched by remember(track.id) { mutableStateOf(false) }
    var failed by remember(track.id) { mutableStateOf(false) }
    var outcome by remember(track.id) { mutableStateOf<CoverSaveOutcome?>(null) }
    var preview by remember(track.id) { mutableStateOf<String?>(null) }
    val busy = operation != CoverOperation.Idle
    fun close(after: () -> Unit = {}) {
        if (closing) return
        closing = true
        job?.cancel()
        scope.launch {
            sheet.hide()
            onDismiss()
            after()
        }
    }
    EchoModalSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().heightIn(max = sizing.heightDp.dp).verticalScroll(rememberScrollState())) {
            EchoSheetHeader(title = stringResource(R.string.song_menu_online_assets), subtitle = track.title)
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.song_assets_cover_source), style = MaterialTheme.typography.bodyMedium)
                preview?.let { uri ->
                    ArtworkTile(uri, Modifier.size(180.dp), accent = MaterialTheme.colorScheme.onSurfaceVariant, cornerRadius = 12.dp, elevation = 0.dp)
                    Button(enabled = !busy && !closing && outcome == null, onClick = {
                        if (operation != CoverOperation.Idle || closing || outcome != null) return@Button
                        operation = CoverOperation.Applying; failed = false
                        job = scope.launch {
                            try {
                                val saved = applyCover(uri)
                                if (saved == CoverSaveOutcome.Failed) failed = true else outcome = saved
                            } catch (e: CancellationException) { throw e }
                            catch (_: Exception) { failed = true }
                            finally { operation = CoverOperation.Idle }
                        }
                    }) { Text(stringResource(if (outcome != null) R.string.song_assets_applied else R.string.song_assets_apply_cover)) }
                }
                Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(stringResource(if (operation == CoverOperation.Searching) R.string.song_assets_searching else R.string.song_assets_saving), style = MaterialTheme.typography.bodySmall)
                    }
                    when {
                        outcome == CoverSaveOutcome.LibraryOnly -> Text(stringResource(R.string.song_assets_library_only))
                        outcome == CoverSaveOutcome.Saved -> Text(stringResource(R.string.song_assets_saved))
                        failed -> Text(stringResource(R.string.song_assets_failed), color = MaterialTheme.colorScheme.error)
                        searched && preview == null && !busy -> Text(stringResource(R.string.song_assets_cover_missing))
                    }
                }
                OutlinedButton(enabled = !busy && !closing, onClick = {
                    if (operation != CoverOperation.Idle || closing) return@OutlinedButton
                    operation = CoverOperation.Searching; failed = false; outcome = null; preview = null
                    job = scope.launch {
                        try { preview = searchCover(); searched = true }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { failed = true }
                        finally { operation = CoverOperation.Idle }
                    }
                }) { Text(stringResource(if (searched || failed) R.string.song_assets_search_again else R.string.song_assets_search_cover)) }
                HorizontalDivider()
                TextButton(enabled = !closing && operation != CoverOperation.Applying, onClick = { close(onLyrics) }) {
                    Text(stringResource(R.string.song_assets_search_lyrics))
                }
                TextButton(enabled = !closing, onClick = { close() }) {
                    Text(stringResource(if (busy) R.string.song_assets_cancel else R.string.song_assets_done))
                }
            }
        }
    }
}
