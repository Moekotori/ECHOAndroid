package app.echo.android.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.model.connect.*
import app.echo.android.model.library.FolderSortMode

/** Keeps only the current Bridge page; legacy PC folder responses retain their existing layout. */
@Composable
internal fun LinkedFolderBrowser(
    state: EchoRemoteLibraryState,
    libraryOnly: Boolean,
    sortMode: FolderSortMode,
    onOpenFolder: (String) -> Unit,
    onChangePage: (Int) -> Unit,
    onPlayLinkedQueue: (List<EchoRemoteTrack>, Int) -> Unit,
    onPlayLinkedQueueOnPc: ((List<EchoRemoteTrack>, Int) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val tracks = state.folderTracks
    val folders = remember(state.folders, sortMode, libraryOnly) {
        if (libraryOnly) state.folders else state.folders.sortedForLinkedLibrary(sortMode)
    }
    val parent = state.folderParentPath ?: EchoLinkLibraryQueryPolicy.parentFolderPath(state.folderPath)
    val listState = rememberLazyListState()
    val paged = state.folderPageCount > 1
    LaunchedEffect(state.folderPath, state.folderPage) { listState.scrollToItem(0) }
    Column(modifier.fillMaxSize()) {
        if (state.folderPath.isNotBlank()) TextButton(onClick = { onOpenFolder(parent) }) {
            EchoIcon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.feature_library_open_parent_folder_2a11c0))
        }
        if (tracks.isNotEmpty() && onPlayLinkedQueueOnPc != null) TextButton(onClick = { onPlayLinkedQueueOnPc(tracks, 0) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.feature_library_play_on_pc_7c21a4))
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(bottom = if (paged) 8.dp else LibraryBottomControlsPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(folders, key = { "folder:${it.path}" }) { folder ->
                LinkedPlaylistRow(EchoRemotePlaylist(id = folder.path, name = folder.name, artworkUrl = folder.artworkUrl,
                    trackCount = folder.trackCount, sourceLabel = if (libraryOnly) "ECHO Bridge" else folder.path),
                    onOpen = { onOpenFolder(folder.path) })
            }
            items(tracks, key = { "track:${it.id ?: "${it.title}-${it.artist}"}" }) { track ->
                TrackRow(track = track.toEchoTrack(), onClick = {
                    onPlayLinkedQueue(tracks, tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0))
                }, onPlayOnPc = onPlayLinkedQueueOnPc?.let { playOnPc -> {
                    playOnPc(tracks, tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0))
                } }, showAudioInfoTags = false)
            }
            if (folders.isEmpty() && tracks.isEmpty() && state.loadingFolderPath == null) item("empty") {
                Text(stringResource(R.string.feature_library_pc_echo_has_no_folders_to_show_9e12b8))
            }
        }
        if (paged) Row(Modifier.fillMaxWidth().padding(bottom = LibraryBottomControlsPadding),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onChangePage(state.folderPage - 1) },
                enabled = state.loadingFolderPath == null && state.folderPage > 1) {
                Text(stringResource(R.string.linked_folder_previous_page))
            }
            Text(stringResource(R.string.linked_folder_page, state.folderPage, state.folderPageCount))
            TextButton(onClick = { onChangePage(state.folderPage + 1) },
                enabled = state.loadingFolderPath == null && state.folderPage < state.folderPageCount) {
                Text(stringResource(R.string.linked_folder_next_page))
            }
        }
    }
}

private fun List<EchoRemoteFolder>.sortedForLinkedLibrary(mode: FolderSortMode): List<EchoRemoteFolder> {
    val byName = compareBy(String.CASE_INSENSITIVE_ORDER, EchoRemoteFolder::name)
        .thenBy(String.CASE_INSENSITIVE_ORDER, EchoRemoteFolder::path)
    return when (mode) {
        FolderSortMode.Path -> sortedWith(byName)
        FolderSortMode.TrackCount -> sortedWith(compareByDescending(EchoRemoteFolder::trackCount).then(byName))
        FolderSortMode.AlbumCount -> sortedWith(compareByDescending(EchoRemoteFolder::childFolderCount).then(byName))
        FolderSortMode.Duration, FolderSortMode.Size, FolderSortMode.RecentlyModified -> sortedWith(byName)
    }
}
