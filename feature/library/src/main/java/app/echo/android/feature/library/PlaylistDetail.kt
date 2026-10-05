package app.echo.android.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import app.echo.android.design.EchoContentMaxWidth
import app.echo.android.design.formatDuration
import app.echo.android.model.library.EchoPlaylist
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.EchoTrackMetadataUpdate
import app.echo.android.model.library.LibraryOfflinePin
import app.echo.android.model.library.LibrarySmartPlaylistKind
import app.echo.android.model.library.LibrarySource
import app.echo.android.model.library.PlaylistTrackOrder
import app.echo.android.model.library.PlaylistStats
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import app.echo.android.feature.library.R as L10nR

private val PlaylistDetailBottomPadding = 168.dp

@Composable
internal fun PlaylistDetailPage(
    playlist: EchoPlaylist,
    tracks: LazyPagingItems<EchoTrack>,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onPlayTrack: (EchoTrack) -> Unit,
    onRenamePlaylist: (String) -> Unit,
    onDeletePlaylist: () -> Unit,
    onRemoveTrack: (EchoTrack) -> Unit,
    onMoveTrack: (fromIndex: Int, toIndex: Int) -> Unit,
    onUpdateTrackMetadata: (suspend (EchoTrackMetadataUpdate) -> Unit)? = null,
    onImportLyrics: ((EchoTrack) -> Unit)? = null,
    onPickArtwork: ((EchoTrack) -> Unit)? = null,
    onMatchNeteaseMetadata: ((EchoTrack) -> Unit)? = null,
    onAddToPlaylist: ((EchoTrack) -> Unit)? = null,
    onPlayNext: ((EchoTrack) -> Unit)? = null,
    onAddNextUp: ((EchoTrack) -> Unit)? = null,
    onEnqueue: ((EchoTrack) -> Unit)? = null,
    onOpenArtist: ((EchoTrack) -> Unit)? = null,
    onOpenAlbum: ((EchoTrack) -> Unit)? = null,
    offlinePin: LibraryOfflinePin? = null,
    onToggleOffline: (() -> Unit)? = null,
    onPinShortcut: ((Boolean) -> Unit)? = null,
    management: PlaylistManagementActions? = null,
    experienceActions: LibraryExperienceActions? = null,
    onCopyCreated: (EchoPlaylist) -> Unit = {},
    onExport: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val loadedTracks = tracks.itemSnapshotList.items
    val displayName = playlistDisplayName(playlist)
    val coverUris = remember(playlist.id, playlist.artworkUri, loadedTracks) {
        playlistCoverUris(playlist, loadedTracks)
    }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var editing by remember(playlist.id) { mutableStateOf(false) }
    var adding by remember(playlist.id) { mutableStateOf(false) }
    var copying by remember(playlist.id) { mutableStateOf(false) }
    var editingRules by remember(playlist.id) { mutableStateOf(false) }
    var busy by remember(playlist.id) { mutableStateOf(false) }
    var actionFailed by remember(playlist.id) { mutableStateOf(false) }
    var addedCount by remember(playlist.id) { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val orderFlow = remember(playlist.id, management) { management?.order?.invoke(playlist.id) ?: flowOf(PlaylistTrackOrder()) }
    val order by orderFlow.collectAsState(initial = PlaylistTrackOrder())
    val statsFlow = remember(playlist.id, management) {
        management?.stats?.invoke(playlist.id) ?: flowOf(PlaylistStats(playlist.trackCount, 0L))
    }
    val stats by statsFlow.collectAsState(initial = PlaylistStats(playlist.trackCount, 0L))
    fun perform(operation: suspend () -> Unit) {
        if (busy) return
        busy = true; actionFailed = false
        scope.launch {
            try { operation() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { actionFailed = true }
            finally { busy = false }
        }
    }
    val colors = rememberPlaylistDetailColors()
    // A paging snapshot is not the whole playlist; only show a complete duration.
    val durationMs = stats.durationMs.takeIf { it > 0L } ?: remember(loadedTracks, playlist.trackCount) {
        if (loadedTracks.size == playlist.trackCount) {
            loadedTracks.sumOf { it.durationMs.coerceAtLeast(0L) }
        } else 0L
    }

    Column(
        modifier = modifier.fillMaxSize().background(colors.surface)
            .detailBackSwipe(onBack).statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.widthIn(max = EchoContentMaxWidth).fillMaxWidth()) {
            PlaylistDetailTopBar(
                title = stringResource(L10nR.string.feature_library_playlists_56bf76),
                canEdit = playlist.canEdit,
                onBack = onBack,
                onRename = { renaming = true },
                onDelete = { deleting = true },
                onCopy = management?.let { { copying = true } },
                onExport = onExport?.let { export -> { export(displayName) } },
                onArtwork = management?.takeIf { playlist.canEdit }?.let { { it.pickArtwork(playlist.id) } },
                onEditRules = experienceActions?.takeIf { playlist.isCustomSmartPlaylist }?.let { { editingRules = true } },
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f).widthIn(max = EchoContentMaxWidth).fillMaxWidth(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = PlaylistDetailBottomPadding),
        ) {
            item(key = "playlist-hero", contentType = "header") {
                PlaylistHero(
                    playlist = playlist.copy(trackCount = stats.trackCount),
                    coverUris = coverUris,
                    durationMs = durationMs,
                    onPlayAll = onPlayAll,
                    onShuffle = onShuffle,
                    offlinePin = offlinePin,
                    onToggleOffline = onToggleOffline,
                )
            }
            item(key = "playlist-tools", contentType = "tools") {
                onPinShortcut?.let { LibraryDesktopShortcutButton(it) }
                PlaylistDetailToolbar(playlist, order, editing, busy,
                    onSort = management?.let { actions -> { next -> perform { actions.sort(playlist.id, next) } } },
                    onEdit = management?.takeIf { playlist.canRemoveTracks }?.let { { editing = !editing } },
                    onAdd = management?.takeIf { playlist.canEdit }?.let { { adding = true } })
                if (actionFailed) PlaylistDetailNotice(stringResource(L10nR.string.playlist_action_error))
                addedCount?.let { PlaylistDetailNotice(pluralStringResource(L10nR.plurals.playlist_added, it, it)) }
            }
            when {
                tracks.isInitialPagingLoad() -> item(key = "playlist-loading") {
                    PlaylistDetailNotice(stringResource(L10nR.string.feature_library_loading_playlist_eb6248))
                }
                tracks.isInitialPagingError() -> item(key = "playlist-error") {
                    PlaylistDetailNotice(stringResource(L10nR.string.feature_library_failed_to_load_playlist_tracks_9130da))
                }
                tracks.itemCount == 0 -> item(key = "playlist-empty") {
                    PlaylistDetailNotice(playlistEmptyMessage(playlist))
                }
                else -> items(
                    count = tracks.itemCount,
                    key = { index -> tracks.peek(index)?.id ?: "playlist-track-$index" },
                    contentType = { "track" },
                ) { index ->
                    val track = tracks[index]
                    if (track == null) PlaylistTrackPlaceholder(editing) else {
                        PlaylistTrackRow(
                            modifier = libraryTrackItemMotion(),
                            track = track,
                            accent = colors.muted,
                            onClick = { onPlayTrack(track) },
                            onUpdateTrackMetadata = onUpdateTrackMetadata,
                            onImportLyrics = onImportLyrics,
                            onPickArtwork = onPickArtwork,
                            onMatchNeteaseMetadata = onMatchNeteaseMetadata,
                            onAddToPlaylist = onAddToPlaylist,
                            onPlayNext = onPlayNext,
                            onAddNextUp = onAddNextUp,
                            onEnqueue = onEnqueue,
                            onRemoveFromPlaylist = if (!playlist.canRemoveTracks) null else { selected ->
                                if (management != null) perform { check(management.remove(playlist.id, selected.id)) }
                                else onRemoveTrack(selected)
                            },
                            onMoveUp = if (playlist.canEdit && order.isManual && index > 0) ({
                                if (management != null) perform { check(management.move(playlist.id, track.id, -1)) }
                                else onMoveTrack(index, index - 1)
                            }) else null,
                            onMoveDown = if (playlist.canEdit && order.isManual && index < stats.trackCount - 1) ({
                                if (management != null) perform { check(management.move(playlist.id, track.id, 1)) }
                                else onMoveTrack(index, index + 1)
                            }) else null,
                            onOpenArtist = onOpenArtist,
                            onOpenAlbum = onOpenAlbum,
                            editing = editing,
                            editBusy = busy,
                        )
                    }
                }
            }
        }
    }

    if (adding && management != null) PlaylistAddTracksSheet(playlist.id, management,
        onAdded = { addedCount = it }, onDismiss = { adding = false })
    if (editingRules && experienceActions != null) SmartPlaylistEditor(playlist, experienceActions) { editingRules = false }
    if (copying && management != null) PlaylistNameDialog(
        title = stringResource(L10nR.string.playlist_copy),
        confirmLabel = stringResource(L10nR.string.feature_library_create_7b4bc9),
        initialName = stringResource(L10nR.string.playlist_copy_name, playlistDisplayName(playlist)).take(80),
        supportingText = stringResource(L10nR.string.playlist_copy_hint),
        onDismiss = { copying = false },
        onConfirm = { name ->
            copying = false
            perform { val created = management.copy(playlist.id, name); check(created != null); onCopyCreated(created) }
        },
    )

    if (renaming) {
        PlaylistNameDialog(
            title = stringResource(L10nR.string.feature_library_rename_playlist_757bb7),
            confirmLabel = stringResource(L10nR.string.feature_library_save_68ae20),
            initialName = playlist.name,
            onDismiss = { renaming = false },
            onConfirm = { name ->
                onRenamePlaylist(name)
                renaming = false
            },
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(L10nR.string.feature_library_delete_playlist_4d9753)) },
            text = {
                Text(
                    stringResource(L10nR.string.feature_library_delete_playlist_name_songs_in_the_library_will_3541db, (playlist.name).toString()),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeletePlaylist()
                        deleting = false
                    },
                ) {
                    Text(stringResource(L10nR.string.feature_library_delete_138ccf))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = false }) {
                    Text(stringResource(L10nR.string.feature_library_cancel_4c5fa5))
                }
            },
        )
    }
}


@Composable
private fun PlaylistDetailNotice(message: String) {
    val colors = rememberPlaylistDetailColors()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = EchoContentMaxWidth)
            .padding(vertical = 24.dp),
    ) {
        Text(message, color = colors.muted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun playlistHeroCaption(playlist: EchoPlaylist, durationMs: Long): String {
    val parts = buildList {
        add(libraryTrackCountLabel(playlist.trackCount))
        if (durationMs > 0L) add(readablePlaylistDuration(durationMs))
    }
    return parts.joinToString(" · ")
}

@Composable
internal fun playlistDisplayName(playlist: EchoPlaylist): String =
    when (playlist.smartKind) {
        LibrarySmartPlaylistKind.Recent -> stringResource(L10nR.string.feature_library_smart_recent)
        LibrarySmartPlaylistKind.Frequent -> stringResource(L10nR.string.feature_library_smart_frequent)
        LibrarySmartPlaylistKind.Never -> stringResource(L10nR.string.feature_library_smart_never)
        LibrarySmartPlaylistKind.Added -> stringResource(L10nR.string.feature_library_smart_added)
        null -> if (playlist.isLikedSongs) {
            stringResource(L10nR.string.feature_library_liked_songs_8d6245)
        } else {
            playlist.name
        }
    }

@Composable
internal fun playlistCaption(playlist: EchoPlaylist): String {
    val count = playlist.trackCount
    return when {
        playlist.isCustomSmartPlaylist -> androidx.compose.ui.res.pluralStringResource(L10nR.plurals.smart_preview_count, count, count)
        playlist.isLikedSongs -> stringResource(L10nR.string.feature_library_count_tracks_liked_songs_e36fb7, (count).toString())
        playlist.isSmartPlaylist -> stringResource(L10nR.string.feature_library_count_tracks_smart, count.toString())
        playlist.canEdit -> stringResource(L10nR.string.feature_library_count_tracks_local_playlist_352a0d, (count).toString())
        else -> stringResource(L10nR.string.feature_library_count_tracks_navidrome_294a65, (count).toString())
    }
}

@Composable
internal fun playlistKindLabel(playlist: EchoPlaylist): String = when {
    playlist.isLikedSongs -> stringResource(L10nR.string.feature_library_liked_c8ac9d)
    playlist.isSmartPlaylist -> stringResource(L10nR.string.feature_library_smart_kind)
    playlist.source == LibrarySource.MediaStore.id -> stringResource(L10nR.string.feature_library_local_9b5178)
    else -> stringResource(L10nR.string.feature_library_linked_5d42a7)
}

@Composable
private fun playlistEmptyMessage(playlist: EchoPlaylist): String =
    when (playlist.smartKind) {
        LibrarySmartPlaylistKind.Recent -> stringResource(L10nR.string.feature_library_smart_empty_recent)
        LibrarySmartPlaylistKind.Frequent -> stringResource(L10nR.string.feature_library_smart_empty_frequent)
        LibrarySmartPlaylistKind.Never -> stringResource(L10nR.string.feature_library_smart_empty_never)
        LibrarySmartPlaylistKind.Added -> stringResource(L10nR.string.feature_library_smart_empty_added)
        null -> if (playlist.isLikedSongs) {
            stringResource(L10nR.string.feature_library_no_liked_songs_yet_heart_a_track_from_ba96cc)
        } else {
            stringResource(L10nR.string.feature_library_this_playlist_is_empty_add_songs_from_a_bebd89)
        }
    }

@Composable
private fun readablePlaylistDuration(durationMs: Long): String {
    val totalMinutes = (durationMs / 60_000L).toInt()
    return if (totalMinutes >= 1) {
        libraryMinutesLabel(totalMinutes)
    } else {
        formatDuration(durationMs)
    }
}

private fun playlistCoverUris(playlist: EchoPlaylist, tracks: List<EchoTrack>): List<String> {
    if (playlist.canEdit) playlist.artworkUri?.takeIf(String::isNotBlank)?.let { return listOf(it) }
    val covers = tracks.asSequence()
        .mapNotNull { it.artworkUri?.takeIf(String::isNotBlank) }
        .distinct()
        .take(4)
        .toList()
    return covers.ifEmpty { listOfNotNull(playlist.artworkUri?.takeIf(String::isNotBlank)) }
}
