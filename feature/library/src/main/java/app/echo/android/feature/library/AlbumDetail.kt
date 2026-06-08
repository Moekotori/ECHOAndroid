package app.echo.android.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import app.echo.android.design.ArtworkPalette
import app.echo.android.design.ArtworkTile
import app.echo.android.design.EchoContentMaxWidth
import app.echo.android.design.RoonInk
import app.echo.android.design.RoonMuted
import app.echo.android.design.formatDuration
import app.echo.android.design.rememberArtworkPalette
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.ArtistSummary
import app.echo.android.model.library.EchoTrack

private val AlbumDetailBottomPadding = 168.dp

@Composable
internal fun AlbumDetailPage(
    album: AlbumSummary,
    tracks: LazyPagingItems<EchoTrack>,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onPlayTrack: (EchoTrack) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = rememberArtworkPalette(album.artworkUri, seedKey = album.albumKey)
    Box(modifier = modifier.fillMaxSize()) {
        // 从封面提取的色调渐变，仅渲染在上半屏，下方融入页面底色
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(440.dp)
                .background(
                    Brush.verticalGradient(
                        0f to palette.vibrant.copy(alpha = 0.55f),
                        0.45f to palette.deep.copy(alpha = 0.30f),
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
                    AlbumHero(album = album, palette = palette)
                    Spacer(Modifier.height(18.dp))
                    AlbumActionBar(palette = palette, onPlayAll = onPlayAll, onShuffle = onShuffle)
                    Spacer(Modifier.height(20.dp))
                    AlbumTracksHeader(count = album.trackCount)
                    Spacer(Modifier.height(10.dp))
                }
            }

            when {
                tracks.loadState.refresh is LoadState.Loading -> item(key = "loading") {
                    AlbumDetailNotice("正在加载曲目...")
                }
                tracks.loadState.refresh is LoadState.Error -> item(key = "error") {
                    AlbumDetailNotice("曲目加载失败。")
                }
                tracks.itemCount == 0 -> item(key = "empty") {
                    AlbumDetailNotice("暂无曲目。")
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
                            )
                        }
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
    modifier: Modifier = Modifier,
) {
    val palette = rememberArtworkPalette(artist.artworkUri, seedKey = artist.artistKey)
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(440.dp)
                .background(
                    Brush.verticalGradient(
                        0f to palette.vibrant.copy(alpha = 0.55f),
                        0.45f to palette.deep.copy(alpha = 0.30f),
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
                    AlbumActionBar(palette = palette, onPlayAll = onPlayAll, onShuffle = onShuffle)
                    Spacer(Modifier.height(20.dp))
                    AlbumTracksHeader(count = artist.trackCount)
                    Spacer(Modifier.height(10.dp))
                }
            }

            when {
                tracks.loadState.refresh is LoadState.Loading -> item(key = "loading") {
                    AlbumDetailNotice("正在加载曲目...")
                }
                tracks.loadState.refresh is LoadState.Error -> item(key = "error") {
                    AlbumDetailNotice("曲目加载失败。")
                }
                tracks.itemCount == 0 -> item(key = "empty") {
                    AlbumDetailNotice("暂无曲目。")
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
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistHero(artist: ArtistSummary, palette: ArtworkPalette) {
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
            cornerRadius = 100.dp,
            elevation = 22.dp,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            artist.name,
            color = RoonInk,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            artistMetaLine(artist),
            color = RoonMuted,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AlbumDetailTopBar(onBack: () -> Unit) {
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
                .background(Color.White.copy(alpha = 0.82f))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.9f)), CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回",
                tint = RoonInk,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun AlbumHero(album: AlbumSummary, palette: ArtworkPalette) {
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
            cornerRadius = 22.dp,
            elevation = 22.dp,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            album.title,
            color = RoonInk,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            album.albumArtist ?: album.artist ?: "未知艺术家",
            color = palette.deep,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            albumMetaLine(album),
            color = RoonMuted,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AlbumActionBar(
    palette: ArtworkPalette,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.horizontalGradient(listOf(palette.vibrant, palette.deep)),
                )
                .clickable(onClick = onPlayAll),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = palette.onColor,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "播放全部",
                color = palette.onColor,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color.White.copy(alpha = 0.78f))
                .border(BorderStroke(1.dp, palette.vibrant.copy(alpha = 0.35f)), RoundedCornerShape(26.dp))
                .clickable(onClick = onShuffle),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Shuffle,
                contentDescription = null,
                tint = palette.deep,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "随机播放",
                color = palette.deep,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun AlbumTracksHeader(count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "曲目",
            color = RoonInk,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "$count 首",
            color = RoonMuted,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun AlbumTrackRow(
    index: Int,
    track: EchoTrack,
    accent: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.62f))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.78f)), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
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
                track.title,
                color = RoonInk,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            track.artist.takeIf { it.isNotBlank() }?.let { artist ->
                Text(
                    artist,
                    color = RoonMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            formatDuration(track.durationMs),
            color = RoonMuted,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun AlbumDetailNotice(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = EchoContentMaxWidth)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(message, color = RoonMuted, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun albumMetaLine(album: AlbumSummary): String {
    val parts = mutableListOf<String>()
    album.year?.takeIf { it > 0 }?.let { parts.add(it.toString()) }
    parts.add("${album.trackCount} 首")
    if (album.durationMs > 0L) {
        val minutes = (album.durationMs / 60000L).toInt()
        parts.add(if (minutes >= 1) "$minutes 分钟" else formatDuration(album.durationMs))
    }
    return parts.joinToString(" · ")
}

private fun artistMetaLine(artist: ArtistSummary): String {
    val parts = mutableListOf<String>()
    if (artist.albumCount > 0) parts.add("${artist.albumCount} 张专辑")
    parts.add("${artist.trackCount} 首")
    if (artist.durationMs > 0L) {
        val minutes = (artist.durationMs / 60000L).toInt()
        parts.add(if (minutes >= 1) "$minutes 分钟" else formatDuration(artist.durationMs))
    }
    return parts.joinToString(" · ")
}
