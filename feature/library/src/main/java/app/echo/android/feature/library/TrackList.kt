package app.echo.android.feature.library

import app.echo.android.feature.library.R as L10nR
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import app.echo.android.design.echoItemMotion
import app.echo.android.design.echoClickable
import app.echo.android.design.echoCombinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Queue
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.paging.compose.LazyPagingItems
import app.echo.android.design.ArtworkTile
import app.echo.android.design.EchoColors
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.displayMetadataOrUnknown
import app.echo.android.design.formatDuration
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.EchoTrackMetadataUpdate
import app.echo.android.model.library.LibrarySource

@Composable
internal fun TrackList(
    tracks: LazyPagingItems<EchoTrack>,
    onPlayTrack: (EchoTrack) -> Unit,
    onUpdateTrackMetadata: ((EchoTrackMetadataUpdate) -> Unit)? = null,
    onImportLyrics: ((EchoTrack) -> Unit)? = null,
    onPickArtwork: ((EchoTrack) -> Unit)? = null,
    onMatchNeteaseMetadata: ((EchoTrack) -> Unit)? = null,
    onAddToPlaylist: ((EchoTrack) -> Unit)? = null,
    onPlayNext: ((EchoTrack) -> Unit)? = null,
    onEnqueue: ((EchoTrack) -> Unit)? = null,
    onRemoveFromPlaylist: ((EchoTrack) -> Unit)? = null,
    onMoveTrack: ((fromIndex: Int, toIndex: Int) -> Unit)? = null,
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
                TrackRow(
                    modifier = echoItemMotion(),
                    track = track,
                    onClick = { onPlayTrack(track) },
                    onUpdateTrackMetadata = onUpdateTrackMetadata,
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
            }
        }
    }
}

@Composable
internal fun TrackList(
    tracks: List<EchoTrack>,
    onPlayTrack: (EchoTrack) -> Unit,
    onUpdateTrackMetadata: ((EchoTrackMetadataUpdate) -> Unit)? = null,
    onImportLyrics: ((EchoTrack) -> Unit)? = null,
    onPickArtwork: ((EchoTrack) -> Unit)? = null,
    onMatchNeteaseMetadata: ((EchoTrack) -> Unit)? = null,
    onAddToPlaylist: ((EchoTrack) -> Unit)? = null,
    onPlayNext: ((EchoTrack) -> Unit)? = null,
    onEnqueue: ((EchoTrack) -> Unit)? = null,
    onRemoveFromPlaylist: ((EchoTrack) -> Unit)? = null,
    onMoveTrack: ((fromIndex: Int, toIndex: Int) -> Unit)? = null,
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
                modifier = echoItemMotion(),
                track = track,
                onClick = { onPlayTrack(track) },
                onUpdateTrackMetadata = onUpdateTrackMetadata,
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

@Composable
internal fun TrackRow(
    track: EchoTrack,
    onClick: () -> Unit,
    onUpdateTrackMetadata: ((EchoTrackMetadataUpdate) -> Unit)? = null,
    onImportLyrics: ((EchoTrack) -> Unit)? = null,
    onPickArtwork: ((EchoTrack) -> Unit)? = null,
    onMatchNeteaseMetadata: ((EchoTrack) -> Unit)? = null,
    onAddToPlaylist: ((EchoTrack) -> Unit)? = null,
    onPlayNext: ((EchoTrack) -> Unit)? = null,
    onEnqueue: ((EchoTrack) -> Unit)? = null,
    onRemoveFromPlaylist: ((EchoTrack) -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    showAudioInfoTags: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    val subtitle = trackSubtitle(track)
    val duration = remember(track.durationMs) { formatDuration(track.durationMs) }
    val sampleRate = remember(showAudioInfoTags, track.sampleRateHz) {
        if (showAudioInfoTags) track.sampleRateHz?.let(::formatTrackSampleRate) else null
    }
    val format = remember(showAudioInfoTags, track.mimeType) {
        if (showAudioInfoTags) formatTrackMimeType(track.mimeType) else null
    }
    val hasTags = format != null || sampleRate != null

    TrackContextMenu(
        track = track,
        onPlay = onClick,
        onUpdateTrackMetadata = onUpdateTrackMetadata,
        onImportLyrics = onImportLyrics,
        onPickArtwork = onPickArtwork,
        onMatchNeteaseMetadata = onMatchNeteaseMetadata,
        onAddToPlaylist = onAddToPlaylist,
        onPlayNext = onPlayNext,
        onEnqueue = onEnqueue,
        onRemoveFromPlaylist = onRemoveFromPlaylist,
        onMoveUp = onMoveUp,
        onMoveDown = onMoveDown,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
    ) { pressModifier ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(pressModifier),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ArtworkTile(
                    track.artworkUri,
                    Modifier.size(68.dp),
                    accent = rememberLibraryArtworkAccent(),
                    cornerRadius = 12.dp,
                    elevation = 3.dp,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        displayMetadataOrUnknown(track.title, unknownTrackLabel()),
                        color = if (dark) Color.White.copy(alpha = 0.98f) else scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        subtitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (dark) Color.White.copy(alpha = 0.80f) else scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (hasTags) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            format?.let { value ->
                                TrackInfoTag(
                                    text = value,
                                    tone = TrackInfoTagTone.Format,
                                )
                            }
                            sampleRate?.let { value ->
                                TrackInfoTag(
                                    text = value,
                                    tone = if (isHiResSampleRate(track.sampleRateHz)) {
                                        TrackInfoTagTone.Gold
                                    } else {
                                        TrackInfoTagTone.Neutral
                                    },
                                )
                            }
                        }
                    }
                }
                Column(
                    modifier = Modifier.widthIn(min = 50.dp, max = 74.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        duration,
                        color = if (dark) Color.White.copy(alpha = 0.82f) else scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .padding(start = 82.dp)
                    .background(if (dark) Color.White.copy(alpha = 0.16f) else scheme.outlineVariant.copy(alpha = 0.36f)),
            )
        }
    }
}

private enum class TrackSheetMode {
    Actions,
    Editor,
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun TrackContextMenu(
    track: EchoTrack,
    onPlay: () -> Unit,
    onUpdateTrackMetadata: ((EchoTrackMetadataUpdate) -> Unit)? = null,
    onImportLyrics: ((EchoTrack) -> Unit)? = null,
    onPickArtwork: ((EchoTrack) -> Unit)? = null,
    onMatchNeteaseMetadata: ((EchoTrack) -> Unit)? = null,
    onAddToPlaylist: ((EchoTrack) -> Unit)? = null,
    onPlayNext: ((EchoTrack) -> Unit)? = null,
    onEnqueue: ((EchoTrack) -> Unit)? = null,
    onRemoveFromPlaylist: ((EchoTrack) -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    var expanded by remember(track.id) { mutableStateOf(false) }
    var sheetMode by remember(track.id) { mutableStateOf<TrackSheetMode?>(null) }
    var showInfo by remember(track.id) { mutableStateOf(false) }
    var showEditor by remember(track.id) { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val canEditMetadata = onUpdateTrackMetadata != null && track.source == LibrarySource.MediaStore

    Box(modifier = modifier) {
        content(
            Modifier.echoCombinedClickable(
                onClick = onPlay,
                onLongClick = { sheetMode = TrackSheetMode.Actions },
            ),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(L10nR.string.feature_library_play_38419a)) },
                leadingIcon = {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    onPlay()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(L10nR.string.feature_library_edit_tags_c8ec75)) },
                leadingIcon = {
                    Icon(Icons.Rounded.Edit, contentDescription = null)
                },
                enabled = canEditMetadata,
                onClick = {
                    expanded = false
                    showEditor = true
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
            DropdownMenuItem(
                text = { Text(stringResource(L10nR.string.feature_library_track_info_36a07b)) },
                leadingIcon = {
                    Icon(Icons.Rounded.Info, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    showInfo = true
                },
            )
        }
    }

    sheetMode?.let { mode ->
        ModalBottomSheet(
            onDismissRequest = { sheetMode = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        ) {
            when (mode) {
                TrackSheetMode.Actions -> TrackActionSheet(
                    track = track,
                    canEditMetadata = canEditMetadata,
                    canImportLyrics = onImportLyrics != null,
                    canPickArtwork = onPickArtwork != null,
                    canAddToPlaylist = onAddToPlaylist != null,
                    canPlayNext = onPlayNext != null,
                    canEnqueue = onEnqueue != null,
                    canRemoveFromPlaylist = onRemoveFromPlaylist != null,
                    canMoveUp = onMoveUp != null,
                    canMoveDown = onMoveDown != null,
                    onPlay = {
                        sheetMode = null
                        onPlay()
                    },
                    onEdit = { sheetMode = TrackSheetMode.Editor },
                    onImportLyrics = {
                        sheetMode = null
                        onImportLyrics?.invoke(track)
                    },
                    onPickArtwork = {
                        sheetMode = null
                        onPickArtwork?.invoke(track)
                    },
                    onAddToPlaylist = {
                        sheetMode = null
                        onAddToPlaylist?.invoke(track)
                    },
                    onPlayNext = {
                        sheetMode = null
                        onPlayNext?.invoke(track)
                    },
                    onEnqueue = {
                        sheetMode = null
                        onEnqueue?.invoke(track)
                    },
                    onRemoveFromPlaylist = {
                        sheetMode = null
                        onRemoveFromPlaylist?.invoke(track)
                    },
                    onMoveUp = {
                        sheetMode = null
                        onMoveUp?.invoke()
                    },
                    onMoveDown = {
                        sheetMode = null
                        onMoveDown?.invoke()
                    },
                    onInfo = {
                        sheetMode = null
                        showInfo = true
                    },
                )

                TrackSheetMode.Editor -> if (onUpdateTrackMetadata != null) {
                    TrackMetadataEditorSheet(
                        track = track,
                        onDismiss = { sheetMode = null },
                        onSave = { update ->
                            onUpdateTrackMetadata(update)
                            sheetMode = null
                        },
                    )
                }
            }
        }
    }

    if (showInfo) {
        TrackInfoDialog(
            track = track,
            onDismiss = { showInfo = false },
        )
    }
    if (showEditor && onUpdateTrackMetadata != null) {
        TrackMetadataEditorDialog(
            track = track,
            onDismiss = { showEditor = false },
            onSave = { update ->
                onUpdateTrackMetadata(update)
                showEditor = false
            },
        )
    }
}

@Composable
private fun TrackActionSheet(
    track: EchoTrack,
    canEditMetadata: Boolean,
    canImportLyrics: Boolean,
    canPickArtwork: Boolean,
    canAddToPlaylist: Boolean,
    canPlayNext: Boolean,
    canEnqueue: Boolean,
    canRemoveFromPlaylist: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onImportLyrics: () -> Unit,
    onPickArtwork: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onPlayNext: () -> Unit,
    onEnqueue: () -> Unit,
    onRemoveFromPlaylist: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onInfo: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TrackSheetHeader(track)
        TrackActionRow(stringResource(L10nR.string.feature_library_play_38419a), Icons.Rounded.PlayArrow, enabled = true, onClick = onPlay)
        if (canPlayNext) {
            TrackActionRow(
                stringResource(L10nR.string.feature_library_play_next_a1f73e),
                Icons.Rounded.SkipNext,
                enabled = true,
                onClick = onPlayNext,
            )
        }
        if (canEnqueue) {
            TrackActionRow(
                stringResource(L10nR.string.feature_library_add_to_queue_1775d3),
                Icons.Rounded.Queue,
                enabled = true,
                onClick = onEnqueue,
            )
        }
        if (canAddToPlaylist) {
            TrackActionRow(
                stringResource(L10nR.string.feature_library_add_to_playlist_0f8338),
                Icons.AutoMirrored.Rounded.PlaylistAdd,
                enabled = true,
                onClick = onAddToPlaylist,
            )
        }
        if (canMoveUp) {
            TrackActionRow(stringResource(L10nR.string.feature_library_move_up_e6d961), Icons.Rounded.KeyboardArrowUp, enabled = true, onClick = onMoveUp)
        }
        if (canMoveDown) {
            TrackActionRow(stringResource(L10nR.string.feature_library_move_down_cf81ae), Icons.Rounded.KeyboardArrowDown, enabled = true, onClick = onMoveDown)
        }
        if (canRemoveFromPlaylist) {
            TrackActionRow(
                stringResource(L10nR.string.feature_library_remove_from_playlist_f07718),
                Icons.Rounded.DeleteOutline,
                enabled = true,
                onClick = onRemoveFromPlaylist,
            )
        }
        TrackActionRow(stringResource(L10nR.string.feature_library_edit_tags_c8ec75), Icons.Rounded.Edit, enabled = canEditMetadata, onClick = onEdit)
        TrackActionRow(
            stringResource(L10nR.string.feature_library_import_lrc_lyrics_8e2810),
            Icons.Rounded.UploadFile,
            enabled = canImportLyrics,
            onClick = onImportLyrics,
        )
        TrackActionRow(
            stringResource(L10nR.string.feature_library_custom_artwork_c8c999),
            Icons.Rounded.Album,
            enabled = canPickArtwork,
            onClick = onPickArtwork,
        )
        TrackActionRow(stringResource(L10nR.string.feature_library_track_info_36a07b), Icons.Rounded.Info, enabled = true, onClick = onInfo)
    }
}

@Composable
private fun TrackSheetHeader(track: EchoTrack) {
    val subtitle = trackSubtitle(track)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ArtworkTile(
            track.artworkUri,
            Modifier.size(64.dp),
            accent = rememberLibraryArtworkAccent(),
            cornerRadius = 16.dp,
            elevation = 4.dp,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                displayMetadataOrUnknown(track.title, unknownTrackLabel()),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun TrackActionRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.42f
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .echoClickable(enabled = enabled, onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.58f else 0.28f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                modifier = Modifier.size(22.dp),
            )
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TrackMetadataEditorSheet(
    track: EchoTrack,
    onDismiss: () -> Unit,
    onSave: (EchoTrackMetadataUpdate) -> Unit,
) {
    var title by remember(track.id) { mutableStateOf(track.title) }
    var artist by remember(track.id) { mutableStateOf(track.artist) }
    var album by remember(track.id) { mutableStateOf(track.album.orEmpty()) }
    var albumArtist by remember(track.id) { mutableStateOf(track.albumArtist.orEmpty()) }
    var trackNumber by remember(track.id) { mutableStateOf(track.trackNumber?.toString().orEmpty()) }
    var discNumber by remember(track.id) { mutableStateOf(track.discNumber?.toString().orEmpty()) }
    var year by remember(track.id) { mutableStateOf(track.year?.toString().orEmpty()) }
    val canSave = title.isNotBlank() && artist.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 620.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TrackSheetHeader(track)
        Spacer(Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(L10nR.string.feature_library_edit_tags_c8ec75),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.titleLarge,
            )
            TextButton(onClick = onDismiss) {
                Text(stringResource(L10nR.string.feature_library_cancel_4c5fa5))
            }
        }
        TextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(stringResource(L10nR.string.feature_library_title_af1111)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TextField(
            value = artist,
            onValueChange = { artist = it },
            label = { Text(stringResource(L10nR.string.feature_library_artist_37d883)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TextField(
            value = album,
            onValueChange = { album = it },
            label = { Text(stringResource(L10nR.string.feature_library_album_eb13be)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TextField(
            value = albumArtist,
            onValueChange = { albumArtist = it },
            label = { Text(stringResource(L10nR.string.feature_library_album_artist_6defa0)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumericMetadataField(
                value = trackNumber,
                onValueChange = { trackNumber = it },
                label = stringResource(L10nR.string.feature_library_track_000e4d),
                modifier = Modifier.weight(1f),
            )
            NumericMetadataField(
                value = discNumber,
                onValueChange = { discNumber = it },
                label = stringResource(L10nR.string.feature_library_disc_3128c0),
                modifier = Modifier.weight(1f),
            )
            NumericMetadataField(
                value = year,
                onValueChange = { year = it },
                label = stringResource(L10nR.string.feature_library_year_fe373f),
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            stringResource(L10nR.string.feature_library_currently_saved_to_the_echoandroid_library_index_imported_e15a70),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Button(
            enabled = canSave,
            onClick = {
                onSave(
                    EchoTrackMetadataUpdate(
                        trackId = track.id,
                        title = title.trim(),
                        artist = artist.trim(),
                        album = album.trim().takeIf { it.isNotBlank() },
                        albumArtist = albumArtist.trim().takeIf { it.isNotBlank() },
                        trackNumber = trackNumber.toPositiveIntOrNull(),
                        discNumber = discNumber.toPositiveIntOrNull(),
                        year = year.toPositiveIntOrNull(),
                        artworkUri = track.artworkUri,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(L10nR.string.feature_library_save_68ae20))
        }
    }
}

@Composable
private fun TrackMetadataEditorDialog(
    track: EchoTrack,
    onDismiss: () -> Unit,
    onSave: (EchoTrackMetadataUpdate) -> Unit,
) {
    var title by remember(track.id) { mutableStateOf(track.title) }
    var artist by remember(track.id) { mutableStateOf(track.artist) }
    var album by remember(track.id) { mutableStateOf(track.album.orEmpty()) }
    var albumArtist by remember(track.id) { mutableStateOf(track.albumArtist.orEmpty()) }
    var trackNumber by remember(track.id) { mutableStateOf(track.trackNumber?.toString().orEmpty()) }
    var discNumber by remember(track.id) { mutableStateOf(track.discNumber?.toString().orEmpty()) }
    var year by remember(track.id) { mutableStateOf(track.year?.toString().orEmpty()) }
    val canSave = title.isNotBlank() && artist.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(L10nR.string.feature_library_edit_tags_c8ec75),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(L10nR.string.feature_library_title_af1111)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text(stringResource(L10nR.string.feature_library_artist_b6e7ad)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = album,
                    onValueChange = { album = it },
                    label = { Text(stringResource(L10nR.string.feature_library_album_eb13be)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = albumArtist,
                    onValueChange = { albumArtist = it },
                    label = { Text(stringResource(L10nR.string.feature_library_album_artist_8a1c9e)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumericMetadataField(
                        value = trackNumber,
                        onValueChange = { trackNumber = it },
                        label = stringResource(L10nR.string.feature_library_track_000e4d),
                        modifier = Modifier.weight(1f),
                    )
                    NumericMetadataField(
                        value = discNumber,
                        onValueChange = { discNumber = it },
                        label = stringResource(L10nR.string.feature_library_disc_3128c0),
                        modifier = Modifier.weight(1f),
                    )
                    NumericMetadataField(
                        value = year,
                        onValueChange = { year = it },
                        label = stringResource(L10nR.string.feature_library_year_fe373f),
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    stringResource(L10nR.string.feature_library_currently_saved_only_to_the_echoandroid_library_index_6c327c),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(
                        EchoTrackMetadataUpdate(
                            trackId = track.id,
                            title = title.trim(),
                            artist = artist.trim(),
                            album = album.trim().takeIf { it.isNotBlank() },
                            albumArtist = albumArtist.trim().takeIf { it.isNotBlank() },
                            trackNumber = trackNumber.toPositiveIntOrNull(),
                            discNumber = discNumber.toPositiveIntOrNull(),
                            year = year.toPositiveIntOrNull(),
                        ),
                    )
                },
            ) {
                Text(stringResource(L10nR.string.feature_library_save_68ae20))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(L10nR.string.feature_library_cancel_4c5fa5))
            }
        },
    )
}

@Composable
private fun NumericMetadataField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = value,
        onValueChange = { next -> onValueChange(next.filter(Char::isDigit).take(4)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun TrackInfoDialog(
    track: EchoTrack,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                displayMetadataOrUnknown(track.title, unknownTrackLabel()),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val notProvided = stringResource(L10nR.string.feature_library_not_available_7d3220)
                TrackInfoLine(
                    stringResource(L10nR.string.feature_library_artist_b6e7ad),
                    displayMetadataOrUnknown(track.artist, unknownArtistLabel()),
                )
                TrackInfoLine(
                    stringResource(L10nR.string.feature_library_album_eb13be),
                    displayMetadataOrUnknown(track.album, unknownAlbumLabel()),
                )
                TrackInfoLine(
                    stringResource(L10nR.string.feature_library_album_artist_8a1c9e),
                    track.albumArtist?.takeIf { it.isNotBlank() } ?: notProvided,
                )
                TrackInfoLine(
                    stringResource(L10nR.string.feature_library_track_000e4d),
                    track.trackNumber?.toString() ?: notProvided,
                )
                TrackInfoLine(
                    stringResource(L10nR.string.feature_library_disc_3128c0),
                    track.discNumber?.toString() ?: notProvided,
                )
                TrackInfoLine(
                    stringResource(L10nR.string.feature_library_year_fe373f),
                    track.year?.toString() ?: notProvided,
                )
                TrackInfoLine(
                    stringResource(L10nR.string.feature_library_format_a7775b),
                    formatTrackMimeType(track.mimeType)
                        ?: track.mimeType?.takeIf { it.isNotBlank() }
                        ?: notProvided,
                )
                TrackInfoLine(
                    stringResource(L10nR.string.feature_library_sample_rate_f6382c),
                    track.sampleRateHz?.let(::formatTrackSampleRate) ?: notProvided,
                )
                TrackInfoLine(
                    stringResource(L10nR.string.feature_library_duration_573e08),
                    formatDuration(track.durationMs),
                )
                TrackInfoLine(
                    stringResource(L10nR.string.feature_library_size_f63a75),
                    formatTrackFileSize(track.sizeBytes),
                )
                TrackInfoLine(stringResource(L10nR.string.feature_library_source_4aff16), track.source.id)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(L10nR.string.feature_library_done_a5e659))
            }
        },
    )
}

@Composable
private fun TrackInfoLine(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            modifier = Modifier.widthIn(min = 76.dp, max = 96.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TrackInfoTag(
    text: String,
    tone: TrackInfoTagTone,
) {
    val dark = LocalEchoDarkTheme.current
    val colors = remember(dark, tone) { trackInfoTagColors(tone, dark) }
    Surface(
        shape = RoundedCornerShape(7.dp),
        color = colors.background,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            color = colors.content,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                lineHeight = 12.sp,
            ),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
internal fun trackSubtitle(track: EchoTrack): String {
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
    }.joinToString(" / ")
}

private fun formatTrackSampleRate(hz: Int): String =
    if (hz % 1000 == 0) {
        "${hz / 1000}kHz"
    } else {
        String.format("%.1fkHz", hz / 1000.0)
    }

private fun String.toPositiveIntOrNull(): Int? =
    trim().toIntOrNull()?.takeIf { it > 0 }

@Composable
private fun formatTrackFileSize(bytes: Long): String =
    when {
        bytes <= 0L -> stringResource(L10nR.string.feature_library_not_available_7d3220)
        bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(bytes / (1024f * 1024f * 1024f))
        bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024f * 1024f))
        bytes >= 1024L -> "%.1f KB".format(bytes / 1024f)
        else -> "$bytes B"
    }

private fun formatTrackMimeType(mimeType: String?): String? {
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

private fun isHiResSampleRate(sampleRateHz: Int?): Boolean =
    sampleRateHz != null && sampleRateHz > 48_000

private enum class TrackInfoTagTone {
    Format,
    Neutral,
    Gold,
}

private data class TrackInfoTagColors(
    val background: Color,
    val border: Color,
    val content: Color,
)

private fun trackInfoTagColors(tone: TrackInfoTagTone, dark: Boolean): TrackInfoTagColors =
    when (tone) {
        TrackInfoTagTone.Format,
        TrackInfoTagTone.Neutral,
        -> if (dark) {
            TrackInfoTagColors(
                background = Color.White.copy(alpha = 0.08f),
                border = Color.White.copy(alpha = 0.12f),
                content = Color.White.copy(alpha = 0.78f),
            )
        } else {
            TrackInfoTagColors(
                background = Color(0xFFF4F4F5),
                border = Color(0xFFDDDEE2),
                content = Color(0xFF646870),
            )
        }
        TrackInfoTagTone.Gold -> if (dark) {
            TrackInfoTagColors(
                background = EchoColors.Brass.copy(alpha = 0.16f),
                border = EchoColors.Brass.copy(alpha = 0.32f),
                content = Color(0xFFE3C07A),
            )
        } else {
            TrackInfoTagColors(
                background = Color(0xFFFFF5DF),
                border = Color(0xFFEAD09A),
                content = Color(0xFF765516),
            )
        }
    }

internal val LibraryBottomControlsPadding = 150.dp
