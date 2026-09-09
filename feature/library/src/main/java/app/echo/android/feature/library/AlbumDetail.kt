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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import app.echo.android.design.ArtworkPalette
import app.echo.android.design.ArtworkTile
import app.echo.android.design.EchoContentMaxWidth
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.displayMetadataOrUnknown
import app.echo.android.design.formatDuration
import app.echo.android.design.rememberArtworkPalette
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.ArtistSummary
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.EchoTrackMetadataUpdate

internal val AlbumDetailBottomPadding = 168.dp
private val LocalAlbumDetail = staticCompositionLocalOf { false }
private val AlbumTextShadow = Shadow(
    color = Color.Black.copy(alpha = 0.28f),
    offset = Offset(0f, 1.5f),
    blurRadius = 8f,
)
private const val DetailBackSwipeThresholdPx = 120f

private data class DetailGlassColors(
    val surface: Color,
    val elevatedSurface: Color,
    val border: Color,
    val content: Color,
    val muted: Color,
)

@Composable
private fun rememberDetailGlassColors(): DetailGlassColors {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    // 列表行会高频调用,真正 remember 避免每次重组都分配
    return remember(scheme, dark) {
        DetailGlassColors(
            surface = if (dark) scheme.surface.copy(alpha = 0.62f) else scheme.surface.copy(alpha = 0.78f),
            elevatedSurface = if (dark) scheme.surfaceVariant.copy(alpha = 0.34f) else scheme.surface.copy(alpha = 0.82f),
            border = if (dark) scheme.outlineVariant.copy(alpha = 0.34f) else scheme.outlineVariant.copy(alpha = 0.46f),
            content = scheme.onSurface.copy(alpha = if (dark) 0.94f else 0.90f),
            muted = scheme.onSurfaceVariant.copy(alpha = if (dark) 0.72f else 0.76f),
        )
    }
}

@Composable
internal fun AlbumDetailPage(
    album: AlbumSummary,
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
    val palette = rememberArtworkPalette(album.artworkUri, seedKey = album.albumKey)
    val loadedTracks = tracks.itemSnapshotList.items
    CompositionLocalProvider(LocalAlbumDetail provides true) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .detailBackSwipe(onBack),
        ) {
            AlbumDetailLightBackground(
                artworkUri = album.artworkUri,
                palette = palette,
                modifier = Modifier.fillMaxSize(),
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                contentPadding = PaddingValues(bottom = AlbumDetailBottomPadding),
            ) {
                item(key = "hero") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = EchoContentMaxWidth)
                            .padding(horizontal = 24.dp),
                    ) {
                        AlbumDetailTopBar(onBack = onBack)
                        Spacer(Modifier.height(8.dp))
                        AlbumHero(album = album)
                        Spacer(Modifier.height(18.dp))
                        AlbumActionBar(onPlayAll = onPlayAll, onShuffle = onShuffle)
                        Spacer(Modifier.height(28.dp))
                        AlbumTracksHeader(
                            count = album.trackCount,
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }

                when {
                    tracks.isInitialPagingLoad() -> item(key = "loading") {
                        AlbumDetailNotice(stringResource(L10nR.string.feature_library_loading_tracks_8e2147))
                    }
                    tracks.isInitialPagingError() -> item(key = "error") {
                        AlbumDetailNotice(stringResource(L10nR.string.feature_library_failed_to_load_tracks_f65c9b))
                    }
                    tracks.itemCount == 0 -> item(key = "empty") {
                        AlbumDetailNotice(stringResource(L10nR.string.feature_library_no_tracks_yet_c4614a))
                    }
                    else -> items(
                        count = tracks.itemCount,
                        key = { index -> tracks.peek(index)?.id ?: "track-$index" },
                    ) { index ->
                        tracks[index]?.let { track ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = EchoContentMaxWidth)
                                    .padding(horizontal = 24.dp),
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
                item(key = "album-online-information") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
                        AlbumOnlineInformation(album)
                    }
                }
                item(key = "album-details") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        Spacer(Modifier.height(16.dp))
                        AlbumInformation(
                            album = album,
                            tracks = loadedTracks,
                        )
                    }
                }

            }
        }
    }
}

@Composable
internal fun AlbumDetailListPage(
    album: AlbumSummary,
    tracks: List<EchoTrack>,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onPlayOnPc: (() -> Unit)? = null,
    onPlayTrack: (EchoTrack) -> Unit,
    onUpdateTrackMetadata: ((EchoTrackMetadataUpdate) -> Unit)? = null,
    onImportLyrics: ((EchoTrack) -> Unit)? = null,
    onPickArtwork: ((EchoTrack) -> Unit)? = null,
    onMatchNeteaseMetadata: ((EchoTrack) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val palette = rememberArtworkPalette(album.artworkUri, seedKey = album.albumKey)
    CompositionLocalProvider(LocalAlbumDetail provides true) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .detailBackSwipe(onBack),
        ) {
            AlbumDetailLightBackground(
                artworkUri = album.artworkUri,
                palette = palette,
                modifier = Modifier.fillMaxSize(),
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                contentPadding = PaddingValues(bottom = AlbumDetailBottomPadding),
            ) {
                item(key = "hero") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = EchoContentMaxWidth)
                            .padding(horizontal = 24.dp),
                    ) {
                        AlbumDetailTopBar(onBack = onBack)
                        Spacer(Modifier.height(8.dp))
                        AlbumHero(album = album)
                        Spacer(Modifier.height(18.dp))
                        AlbumActionBar(onPlayAll = onPlayAll, onShuffle = onShuffle, onPlayOnPc = onPlayOnPc)
                        Spacer(Modifier.height(28.dp))
                        AlbumTracksHeader(
                            count = album.trackCount,
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }

                if (tracks.isEmpty()) {
                    item(key = "empty") {
                        AlbumDetailNotice(stringResource(L10nR.string.feature_library_no_tracks_yet_c4614a))
                    }
                } else {
                    itemsIndexed(
                        items = tracks,
                        key = { _, track -> track.id },
                    ) { index, track ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = EchoContentMaxWidth)
                                .padding(horizontal = 24.dp),
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
                            )
                        }
                    }
                }
                item(key = "album-online-information") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
                        AlbumOnlineInformation(album)
                    }
                }
                item(key = "album-details") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        Spacer(Modifier.height(16.dp))
                        AlbumInformation(
                            album = album,
                            tracks = tracks,
                        )
                    }
                }
            }
        }
    }
}


@Composable
internal fun ArtistDetailPage(
    artist: ArtistSummary,
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
    val palette = rememberArtworkPalette(artist.artworkUri, seedKey = artist.artistKey)
    val loadedTracks = tracks.itemSnapshotList.items
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
            item(key = "hero") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = EchoContentMaxWidth)
                        .padding(horizontal = 20.dp),
                ) {
                    AlbumDetailTopBar(onBack = onBack)
                    Spacer(Modifier.height(8.dp))
                    ArtistHero(artist = artist, palette = palette)
                    Spacer(Modifier.height(18.dp))
                    AlbumActionBar(onPlayAll = onPlayAll, onShuffle = onShuffle)
                    Spacer(Modifier.height(18.dp))
                    AlbumDetailInsights(
                        source = sourceInsight(loadedTracks),
                        info = formatInsight(loadedTracks),
                        palette = palette,
                    )
                    Spacer(Modifier.height(22.dp))
                    AlbumTracksHeader(count = artist.trackCount)
                    Spacer(Modifier.height(10.dp))
                }
            }

            when {
                tracks.isInitialPagingLoad() -> item(key = "loading") {
                    AlbumDetailNotice(stringResource(L10nR.string.feature_library_loading_tracks_8e2147))
                }
                tracks.isInitialPagingError() -> item(key = "error") {
                    AlbumDetailNotice(stringResource(L10nR.string.feature_library_failed_to_load_tracks_f65c9b))
                }
                tracks.itemCount == 0 -> item(key = "empty") {
                    AlbumDetailNotice(stringResource(L10nR.string.feature_library_no_tracks_yet_c4614a))
                }
                else -> items(
                    count = tracks.itemCount,
                    key = { index -> tracks.peek(index)?.id ?: "track-$index" },
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
internal fun ArtistDetailListPage(
    artist: ArtistSummary,
    tracks: List<EchoTrack>,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onPlayOnPc: (() -> Unit)? = null,
    onPlayTrack: (EchoTrack) -> Unit,
    onUpdateTrackMetadata: ((EchoTrackMetadataUpdate) -> Unit)? = null,
    onImportLyrics: ((EchoTrack) -> Unit)? = null,
    onPickArtwork: ((EchoTrack) -> Unit)? = null,
    onMatchNeteaseMetadata: ((EchoTrack) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val palette = rememberArtworkPalette(artist.artworkUri, seedKey = artist.artistKey)
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
            item(key = "hero") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = EchoContentMaxWidth)
                        .padding(horizontal = 20.dp),
                ) {
                    AlbumDetailTopBar(onBack = onBack)
                    Spacer(Modifier.height(8.dp))
                    ArtistHero(artist = artist, palette = palette)
                    Spacer(Modifier.height(18.dp))
                    AlbumActionBar(onPlayAll = onPlayAll, onShuffle = onShuffle, onPlayOnPc = onPlayOnPc)
                    Spacer(Modifier.height(18.dp))
                    AlbumDetailInsights(
                        source = sourceInsight(tracks),
                        info = formatInsight(tracks),
                        palette = palette,
                    )
                    Spacer(Modifier.height(22.dp))
                    AlbumTracksHeader(count = artist.trackCount)
                    Spacer(Modifier.height(10.dp))
                }
            }

            if (tracks.isEmpty()) {
                item(key = "empty") {
                    AlbumDetailNotice(stringResource(L10nR.string.feature_library_no_tracks_yet_c4614a))
                }
            } else {
                itemsIndexed(
                    items = tracks,
                    key = { _, track -> track.id },
                ) { index, track ->
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
                        )
                    }
                }
            }
        }
    }
}

internal fun Modifier.detailBackSwipe(onBack: () -> Unit): Modifier = pointerInput(onBack) {
    var dragX = 0f
    detectHorizontalDragGestures(
        onDragStart = { dragX = 0f },
        onHorizontalDrag = { _, dragAmount ->
            dragX += dragAmount
        },
        onDragEnd = {
            if (kotlin.math.abs(dragX) >= DetailBackSwipeThresholdPx) onBack()
        },
        onDragCancel = { dragX = 0f },
    )
}

@Composable
private fun AlbumDetailLightBackground(
    artworkUri: String?,
    palette: ArtworkPalette,
    modifier: Modifier = Modifier,
) {
    val dark = LocalEchoDarkTheme.current
    val base = MaterialTheme.colorScheme.background
    // Missing artwork must not turn a hash-generated placeholder color into a full-screen wash.
    Box(modifier = modifier.background(base)) {
        if (!artworkUri.isNullOrBlank()) {
            Box(
                Modifier.fillMaxWidth().height(400.dp).background(
                    Brush.verticalGradient(
                        0f to palette.vibrant.copy(alpha = if (dark) 0.12f else 0.08f),
                        1f to Color.Transparent,
                    ),
                ),
            )
        }
    }
}

@Composable
private fun ArtistHero(artist: ArtistSummary, palette: ArtworkPalette) {
    val colors = rememberDetailGlassColors()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtworkTile(
            artworkUri = artist.artworkUri,
            modifier = Modifier
                .padding(top = 6.dp)
                .size(200.dp),
            accent = palette.vibrant,
            showSignal = artist.artworkUri == null,
            cornerRadius = 0.dp,
            elevation = 22.dp,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            displayMetadataOrUnknown(artist.name, unknownArtistLabel()),
            color = colors.content,
            style = MaterialTheme.typography.headlineSmall.copy(shadow = AlbumTextShadow),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            artistMetaLine(artist),
            color = colors.muted,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun AlbumDetailTopBar(onBack: () -> Unit) {
    val colors = rememberDetailGlassColors()
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
private fun AlbumHero(
    album: AlbumSummary,
) {
    val colors = rememberDetailGlassColors()
    val titleColor = colors.content
    val artistColor = colors.muted
    val metaColor = colors.muted
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
    ) {
        ArtworkTile(
            artworkUri = album.artworkUri,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 4.dp)
                .size(208.dp),
            accent = MaterialTheme.colorScheme.primary,
            showSignal = album.artworkUri == null,
            cornerRadius = 8.dp,
            elevation = 14.dp,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            displayMetadataOrUnknown(album.title, unknownAlbumLabel()),
            color = titleColor,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Start,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            displayMetadataOrUnknown(album.albumArtist ?: album.artist, unknownArtistLabel()),
            color = artistColor,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Start,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            albumMetaLine(album),
            color = metaColor,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Start,
        )
    }
}

@Composable
internal fun AlbumActionBar(
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onPlayOnPc: (() -> Unit)? = null,
) {
    val refined = LocalAlbumDetail.current
    val colors = rememberDetailGlassColors()
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    val actionContent = scheme.primary
    val actionBorder = scheme.primary.copy(alpha = if (dark) 0.36f else 0.30f)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AlbumDetailActionButton(
            icon = Icons.Rounded.PlayArrow,
            label = stringResource(L10nR.string.feature_library_play_all_55c80e),
            iconSize = 24.dp,
            contentColor = if (refined) scheme.onPrimary else actionContent,
            containerColor = if (refined) scheme.primary else colors.elevatedSurface,
            borderColor = if (refined) Color.Transparent else actionBorder,
            onClick = onPlayAll,
            modifier = Modifier.weight(1f),
        )
        AlbumDetailActionButton(
            icon = Icons.Rounded.Shuffle,
            label = stringResource(L10nR.string.feature_library_shuffle_34e7ce),
            iconSize = 22.dp,
            contentColor = if (refined) colors.content else actionContent,
            containerColor = colors.elevatedSurface,
            borderColor = if (refined) colors.border else actionBorder,
            onClick = onShuffle,
            modifier = Modifier.weight(1f),
        )
    }
    if (onPlayOnPc != null) {
        AlbumDetailActionButton(
            icon = Icons.Rounded.PlayArrow,
            label = stringResource(L10nR.string.feature_library_play_on_pc_7c21a4),
            iconSize = 22.dp,
            contentColor = if (refined) colors.content else actionContent,
            containerColor = colors.elevatedSurface,
            borderColor = if (refined) colors.border else actionBorder,
            onClick = onPlayOnPc,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    }
}

@Composable
private fun AlbumDetailActionButton(
    icon: ImageVector,
    label: String,
    iconSize: Dp,
    contentColor: Color,
    containerColor: Color,
    borderColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(if (LocalAlbumDetail.current) 14.dp else 26.dp)
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(shape)
            .background(containerColor)
            .border(BorderStroke(1.dp, borderColor), shape)
            .echoClickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(iconSize),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            color = contentColor,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Facts describe the catalog files, not the active audio output route. */
@Composable
private fun AlbumInformation(album: AlbumSummary, tracks: List<EchoTrack>) {
    val context = LocalContext.current
    val colors = rememberDetailGlassColors()
    val facts = remember(album, tracks) {
        AlbumFileFacts(
            artists = tracks.map { it.artist.trim() }.filter { it.isNotEmpty() }.distinct(),
            discs = tracks.mapNotNull { it.discNumber?.takeIf { number -> number > 0 } }.distinct().size,
            sources = tracks.map { it.source.id }.distinct(),
            formats = tracks.mapNotNull { formatMimeType(it.mimeType) }.distinct(),
            sampleRates = tracks.mapNotNull { it.sampleRateHz?.takeIf { hz -> hz > 0 } }.distinct(),
            sizeBytes = tracks.sumOf { it.sizeBytes.coerceAtLeast(0L) },
            knownSizes = tracks.count { it.sizeBytes > 0L },
            knownSampleRates = tracks.count { (it.sampleRateHz ?: 0) > 0 },
        )
    }
    val addedDate = remember(album.addedAtSeconds, context.resources.configuration) {
        album.addedAtSeconds.takeIf { it > 0L }?.let {
            android.text.format.DateFormat.getDateFormat(context).format(java.util.Date(it * 1000L))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(L10nR.string.album_information_title), color = colors.content,
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (tracks.size < album.trackCount) {
            Text(stringResource(L10nR.string.album_information_partial, tracks.size, album.trackCount),
                color = colors.muted, style = MaterialTheme.typography.bodySmall)
        }
        AlbumInformationRow(stringResource(L10nR.string.album_information_artist),
            displayMetadataOrUnknown(album.albumArtist ?: album.artist, unknownArtistLabel()))
        album.year?.takeIf { it > 0 }?.let {
            AlbumInformationRow(stringResource(L10nR.string.album_information_year), it.toString())
        }
        AlbumInformationRow(stringResource(L10nR.string.feature_library_tracks_2d80e8), libraryTrackCountLabel(album.trackCount))
        if (album.durationMs > 0L) {
            AlbumInformationRow(stringResource(L10nR.string.album_information_duration), formatDuration(album.durationMs))
        }
        addedDate?.let { AlbumInformationRow(stringResource(L10nR.string.album_information_added), it) }
        if (facts.discs > 1 && tracks.size == album.trackCount) {
            AlbumInformationRow(stringResource(L10nR.string.album_information_discs), facts.discs.toString())
        }
        if (facts.artists.size > 1) {
            AlbumInformationRow(stringResource(L10nR.string.album_information_track_artists), facts.artists.joinToString(" / ", limit = 8, truncated = "…"))
        }
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
        Spacer(Modifier.height(4.dp))
        Text(stringResource(L10nR.string.album_information_audio), color = colors.content,
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (facts.sources.isNotEmpty()) {
            AlbumInformationRow(stringResource(L10nR.string.feature_library_source_4aff16), facts.sources.map { sourceLabel(it) }.joinToString(" / "))
        }
        if (facts.formats.isNotEmpty()) {
            AlbumInformationRow(stringResource(L10nR.string.feature_library_format_a7775b), facts.formats.joinToString(" / "))
        }
        formatSampleRates(facts.sampleRates)?.let {
            AlbumInformationRow(stringResource(L10nR.string.album_information_sample_rate), it)
        }
        if (facts.sizeBytes > 0L) {
            AlbumInformationRow(stringResource(if (facts.knownSizes == album.trackCount) L10nR.string.album_information_size else L10nR.string.album_information_known_size), formatFileSize(facts.sizeBytes))
        }
        if (tracks.isNotEmpty() && (facts.knownSampleRates < tracks.size || facts.knownSizes < tracks.size)) {
            Text(stringResource(L10nR.string.album_information_missing_specs), color = colors.muted, style = MaterialTheme.typography.bodySmall)
        }
        Text(stringResource(L10nR.string.album_information_file_specs), color = colors.muted, style = MaterialTheme.typography.bodySmall)
    }
}

private data class AlbumFileFacts(
    val artists: List<String>,
    val discs: Int,
    val sources: List<String>,
    val formats: List<String>,
    val sampleRates: List<Int>,
    val sizeBytes: Long,
    val knownSizes: Int,
    val knownSampleRates: Int,
)

@Composable
private fun AlbumInformationRow(label: String, value: String) {
    val colors = rememberDetailGlassColors()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(label, modifier = Modifier.weight(0.36f), color = colors.muted, style = MaterialTheme.typography.bodyMedium)
        Text(value, modifier = Modifier.weight(0.64f), color = colors.content,
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
    }
}

@Composable
private fun AlbumDetailInsights(
    source: DetailInsight,
    info: DetailInsight,
    palette: ArtworkPalette,
) {
    val colors = rememberDetailGlassColors()
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    val shape = RoundedCornerShape(20.dp)
    val refined = LocalAlbumDetail.current
    if (refined) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = colors.muted, modifier = Modifier.size(16.dp))
                Text(source.primary, color = colors.muted, style = MaterialTheme.typography.bodySmall)
                Text("·", color = colors.muted, style = MaterialTheme.typography.bodySmall)
                Text(info.primary, color = colors.muted, style = MaterialTheme.typography.labelMedium)
            }
            Text(info.secondary, color = colors.muted, style = MaterialTheme.typography.bodySmall)
        }
        return
    }
    val containerBrush = Brush.linearGradient(
        if (dark) {
            listOf(
                scheme.surface.copy(alpha = 0.54f),
                scheme.surfaceVariant.copy(alpha = 0.30f),
                palette.deep.copy(alpha = 0.12f),
            )
        } else {
            listOf(
                scheme.surface.copy(alpha = 0.84f),
                scheme.surfaceVariant.copy(alpha = 0.50f),
                palette.vibrant.copy(alpha = 0.08f),
            )
        },
    )
    val borderColor = if (refined) {
        colors.border
    } else if (dark) {
        palette.vibrant.copy(alpha = 0.22f)
    } else {
        colors.border
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(shape)
            .then(if (refined) Modifier.background(scheme.surface) else Modifier.background(containerBrush))
            .border(BorderStroke(1.dp, borderColor), shape)
            .padding(horizontal = if (refined) 4.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DetailInsightCell(
            insight = source,
            icon = Icons.Rounded.MusicNote,
            accent = palette.vibrant,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(42.dp)
                .background(borderColor),
        )
        DetailInsightCell(
            insight = info,
            icon = Icons.Rounded.GraphicEq,
            accent = palette.deep,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DetailInsightCell(
    insight: DetailInsight,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val colors = rememberDetailGlassColors()
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    val iconTint = if (LocalAlbumDetail.current) colors.muted else if (dark) accent.copy(alpha = 0.82f) else scheme.primary.copy(alpha = 0.74f)
    val titleColor = if (LocalAlbumDetail.current) colors.muted else if (dark) accent.copy(alpha = 0.86f) else scheme.primary
    Row(
        modifier = modifier
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(if (LocalAlbumDetail.current) 20.dp else 26.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            Text(
                insight.title,
                color = titleColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                insight.primary,
                color = colors.content,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                insight.secondary,
                color = colors.muted,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun AlbumTracksHeader(
    count: Int,
    titleColor: Color? = null,
    metaColor: Color? = null,
) {
    val colors = rememberDetailGlassColors()
    val resolvedTitleColor = titleColor ?: colors.content
    val resolvedMetaColor = metaColor ?: colors.muted
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(L10nR.string.feature_library_tracks_2d80e8),
            color = resolvedTitleColor,
            style = if (LocalAlbumDetail.current) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            libraryTrackCountLabel(count),
            color = resolvedMetaColor,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

private data class DetailInsight(
    val title: String,
    val primary: String,
    val secondary: String,
)

@Composable
private fun sourceInsight(tracks: List<EchoTrack>): DetailInsight {
    val resolvedSource = tracks
        .map { it.source.id }
        .distinct()
        .singleOrNull()
        ?.let { sourceLabel(it) }
        ?: if (tracks.isEmpty()) {
            stringResource(L10nR.string.feature_library_local_library_ddf4b1)
        } else {
            stringResource(L10nR.string.feature_library_multiple_sources_0f53d5)
        }
    val secondary = if (tracks.isEmpty()) {
        stringResource(L10nR.string.feature_library_waiting_for_track_info_f2add7)
    } else {
        libraryTrackCountLabel(tracks.size)
    }
    return DetailInsight(stringResource(L10nR.string.feature_library_source_4aff16), resolvedSource, secondary)
}

@Composable
private fun albumInfoInsight(album: AlbumSummary, tracks: List<EchoTrack>): DetailInsight {
    val primary = displayMetadataOrUnknown(album.albumArtist ?: album.artist, unknownArtistLabel())
    val year = album.year?.takeIf { it > 0 }?.toString()
    val discs = tracks.mapNotNull { it.discNumber?.takeIf { disc -> disc > 0 } }.distinct().size
    val secondary = buildList {
        year?.let { add(it) }
        add(libraryTrackCountLabel(album.trackCount))
        if (discs > 1) {
            add(stringResource(L10nR.string.feature_library_discs_discs_902194, (discs).toString()))
        }
        if (album.durationMs > 0L) add(readableDuration(album.durationMs))
    }.joinToString(" · ")
    return DetailInsight(stringResource(L10nR.string.feature_library_info_41ae25), primary, secondary)
}

@Composable
private fun artistInfoInsight(artist: ArtistSummary, tracks: List<EchoTrack>): DetailInsight {
    val formats = tracks.mapNotNull { formatMimeType(it.mimeType) }.distinct().take(2)
    val primary = libraryAlbumCountLabel(artist.albumCount.coerceAtLeast(0))
    val secondary = buildList {
        add(libraryTrackCountLabel(artist.trackCount))
        if (artist.durationMs > 0L) add(readableDuration(artist.durationMs))
        if (formats.isNotEmpty()) add(formats.joinToString(" / "))
    }.joinToString(" · ")
    return DetailInsight(stringResource(L10nR.string.feature_library_info_41ae25), primary, secondary)
}

@Composable
private fun formatInsight(tracks: List<EchoTrack>): DetailInsight {
    val formats = tracks.mapNotNull { formatMimeType(it.mimeType) }.distinct().take(3)
    val primary = formats.takeIf { it.isNotEmpty() }?.joinToString(" / ")
        ?: stringResource(L10nR.string.feature_library_format_pending_ce16a1)
    val size = tracks.sumOf { it.sizeBytes }.takeIf { it > 0L }?.let(::formatFileSize)
    val sampleRate = formatSampleRates(tracks.mapNotNull { it.sampleRateHz?.takeIf { hz -> hz > 0 } }.distinct())
    val secondary = buildList {
        size?.let { add(it) }
        add(sampleRate ?: stringResource(L10nR.string.feature_library_sample_rate_pending_bd6e6d))
    }.joinToString(" · ")
    return DetailInsight(stringResource(L10nR.string.feature_library_format_a7775b), primary, secondary)
}

@Composable
private fun sourceLabel(sourceId: String): String = when (sourceId.lowercase()) {
    "mediastore" -> stringResource(L10nR.string.feature_library_local_library_ddf4b1)
    "subsonic" -> "Subsonic / Navidrome"
    "webdav" -> "WebDAV"
    "jellyfin" -> "Jellyfin / Emby"
    "unknown" -> stringResource(L10nR.string.feature_library_unknown_source_d8c291)
    else -> when {
        sourceId.startsWith("subsonic:", ignoreCase = true) -> "Subsonic / Navidrome"
        sourceId.startsWith("webdav:", ignoreCase = true) -> "WebDAV"
        sourceId.startsWith("jellyfin:", ignoreCase = true) -> "Jellyfin / Emby"
        else -> sourceId
    }
}

private fun formatMimeType(mimeType: String?): String? {
    val raw = mimeType?.substringAfter("audio/", missingDelimiterValue = mimeType)
        ?.substringBefore(";")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: return null
    return when {
        raw.equals("mpeg", ignoreCase = true) -> "MP3"
        raw.equals("mp4", ignoreCase = true) || raw.equals("mp4a-latm", ignoreCase = true) -> "AAC"
        raw.equals("x-wav", ignoreCase = true) || raw.equals("wav", ignoreCase = true) -> "WAV"
        raw.equals("x-flac", ignoreCase = true) || raw.equals("flac", ignoreCase = true) -> "FLAC"
        else -> raw.uppercase()
    }
}

private fun formatSampleRates(sampleRates: List<Int>): String? {
    if (sampleRates.isEmpty()) return null
    val sorted = sampleRates.sorted()
    val first = sorted.first()
    val last = sorted.last()
    return if (first == last) {
        formatSampleRate(first)
    } else {
        "${formatSampleRate(first)}-${formatSampleRate(last)}"
    }
}

private fun formatSampleRate(hz: Int): String =
    if (hz % 1000 == 0) {
        "${hz / 1000} kHz"
    } else {
        String.format("%.1f kHz", hz / 1000.0)
    }

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    return if (value >= 100.0) {
        "${value.toInt()} ${units[unitIndex]}"
    } else {
        String.format("%.1f %s", value, units[unitIndex])
    }
}

@Composable
private fun readableDuration(durationMs: Long): String {
    val minutes = (durationMs / 60000L).toInt()
    return if (minutes >= 1) libraryMinutesLabel(minutes) else formatDuration(durationMs)
}

@Composable
internal fun AlbumTrackRow(
    index: Int,
    track: EchoTrack,
    accent: Color,
    palette: ArtworkPalette? = null,
    onClick: () -> Unit,
    onUpdateTrackMetadata: ((EchoTrackMetadataUpdate) -> Unit)? = null,
    onImportLyrics: ((EchoTrack) -> Unit)? = null,
    onPickArtwork: ((EchoTrack) -> Unit)? = null,
    onMatchNeteaseMetadata: ((EchoTrack) -> Unit)? = null,
    onAddToPlaylist: ((EchoTrack) -> Unit)? = null,
    onPlayNext: ((EchoTrack) -> Unit)? = null,
    onEnqueue: ((EchoTrack) -> Unit)? = null,
) {
    val colors = rememberDetailGlassColors()
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
        val dark = LocalEchoDarkTheme.current
        val refined = LocalAlbumDetail.current
        // 每行的渐变按 (主题, 表面色, 强调色) 记忆,滚动/重组时不再重复分配 Brush
        val rowBrush = remember(dark, colors.surface, accent) {
            Brush.linearGradient(
                listOf(
                    if (dark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.76f),
                    colors.surface,
                    accent.copy(alpha = if (dark) 0.10f else 0.05f),
                ),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(16.dp))
                .then(if (refined) Modifier else Modifier
                    .background(rowBrush)
                    .border(BorderStroke(1.dp, colors.border), RoundedCornerShape(16.dp)))
                .then(pressModifier)
                .padding(horizontal = if (refined) 4.dp else 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = (track.trackNumber ?: (index + 1)).toString().padStart(2, '0'),
                color = if (refined) colors.muted else accent,
                style = if (refined) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(26.dp),
                textAlign = TextAlign.Center,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    displayMetadataOrUnknown(track.title, unknownTrackLabel()),
                    color = colors.content,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    displayMetadataOrUnknown(track.artist, unknownArtistLabel()),
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
            )
        }
    }
}

@Composable
internal fun AlbumDetailNotice(message: String) {
    val colors = rememberDetailGlassColors()
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
private fun albumMetaLine(album: AlbumSummary): String {
    val parts = mutableListOf<String>()
    album.year?.takeIf { it > 0 }?.let { parts.add(it.toString()) }
    parts.add(libraryTrackCountLabel(album.trackCount))
    if (album.durationMs > 0L) {
        val minutes = (album.durationMs / 60000L).toInt()
        parts.add(if (minutes >= 1) libraryMinutesLabel(minutes) else formatDuration(album.durationMs))
    }
    return parts.joinToString(" · ")
}

@Composable
private fun artistMetaLine(artist: ArtistSummary): String {
    val parts = mutableListOf<String>()
    if (artist.albumCount > 0) parts.add(libraryAlbumCountLabel(artist.albumCount))
    parts.add(libraryTrackCountLabel(artist.trackCount))
    if (artist.durationMs > 0L) {
        val minutes = (artist.durationMs / 60000L).toInt()
        parts.add(if (minutes >= 1) libraryMinutesLabel(minutes) else formatDuration(artist.durationMs))
    }
    return parts.joinToString(" · ")
}
