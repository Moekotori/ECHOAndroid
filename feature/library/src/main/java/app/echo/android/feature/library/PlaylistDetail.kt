package app.echo.android.feature.library

import app.echo.android.feature.library.R as L10nR
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import app.echo.android.design.echoClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import app.echo.android.design.ArtworkPalette
import app.echo.android.design.ArtworkTile
import app.echo.android.design.BlurredArtworkBackground
import app.echo.android.design.EchoContentMaxWidth
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.displayMetadataOrUnknown
import app.echo.android.design.echoAccentColor
import app.echo.android.design.echoOnAccentColor
import app.echo.android.design.formatDuration
import app.echo.android.design.rememberArtworkPalette
import app.echo.android.design.echoTheme
import app.echo.android.model.library.EchoPlaylist
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.EchoTrackMetadataUpdate
import app.echo.android.model.library.LibrarySource

private val PlaylistDetailBottomPadding = 168.dp
private const val PlaylistBackSwipeThresholdPx = 120f
private val PlaylistTitleShadow = Shadow(
    color = Color.Black.copy(alpha = 0.22f),
    offset = Offset(0f, 1.4f),
    blurRadius = 8f,
)

private data class PlaylistDetailColors(
    val surface: Color,
    val elevatedSurface: Color,
    val border: Color,
    val content: Color,
    val muted: Color,
)

@Composable
private fun rememberPlaylistDetailColors(): PlaylistDetailColors {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    // 列表行会高频调用,真正 remember 避免每次重组都分配
    return remember(scheme, dark) {
        PlaylistDetailColors(
            surface = if (dark) scheme.surface.copy(alpha = 0.62f) else scheme.surface.copy(alpha = 0.86f),
            elevatedSurface = if (dark) scheme.surfaceVariant.copy(alpha = 0.38f) else Color.White.copy(alpha = 0.94f),
            border = if (dark) scheme.outlineVariant.copy(alpha = 0.34f) else scheme.outlineVariant.copy(alpha = 0.42f),
            content = scheme.onSurface.copy(alpha = if (dark) 0.96f else 0.92f),
            muted = scheme.onSurfaceVariant.copy(alpha = if (dark) 0.76f else 0.78f),
        )
    }
}

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
    onEnqueue: ((EchoTrack) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val loadedTracks = tracks.itemSnapshotList.items
    val coverUris = remember(playlist.artworkUri, loadedTracks) {
        playlistCoverUris(playlist, loadedTracks)
    }
    val palette = rememberArtworkPalette(
        artworkUri = coverUris.firstOrNull(),
        seedKey = playlist.id,
    )
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val colors = rememberPlaylistDetailColors()
    val durationMs = loadedTracks.sumOf { it.durationMs.coerceAtLeast(0L) }
        .takeIf { it > 0L }
        ?: 0L

    Box(
        modifier = modifier
            .fillMaxSize()
            .playlistBackSwipe(onBack),
    ) {
        PlaylistDetailBackground(
            artworkUri = coverUris.firstOrNull(),
            palette = palette,
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = PlaylistDetailBottomPadding),
        ) {
            item(key = "playlist-hero") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = EchoContentMaxWidth)
                        .padding(horizontal = 20.dp),
                ) {
                    PlaylistDetailTopBar(
                        canEdit = playlist.canEdit,
                        onBack = onBack,
                        onRename = { renaming = true },
                        onDelete = { deleting = true },
                    )
                    Spacer(Modifier.height(10.dp))
                    PlaylistHero(
                        playlist = playlist,
                        coverUris = coverUris,
                        palette = palette,
                        durationMs = durationMs,
                        onPlayAll = onPlayAll,
                        onShuffle = onShuffle,
                    )
                    Spacer(Modifier.height(16.dp))
                    PlaylistInsightRow(
                        trackCount = playlist.trackCount,
                        durationMs = durationMs,
                        playlist = playlist,
                    )
                    Spacer(Modifier.height(22.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(L10nR.string.feature_library_tracks_2d80e8),
                            color = colors.content,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            libraryTrackCountLabel(playlist.trackCount),
                            color = colors.muted,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }

            when {
                tracks.isInitialPagingLoad() -> item(key = "playlist-loading") {
                    PlaylistDetailNotice(
                        stringResource(L10nR.string.feature_library_loading_playlist_eb6248),
                    )
                }
                tracks.isInitialPagingError() -> item(key = "playlist-error") {
                    PlaylistDetailNotice(
                        stringResource(L10nR.string.feature_library_failed_to_load_playlist_tracks_9130da),
                    )
                }
                tracks.itemCount == 0 -> item(key = "playlist-empty") {
                    PlaylistDetailNotice(playlistEmptyMessage(playlist))
                }
                else -> items(
                    count = tracks.itemCount,
                    key = { index -> tracks.peek(index)?.id ?: "playlist-track-$index" },
                ) { index ->
                    tracks[index]?.let { track ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = EchoContentMaxWidth)
                                .padding(horizontal = 20.dp),
                        ) {
                            PlaylistTrackRow(
                                index = index,
                                track = track,
                                accent = palette.vibrant,
                                onClick = { onPlayTrack(track) },
                                onUpdateTrackMetadata = onUpdateTrackMetadata,
                                onImportLyrics = onImportLyrics,
                                onPickArtwork = onPickArtwork,
                                onMatchNeteaseMetadata = onMatchNeteaseMetadata,
                                onAddToPlaylist = onAddToPlaylist,
                                onPlayNext = onPlayNext,
                                onEnqueue = onEnqueue,
                                onRemoveFromPlaylist = onRemoveTrack.takeIf { playlist.canRemoveTracks },
                                onMoveUp = onMoveTrack.takeIf { playlist.canEdit && index > 0 }?.let { move ->
                                    { move(index, index - 1) }
                                },
                                onMoveDown = onMoveTrack.takeIf { playlist.canEdit && index < tracks.itemCount - 1 }?.let { move ->
                                    { move(index, index + 1) }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

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

private fun Modifier.playlistBackSwipe(onBack: () -> Unit): Modifier = pointerInput(onBack) {
    var dragX = 0f
    detectHorizontalDragGestures(
        onDragStart = { dragX = 0f },
        onHorizontalDrag = { _, dragAmount -> dragX += dragAmount },
        onDragEnd = {
            if (dragX >= PlaylistBackSwipeThresholdPx) onBack()
            dragX = 0f
        },
        onDragCancel = { dragX = 0f },
    )
}

@Composable
private fun PlaylistDetailBackground(
    artworkUri: String?,
    palette: ArtworkPalette,
) {
    val dark = LocalEchoDarkTheme.current
    Box(Modifier.fillMaxSize()) {
        BlurredArtworkBackground(
            artworkUri = artworkUri,
            palette = palette,
            modifier = Modifier.fillMaxSize(),
            artworkScale = 1.18f,
            artworkBlur = 22.dp,
            artworkAlpha = if (dark) 0.46f else 0.62f,
            overlayStartAlpha = 0.08f,
            overlayMidAlpha = 0.02f,
            overlayEndAlpha = if (dark) 0.22f else 0.08f,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (dark) {
                        Brush.verticalGradient(
                            0f to echoTheme().ink.copy(alpha = 0.18f),
                            0.46f to Color.Transparent,
                            1f to echoTheme().panel.copy(alpha = 0.16f),
                        )
                    } else {
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.28f),
                            0.42f to Color.White.copy(alpha = 0.12f),
                            1f to Color(0xFFF6F1F3).copy(alpha = 0.18f),
                        )
                    },
                ),
        )
    }
}

@Composable
private fun PlaylistDetailTopBar(
    canEdit: Boolean,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaylistRoundButton(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            description = stringResource(L10nR.string.feature_library_back_49093c),
            onClick = onBack,
        )
        if (canEdit) {
            Box {
                PlaylistRoundButton(
                    icon = Icons.Rounded.MoreVert,
                    description = stringResource(L10nR.string.feature_library_playlist_actions_044945),
                    onClick = { menuOpen = true },
                )
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(L10nR.string.feature_library_rename_d1a28e)) },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(L10nR.string.feature_library_delete_138ccf)) },
                        leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistRoundButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    val colors = rememberPlaylistDetailColors()
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(colors.elevatedSurface)
            .border(BorderStroke(1.dp, colors.border), CircleShape)
            .echoClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = colors.content, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun PlaylistHero(
    playlist: EchoPlaylist,
    coverUris: List<String>,
    palette: ArtworkPalette,
    durationMs: Long,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
) {
    val colors = rememberPlaylistDetailColors()
    val dark = LocalEchoDarkTheme.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PlaylistCover(
            playlist = playlist,
            coverUris = coverUris,
            accent = palette.vibrant,
            modifier = Modifier.size(196.dp),
        )
        Spacer(Modifier.height(18.dp))
        Text(
            playlistDisplayName(playlist),
            color = colors.content,
            style = MaterialTheme.typography.headlineSmall.copy(shadow = if (dark) PlaylistTitleShadow else null),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            playlistHeroCaption(playlist, durationMs),
            color = colors.muted,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlaylistActionButton(
                icon = Icons.Rounded.PlayArrow,
                label = stringResource(L10nR.string.feature_library_play_all_55c80e),
                filled = true,
                onClick = onPlayAll,
                modifier = Modifier.weight(1f),
            )
            PlaylistActionButton(
                icon = Icons.Rounded.Shuffle,
                label = stringResource(L10nR.string.feature_library_shuffle_34e7ce),
                filled = false,
                onClick = onShuffle,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PlaylistCover(
    playlist: EchoPlaylist,
    coverUris: List<String>,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .shadow(elevation = 20.dp, shape = shape, clip = false)
            .clip(shape),
    ) {
        when {
            playlist.isLikedSongs && coverUris.isEmpty() -> PlaylistLikedCover(accent = accent)
            coverUris.size <= 1 -> ArtworkTile(
                artworkUri = coverUris.firstOrNull() ?: playlist.artworkUri,
                modifier = Modifier.fillMaxSize(),
                accent = accent,
                showSignal = coverUris.isEmpty() && playlist.artworkUri.isNullOrBlank(),
                cornerRadius = 0.dp,
                elevation = 0.dp,
                placeholderIconSize = 64.dp,
            )
            else -> PlaylistCoverMosaic(coverUris = coverUris, accent = accent)
        }
    }
}

@Composable
private fun PlaylistLikedCover(accent: Color) {
    val dark = LocalEchoDarkTheme.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(
                        accent.copy(alpha = if (dark) 0.88f else 0.92f),
                        accent.copy(alpha = 0.58f),
                        Color(0xFF3A2430),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Favorite,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.94f),
            modifier = Modifier.size(72.dp),
        )
    }
}

@Composable
private fun PlaylistCoverMosaic(
    coverUris: List<String>,
    accent: Color,
) {
    val cells = (coverUris + List(4) { null }).take(4)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.weight(1f)) {
            MosaicCell(cells.getOrNull(0), accent, Modifier.weight(1f))
            MosaicCell(cells.getOrNull(1), accent, Modifier.weight(1f))
        }
        Row(Modifier.weight(1f)) {
            MosaicCell(cells.getOrNull(2), accent, Modifier.weight(1f))
            MosaicCell(cells.getOrNull(3), accent, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MosaicCell(
    artworkUri: String?,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(0.6.dp),
    ) {
        if (artworkUri.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(accent.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = null,
                    tint = accent.copy(alpha = 0.72f),
                    modifier = Modifier.size(22.dp),
                )
            }
        } else {
            ArtworkTile(
                artworkUri = artworkUri,
                modifier = Modifier.fillMaxSize(),
                accent = accent,
                cornerRadius = 0.dp,
                elevation = 0.dp,
                placeholderIconSize = 22.dp,
            )
        }
    }
}

@Composable
private fun PlaylistActionButton(
    icon: ImageVector,
    label: String,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberPlaylistDetailColors()
    val shape = RoundedCornerShape(26.dp)
    val container = if (filled) echoAccentColor() else colors.elevatedSurface
    val content = if (filled) echoOnAccentColor() else echoAccentColor()
    val border = if (filled) Color.Transparent else echoAccentColor().copy(alpha = 0.28f)
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(shape)
            .background(container)
            .border(BorderStroke(1.dp, border), shape)
            .echoClickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(if (filled) 24.dp else 22.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            color = content,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlaylistInsightRow(
    trackCount: Int,
    durationMs: Long,
    playlist: EchoPlaylist,
) {
    val colors = rememberPlaylistDetailColors()
    val dark = LocalEchoDarkTheme.current
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(74.dp)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    if (dark) {
                        listOf(
                            scheme.surface.copy(alpha = 0.54f),
                            scheme.surfaceVariant.copy(alpha = 0.30f),
                        )
                    } else {
                        listOf(
                            Color.White.copy(alpha = 0.86f),
                            scheme.surface.copy(alpha = 0.78f),
                        )
                    },
                ),
            )
            .border(BorderStroke(1.dp, colors.border), shape)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaylistInsightCell(
            icon = Icons.Rounded.MusicNote,
            label = stringResource(L10nR.string.feature_library_songs_107b60),
            value = libraryTrackCountLabel(trackCount),
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(38.dp)
                .background(colors.border),
        )
        PlaylistInsightCell(
            icon = Icons.Rounded.GraphicEq,
            label = stringResource(L10nR.string.feature_library_duration_573e08),
            value = if (durationMs > 0L) readablePlaylistDuration(durationMs) else stringResource(L10nR.string.feature_library_text_e9396c),
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(38.dp)
                .background(colors.border),
        )
        PlaylistInsightCell(
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
            label = stringResource(L10nR.string.feature_library_kind_2dcbb1),
            value = playlistKindLabel(playlist),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PlaylistInsightCell(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val colors = rememberPlaylistDetailColors()
    val accent = echoAccentColor()
    Row(
        modifier = modifier.padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = accent.copy(alpha = 0.86f), modifier = Modifier.size(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = colors.muted, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            Text(
                value,
                color = colors.content,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PlaylistTrackRow(
    index: Int,
    track: EchoTrack,
    accent: Color,
    onClick: () -> Unit,
    onUpdateTrackMetadata: (suspend (EchoTrackMetadataUpdate) -> Unit)?,
    onImportLyrics: ((EchoTrack) -> Unit)?,
    onPickArtwork: ((EchoTrack) -> Unit)?,
    onMatchNeteaseMetadata: ((EchoTrack) -> Unit)?,
    onAddToPlaylist: ((EchoTrack) -> Unit)?,
    onPlayNext: ((EchoTrack) -> Unit)?,
    onEnqueue: ((EchoTrack) -> Unit)?,
    onRemoveFromPlaylist: ((EchoTrack) -> Unit)?,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
) {
    val colors = rememberPlaylistDetailColors()
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
        modifier = Modifier.fillMaxWidth(),
    ) { pressModifier ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .shadow(
                    elevation = 2.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = Color.Black.copy(alpha = 0.04f),
                    spotColor = accent.copy(alpha = 0.06f),
                )
                .clip(RoundedCornerShape(16.dp))
                .background(colors.elevatedSurface)
                .border(BorderStroke(1.dp, colors.border.copy(alpha = 0.78f)), RoundedCornerShape(16.dp))
                .then(pressModifier)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = (index + 1).toString().padStart(2, '0'),
                color = accent.copy(alpha = 0.86f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                modifier = Modifier.width(28.dp),
            )
            ArtworkTile(
                artworkUri = track.artworkUri,
                modifier = Modifier.size(52.dp),
                accent = accent,
                cornerRadius = 10.dp,
                elevation = 3.dp,
                placeholderIconSize = 24.dp,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    displayMetadataOrUnknown(track.title, unknownTrackLabel()),
                    color = colors.content,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    trackSubtitle(track),
                    color = colors.muted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                formatDuration(track.durationMs),
                color = colors.muted,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PlaylistDetailNotice(message: String) {
    val colors = rememberPlaylistDetailColors()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = EchoContentMaxWidth)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(message, color = colors.muted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun playlistHeroCaption(playlist: EchoPlaylist, durationMs: Long): String {
    val parts = buildList {
        add(libraryTrackCountLabel(playlist.trackCount))
        if (durationMs > 0L) add(readablePlaylistDuration(durationMs))
        add(playlistKindLabel(playlist))
    }
    return parts.joinToString(" · ")
}

@Composable
internal fun playlistDisplayName(playlist: EchoPlaylist): String =
    if (playlist.isLikedSongs) {
        stringResource(L10nR.string.feature_library_liked_songs_8d6245)
    } else {
        playlist.name
    }

@Composable
internal fun playlistCaption(playlist: EchoPlaylist): String {
    val count = playlist.trackCount
    return when {
        playlist.isLikedSongs -> stringResource(L10nR.string.feature_library_count_tracks_liked_songs_e36fb7, (count).toString())
        playlist.canEdit -> stringResource(L10nR.string.feature_library_count_tracks_local_playlist_352a0d, (count).toString())
        else -> stringResource(L10nR.string.feature_library_count_tracks_navidrome_294a65, (count).toString())
    }
}

@Composable
private fun playlistKindLabel(playlist: EchoPlaylist): String = when {
    playlist.isLikedSongs -> stringResource(L10nR.string.feature_library_liked_c8ac9d)
    playlist.source == LibrarySource.MediaStore.id -> stringResource(L10nR.string.feature_library_local_9b5178)
    else -> stringResource(L10nR.string.feature_library_linked_5d42a7)
}

@Composable
private fun playlistEmptyMessage(playlist: EchoPlaylist): String =
    if (playlist.isLikedSongs) {
        stringResource(L10nR.string.feature_library_no_liked_songs_yet_heart_a_track_from_ba96cc)
    } else {
        stringResource(L10nR.string.feature_library_this_playlist_is_empty_add_songs_from_a_bebd89)
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
    val fromTracks = tracks.mapNotNull { it.artworkUri?.takeIf(String::isNotBlank) }.distinct()
    if (fromTracks.isNotEmpty()) return fromTracks.take(4)
    return listOfNotNull(playlist.artworkUri?.takeIf(String::isNotBlank))
}
