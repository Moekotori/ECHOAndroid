package app.echo.android.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.EchoTrackVersions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

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
                    try { currentSave(anchor, id); versions = loaded.copy(preferredTrackId = id) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { failed = true }
                    finally { saving = false }
                }
            }, onDismiss = { target = null })
        else AlertDialog(onDismissRequest = { target = null },
            title = { Text(stringResource(R.string.track_versions_title)) },
            text = { Column {
                if (failed) Text(stringResource(R.string.track_versions_error))
                else CircularProgressIndicator()
            } },
            confirmButton = { if (failed) TextButton(onClick = { retry++ }) { Text(stringResource(R.string.remote_playlist_retry)) } },
            dismissButton = { TextButton(onClick = { target = null }) { Text(stringResource(R.string.track_versions_close)) } })
    }
}
