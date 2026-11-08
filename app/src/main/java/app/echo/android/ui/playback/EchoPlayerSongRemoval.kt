package app.echo.android.ui.playback

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.echo.android.EchoAndroidViewModel
import app.echo.android.data.LibraryTrackDeletionPolicy
import app.echo.android.feature.library.LibraryBatchDeleteDialog
import app.echo.android.feature.library.LibraryHideTrackDialog
import app.echo.android.feature.library.R as LibraryR
import app.echo.android.model.library.EchoTrack
import app.echo.android.ui.library.rememberLibraryBatchDelete
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal class PlayerSongRemovalActions(
    val revision: Int,
    val hide: (EchoTrack) -> Unit,
    val delete: (EchoTrack) -> Unit,
)

/** Lives outside the current-track key, so a queue change cannot cancel its confirmation/result. */
@Composable
internal fun rememberPlayerSongRemoval(viewModel: EchoAndroidViewModel): PlayerSongRemovalActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val deleteTracks = rememberLibraryBatchDelete(viewModel)
    var hiding by remember { mutableStateOf<EchoTrack?>(null) }
    var deleting by remember { mutableStateOf<EchoTrack?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }

    hiding?.let { selected -> LibraryHideTrackDialog(selected, busy, failed,
        onDismiss = { if (!busy) hiding = null },
        onConfirm = {
            if (!busy) {
                busy = true; failed = false
                scope.launch {
                    try {
                        viewModel.hideLibraryTracks(listOf(selected.id))
                        revision++
                        hiding = null
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { failed = true }
                    finally { busy = false }
                }
            }
        },
    ) }
    deleting?.let { selected -> LibraryBatchDeleteDialog(
        count = 1,
        trackTitle = selected.title,
        canDeleteOriginals = LibraryTrackDeletionPolicy.canDeleteOriginal(selected),
        onDismiss = { deleting = null },
        onConfirm = { deleteOriginals ->
            if (!busy) {
                deleting = null; busy = true
                scope.launch {
                    try {
                        val result = deleteTracks(listOf(selected), deleteOriginals)
                        if (result.removedIds.isNotEmpty()) revision++
                        if (!result.cancelled || result.removedIds.isNotEmpty()) {
                            val removed = if (selected.id in result.removedIds) 1 else 0
                            val message = if (removed == 1) context.resources.getQuantityString(LibraryR.plurals.batch_delete_done, 1, 1)
                                else context.getString(LibraryR.string.batch_delete_partial, 0, 1)
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) {
                        Toast.makeText(context, LibraryR.string.hidden_track_failed, Toast.LENGTH_SHORT).show()
                    } finally { busy = false }
                }
            }
        },
    ) }
    return PlayerSongRemovalActions(revision,
        hide = { track -> if (!busy && hiding == null && deleting == null) { failed = false; hiding = track } },
        delete = { track -> if (!busy && hiding == null && deleting == null) deleting = track },
    )
}
