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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import app.echo.android.design.ArtworkTile
import app.echo.android.design.EchoAccent
import app.echo.android.design.EchoAccentDeep
import app.echo.android.design.EchoContentMaxWidth
import app.echo.android.design.echoAccentColor
import app.echo.android.design.echoOnAccentColor

import app.echo.android.design.EchoGlassInk
import app.echo.android.design.EchoGlassPanel
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.RoonInk
import app.echo.android.design.RoonMuted
import app.echo.android.design.displayMetadataOrUnknown
import app.echo.android.design.formatDuration
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.EchoTrackMetadataUpdate
import app.echo.android.model.library.FolderSummary

private val FolderDetailBottomPadding = 168.dp
private const val FolderBackSwipeThresholdPx = 120f
private val FolderTitleShadow = Shadow(
    color = Color.Black.copy(alpha = 0.18f),
    offset = Offset(0f, 1.4f),
    blurRadius = 8f,
)

private data class FolderDetailColors(
    val surface: Color,
    val elevatedSurface: Color,
    val border: Color,
    val content: Color,
    val muted: Color,
)

@Composable
private fun rememberFolderDetailColors(): FolderDetailColors {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    // 列表行会高频调用,真正 remember 避免每次重组都分配
    return remember(scheme, dark) {
        FolderDetailColors(
            surface = if (dark) EchoGlassPanel.copy(alpha = 0.82f) else Color.White.copy(alpha = 0.94f),
            elevatedSurface = if (dark) EchoGlassInk.copy(alpha = 0.86f) else Color.White.copy(alpha = 0.98f),
            border = if (dark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.92f),
            content = if (dark) Color.White.copy(alpha = 0.96f) else RoonInk,
            muted = if (dark) Color.White.copy(alpha = 0.74f) else RoonMuted,
        )
    }
}

@Composable
internal fun FolderDetailPage(
    folder: FolderSummary,
    tracks: LazyPagingItems<EchoTrack>,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onPlayTrack: (EchoTrack) -> Unit,
    onUpdateTrackMetadata: ((EchoTrackMetadataUpdate) -> Unit)? = null,
    onImportLyrics: ((EchoTrack) -> Unit)? = null,
    onPickArtwork: ((EchoTrack) -> Unit)? = null,
    onMatchNeteaseMetadata: ((EchoTrack) -> Unit)? = null,
    onAddToPlaylist: ((EchoTrack) -> Unit)? = null,
    onPlayNext: ((EchoTrack) -> Unit)? = null,
    onEnqueue: ((EchoTrack) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = rememberFolderDetailColors()
    val heroArtworkUri = tracks.itemSnapshotList.items.firstOrNull()
        ?.artworkUri
        ?.takeIf { it.isNotBlank() }
        ?: tracks.itemSnapshotList.items.firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri
    Box(
        modifier = modifier
            .fillMaxSize()
            .folderBackSwipe(onBack),
    ) {
        FolderDetailBackground()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = FolderDetailBottomPadding),
        ) {
            item(key = "folder-hero") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = EchoContentMaxWidth)
                        .padding(horizontal = 20.dp),
                ) {
                    FolderDetailTopBar(onBack = onBack)
                    Spacer(Modifier.height(10.dp))
                    FolderHero(
                        folder = folder,
                        artworkUri = heroArtworkUri,
                        onPlayAll = onPlayAll,
                    )
                    Spacer(Modifier.height(14.dp))
                    FolderInsightGrid(folder = folder)
                    Spacer(Modifier.height(22.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(L10nR.string.feature_library_folder_tracks_859f02),
                            color = colors.content,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            libraryTrackCountLabel(folder.trackCount),
                            color = colors.muted,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }

            when {
                tracks.isInitialPagingLoad() -> item(key = "folder-loading") {
                    FolderDetailNotice(
                        stringResource(L10nR.string.feature_library_loading_folder_tracks_83d4ec),
                    )
                }
                tracks.isInitialPagingError() -> item(key = "folder-error") {
                    FolderDetailNotice(
                        stringResource(L10nR.string.feature_library_failed_to_load_folder_tracks_0966ba),
                    )
                }
                tracks.itemCount == 0 -> item(key = "folder-empty") {
                    FolderDetailNotice(
                        stringResource(L10nR.string.feature_library_this_folder_has_no_tracks_yet_f1a6e0),
                    )
                }
                else -> items(
                    count = tracks.itemCount,
                    key = { index -> tracks.peek(index)?.id ?: "folder-track-$index" },
                ) { index ->
                    tracks[index]?.let { track ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = EchoContentMaxWidth)
                                .padding(horizontal = 20.dp),
                        ) {
                            FolderTrackRow(
                                index = index,
                                track = track,
                                onClick = { onPlayTrack(track) },
                                onUpdateTrackMetadata = onUpdateTrackMetadata,
                                onImportLyrics = onImportLyrics,
                                onPickArtwork = onPickArtwork,
                                onMatchNeteaseMetadata = onMatchNeteaseMetadata,
                                onAddToPlaylist = onAddToPlaylist,
                                onPlayNext = onPlayNext,
                                onEnqueue = onEnqueue,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.folderBackSwipe(onBack: () -> Unit): Modifier = pointerInput(onBack) {
    var dragX = 0f
    detectHorizontalDragGestures(
        onDragStart = { dragX = 0f },
        onHorizontalDrag = { _, dragAmount -> dragX += dragAmount },
        onDragEnd = {
            if (dragX >= FolderBackSwipeThresholdPx) onBack()
            dragX = 0f
        },
        onDragCancel = { dragX = 0f },
    )
}

@Composable
private fun FolderDetailBackground() {
    val dark = LocalEchoDarkTheme.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(520.dp)
            .background(
                Brush.verticalGradient(
                    if (dark) {
                        listOf(
                            EchoGlassInk.copy(alpha = 0.24f),
                            EchoGlassPanel.copy(alpha = 0.10f),
                            Color.Transparent,
                        )
                    } else {
                        listOf(
                            Color.White.copy(alpha = 0.36f),
                            Color.White.copy(alpha = 0.14f),
                            Color.Transparent,
                        )
                    },
                ),
            ),
    )
}

@Composable
private fun FolderDetailTopBar(onBack: () -> Unit) {
    val colors = rememberFolderDetailColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(colors.elevatedSurface)
                .border(BorderStroke(1.dp, colors.border), CircleShape)
                .echoClickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(L10nR.string.feature_library_back_49093c),
                tint = colors.content,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun FolderHero(
    folder: FolderSummary,
    artworkUri: String?,
    onPlayAll: () -> Unit,
) {
    val colors = rememberFolderDetailColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        if (LocalEchoDarkTheme.current) EchoGlassPanel.copy(alpha = 0.88f) else Color.White.copy(alpha = 0.98f),
                        colors.surface,
                        colors.elevatedSurface,
                    ),
                ),
            )
            .border(BorderStroke(1.dp, colors.border), RoundedCornerShape(30.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtworkTile(
                artworkUri = artworkUri,
                modifier = Modifier.size(104.dp),
                accent = echoAccentColor(),
                showSignal = artworkUri.isNullOrBlank(),
                cornerRadius = 18.dp,
                elevation = 14.dp,
                placeholderIconSize = 44.dp,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    folderDisplayName(folder),
                    color = colors.content,
                    style = MaterialTheme.typography.headlineSmall.copy(shadow = FolderTitleShadow),
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    folderPathLabel(folder),
                    color = colors.muted,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(echoAccentColor())
                .echoClickable(onClick = onPlayAll),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = echoOnAccentColor(), modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(L10nR.string.feature_library_play_this_folder_77afdc),
                color = echoOnAccentColor(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun FolderInsightGrid(folder: FolderSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
            FolderInsightCard(
                Icons.Rounded.MusicNote,
                stringResource(L10nR.string.feature_library_songs_107b60),
                libraryTrackCountLabel(folder.trackCount),
                Modifier.weight(1f),
            )
            FolderInsightCard(
                Icons.Rounded.LibraryMusic,
                stringResource(L10nR.string.feature_library_albums_e68c2b),
                stringResource(L10nR.string.feature_library_folder_albumcount_albums_3c43c1, (folder.albumCount).toString()),
                Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
            FolderInsightCard(
                Icons.Rounded.GraphicEq,
                stringResource(L10nR.string.feature_library_duration_573e08),
                readableFolderDuration(folder.durationMs),
                Modifier.weight(1f),
            )
            FolderInsightCard(
                Icons.Rounded.FolderOpen,
                stringResource(L10nR.string.feature_library_size_49c9f8),
                formatFolderByteSize(folder.totalSizeBytes),
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FolderInsightCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val colors = rememberFolderDetailColors()
    Row(
        modifier = modifier
            .height(66.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        if (LocalEchoDarkTheme.current) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.72f),
                        colors.surface,
                    ),
                ),
            )
            .border(BorderStroke(1.dp, colors.border), RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = EchoAccentDeep, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = colors.muted, style = MaterialTheme.typography.labelMedium)
            Text(value, color = colors.content, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun FolderTrackRow(
    index: Int,
    track: EchoTrack,
    onClick: () -> Unit,
    onUpdateTrackMetadata: ((EchoTrackMetadataUpdate) -> Unit)? = null,
    onImportLyrics: ((EchoTrack) -> Unit)? = null,
    onPickArtwork: ((EchoTrack) -> Unit)? = null,
    onMatchNeteaseMetadata: ((EchoTrack) -> Unit)? = null,
    onAddToPlaylist: ((EchoTrack) -> Unit)? = null,
    onPlayNext: ((EchoTrack) -> Unit)? = null,
    onEnqueue: ((EchoTrack) -> Unit)? = null,
) {
    val colors = rememberFolderDetailColors()
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
        modifier = Modifier.fillMaxWidth(),
    ) { pressModifier ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp)
                .shadow(
                    elevation = 3.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = Color.Black.copy(alpha = 0.03f),
                    spotColor = EchoAccent.copy(alpha = 0.05f),
                )
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            if (LocalEchoDarkTheme.current) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.84f),
                            colors.elevatedSurface,
                        ),
                    ),
                )
                .border(BorderStroke(1.dp, colors.border.copy(alpha = 0.78f)), RoundedCornerShape(16.dp))
                .then(pressModifier)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = (index + 1).toString().padStart(2, '0'),
                color = EchoAccentDeep,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                modifier = Modifier.width(28.dp),
            )
            ArtworkTile(
                artworkUri = track.artworkUri,
                modifier = Modifier.size(58.dp),
                accent = echoAccentColor(),
                cornerRadius = 10.dp,
                elevation = 4.dp,
                placeholderIconSize = 27.dp,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    displayMetadataOrUnknown(track.title, unknownTrackLabel()),
                    color = colors.content,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
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
private fun FolderDetailNotice(message: String) {
    val colors = rememberFolderDetailColors()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = EchoContentMaxWidth)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(message, color = colors.muted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun folderPathLabel(folder: FolderSummary): String =
    folder.path?.takeIf { it.isNotBlank() }
        ?: stringResource(L10nR.string.feature_library_mediastore_did_not_provide_a_path_53b27e)

@Composable
private fun readableFolderDuration(durationMs: Long): String {
    val minutes = (durationMs / 60000L).toInt()
    return if (minutes >= 1) libraryMinutesLabel(minutes) else formatDuration(durationMs)
}

private fun formatFolderByteSize(bytes: Long): String =
    when {
        bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(bytes / (1024f * 1024f * 1024f))
        bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024f * 1024f))
        bytes >= 1024L -> "%.1f KB".format(bytes / 1024f)
        else -> "$bytes B"
    }
