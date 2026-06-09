package app.echo.android.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Scanner
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import app.echo.android.design.ArtworkPalette
import app.echo.android.design.ArtworkTile
import app.echo.android.design.EchoAccent
import app.echo.android.design.EchoAccentDeep
import app.echo.android.design.EchoAccentText
import app.echo.android.design.EchoContentMaxWidth
import app.echo.android.design.EchoColors
import app.echo.android.design.EchoGlassBorder
import app.echo.android.design.EchoHomeBlue
import app.echo.android.design.EchoHomeMist
import app.echo.android.design.EchoArtworkImage
import app.echo.android.design.EchoIconBadge
import app.echo.android.design.EchoPanel
import app.echo.android.design.EchoTextButton
import app.echo.android.design.EmptyState
import app.echo.android.design.PageChrome
import app.echo.android.design.RoonInk
import app.echo.android.design.RoonMuted
import app.echo.android.design.formatDuration
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.ArtistSummary
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.LibraryScanPhase
import app.echo.android.model.library.LibraryScanProgress
import kotlinx.coroutines.launch

@Composable
internal fun LibraryPagerTabs(
    selectedMode: LibraryViewMode,
    onSelectMode: (LibraryViewMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 0.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LibraryViewMode.entries.forEach { mode ->
            val selected = selectedMode == mode
            Text(
                text = mode.label,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onSelectMode(mode) }
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                color = if (selected) EchoAccentText else RoonMuted,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun LibraryPlaceholderPage(
    title: String,
    subtitle: String,
) {
    EchoPanel(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EchoIconBadge(Icons.Rounded.LibraryMusic)
            Text(title, color = RoonInk, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, color = RoonMuted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun LibraryOverview(
    trackCount: Int,
    albumCount: Int,
    artistCount: Int,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.72f),
                        EchoHomeMist.copy(alpha = 0.60f),
                        EchoAccentDeep.copy(alpha = 0.08f),
                    ),
                ),
            )
            .border(BorderStroke(1.dp, EchoGlassBorder.copy(alpha = 0.86f)), RoundedCornerShape(22.dp))
            .padding(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            LibraryMetric("歌曲", trackCount.toString(), Modifier.weight(1f))
            LibraryMetric("专辑", albumCount.toString(), Modifier.weight(1f))
            LibraryMetric("艺人", artistCount.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
internal fun LibraryMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, color = RoonInk, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, color = RoonMuted, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun LibraryViewSwitcher(
    selectedMode: LibraryViewMode,
    onSelectMode: (LibraryViewMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.54f))
            .border(BorderStroke(1.dp, EchoGlassBorder.copy(alpha = 0.74f)), RoundedCornerShape(18.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LibraryViewMode.entries.forEach { mode ->
            val selected = selectedMode == mode
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) EchoHomeBlue.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable { onSelectMode(mode) }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    mode.icon,
                    contentDescription = mode.label,
                    tint = if (selected) EchoAccentText else RoonMuted,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    mode.label,
                    color = if (selected) RoonInk else RoonMuted,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun LibraryViewModeMenu(
    selectedMode: LibraryViewMode,
    onSelectMode: (LibraryViewMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.62f))
                .border(BorderStroke(1.dp, EchoGlassBorder), RoundedCornerShape(14.dp))
                .clickable { expanded = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                selectedMode.icon,
                contentDescription = "切换曲库显示方式",
                tint = EchoHomeBlue,
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = Color.White,
        ) {
            LibraryViewMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Text(
                            mode.label,
                            color = if (mode == selectedMode) EchoHomeBlue else RoonInk,
                            fontWeight = if (mode == selectedMode) FontWeight.Bold else FontWeight.SemiBold,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            mode.icon,
                            contentDescription = null,
                            tint = if (mode == selectedMode) EchoHomeBlue else RoonMuted,
                        )
                    },
                    onClick = {
                        onSelectMode(mode)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
internal fun AlbumWall(
    albums: LazyPagingItems<AlbumSummary>,
    onOpenAlbum: (AlbumSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (albums.loadState.refresh is LoadState.Loading) {
        EmptyState("正在加载专辑...")
        return
    }
    if (albums.loadState.refresh is LoadState.Error) {
        EmptyState("专辑加载失败。")
        return
    }
    if (albums.itemCount == 0) {
        EmptyState("当前曲库还没有可展示的专辑。")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(bottom = LibraryBottomControlsPadding),
    ) {
        items(
            count = albums.itemCount,
            key = { index: Int -> albums.peek(index)?.albumKey ?: "album-$index" },
        ) { index: Int ->
            albums[index]?.let { album ->
                AlbumWallCard(album = album, onClick = { onOpenAlbum(album) })
            }
        }
    }
}

@Composable
internal fun AlbumWallCard(
    album: AlbumSummary,
    onClick: () -> Unit,
) {
    val artistLabel = album.albumArtist ?: album.artist ?: "未知艺术家"
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ArtworkTile(
            artworkUri = album.artworkUri,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            accent = EchoAccent,
            showSignal = album.artworkUri == null,
            cornerRadius = 14.dp,
            elevation = 8.dp,
        )
        Text(album.title, color = RoonInk, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("$artistLabel / ${album.trackCount} 首", color = RoonMuted, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun ArtistWall(
    artists: LazyPagingItems<ArtistSummary>,
    onOpenArtist: (ArtistSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (artists.loadState.refresh is LoadState.Loading) {
        EmptyState("正在加载艺人...")
        return
    }
    if (artists.loadState.refresh is LoadState.Error) {
        EmptyState("艺人加载失败。")
        return
    }
    if (artists.itemCount == 0) {
        EmptyState("当前曲库还没有可展示的艺人。")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 158.dp),
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = LibraryBottomControlsPadding),
    ) {
        items(
            count = artists.itemCount,
            key = { index: Int -> artists.peek(index)?.artistKey ?: "artist-$index" },
        ) { index: Int ->
            artists[index]?.let { artist ->
                ArtistWallCard(artist = artist, onClick = { onOpenArtist(artist) })
            }
        }
    }
}

@Composable
internal fun ArtistWallCard(
    artist: ArtistSummary,
    onClick: () -> Unit,
) {
    // 每位艺人按标识生成稳定柔和的取色，呼应详情页（同步、无额外位图解码）
    val palette = remember(artist.artistKey) { ArtworkPalette.fromSeed(artist.artistKey) }
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ArtistWallAvatar(
            artworkUri = artist.artworkUri,
            palette = palette,
        )
        Text(
            artist.name,
            color = RoonInk,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            "${artist.albumCount} 张专辑 · ${artist.trackCount} 首",
            color = RoonMuted,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ArtistWallAvatar(
    artworkUri: String?,
    palette: ArtworkPalette,
) {
    val shape = CircleShape
    Box(
        modifier = Modifier
            .size(86.dp)
            .shadow(
                elevation = 8.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.06f),
                spotColor = Color.Black.copy(alpha = 0.12f),
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        palette.vibrant.copy(alpha = 0.55f),
                        Color.White.copy(alpha = 0.70f),
                        palette.soft.copy(alpha = 0.42f),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (artworkUri.isNullOrBlank()) {
            Icon(
                Icons.Rounded.Person,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.88f),
                modifier = Modifier.size(38.dp),
            )
        } else {
            EchoArtworkImage(
                artworkUri = artworkUri,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.34f),
                shape = CircleShape,
            )
        }
    }
}

@Composable
internal fun LibraryScanStatus(
    scanState: LibraryScanProgress,
    onCancelScan: () -> Unit,
) {
    EchoPanel(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EchoIconBadge(Icons.Rounded.Scanner)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = scanPhaseLabel(scanState.phase),
                        color = RoonInk,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = scanState.currentTitle
                            ?.takeIf { it.isNotBlank() }
                            ?: "正在增量索引本机音乐",
                        color = RoonMuted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                EchoTextButton(
                    text = "取消扫描",
                    onClick = onCancelScan,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LibraryMetric("已扫描", scanProgressValue(scanState), Modifier.weight(1f))
                LibraryMetric("新增", scanState.insertedCount.toString(), Modifier.weight(1f))
                LibraryMetric("更新", scanState.updatedCount.toString(), Modifier.weight(1f))
                LibraryMetric("删除", scanState.deletedCount.toString(), Modifier.weight(1f))
            }
            scanState.error?.takeIf { it.isNotBlank() }?.let { error ->
                Text(error, color = Color(0xFFE0796E), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun scanProgressValue(scanState: LibraryScanProgress): String =
    scanState.totalCount?.let { total -> "${scanState.scannedCount}/$total" }
        ?: scanState.scannedCount.toString()

private fun scanPhaseLabel(phase: LibraryScanPhase): String =
    when (phase) {
        LibraryScanPhase.Idle -> "等待扫描"
        LibraryScanPhase.Preparing -> "准备扫描"
        LibraryScanPhase.QueryingMediaStore -> "正在读取 MediaStore"
        LibraryScanPhase.Diffing -> "正在对比曲库"
        LibraryScanPhase.WritingDatabase -> "正在写入曲库"
        LibraryScanPhase.CleaningRemoved -> "正在清理已移除音乐"
        LibraryScanPhase.Completed -> "扫描完成"
        LibraryScanPhase.Cancelled -> "扫描已取消"
        LibraryScanPhase.Error -> "扫描失败"
    }

@Composable
internal fun LibraryScanResultBanner(scanState: LibraryScanProgress) {
    val message = when (scanState.phase) {
        LibraryScanPhase.Completed -> "扫描完成：${scanState.scannedCount} 首，新增 ${scanState.insertedCount}，更新 ${scanState.updatedCount}，删除 ${scanState.deletedCount}"
        LibraryScanPhase.Cancelled -> "扫描已取消，已保留现有曲库。"
        LibraryScanPhase.Error -> scanState.error ?: "曲库扫描失败。"
        LibraryScanPhase.Idle -> "扫描完成：${scanState.scannedCount} 首，新增 ${scanState.insertedCount}，更新 ${scanState.updatedCount}，删除 ${scanState.deletedCount}"
        else -> null
    } ?: return

    EchoPanel(Modifier.fillMaxWidth()) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            color = if (scanState.phase == LibraryScanPhase.Error) Color(0xFFE0796E) else RoonMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
internal fun LibraryBootstrapState() {
    EchoPanel(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EchoIconBadge(Icons.Rounded.LibraryMusic)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("暂无本机歌曲", color = RoonInk, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "点右上角扫描本机音乐。",
                    color = RoonMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
internal fun LibraryDetailPage(
    title: String,
    subtitle: String?,
    tracks: LazyPagingItems<EchoTrack>,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onPlayTrack: (EchoTrack) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EchoPanel(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            title,
                            color = RoonInk,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        subtitle?.takeIf { it.isNotBlank() }?.let { value ->
                            Text(
                                value,
                                color = RoonMuted,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    EchoTextButton(text = "返回", onClick = onBack)
                    EchoTextButton(text = "播放全部", onClick = onPlayAll)
                }
            }
        }

        when {
            tracks.loadState.refresh is LoadState.Loading -> EmptyState("正在加载曲目...")
            tracks.loadState.refresh is LoadState.Error -> EmptyState("曲目加载失败。")
            tracks.itemCount == 0 -> EmptyState("暂无曲目。")
            else -> TrackList(
                tracks = tracks,
                onPlayTrack = onPlayTrack,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun TrackList(
    tracks: LazyPagingItems<EchoTrack>,
    onPlayTrack: (EchoTrack) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = LibraryBottomControlsPadding),
    ) {
        items(
            count = tracks.itemCount,
            key = { index: Int -> tracks.peek(index)?.id ?: "track-$index" },
        ) { index: Int ->
            tracks[index]?.let { track ->
                TrackRow(
                    track = track,
                    onClick = { onPlayTrack(track) },
                )
            }
        }
    }
}

@Composable
internal fun TrackRow(
    track: EchoTrack,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.66f))
            .border(BorderStroke(1.dp, EchoGlassBorder.copy(alpha = 0.84f)), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ArtworkTile(track.artworkUri, Modifier.size(50.dp), accent = EchoAccent, cornerRadius = 13.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    track.title,
                    color = RoonInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    trackSubtitle(track),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = RoonMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                formatDuration(track.durationMs),
                color = RoonMuted,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

internal fun trackSubtitle(track: EchoTrack): String =
    listOf(track.artist, track.album)
        .mapNotNull { value -> value?.takeIf { it.isNotBlank() } }
        .ifEmpty { listOf("本机音频") }
        .joinToString(" / ")

private val LibraryBottomControlsPadding = 150.dp
