package app.echo.android.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.EchoTrackVersions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoModalSheet
import app.echo.android.design.EchoSheetHeader
import app.echo.android.design.LocalEchoEffectivePerformanceMode

@Composable
fun TrackVersionsProvider(
    load: suspend (EchoTrack) -> EchoTrackVersions,
    save: suspend (EchoTrack, String?) -> Unit,
    content: @Composable () -> Unit,
) {
    var target by remember { mutableStateOf<EchoTrack?>(null) }
    var versions by remember { mutableStateOf<EchoTrackVersions?>(null) }
    var failed by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val currentLoad by rememberUpdatedState(load)
    val currentSave by rememberUpdatedState(save)
    LaunchedEffect(target, retry) {
        versions = null; failed = false
        val track = target ?: return@LaunchedEffect
        try { versions = currentLoad(track) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    val action = remember { { track: EchoTrack -> if (!saving) target = track; Unit } }
    CompositionLocalProvider(LocalTrackVersionsAction provides action, content = content)
    target?.let { anchor ->
        val loaded = versions
        if (loaded != null) TrackVersionsDialog(loaded, saving = saving, error = failed,
            onChoose = { id ->
                saving = true; failed = false
                scope.launch {
                    try { currentSave(anchor, id); versions = currentLoad(anchor) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { failed = true }
                    finally { saving = false }
                }
            }, onDismiss = { target = null }, onRetry = { retry++ })
        else TrackVersionsLoadingSheet(failed, onRetry = { retry++ }, onDismiss = { target = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackVersionsLoadingSheet(failed: Boolean, onRetry: () -> Unit, onDismiss: () -> Unit) {
    EchoModalSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        EchoSheetHeader(stringResource(R.string.track_versions_title), trailing = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.track_versions_close)) }
        })
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (failed) {
                Text(stringResource(R.string.track_versions_error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.duplicates_reload_versions)) }
            } else {
                if (!LocalEchoEffectivePerformanceMode.current.isLightweight) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.duplicates_loading_versions), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
