package app.echo.android.feature.connect

import app.echo.android.design.EchoModalSheet
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.connect.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class PersonalStateSyncActions(
    val preview: suspend () -> EchoPersonalStatePreview,
    val apply: suspend (EchoPersonalStatePreview, Boolean) -> EchoPersonalStateResult,
    val resume: suspend (EchoPersonalResume) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PersonalStateSyncSheet(actions: PersonalStateSyncActions, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<EchoPersonalStatePreview?>(null) }
    var fromPc by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var unsupported by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<EchoPersonalStateResult?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var operation by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    LaunchedEffect(refresh) {
        busy = true; error = false; unsupported = false
        try { snapshot = actions.preview() }
        catch (e: CancellationException) { throw e }
        catch (_: EchoSyncUnsupportedException) { unsupported = true }
        catch (_: Exception) { error = true }
        finally { busy = false }
    }
    EchoModalSheet(onDismissRequest = { operation?.cancel(); onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text(stringResource(R.string.personal_sync_title), style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Text(stringResource(R.string.personal_sync_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (unsupported) item { Text(stringResource(R.string.sync_unsupported)) }
            if (error) item {
                Text(stringResource(R.string.personal_sync_error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { snapshot = null; result = null; refresh++ }, enabled = !busy) { Text(stringResource(R.string.sync_retry)) }
            }
            snapshot?.let { reviewed ->
                item {
                    Text(stringResource(R.string.personal_sync_phone_counts, reviewed.phone.history.size, reviewed.phone.bookmarks.size, reviewed.phone.lyricsOffsets.size))
                    Text(stringResource(R.string.personal_sync_pc_counts, reviewed.pc.history.size, reviewed.pc.bookmarks.size, reviewed.pc.lyricsOffsets.size))
                    Text(stringResource(R.string.personal_sync_limits), style = MaterialTheme.typography.bodySmall)
                }
                if (result == null) item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(fromPc, { fromPc = true }, enabled = !busy, label = { Text(stringResource(R.string.personal_sync_to_phone)) })
                        FilterChip(!fromPc, { fromPc = false }, enabled = !busy, label = { Text(stringResource(R.string.personal_sync_to_pc)) })
                    }
                    Text(stringResource(R.string.personal_sync_overwrite), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.personal_sync_bookmark_compatibility), style = MaterialTheme.typography.bodySmall)
                    Button(onClick = {
                        val direction = fromPc; busy = true; error = false
                        operation = scope.launch {
                            try { result = actions.apply(reviewed, direction) }
                            catch (e: CancellationException) { throw e }
                            catch (_: Exception) { error = true; snapshot = null }
                            finally { busy = false }
                        }
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.personal_sync_apply)) }
                }
                reviewed.pc.resume?.let { resume -> item {
                    HorizontalDivider()
                    Text(resume.track.title, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.personal_sync_resume_hint), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = {
                        busy = true; error = false
                        operation = scope.launch {
                            try { actions.resume(resume); onDismiss() }
                            catch (e: CancellationException) { throw e }
                            catch (_: Exception) { error = true }
                            finally { busy = false }
                        }
                    }, enabled = !busy) { Text(stringResource(R.string.personal_sync_resume)) }
                } }
            }
            result?.let { outcome -> item {
                Text(stringResource(R.string.personal_sync_result, outcome.historyAdded, outcome.bookmarksApplied, outcome.offsetsApplied, outcome.missing))
                if (outcome.unsupportedBookmarks > 0) Text(stringResource(R.string.personal_sync_unsupported_bookmarks, outcome.unsupportedBookmarks))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.sync_close)) }
            } }
        }
    }
}
