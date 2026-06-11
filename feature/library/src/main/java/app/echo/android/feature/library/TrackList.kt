package app.echo.android.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
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
import app.echo.android.design.EchoAccent
import app.echo.android.design.LocalEchoDarkTheme
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
                    track = track,
                    onClick = { onPlayTrack(track) },
                    onUpdateTrackMetadata = onUpdateTrackMetadata,
                    onImportLyrics = onImportLyrics,
                    onPickArtwork = onPickArtwork,
                    onMatchNeteaseMetadata = onMatchNeteaseMetadata,
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
                track = track,
                onClick = { onPlayTrack(track) },
                onUpdateTrackMetadata = onUpdateTrackMetadata,
                onImportLyrics = onImportLyrics,
                onPickArtwork = onPickArtwork,
                onMatchNeteaseMetadata = onMatchNeteaseMetadata,
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
    showAudioInfoTags: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    val subtitle = remember(track.artist, track.album) { trackSubtitle(track) }
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
        modifier = Modifier
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
                    accent = EchoAccent,
                    cornerRadius = 12.dp,
                    elevation = 3.dp,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        track.title,
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
            Modifier.combinedClickable(
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
                text = { Text("播放") },
                leadingIcon = {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    onPlay()
                },
            )
            DropdownMenuItem(
                text = { Text("编辑标签") },
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
                    text = { Text("导入歌词") },
                    onClick = {
                        expanded = false
                        onImportLyrics(track)
                    },
                )
            }
            if (onPickArtwork != null) {
                DropdownMenuItem(
                    text = { Text("更换封面") },
                    onClick = {
                        expanded = false
                        onPickArtwork(track)
                    },
                )
            }
            if (onMatchNeteaseMetadata != null) {
                DropdownMenuItem(
                    text = { Text("匹配网易云信息") },
                    onClick = {
                        expanded = false
                        onMatchNeteaseMetadata(track)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("歌曲信息") },
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
                    canMatchNeteaseMetadata = onMatchNeteaseMetadata != null && track.source == LibrarySource.MediaStore,
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
                    onMatchNeteaseMetadata = {
                        sheetMode = null
                        onMatchNeteaseMetadata?.invoke(track)
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
    canMatchNeteaseMetadata: Boolean,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onImportLyrics: () -> Unit,
    onPickArtwork: () -> Unit,
    onMatchNeteaseMetadata: () -> Unit,
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
        TrackActionRow("播放", Icons.Rounded.PlayArrow, enabled = true, onClick = onPlay)
        TrackActionRow("编辑标签", Icons.Rounded.Edit, enabled = canEditMetadata, onClick = onEdit)
        TrackActionRow("导入 LRC 歌词", Icons.Rounded.UploadFile, enabled = canImportLyrics, onClick = onImportLyrics)
        TrackActionRow("自定义封面", Icons.Rounded.Album, enabled = canPickArtwork, onClick = onPickArtwork)
        TrackActionRow("网易云匹配并写入", Icons.Rounded.CloudQueue, enabled = canMatchNeteaseMetadata, onClick = onMatchNeteaseMetadata)
        TrackActionRow("歌曲信息", Icons.Rounded.Info, enabled = true, onClick = onInfo)
    }
}

@Composable
private fun TrackSheetHeader(track: EchoTrack) {
    val subtitle = remember(track.artist, track.album) { trackSubtitle(track) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ArtworkTile(
            track.artworkUri,
            Modifier.size(64.dp),
            accent = EchoAccent,
            cornerRadius = 16.dp,
            elevation = 4.dp,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                track.title,
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
            .clickable(enabled = enabled, onClick = onClick),
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
                "编辑标签",
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.titleLarge,
            )
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
        TextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("标题") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TextField(
            value = artist,
            onValueChange = { artist = it },
            label = { Text("艺人") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TextField(
            value = album,
            onValueChange = { album = it },
            label = { Text("专辑") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TextField(
            value = albumArtist,
            onValueChange = { albumArtist = it },
            label = { Text("专辑艺人") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumericMetadataField(
                value = trackNumber,
                onValueChange = { trackNumber = it },
                label = "音轨",
                modifier = Modifier.weight(1f),
            )
            NumericMetadataField(
                value = discNumber,
                onValueChange = { discNumber = it },
                label = "碟号",
                modifier = Modifier.weight(1f),
            )
            NumericMetadataField(
                value = year,
                onValueChange = { year = it },
                label = "年份",
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            "当前保存到 ECHOAndroid 曲库索引；导入歌词、封面与网易云匹配也会绑定到这首歌。",
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
            Text("保存")
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
                "编辑标签",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("艺术家") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = album,
                    onValueChange = { album = it },
                    label = { Text("专辑") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = albumArtist,
                    onValueChange = { albumArtist = it },
                    label = { Text("专辑艺术家") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumericMetadataField(
                        value = trackNumber,
                        onValueChange = { trackNumber = it },
                        label = "音轨",
                        modifier = Modifier.weight(1f),
                    )
                    NumericMetadataField(
                        value = discNumber,
                        onValueChange = { discNumber = it },
                        label = "碟号",
                        modifier = Modifier.weight(1f),
                    )
                    NumericMetadataField(
                        value = year,
                        onValueChange = { year = it },
                        label = "年份",
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    "当前只保存到 ECHOAndroid 曲库索引，不直接写入音频文件。",
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
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
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
                track.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TrackInfoLine("艺术家", track.artist.ifBlank { "未知艺术家" })
                TrackInfoLine("专辑", track.album?.takeIf { it.isNotBlank() } ?: "未知专辑")
                TrackInfoLine("专辑艺术家", track.albumArtist?.takeIf { it.isNotBlank() } ?: "未提供")
                TrackInfoLine("音轨", track.trackNumber?.toString() ?: "未提供")
                TrackInfoLine("碟号", track.discNumber?.toString() ?: "未提供")
                TrackInfoLine("年份", track.year?.toString() ?: "未提供")
                TrackInfoLine(
                    "格式",
                    formatTrackMimeType(track.mimeType)
                        ?: track.mimeType?.takeIf { it.isNotBlank() }
                        ?: "未提供",
                )
                TrackInfoLine("采样率", track.sampleRateHz?.let(::formatTrackSampleRate) ?: "未提供")
                TrackInfoLine("时长", formatDuration(track.durationMs))
                TrackInfoLine("大小", formatTrackFileSize(track.sizeBytes))
                TrackInfoLine("来源", track.source.id)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成")
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
    Surface(
        shape = RoundedCornerShape(7.dp),
        color = tone.background,
        border = BorderStroke(1.dp, tone.border),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            color = tone.content,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                lineHeight = 12.sp,
            ),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

internal fun trackSubtitle(track: EchoTrack): String =
    listOf(track.artist, track.album)
        .mapNotNull { value -> value?.takeIf { it.isNotBlank() } }
        .ifEmpty { listOf("本机音频") }
        .joinToString(" / ")

private fun formatTrackSampleRate(hz: Int): String =
    if (hz % 1000 == 0) {
        "${hz / 1000}kHz"
    } else {
        String.format("%.1fkHz", hz / 1000.0)
    }

private fun String.toPositiveIntOrNull(): Int? =
    trim().toIntOrNull()?.takeIf { it > 0 }

private fun formatTrackFileSize(bytes: Long): String =
    when {
        bytes <= 0L -> "未提供"
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

private enum class TrackInfoTagTone(
    val background: Color,
    val border: Color,
    val content: Color,
) {
    Format(
        background = Color(0xFFEFF7FF),
        border = Color(0xFFCFE7FF),
        content = Color(0xFF1375B8),
    ),
    Neutral(
        background = Color(0xFFF4F4F5),
        border = Color(0xFFDDDEE2),
        content = Color(0xFF646870),
    ),
    Gold(
        background = Color(0xFFFFF5DF),
        border = Color(0xFFEAD09A),
        content = Color(0xFF765516),
    ),
}

internal val LibraryBottomControlsPadding = 150.dp
