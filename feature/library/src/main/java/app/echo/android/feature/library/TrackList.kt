package app.echo.android.feature.library

import app.echo.android.design.EchoModalSheet
import app.echo.android.feature.library.R as L10nR
import androidx.compose.ui.res.stringResource

import androidx.compose.animation.AnimatedVisibility
import app.echo.android.design.echoCombinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import app.echo.android.design.EchoIcon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.RippleDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import app.echo.android.design.ArtworkTile
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.displayMetadataOrUnknown
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.libraryDisplayTitle
import app.echo.android.model.library.EchoTrackMetadataUpdate
import kotlinx.coroutines.launch

@Composable
internal fun TrackList(
    tracks: LazyPagingItems<EchoTrack>,
    playingTrackId: String? = null,
    isPlaying: Boolean = false,
    onPlayTrack: (EchoTrack) -> Unit,
    onUpdateTrackMetadata: (suspend (EchoTrackMetadataUpdate) -> Boolean)? = null,
    onImportLyrics: ((EchoTrack) -> Unit)? = null,
    onPickArtwork: ((EchoTrack) -> Unit)? = null,
    onMatchNeteaseMetadata: ((EchoTrack) -> Unit)? = null,
    onAddToPlaylist: ((EchoTrack) -> Unit)? = null,
    onPlayNext: ((EchoTrack) -> Unit)? = null,
    onEnqueue: ((EchoTrack) -> Unit)? = null,
    onRemoveFromPlaylist: ((EchoTrack) -> Unit)? = null,
    onMoveTrack: ((fromIndex: Int, toIndex: Int) -> Unit)? = null,
    onOpenArtist: ((EchoTrack) -> Unit)? = null,
    onOpenAlbum: ((EchoTrack) -> Unit)? = null,
    showAudioInfoTags: Boolean = true,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(bottom = LibraryBottomControlsPadding),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        items(
            count = tracks.itemCount,
            key = { index: Int -> tracks.peek(index)?.id ?: "track-$index" },
        ) { index: Int ->
            tracks[index]?.let { track ->
                val isCurrentTrack = track.id == playingTrackId
                TrackRow(
                    modifier = libraryTrackItemMotion(),
                    isCurrentTrack = isCurrentTrack,
                    isPlaying = isPlaying && isCurrentTrack,
                    track = track,
                    onClick = { onPlayTrack(track) },
                    onUpdateTrackMetadata = onUpdateTrackMetadata,
                    onOpenArtist = onOpenArtist,
                    onOpenAlbum = onOpenAlbum,
                    onImportLyrics = onImportLyrics,
                    onPickArtwork = onPickArtwork,
                    onMatchNeteaseMetadata = onMatchNeteaseMetadata,
                    onAddToPlaylist = onAddToPlaylist,
                    onPlayNext = onPlayNext,
                    onEnqueue = onEnqueue,
                    onRemoveFromPlaylist = onRemoveFromPlaylist,
                    onMoveUp = onMoveTrack?.takeIf { index > 0 }?.let { move ->
                        { move(index, index - 1) }
                    },
                    onMoveDown = onMoveTrack?.takeIf { index < tracks.itemCount - 1 }?.let { move ->
                        { move(index, index + 1) }
                    },
                    showAudioInfoTags = showAudioInfoTags,
                )
            } ?: Spacer(Modifier.fillMaxWidth().height(LibraryTrackRowMinHeight))
        }
    }
}

@Composable
internal fun TrackList(
    tracks: List<EchoTrack>,
    onPlayTrack: (EchoTrack) -> Unit,
    onUpdateTrackMetadata: (suspend (EchoTrackMetadataUpdate) -> Boolean)? = null,
    onImportLyrics: ((EchoTrack) -> Unit)? = null,
    onPickArtwork: ((EchoTrack) -> Unit)? = null,
    onMatchNeteaseMetadata: ((EchoTrack) -> Unit)? = null,
    onAddToPlaylist: ((EchoTrack) -> Unit)? = null,
    onPlayNext: ((EchoTrack) -> Unit)? = null,
    onEnqueue: ((EchoTrack) -> Unit)? = null,
    onRemoveFromPlaylist: ((EchoTrack) -> Unit)? = null,
    onMoveTrack: ((fromIndex: Int, toIndex: Int) -> Unit)? = null,
    onOpenArtist: ((EchoTrack) -> Unit)? = null,
    onOpenAlbum: ((EchoTrack) -> Unit)? = null,
    showAudioInfoTags: Boolean = true,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(bottom = LibraryBottomControlsPadding),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        items(
            count = tracks.size,
            key = { index -> tracks[index].id },
        ) { index ->
            val track = tracks[index]
            TrackRow(
                modifier = libraryTrackItemMotion(),
                track = track,
                onClick = { onPlayTrack(track) },
                onUpdateTrackMetadata = onUpdateTrackMetadata,
                onOpenArtist = onOpenArtist,
                onOpenAlbum = onOpenAlbum,
                onImportLyrics = onImportLyrics,
                onPickArtwork = onPickArtwork,
                onMatchNeteaseMetadata = onMatchNeteaseMetadata,
                onAddToPlaylist = onAddToPlaylist,
                onPlayNext = onPlayNext,
                onEnqueue = onEnqueue,
                onRemoveFromPlaylist = onRemoveFromPlaylist,
                onMoveUp = onMoveTrack?.takeIf { index > 0 }?.let { move ->
                    { move(index, index - 1) }
                },
                onMoveDown = onMoveTrack?.takeIf { index < tracks.lastIndex }?.let { move ->
                    { move(index, index + 1) }
                },
                showAudioInfoTags = showAudioInfoTags,
            )
        }
    }
}

// A pointer resting on a song should not make it look selected.
private val TrackRowRippleConfiguration = with(RippleDefaults.RippleAlpha) {
    RippleConfiguration(rippleAlpha = RippleAlpha(
        pressedAlpha = pressedAlpha,
        focusedAlpha = focusedAlpha,
        draggedAlpha = draggedAlpha,
        hoveredAlpha = 0f,
    ))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrackContextMenu(
    track: EchoTrack,
    onPlay: () -> Unit,
    onUpdateTrackMetadata: (suspend (EchoTrackMetadataUpdate) -> Boolean)? = null,
    onImportLyrics: ((EchoTrack) -> Unit)? = null,
    onPickArtwork: ((EchoTrack) -> Unit)? = null,
    onMatchNeteaseMetadata: ((EchoTrack) -> Unit)? = null,
    onAddToPlaylist: ((EchoTrack) -> Unit)? = null,
    onPlayNext: ((EchoTrack) -> Unit)? = null,
    onEnqueue: ((EchoTrack) -> Unit)? = null,
    onRemoveFromPlaylist: ((EchoTrack) -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onOpenArtist: (() -> Unit)? = null,
    onOpenAlbum: (() -> Unit)? = null,
    onPlayOnPc: (() -> Unit)? = null,
    showMoreAction: Boolean = false,
    compactMoreIcon: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val selection = LocalLibraryBatchSelection.current.takeUnless { track.source == app.echo.android.model.library.LibrarySource.FilePlaylist }
    val shareOriginal = LocalOriginalAudioShare.current
    val requestTrackDeletion = LocalLibraryDeleteTrack.current.takeUnless { track.source == app.echo.android.model.library.LibrarySource.FilePlaylist }
    val hiddenTracks = LocalHiddenTracksUi.current.takeUnless { track.source == app.echo.android.model.library.LibrarySource.FilePlaylist }
    val openTrackVersions = LocalTrackVersionsAction.current
    val selecting = selection?.active == true
    val selected = selecting && track.id in selection.selected
    val canSelect = selection?.locked != true && (!selecting || selected || selection.selected.size < LibraryBatchSelection.Limit)
    val selectionProgress = rememberLibrarySelectionProgress(selected)
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val onSelectForBatch = selection?.let { { it.start(track) } }
    val actionScope = rememberCoroutineScope()
    var expanded by remember(track.id) { mutableStateOf(false) }
    var showActions by rememberSaveable(track.id) { mutableStateOf(false) }
    var showInfo by remember(track.id) { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val openTagEditor = LocalTrackMetadataEditorLauncher.current
    val canEditMetadata = openTagEditor != null && onUpdateTrackMetadata != null && track.canEditFileTags()

    fun editTags() {
        showActions = false
        openTagEditor?.invoke(
            track,
            onPickArtwork != null && track.source != app.echo.android.model.library.LibrarySource.FilePlaylist,
        )
    }

    Box(modifier = modifier
        .libraryDeletionFeedback(track.id in selection?.deletingIds.orEmpty())
        .librarySelectionBackground(selectionProgress, MaterialTheme.colorScheme.primary)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LibraryTrackSelection(
                active = selecting,
                selected = selected,
                enabled = canSelect,
                progress = selectionProgress,
                label = displayMetadataOrUnknown(track.libraryDisplayTitle(LocalLibraryFileNames.current), unknownTrackLabel()),
                onToggle = { selection?.toggle(track) },
                dragModifier = Modifier.libraryBatchDrag(track, selection),
            )
            Box(Modifier.weight(1f)) {
                CompositionLocalProvider(LocalRippleConfiguration provides TrackRowRippleConfiguration) {
                    content(Modifier.echoCombinedClickable(
                        enabled = !selecting || canSelect,
                        role = if (selecting) Role.Checkbox else null,
                        onClick = { if (selecting) selection.toggle(track) else onPlay() },
                        onLongClick = { showActions = true },
                    ).then(if (selecting) Modifier.semantics {
                        toggleableState = if (selected) ToggleableState.On else ToggleableState.Off
                    } else Modifier))
                }
            }
            if (!selecting) {
                if (LocalLibraryPlayNext.current && onPlayNext != null) {
                    IconButton(onClick = { onPlayNext(track) }) {
                        EchoIcon(
                            Icons.Rounded.PlaylistPlay,
                            contentDescription = stringResource(
                                L10nR.string.library_inline_play_next_track,
                                track.libraryDisplayTitle(LocalLibraryFileNames.current),
                            ),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = showMoreAction && !selecting,
                enter = librarySelectionEnter(lightweight),
                exit = librarySelectionExit(lightweight),
            ) {
                IconButton(onClick = { showActions = true }) {
                    EchoIcon(Icons.Rounded.MoreVert, contentDescription = stringResource(L10nR.string.library_track_actions),
                        modifier = Modifier.offset(x = 8.dp)
                            .then(if (compactMoreIcon) Modifier.size(20.dp) else Modifier),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(L10nR.string.feature_library_play_38419a)) },
                leadingIcon = {
                    EchoIcon(Icons.Rounded.PlayArrow, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    onPlay()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(L10nR.string.feature_library_edit_tags_c8ec75)) },
                leadingIcon = {
                    EchoIcon(Icons.Rounded.Edit, contentDescription = null)
                },
                enabled = canEditMetadata,
                onClick = {
                    expanded = false
                    editTags()
                },
            )
            if (onImportLyrics != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(L10nR.string.feature_library_import_lyrics_744b75)) },
                    onClick = {
                        expanded = false
                        onImportLyrics(track)
                    },
                )
            }
            if (onPickArtwork != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(L10nR.string.feature_library_change_artwork_d37180)) },
                    onClick = {
                        expanded = false
                        onPickArtwork(track)
                    },
                )
            }
            if (openTrackVersions != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(L10nR.string.track_versions_title)) },
                    onClick = { expanded = false; openTrackVersions(track) },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(L10nR.string.feature_library_track_info_36a07b)) },
                leadingIcon = {
                    EchoIcon(Icons.Rounded.Info, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    showInfo = true
                },
            )
        }
    }

    if (showActions) {
        EchoModalSheet(
            onDismissRequest = { showActions = false },
            sheetState = sheetState,
            // Changing top inset padding during a drag would resize the sheet and move its anchor.
            contentWindowInsets = {
                WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
            },
        ) {
            TrackActionSheet(
                track = track,
                canEditMetadata = canEditMetadata,
                canImportLyrics = onImportLyrics != null,
                canAddToPlaylist = onAddToPlaylist != null,
                canPlayNext = onPlayNext != null,
                canEnqueue = onEnqueue != null,
                canRemoveFromPlaylist = onRemoveFromPlaylist != null,
                canMoveUp = onMoveUp != null,
                canMoveDown = onMoveDown != null,
                canOpenArtist = onOpenArtist != null,
                canOpenAlbum = onOpenAlbum != null,
                canPlayOnPc = onPlayOnPc != null,
                onPlay = {
                    showActions = false
                    onPlay()
                },
                onPlayOnPc = {
                    showActions = false
                    onPlayOnPc?.invoke()
                },
                onOpenArtist = {
                    showActions = false
                    onOpenArtist?.invoke()
                },
                onOpenAlbum = {
                    showActions = false
                    onOpenAlbum?.invoke()
                },
                onEdit = ::editTags,
                onVersions = openTrackVersions?.let { open -> { showActions = false; open(track) } },
                onHide = hiddenTracks?.takeIf { track.source != app.echo.android.model.library.LibrarySource.EchoLink }?.let { ui ->
                    { showActions = false; ui.requestHide(track) }
                },
                onDelete = requestTrackDeletion?.let { request ->
                    {
                        showActions = false
                        request(track)
                    }
                },
                onImportLyrics = {
                    showActions = false
                    onImportLyrics?.invoke(track)
                },
                onAddToPlaylist = {
                    showActions = false
                    onAddToPlaylist?.invoke(track)
                },
                onPlayNext = {
                    showActions = false
                    onPlayNext?.invoke(track)
                },
                onEnqueue = {
                    showActions = false
                    onEnqueue?.invoke(track)
                },
                onRemoveFromPlaylist = {
                    showActions = false
                    onRemoveFromPlaylist?.invoke(track)
                },
                onMoveUp = {
                    showActions = false
                    onMoveUp?.invoke()
                },
                onMoveDown = {
                    showActions = false
                    onMoveDown?.invoke()
                },
                onInfo = {
                    showActions = false
                    showInfo = true
                },
                onShare = shareOriginal?.let { share -> { showActions = false; share(listOf(track)) } },
                onSelectForBatch = onSelectForBatch?.let { select ->
                    {
                        actionScope.launch {
                            sheetState.hide()
                            showActions = false
                            select()
                        }
                        Unit
                    }
                },
            )
        }
    }

    if (showInfo) {
        TrackInfoDialog(
            track = track,
            onDismiss = { showInfo = false },
            onOpenArtist = onOpenArtist?.let { open ->
                {
                    showInfo = false
                    open()
                }
            },
        )
    }
}

@Composable
internal fun TrackSheetHeader(track: EchoTrack, artworkSize: Dp = 64.dp, modifier: Modifier = Modifier) {
    val subtitle = trackSubtitle(track)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ArtworkTile(
            track.artworkUri,
            Modifier.size(artworkSize),
            accent = rememberLibraryArtworkAccent(),
            cornerRadius = 14.dp,
            elevation = 0.dp,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                displayMetadataOrUnknown(track.libraryDisplayTitle(LocalLibraryFileNames.current), unknownTrackLabel()),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}

@Composable
internal fun trackSubtitle(track: EchoTrack, separator: String = " / "): String {
    val parts = buildList {
        track.artist.takeIf { it.isNotBlank() }?.let { artist ->
            add(displayMetadataOrUnknown(artist, unknownArtistLabel()))
        }
        track.album?.takeIf { it.isNotBlank() }?.let { album ->
            add(displayMetadataOrUnknown(album, unknownAlbumLabel()))
        }
    }
    return parts.ifEmpty {
        listOf(stringResource(L10nR.string.feature_library_local_audio_7fc2a6))
    }.joinToString(separator)
}

internal fun formatTrackSampleRate(hz: Int): String =
    if (hz % 1000 == 0) {
        "${hz / 1000}kHz"
    } else {
        String.format(java.util.Locale.ROOT, "%.1fkHz", hz / 1000.0)
    }

@Composable
internal fun formatTrackFileSize(bytes: Long): String =
    when {
        bytes <= 0L -> stringResource(L10nR.string.feature_library_not_available_7d3220)
        bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(bytes / (1024f * 1024f * 1024f))
        bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024f * 1024f))
        bytes >= 1024L -> "%.1f KB".format(bytes / 1024f)
        else -> "$bytes B"
    }

internal fun formatTrackMimeType(mimeType: String?): String? {
    val raw = mimeType
        ?.substringAfter("audio/", missingDelimiterValue = mimeType)
        ?.substringBefore(";")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: return null
    return when {
        raw.equals("mpeg", ignoreCase = true) || raw.equals("mp3", ignoreCase = true) -> "MP3"
        raw.equals("mp4", ignoreCase = true) || raw.equals("mp4a-latm", ignoreCase = true) -> "AAC"
        raw.equals("x-wav", ignoreCase = true) || raw.equals("wav", ignoreCase = true) -> "WAV"
        raw.equals("x-flac", ignoreCase = true) || raw.equals("flac", ignoreCase = true) -> "FLAC"
        raw.equals("ogg", ignoreCase = true) || raw.equals("vorbis", ignoreCase = true) -> "OGG"
        else -> raw.uppercase()
    }
}

internal val LibraryBottomControlsPadding = 150.dp
