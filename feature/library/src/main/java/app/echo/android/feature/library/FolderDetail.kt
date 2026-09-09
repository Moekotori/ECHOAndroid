package app.echo.android.feature.library

import app.echo.android.feature.library.R as L10nR
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import app.echo.android.design.ArtworkTile
import app.echo.android.design.EchoContentMaxWidth
import app.echo.android.design.formatDuration
import app.echo.android.design.rememberArtworkPalette
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.EchoTrackMetadataUpdate
import app.echo.android.model.library.FolderSummary

private val FolderTitleShadow = Shadow(
    color = Color.Black.copy(alpha = 0.28f),
    offset = Offset(0f, 1.5f),
    blurRadius = 8f,
)

@Composable
internal fun FolderDetailPage(
    folder: FolderSummary,
    tracks: LazyPagingItems<EchoTrack>,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
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
    val heroArtworkUri = folder.artworkUri?.takeIf { it.isNotBlank() }
        ?: tracks.itemSnapshotList.items.firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri
    val palette = rememberArtworkPalette(heroArtworkUri, seedKey = folder.folderKey)
    Box(
        modifier = modifier
            .fillMaxSize()
            .detailBackSwipe(onBack),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(440.dp)
                .background(
                    Brush.verticalGradient(
                        0f to palette.vibrant.copy(alpha = 0.34f),
                        0.45f to palette.deep.copy(alpha = 0.18f),
                        1f to Color.Transparent,
                    ),
                ),
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = AlbumDetailBottomPadding),
        ) {
            item(key = "folder-hero") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = EchoContentMaxWidth)
                        .padding(horizontal = 20.dp),
                ) {
                    AlbumDetailTopBar(onBack = onBack)
                    Spacer(Modifier.height(8.dp))
                    FolderHero(
                        folder = folder,
                        artworkUri = heroArtworkUri,
                    )
                    Spacer(Modifier.height(18.dp))
                    AlbumActionBar(onPlayAll = onPlayAll, onShuffle = onShuffle)
                    Spacer(Modifier.height(22.dp))
                    AlbumTracksHeader(count = folder.trackCount)
                    Spacer(Modifier.height(10.dp))
                }
            }

            when {
                tracks.isInitialPagingLoad() -> item(key = "folder-loading") {
                    AlbumDetailNotice(
                        stringResource(L10nR.string.feature_library_loading_folder_tracks_83d4ec),
                    )
                }
                tracks.isInitialPagingError() -> item(key = "folder-error") {
                    AlbumDetailNotice(
                        stringResource(L10nR.string.feature_library_failed_to_load_folder_tracks_0966ba),
                    )
                }
                tracks.itemCount == 0 -> item(key = "folder-empty") {
                    AlbumDetailNotice(
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
                            AlbumTrackRow(
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
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderHero(
    folder: FolderSummary,
    artworkUri: String?,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtworkTile(
            artworkUri = artworkUri,
            modifier = Modifier
                .padding(top = 6.dp)
                .size(200.dp),
            accent = MaterialTheme.colorScheme.primary,
            showSignal = artworkUri.isNullOrBlank(),
            cornerRadius = 8.dp,
            elevation = 22.dp,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            folderDisplayName(folder),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineSmall.copy(shadow = FolderTitleShadow),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            folderPathLabel(folder),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            folderMetaLine(folder),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun folderPathLabel(folder: FolderSummary): String =
    folder.path?.takeIf { it.isNotBlank() }
        ?: stringResource(L10nR.string.feature_library_mediastore_did_not_provide_a_path_53b27e)

@Composable
private fun folderMetaLine(folder: FolderSummary): String {
    val parts = mutableListOf(
        libraryTrackCountLabel(folder.trackCount),
        libraryAlbumCountLabel(folder.albumCount),
    )
    if (folder.durationMs > 0L) {
        val minutes = (folder.durationMs / 60000L).toInt()
        parts += if (minutes >= 1) libraryMinutesLabel(minutes) else formatDuration(folder.durationMs)
    }
    if (folder.totalSizeBytes > 0L) {
        parts += formatFolderByteSize(folder.totalSizeBytes)
    }
    return parts.joinToString(" · ")
}

private fun formatFolderByteSize(bytes: Long): String =
    when {
        bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(bytes / (1024f * 1024f * 1024f))
        bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024f * 1024f))
        bytes >= 1024L -> "%.1f KB".format(bytes / 1024f)
        else -> "$bytes B"
    }
