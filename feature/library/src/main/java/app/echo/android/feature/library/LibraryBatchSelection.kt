package app.echo.android.feature.library

import androidx.compose.foundation.layout.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.library.*
import app.echo.android.design.EchoMotion
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.LocalEchoContentMaxWidth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class LibraryBatchActions(
    val playlists: List<EchoPlaylist>,
    val favorites: suspend (List<String>, Boolean) -> Unit,
    val addToPlaylist: suspend (String, List<String>) -> Unit,
    val queue: suspend (List<EchoTrack>, Int) -> Unit,
    val tags: suspend (List<String>, EchoBatchTagPatch, (Int) -> Unit) -> EchoBatchResult,
    val writeAccess: suspend (List<EchoTrack>) -> Boolean = { true },
    val delete: (suspend (List<EchoTrack>, Boolean) -> LibraryBatchDeleteResult)? = null,
    val canDeleteOriginals: (List<EchoTrack>) -> Boolean = { false },
)

internal class LibraryBatchSelection {
    val selected = mutableStateMapOf<String, EchoTrack>()
    private val orderedIds = ArrayList<String>()
    var active by mutableStateOf(false)
        private set
    var locked by mutableStateOf(false)
    var deletingIds by mutableStateOf<Set<String>>(emptySet())
        private set
    fun beginDeletion(ids: Set<String>) {
        check(!locked)
        require(ids.size <= Limit)
        deletingIds = if (active) ids.intersect(selected.keys) else ids.toSet()
        locked = true
    }
    fun finishDeletion() {
        deletingIds = emptySet()
        locked = false
    }
    fun start(track: EchoTrack) {
        if (locked) return
        active = true
        if (track.id in selected) selected[track.id] = track
        else if (selected.size < Limit) {
            orderedIds.add(track.id)
            selected[track.id] = track
        }
    }
    fun toggle(track: EchoTrack) {
        if (!active || locked) return
        if (track.id in selected) {
            selected.remove(track.id)
            orderedIds.remove(track.id)
        } else if (selected.size < Limit) {
            orderedIds.add(track.id)
            selected[track.id] = track
        }
    }
    fun tracks(): List<EchoTrack> = orderedIds.mapNotNull { selected[it] }
    fun remove(ids: Set<String>) {
        if (locked) return
        ids.forEach(selected::remove)
        orderedIds.removeAll(ids)
    }
    fun clear() {
        if (locked) return
        selected.clear()
        orderedIds.clear()
    }
    fun exit() {
        if (locked) return
        clear()
        active = false
    }
    companion object { const val Limit = 200 }
}
internal val LocalLibraryBatchSelection = staticCompositionLocalOf<LibraryBatchSelection?> { null }
internal val LocalLibraryDeleteTrack = staticCompositionLocalOf<((EchoTrack) -> Unit)?> { null }

internal fun LibraryBatchSelection.ownsBack(pageInteractive: Boolean, modalOpen: Boolean): Boolean =
    pageInteractive && active && !modalOpen

@Composable
fun LibraryBatchProvider(actions: LibraryBatchActions, pageInteractive: Boolean = true, content: @Composable () -> Unit) {
    val selection = remember { LibraryBatchSelection() }
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var choosingPlaylist by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<List<EchoTrack>?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    fun delete(tracks: List<EchoTrack>, deleteOriginals: Boolean) {
        val operation = actions.delete ?: return
        if (busy || tracks.isEmpty()) return
        deleting = null
        busy = true; selection.beginDeletion(tracks.map(EchoTrack::id).toSet()); error = false
        scope.launch {
            try {
                val result = operation(tracks, deleteOriginals)
                val removed = result.removedIds.intersect(tracks.map(EchoTrack::id).toSet())
                selection.locked = false
                selection.remove(removed)
                if (selection.selected.isEmpty()) selection.exit()
                if (!result.cancelled || removed.isNotEmpty()) {
                    val remaining = tracks.size - removed.size
                    val message = if (remaining == 0) context.resources.getQuantityString(R.plurals.batch_delete_done, removed.size, removed.size)
                        else context.getString(R.string.batch_delete_partial, removed.size, remaining)
                    snackbar.currentSnackbarData?.dismiss()
                    scope.launch { snackbar.showSnackbar(message) }
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { error = true }
            finally { busy = false; selection.finishDeletion() }
        }
    }
    fun operate(successMessage: String? = null, operation: suspend (List<EchoTrack>) -> Unit) {
        if (busy || selection.selected.isEmpty()) return
        val tracks = selection.tracks()
        busy = true; selection.locked = true; error = false
        scope.launch {
            try {
                operation(tracks)
                selection.locked = false
                selection.exit()
                if (successMessage != null) {
                    snackbar.currentSnackbarData?.dismiss()
                    scope.launch { snackbar.showSnackbar(successMessage) }
                }
            }
            catch (e: CancellationException) { throw e } catch (_: Exception) { error = true }
            finally { busy = false; selection.locked = false }
        }
    }
    val ownsBack = selection.ownsBack(pageInteractive, editing || choosingPlaylist || deleting != null)
    val latestOwnsBack by rememberUpdatedState(ownsBack)
    BackHandler(enabled = ownsBack) {
        if (latestOwnsBack && !busy) { selection.exit(); error = false }
    }
    val requestTrackDeletion: ((EchoTrack) -> Unit)? = remember(actions.delete, selection) {
        if (actions.delete != null) {
            { track ->
                if (!busy && !selection.locked && deleting == null) deleting = listOf(track)
            }
        } else null
    }
    CompositionLocalProvider(
        LocalLibraryBatchSelection provides selection,
        LocalLibraryDeleteTrack provides requestTrackDeletion,
    ) {
        val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            content()
            AnimatedVisibility(
                visible = selection.active,
                enter = if (lightweight) EnterTransition.None else EchoMotion.overlayEnter(enterMs = 260, fadeMs = 180),
                exit = if (lightweight) ExitTransition.None else EchoMotion.overlayExit(exitMs = 180, fadeMs = 120),
                modifier = Modifier.widthIn(max = LocalEchoContentMaxWidth.current)
                    .fillMaxWidth().statusBarsPadding(),
            ) {
                LibraryBatchToolbar(
                    count = selection.selected.size,
                    busy = busy,
                    error = error,
                    canEditTags = selection.selected.isNotEmpty() && selection.selected.values.all { it.canEditFileTags() },
                    canDelete = actions.delete != null,
                    deleting = selection.deletingIds.isNotEmpty(),
                    onExit = { selection.exit(); error = false },
                    onClear = { selection.clear(); error = false },
                    onCommand = { command ->
                        when (command) {
                            LibraryBatchCommand.Favorite -> operate { actions.favorites(it.map(EchoTrack::id), true) }
                            LibraryBatchCommand.Unfavorite -> operate { actions.favorites(it.map(EchoTrack::id), false) }
                            LibraryBatchCommand.Playlist -> choosingPlaylist = true
                            LibraryBatchCommand.Queue -> operate { actions.queue(it, 0) }
                            LibraryBatchCommand.Next -> operate { actions.queue(it, 1) }
                            LibraryBatchCommand.NextUp -> operate { actions.queue(it, 2) }
                            LibraryBatchCommand.Tags -> editing = true
                            LibraryBatchCommand.Delete -> deleting = selection.tracks()
                        }
                    },
                )
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = LibraryBottomControlsPadding))
        }
    }
    deleting?.let { tracks ->
        LibraryBatchDeleteDialog(
            count = tracks.size,
            canDeleteOriginals = actions.canDeleteOriginals(tracks),
            onDismiss = { deleting = null },
            onConfirm = { delete(tracks, it) },
        )
    }
    if (editing) BatchTagEditor(selection.tracks(), actions) { editing = false }
    if (choosingPlaylist) LibraryBatchPlaylistSheet(
        playlists = actions.playlists,
        count = selection.selected.size,
        onDismiss = { choosingPlaylist = false },
        onSelect = { playlist ->
            choosingPlaylist = false
            operate(context.getString(R.string.batch_playlist_added, playlist.name)) {
                actions.addToPlaylist(playlist.id, it.map(EchoTrack::id))
            }
        },
    )
}
