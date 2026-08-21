package app.echo.android.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import app.echo.android.design.ArtworkPalette
import app.echo.android.design.ArtworkTile
import app.echo.android.design.BlurredArtworkBackground
import app.echo.android.design.EchoContentMaxWidth
import app.echo.android.design.EchoGlassInk
import app.echo.android.design.EchoGlassPanel
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.displayMetadataOrUnknown
import app.echo.android.design.echoString
import app.echo.android.design.formatDuration
import app.echo.android.design.rememberArtworkPalette
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.ArtistSummary
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.EchoTrackMetadataUpdate

private val AlbumDetailBottomPadding = 168.dp
private val AlbumOnArtwork = Color.White
private val AlbumOnArtworkMuted = Color.White.copy(alpha = 0.70f)
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
    return DetailGlassColors(
        surface = if (dark) scheme.surface.copy(alpha = 0.62f) else scheme.surface.copy(alpha = 0.78f),
        elevatedSurface = if (dark) scheme.surfaceVariant.copy(alpha = 0.34f) else scheme.surface.copy(alpha = 0.82f),
        border = if (dark) scheme.outlineVariant.copy(alpha = 0.34f) else scheme.outlineVariant.copy(alpha = 0.46f),
        content = scheme.onSurface.copy(alpha = if (dark) 0.94f else 0.90f),
        muted = scheme.onSurfaceVariant.copy(alpha = if (dark) 0.72f else 0.76f),
    )
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
                        .padding(horizontal = 20.dp),
                ) {
                    AlbumDetailTopBar(onBack = onBack)
                    Spacer(Modifier.height(8.dp))
                    AlbumHero(album = album, palette = palette, onArtworkBackground = true)
                    Spacer(Modifier.height(18.dp))
                    AlbumActionBar(onPlayAll = onPlayAll, onShuffle = onShuffle)
                    Spacer(Modifier.height(18.dp))
                    AlbumDetailInsights(
                        source = sourceInsight(loadedTracks),
                        info = formatInsight(loadedTracks),
                        palette = palette,
                    )
                    Spacer(Modifier.height(22.dp))
                    AlbumTracksHeader(
                        count = album.trackCount,
                        titleColor = AlbumOnArtwork,
                        metaColor = AlbumOnArtworkMuted,
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }

            when {
                tracks.loadState.refresh is LoadState.Loading -> item(key = "loading") {
                    AlbumDetailNotice(echoString(en = "Loading tracks...", zh = "正在加载曲目...", ja = "曲を読み込み中..."))
                }
                tracks.loadState.refresh is LoadState.Error -> item(key = "error") {
                    AlbumDetailNotice(echoString(en = "Failed to load tracks.", zh = "曲目加载失败。", ja = "曲の読み込みに失敗しました。"))
                }
                tracks.itemCount == 0 -> item(key = "empty") {
                    AlbumDetailNotice(echoString(en = "No tracks yet.", zh = "暂无曲目。", ja = "曲はまだありません。"))
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
                            )
                        }
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
    onPlayTrack: (EchoTrack) -> Unit,
    onUpdateTrackMetadata: ((EchoTrackMetadataUpdate) -> Unit)? = null,
    onImportLyrics: ((EchoTrack) -> Unit)? = null,
    onPickArtwork: ((EchoTrack) -> Unit)? = null,
    onMatchNeteaseMetadata: ((EchoTrack) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val palette = rememberArtworkPalette(album.artworkUri, seedKey = album.albumKey)
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
                        .padding(horizontal = 20.dp),
                ) {
                    AlbumDetailTopBar(onBack = onBack)
                    Spacer(Modifier.height(8.dp))
                    AlbumHero(album = album, palette = palette, onArtworkBackground = true)
                    Spacer(Modifier.height(18.dp))
                    AlbumActionBar(onPlayAll = onPlayAll, onShuffle = onShuffle)
                    Spacer(Modifier.height(18.dp))
                    AlbumDetailInsights(
                        source = sourceInsight(tracks),
                        info = formatInsight(tracks),
                        palette = palette,
                    )
                    Spacer(Modifier.height(22.dp))
                    AlbumTracksHeader(
                        count = album.trackCount,
                        titleColor = AlbumOnArtwork,
                        metaColor = AlbumOnArtworkMuted,
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }

            if (tracks.isEmpty()) {
                item(key = "empty") {
                    AlbumDetailNotice(echoString(en = "No tracks yet.", zh = "暂无曲目。", ja = "曲はまだありません。"))
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
                tracks.loadState.refresh is LoadState.Loading -> item(key = "loading") {
                    AlbumDetailNotice(echoString(en = "Loading tracks...", zh = "正在加载曲目...", ja = "曲を読み込み中..."))
                }
                tracks.loadState.refresh is LoadState.Error -> item(key = "error") {
                    AlbumDetailNotice(echoString(en = "Failed to load tracks.", zh = "曲目加载失败。", ja = "曲の読み込みに失敗しました。"))
                }
                tracks.itemCount == 0 -> item(key = "empty") {
                    AlbumDetailNotice(echoString(en = "No tracks yet.", zh = "暂无曲目。", ja = "曲はまだありません。"))
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
                    AlbumActionBar(onPlayAll = onPlayAll, onShuffle = onShuffle)
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
                    AlbumDetailNotice(echoString(en = "No tracks yet.", zh = "暂无曲目。", ja = "曲はまだありません。"))
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

private fun Modifier.detailBackSwipe(onBack: () -> Unit): Modifier = pointerInput(onBack) {
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
    Box(modifier = modifier) {
        BlurredArtworkBackground(
            artworkUri = artworkUri,
            palette = palette,
            modifier = Modifier.fillMaxSize(),
            artworkScale = 1.12f,
            artworkBlur = 14.dp,
            artworkAlpha = if (dark) 0.58f else 0.78f,
            overlayStartAlpha = 0f,
            overlayMidAlpha = 0f,
            overlayEndAlpha = 0.03f,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (dark) {
                        Brush.verticalGradient(
                            0f to EchoGlassInk.copy(alpha = 0.08f),
                            0.42f to Color.Transparent,
                            1f to EchoGlassPanel.copy(alpha = 0.12f),
                        )
                    } else {
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.26f),
                            0.42f to Color.White.copy(alpha = 0.16f),
                            1f to Color(0xFFE5F5FA).copy(alpha = 0.12f),
                        )
                    },
                ),
        )
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
private fun AlbumDetailTopBar(onBack: () -> Unit) {
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
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = echoString(en = "Back", zh = "返回", ja = "戻る"),
                tint = colors.content,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun AlbumHero(
    album: AlbumSummary,
    palette: ArtworkPalette,
    onArtworkBackground: Boolean = false,
) {
    val colors = rememberDetailGlassColors()
    val titleColor = if (onArtworkBackground) AlbumOnArtwork else colors.content
    val artistColor = if (onArtworkBackground) AlbumOnArtwork.copy(alpha = 0.88f) else palette.deep
    val metaColor = if (onArtworkBackground) AlbumOnArtworkMuted else colors.muted
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtworkTile(
            artworkUri = album.artworkUri,
            modifier = Modifier
                .padding(top = 6.dp)
                .size(232.dp),
            accent = palette.vibrant,
            showSignal = album.artworkUri == null,
            cornerRadius = 0.dp,
            elevation = 22.dp,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            displayMetadataOrUnknown(album.title, unknownAlbumLabel()),
            color = titleColor,
            style = MaterialTheme.typography.headlineSmall.copy(
                shadow = if (onArtworkBackground) AlbumTextShadow else null,
            ),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            displayMetadataOrUnknown(album.albumArtist ?: album.artist, unknownArtistLabel()),
            color = artistColor,
            style = MaterialTheme.typography.titleMedium.copy(
                shadow = if (onArtworkBackground) AlbumTextShadow else null,
            ),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            albumMetaLine(album),
            color = metaColor,
            style = MaterialTheme.typography.labelLarge.copy(
                shadow = if (onArtworkBackground) AlbumTextShadow else null,
            ),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AlbumActionBar(
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
) {
    val colors = rememberDetailGlassColors()
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    val actionContent = scheme.primary
    val actionBorder = scheme.primary.copy(alpha = if (dark) 0.36f else 0.30f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AlbumDetailActionButton(
            icon = Icons.Rounded.PlayArrow,
            label = echoString(en = "Play all", zh = "播放全部", ja = "すべて再生"),
            iconSize = 24.dp,
            contentColor = actionContent,
            containerColor = colors.elevatedSurface,
            borderColor = actionBorder,
            onClick = onPlayAll,
            modifier = Modifier.weight(1f),
        )
        AlbumDetailActionButton(
            icon = Icons.Rounded.Shuffle,
            label = echoString(en = "Shuffle", zh = "随机播放", ja = "シャッフル"),
            iconSize = 22.dp,
            contentColor = actionContent,
            containerColor = colors.elevatedSurface,
            borderColor = actionBorder,
            onClick = onShuffle,
            modifier = Modifier.weight(1f),
        )
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
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(shape)
            .background(containerColor)
            .border(BorderStroke(1.dp, borderColor), shape)
            .clickable(onClick = onClick),
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
    val borderColor = if (dark) {
        palette.vibrant.copy(alpha = 0.22f)
    } else {
        colors.border
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(shape)
            .background(containerBrush)
            .border(BorderStroke(1.dp, borderColor), shape)
            .padding(horizontal = 14.dp),
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
    val iconTint = if (dark) accent.copy(alpha = 0.82f) else scheme.primary.copy(alpha = 0.74f)
    val titleColor = if (dark) accent.copy(alpha = 0.86f) else scheme.primary
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
            modifier = Modifier.size(26.dp),
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
private fun AlbumTracksHeader(
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
            echoString(en = "Tracks", zh = "曲目", ja = "曲"),
            color = resolvedTitleColor,
            style = MaterialTheme.typography.titleLarge,
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
            echoString(en = "Local library", zh = "本机媒体库", ja = "ローカルライブラリ")
        } else {
            echoString(en = "Multiple sources", zh = "多来源", ja = "複数のソース")
        }
    val secondary = if (tracks.isEmpty()) {
        echoString(en = "Waiting for track info", zh = "等待曲目信息", ja = "曲情報を待っています")
    } else {
        libraryTrackCountLabel(tracks.size)
    }
    return DetailInsight(echoString(en = "Source", zh = "来源", ja = "ソース"), resolvedSource, secondary)
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
            add(echoString(en = "$discs discs", zh = "$discs 碟", ja = "$discs 枚"))
        }
        if (album.durationMs > 0L) add(readableDuration(album.durationMs))
    }.joinToString(" · ")
    return DetailInsight(echoString(en = "Info", zh = "信息", ja = "情報"), primary, secondary)
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
    return DetailInsight(echoString(en = "Info", zh = "信息", ja = "情報"), primary, secondary)
}

@Composable
private fun formatInsight(tracks: List<EchoTrack>): DetailInsight {
    val formats = tracks.mapNotNull { formatMimeType(it.mimeType) }.distinct().take(3)
    val primary = formats.takeIf { it.isNotEmpty() }?.joinToString(" / ")
        ?: echoString(en = "Format pending", zh = "格式待解析", ja = "フォーマット未解析")
    val size = tracks.sumOf { it.sizeBytes }.takeIf { it > 0L }?.let(::formatFileSize)
    val sampleRate = formatSampleRates(tracks.mapNotNull { it.sampleRateHz?.takeIf { hz -> hz > 0 } }.distinct())
    val secondary = buildList {
        size?.let { add(it) }
        add(sampleRate ?: echoString(en = "Sample rate pending", zh = "采样率待解析", ja = "サンプリングレート未解析"))
    }.joinToString(" · ")
    return DetailInsight(echoString(en = "Format", zh = "格式", ja = "フォーマット"), primary, secondary)
}

@Composable
private fun sourceLabel(sourceId: String): String = when (sourceId.lowercase()) {
    "mediastore" -> echoString(en = "Local library", zh = "本机媒体库", ja = "ローカルライブラリ")
    "subsonic" -> "Subsonic / Navidrome"
    "webdav" -> "WebDAV"
    "unknown" -> echoString(en = "Unknown source", zh = "未知来源", ja = "不明なソース")
    else -> when {
        sourceId.startsWith("subsonic:", ignoreCase = true) -> "Subsonic / Navidrome"
        sourceId.startsWith("webdav:", ignoreCase = true) -> "WebDAV"
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
private fun AlbumTrackRow(
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
        modifier = Modifier.fillMaxWidth(),
    ) { pressModifier ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            if (LocalEchoDarkTheme.current) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.76f),
                            colors.surface,
                            accent.copy(alpha = if (LocalEchoDarkTheme.current) 0.10f else 0.05f),
                        ),
                    ),
                )
                .border(BorderStroke(1.dp, colors.border), RoundedCornerShape(16.dp))
                .then(pressModifier)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = (track.trackNumber ?: (index + 1)).toString().padStart(2, '0'),
                color = accent,
                style = MaterialTheme.typography.titleSmall,
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
private fun AlbumDetailNotice(message: String) {
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
