package app.echo.android.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.echo.android.design.ArtworkTile
import app.echo.android.design.EchoAccent
import app.echo.android.design.EchoAccentDeep
import app.echo.android.design.EchoAccentText
import app.echo.android.design.EchoColors
import app.echo.android.design.EchoDarkGlassBorder
import app.echo.android.design.EchoGlassBorder
import app.echo.android.design.EchoGlassInk
import app.echo.android.design.EchoGlassCyan
import app.echo.android.design.EchoGlassPanel
import app.echo.android.design.EchoHomeBlue
import app.echo.android.design.EchoHomeBlueDeep
import app.echo.android.design.EchoHomeMist
import app.echo.android.design.EchoIconBadge
import app.echo.android.design.EchoPanel
import app.echo.android.design.EchoPlaceholderLine
import app.echo.android.design.EchoSectionTitle
import app.echo.android.design.EchoSoftLine
import app.echo.android.design.AmbientPlanet
import app.echo.android.design.GlassIconButton
import app.echo.android.design.GlassSurface
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.RoonInk
import app.echo.android.design.RoonMuted
import app.echo.android.design.echoDarkGlassBorder
import app.echo.android.design.echoString
import app.echo.android.design.rememberEchoHapticPerformer
import app.echo.android.design.formatDuration
import app.echo.android.design.progressFraction
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.ArtistSummary
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.playback.EchoPlaybackState
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoRepeatMode
import app.echo.android.model.playback.PlaybackHeatmapDay
import java.util.Calendar
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

enum class SearchResultType { Track, Album, Artist }

data class SearchResult(
    val type: SearchResultType,
    val title: String,
    val subtitle: String,
    val id: String,
    val artworkUri: String? = null,
)

private const val HomeHeatmapWeeks = 12

@Composable
internal fun homePanelColor(lightAlpha: Float = 0.90f): Color {
    return if (LocalEchoDarkTheme.current) {
        EchoGlassPanel.copy(alpha = (lightAlpha * 0.58f).coerceIn(0.42f, 0.62f))
    } else {
        Color.White.copy(alpha = lightAlpha.coerceIn(0.95f, 1.00f))
    }
}

@Composable
private fun homePanelBorder(lightAlpha: Float = 0.94f): BorderStroke {
    return BorderStroke(
        1.dp,
        if (LocalEchoDarkTheme.current) Color.White.copy(alpha = 0.07f) else EchoSoftLine.copy(alpha = lightAlpha.coerceIn(0.74f, 0.96f)),
    )
}

@Composable
private fun homeTitleColor(): Color =
    if (LocalEchoDarkTheme.current) Color.White.copy(alpha = 0.92f) else RoonInk

@Composable
internal fun homeBodyColor(): Color =
    if (LocalEchoDarkTheme.current) Color.White.copy(alpha = 0.66f) else RoonMuted.copy(alpha = 0.94f)

@Composable
private fun homePanelBrush(): Brush {
    return if (LocalEchoDarkTheme.current) {
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.035f),
                EchoGlassPanel.copy(alpha = 0.56f),
                EchoGlassInk.copy(alpha = 0.62f),
            ),
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 1.00f),
                Color(0xFFF7FAFE),
                EchoHomeMist.copy(alpha = 0.76f),
            ),
        )
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
            .background(homePanelBrush())
            .border(homePanelBorder(), RoundedCornerShape(22.dp))
            .padding(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            LibraryMetric(echoString(en = "Songs", zh = "歌曲", ja = "曲"), trackCount.toString(), Modifier.weight(1f))
            LibraryMetric(echoString(en = "Albums", zh = "专辑", ja = "アルバム"), albumCount.toString(), Modifier.weight(1f))
            LibraryMetric(echoString(en = "Artists", zh = "艺人", ja = "アーティスト"), artistCount.toString(), Modifier.weight(1f))
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
        Text(value, color = homeTitleColor(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, color = homeBodyColor(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun RoonHomeHeader(
    status: EchoPlaybackStatus,
    compact: Boolean,
    onOpenSearch: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 22.dp,
                top = if (compact) 4.dp else 8.dp,
                end = 22.dp,
                bottom = if (compact) 8.dp else 12.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val shape = RoundedCornerShape(28.dp)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenSearch() },
                shape = shape,
                color = homePanelColor(0.94f),
                border = homePanelBorder(0.88f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = homeBodyColor(), modifier = Modifier.size(20.dp))
                    Text(
                        echoString(
                            en = "Search local music, albums, and artists",
                            zh = "搜索本机音乐、专辑、歌手",
                            ja = "端末の曲、アルバム、アーティストを検索",
                        ),
                        color = homeBodyColor().copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultItem(result: SearchResult, onClick: (SearchResult) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(result) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (result.artworkUri.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(if (result.type == SearchResultType.Artist) CircleShape else RoundedCornerShape(6.dp))
                    .background(homeBodyColor().copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = when (result.type) {
                        SearchResultType.Track -> Icons.Rounded.MusicNote
                        SearchResultType.Album -> Icons.Rounded.Album
                        SearchResultType.Artist -> Icons.Rounded.Person
                    },
                    contentDescription = null,
                    tint = homeBodyColor().copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            ArtworkTile(
                artworkUri = result.artworkUri,
                modifier = Modifier
                    .size(36.dp)
                    .clip(if (result.type == SearchResultType.Artist) CircleShape else RoundedCornerShape(6.dp)),
                accent = EchoAccent,
                showSignal = false,
                cornerRadius = if (result.type == SearchResultType.Artist) 18.dp else 6.dp,
                elevation = 0.dp,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.title,
                color = homeBodyColor(),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (result.subtitle.isNotBlank()) {
                Text(
                    text = result.subtitle,
                    color = homeBodyColor().copy(alpha = 0.45f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun RoonRecentActivitySection(
    recentPlayedAlbums: List<AlbumSummary>,
    recentlyAddedAlbums: List<AlbumSummary>,
    onOpenAlbum: (AlbumSummary) -> Unit,
    onOpenLibrary: () -> Unit,
) {
    var selectedMode by remember { mutableStateOf(RecentActivityMode.Played) }
    val albums = when (selectedMode) {
        RecentActivityMode.Played -> recentPlayedAlbums
        RecentActivityMode.Added -> recentlyAddedAlbums
    }
    val displayAlbums = if (albums.isEmpty() && selectedMode == RecentActivityMode.Played) {
        recentlyAddedAlbums
    } else {
        albums
    }
    val displayMode = if (albums.isEmpty() && selectedMode == RecentActivityMode.Played && recentlyAddedAlbums.isNotEmpty()) {
        RecentActivityMode.Added
    } else {
        selectedMode
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .shadow(
                elevation = if (LocalEchoDarkTheme.current) 0.dp else 14.dp,
                shape = RoundedCornerShape(30.dp),
                ambientColor = Color.Black.copy(alpha = 0.045f),
                spotColor = Color.Black.copy(alpha = 0.035f),
            )
            .clip(RoundedCornerShape(28.dp))
            .background(homePanelBrush())
            .border(homePanelBorder(0.94f), RoundedCornerShape(28.dp))
            .padding(top = 14.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                echoString(en = "Recent activity", zh = "最近活动", ja = "最近のアクティビティ"),
                color = homeTitleColor(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            RecentActivityTabs(
                selectedMode = selectedMode,
                onSelect = { selectedMode = it },
            )
        }
        LazyRow(
            modifier = Modifier.height(if (displayAlbums.isEmpty()) RecentActivityEmptyCardHeight else RecentActivityAlbumCardHeight),
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (displayAlbums.isEmpty()) {
                item {
                    RecentActivityEmptyAlbumCard(
                        title = if (selectedMode == RecentActivityMode.Played) {
                            echoString(en = "Nothing played yet", zh = "暂无已播放", ja = "再生履歴はまだありません")
                        } else {
                            echoString(en = "No new albums yet", zh = "暂无新增专辑", ja = "新しいアルバムはまだありません")
                        },
                        subtitle = if (selectedMode == RecentActivityMode.Played) {
                            echoString(en = "Appears after you play an album", zh = "播放专辑后显示", ja = "アルバムを再生すると表示されます")
                        } else {
                            echoString(en = "Appears after you scan your library", zh = "扫描曲库后显示", ja = "ライブラリをスキャンすると表示されます")
                        },
                        onClick = onOpenLibrary,
                    )
                }
            } else {
                items(displayAlbums, key = { it.albumKey }) { album ->
                    RecentAlbumCard(
                        album = album,
                        mode = displayMode,
                        onClick = { onOpenAlbum(album) },
                    )
                }
            }
        }
    }
}

internal enum class RecentActivityMode {
    Played,
    Added,
}

private val RecentActivityAlbumCardWidth = 124.dp
private val RecentActivityAlbumCardHeight = 202.dp
private val RecentActivityEmptyCardWidth = 126.dp
private val RecentActivityEmptyCardHeight = 184.dp

@Composable
internal fun RecentActivityTabs(
    selectedMode: RecentActivityMode,
    onSelect: (RecentActivityMode) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = homePanelColor(0.94f),
        border = homePanelBorder(0.84f),
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RecentActivityModeTab(
                label = echoString(en = "Played", zh = "已播放", ja = "再生済み"),
                selected = selectedMode == RecentActivityMode.Played,
                onClick = { onSelect(RecentActivityMode.Played) },
            )
            RecentActivityModeTab(
                label = echoString(en = "Added", zh = "添加于", ja = "追加日"),
                selected = selectedMode == RecentActivityMode.Added,
                onClick = { onSelect(RecentActivityMode.Added) },
            )
        }
    }
}

@Composable
private fun RecentActivityModeTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) scheme.primary.copy(alpha = 0.24f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) scheme.primary else homeBodyColor(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
internal fun RecentPlayedAlbumsTab() {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = homePanelColor(0.82f),
        border = homePanelBorder(0.80f),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(scheme.primary.copy(alpha = if (LocalEchoDarkTheme.current) 0.16f else 0.16f))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                echoString(en = "Played", zh = "已播放", ja = "再生済み"),
                color = scheme.primary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun RecentAlbumCard(
    album: AlbumSummary,
    mode: RecentActivityMode,
    onClick: () -> Unit,
) {
    val artistLabel = album.albumArtist ?: album.artist ?: echoString(en = "Unknown artist", zh = "未知艺人", ja = "不明なアーティスト")
    Column(
        modifier = Modifier
            .width(RecentActivityAlbumCardWidth)
            .height(RecentActivityAlbumCardHeight)
            .clickable(onClick = onClick),
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
            elevation = if (LocalEchoDarkTheme.current) 0.dp else 7.dp,
        )
        Text(
            album.title,
            color = homeTitleColor(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            recentAlbumSubtitle(album, mode, artistLabel),
            color = homeBodyColor(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun recentAlbumSubtitle(
    album: AlbumSummary,
    mode: RecentActivityMode,
    artistLabel: String,
): String {
    val dateLabel = album.addedAtSeconds.takeIf { it > 0L }?.let { formatAlbumDate(it) }
    return if (mode == RecentActivityMode.Added && dateLabel != null) {
        "$artistLabel \u00b7 $dateLabel"
    } else {
        artistLabel
    }
}

@Composable
private fun formatAlbumDate(seconds: Long): String {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = seconds * 1000L
    }
    val month = calendar.get(Calendar.MONTH) + 1
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    return echoString(
        en = "$month/$day",
        zh = "${month}月${day}日",
        ja = "${month}月${day}日",
    )
}

@Composable
private fun RecentActivityEmptyAlbumCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(RecentActivityEmptyCardWidth)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(homePanelColor(0.88f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.LibraryMusic,
                contentDescription = null,
                tint = EchoHomeBlue,
                modifier = Modifier.size(34.dp),
            )
        }
        Text(
            title,
            color = homeTitleColor(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            subtitle,
            color = homeBodyColor(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun EmptyRecentAlbumsCard(
    title: String = echoString(en = "No albums yet", zh = "暂无专辑", ja = "アルバムはまだありません"),
    subtitle: String = echoString(
        en = "Appears after you scan your library",
        zh = "扫描曲库后显示",
        ja = "ライブラリをスキャンすると表示されます",
    ),
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(homePanelColor(0.74f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.LibraryMusic,
                contentDescription = null,
                tint = EchoHomeBlue,
                modifier = Modifier.size(34.dp),
            )
        }
        Text(
            title,
            color = homeTitleColor(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            subtitle,
            color = homeBodyColor(),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun RoonRecentActivitySection(
    status: EchoPlaybackStatus,
    onPlayPause: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(30.dp),
                ambientColor = Color.Black.copy(alpha = 0.04f),
                spotColor = Color.Black.copy(alpha = 0.03f),
            )
            .clip(RoundedCornerShape(32.dp))
            .background(homePanelBrush())
            .border(homePanelBorder(0.94f), RoundedCornerShape(32.dp))
            .padding(top = 18.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    echoString(en = "Recent activity", zh = "最近活动", ja = "最近のアクティビティ"),
                    color = homeTitleColor(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                RecentActivityTabs()
            }
        }
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RoonRecentActivityCard(
                title = status.track?.title ?: echoString(en = "Local music", zh = "本地音乐", ja = "ローカルミュージック"),
                subtitle = status.track?.artist ?: echoString(en = "Pick from your library", zh = "从曲库选择", ja = "ライブラリから選ぶ"),
                artworkUri = status.track?.artworkUri,
                accent = EchoAccent,
                onClick = if (status.track != null) onPlayPause else onOpenLibrary,
            )
            RoonRecentActivityCard(
                title = echoString(en = "Daily mix", zh = "每日推荐", ja = "今日のおすすめ"),
                subtitle = echoString(en = "From your local library", zh = "按你的本机曲库", ja = "端末のライブラリから"),
                artworkUri = null,
                accent = EchoColors.Brass,
                onClick = onOpenLibrary,
            )
            RoonRecentActivityCard(
                title = "PC ECHO",
                subtitle = echoString(en = "Desktop handoff playback", zh = "桌面接力播放", ja = "デスクトップへ引き継いで再生"),
                artworkUri = null,
                accent = EchoColors.Coral,
                onClick = onOpenLibrary,
            )
        }
    }
}

@Composable
internal fun HomeAlbumRecommendationsSection(
    albums: List<AlbumSummary>,
    onRefresh: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenAlbum: (AlbumSummary) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .shadow(
                elevation = if (LocalEchoDarkTheme.current) 0.dp else 12.dp,
                shape = RoundedCornerShape(26.dp),
                ambientColor = Color.Black.copy(alpha = 0.04f),
                spotColor = Color.Black.copy(alpha = 0.03f),
            )
            .clip(RoundedCornerShape(24.dp))
            .background(homePanelBrush())
            .border(homePanelBorder(), RoundedCornerShape(24.dp))
            .padding(top = 18.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                echoString(en = "Recommended for you", zh = "为你推荐", ja = "あなたへのおすすめ"),
                color = homeTitleColor(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
            )
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onRefresh),
                shape = RoundedCornerShape(16.dp),
                color = homePanelColor(0.94f),
                border = homePanelBorder(0.84f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, tint = homeBodyColor(), modifier = Modifier.size(15.dp))
                    Text(
                        echoString(en = "Refresh", zh = "刷新", ja = "更新"),
                        color = homeBodyColor(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (albums.isEmpty()) {
                item {
                    EmptyRecentAlbumsCard(onClick = onOpenLibrary)
                }
            } else {
                items(albums, key = { it.albumKey }) { album ->
                    RecommendedAlbumCard(
                        album = album,
                        onClick = { onOpenAlbum(album) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun RecommendedAlbumCard(
    album: AlbumSummary,
    onClick: () -> Unit,
) {
    val artistLabel = album.albumArtist ?: album.artist ?: echoString(en = "Unknown artist", zh = "未知艺人", ja = "不明なアーティスト")
    Column(
        modifier = Modifier
            .width(136.dp)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        ArtworkTile(
            artworkUri = album.artworkUri,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            accent = EchoAccentDeep,
            showSignal = album.artworkUri == null,
            cornerRadius = 14.dp,
            elevation = if (LocalEchoDarkTheme.current) 0.dp else 6.dp,
        )
        Text(
            album.title,
            color = homeTitleColor(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            artistLabel,
            color = homeBodyColor(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun HomeArtistRankingSection(
    artists: List<ArtistSummary>,
    onOpenArtist: (ArtistSummary) -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(homePanelColor(0.90f))
            .border(homePanelBorder(), RoundedCornerShape(26.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            echoString(en = "Artist ranking", zh = "艺人排行榜", ja = "アーティストランキング"),
            color = scheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
        )
        if (artists.isEmpty()) {
            EmptyRankingNotice(
                title = echoString(en = "No ranking data yet", zh = "暂无排行数据", ja = "ランキングデータはまだありません"),
                subtitle = echoString(
                    en = "Appears after you play an artist",
                    zh = "播放艺人歌曲后显示",
                    ja = "アーティストの曲を再生すると表示されます",
                ),
                onClick = onOpenLibrary,
            )
        } else {
            val maxTracks = artists.maxOf { it.trackCount.coerceAtLeast(1) }
            artists.take(5).forEachIndexed { index, artist ->
                ArtistRankRow(
                    rank = index + 1,
                    artist = artist,
                    progress = artist.trackCount.coerceAtLeast(1).toFloat() / maxTracks.toFloat(),
                    onClick = { onOpenArtist(artist) },
                )
            }
        }
    }
}

@Composable
private fun ArtistRankRow(
    rank: Int,
    artist: ArtistSummary,
    progress: Float,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    val durationLabel = artistReadableDuration(artist.durationMs)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .background(
                if (rank == 1) {
                    Brush.horizontalGradient(
                        listOf(
                            scheme.primary.copy(alpha = if (dark) 0.12f else 0.12f),
                            if (dark) Color.White.copy(alpha = 0.025f) else EchoAccentDeep.copy(alpha = 0.10f),
                            Color.Transparent,
                        ),
                    )
                } else {
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                },
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            rank.toString().padStart(2, '0'),
            color = if (rank == 1) scheme.primary else scheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.width(34.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                artist.name,
                color = scheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                echoString(
                    en = "${artist.trackCount} tracks · $durationLabel",
                    zh = "${artist.trackCount} 首 · $durationLabel",
                    ja = "${artist.trackCount} 曲 · $durationLabel",
                ),
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (dark) Color.White.copy(alpha = 0.09f) else scheme.surfaceVariant.copy(alpha = 0.52f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0.08f, 1f))
                        .height(4.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(
                            Brush.horizontalGradient(
                                if (dark) {
                                    listOf(scheme.primary.copy(alpha = 0.64f), scheme.primary.copy(alpha = 0.36f))
                                } else {
                                    listOf(EchoAccent, EchoAccentDeep)
                                },
                            ),
                        ),
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(99.dp),
            color = homePanelColor(0.78f),
            border = homePanelBorder(0.76f),
        ) {
            Text(
                echoString(
                    en = "${artist.albumCount.coerceAtLeast(0)} albums",
                    zh = "${artist.albumCount.coerceAtLeast(0)} 专辑",
                    ja = "${artist.albumCount.coerceAtLeast(0)} 枚",
                ),
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun HomeFavoriteAlbumsSection(
    albums: List<AlbumSummary>,
    heatmapDays: List<PlaybackHeatmapDay>,
    onOpenAlbum: (AlbumSummary) -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(homePanelColor(0.90f))
            .border(homePanelBorder(), RoundedCornerShape(26.dp))
            .padding(top = 16.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            echoString(en = "Albums you like", zh = "你喜欢的专辑", ja = "お気に入りのアルバム"),
            color = scheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 18.dp),
        )
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (albums.isEmpty()) {
                RecentActivityEmptyAlbumCard(
                    title = echoString(en = "No favorite albums yet", zh = "暂无偏好专辑", ja = "お気に入りのアルバムはまだありません"),
                    subtitle = echoString(
                        en = "Star an album on the player to see it here",
                        zh = "在播放页点收藏后显示",
                        ja = "再生画面で保存すると表示されます",
                    ),
                    onClick = onOpenLibrary,
                )
            } else {
                albums.take(4).forEach { album ->
                    RecommendedAlbumCard(
                        album = album,
                        onClick = { onOpenAlbum(album) },
                    )
                }
            }
        }
        FavoriteAlbumHeatmap(days = heatmapDays)
    }
}

@Composable
private fun FavoriteAlbumHeatmap(days: List<PlaybackHeatmapDay>) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    val heatmapLocale = Locale.getDefault()
    val heatmap = remember(days, heatmapLocale) { buildFavoriteHeatmap(days) }
    Column(
        modifier = Modifier
            .padding(horizontal = 18.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (dark) EchoGlassPanel.copy(alpha = 0.38f) else EchoHomeMist.copy(alpha = 0.52f))
            .border(
                BorderStroke(
                    1.dp,
                    if (dark) EchoDarkGlassBorder else EchoGlassBorder,
                ),
                RoundedCornerShape(14.dp),
            )
            .padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                echoString(
                    en = "Listening heatmap · last 12 weeks",
                    zh = "近 12 周播放热力图",
                    ja = "直近12週の再生ヒートマップ",
                ),
                color = scheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                echoString(
                    en = "${heatmap.activeWeeks} weeks active",
                    zh = "${heatmap.activeWeeks} 周活跃",
                    ja = "${heatmap.activeWeeks} 週アクティブ",
                ),
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val weekCount = heatmap.weeks.size
            val labelWidth = 22.dp
            val cellGap = 3.dp
            val cellSize = ((maxWidth - labelWidth - cellGap * weekCount) / weekCount)
                .coerceIn(8.dp, 14.dp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(cellGap), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(labelWidth))
                    heatmap.weeks.forEach { week ->
                        Text(
                            text = week.monthLabel,
                            color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.width(cellSize),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Column(
                        modifier = Modifier.width(labelWidth),
                        verticalArrangement = Arrangement.spacedBy(cellGap),
                    ) {
                        HeatmapWeekdayLabel(DayOfWeek.MONDAY.getDisplayName(TextStyle.NARROW, heatmapLocale), cellSize)
                        HeatmapWeekdayLabel("", cellSize)
                        HeatmapWeekdayLabel(DayOfWeek.WEDNESDAY.getDisplayName(TextStyle.NARROW, heatmapLocale), cellSize)
                        HeatmapWeekdayLabel("", cellSize)
                        HeatmapWeekdayLabel(DayOfWeek.FRIDAY.getDisplayName(TextStyle.NARROW, heatmapLocale), cellSize)
                        HeatmapWeekdayLabel("", cellSize)
                        HeatmapWeekdayLabel("", cellSize)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(cellGap)) {
                        heatmap.weeks.forEach { week ->
                            Column(verticalArrangement = Arrangement.spacedBy(cellGap)) {
                                week.days.forEach { day ->
                                    Box(
                                        modifier = Modifier
                                            .size(cellSize)
                                            .alpha(if (day.isFuture) 0.42f else 1f)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(heatmapLevelColor(day.level))
                                            .border(
                                                BorderStroke(
                                                    1.dp,
                                                    if (dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.58f),
                                                ),
                                                RoundedCornerShape(3.dp),
                                            ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                echoString(en = "Less", zh = "少", ja = "少"),
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(5.dp))
            (0..4).forEach { level ->
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(heatmapLevelColor(level))
                        .border(
                            BorderStroke(
                                1.dp,
                                if (dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.58f),
                            ),
                            RoundedCornerShape(2.dp),
                        ),
                )
                Spacer(Modifier.width(3.dp))
            }
            Text(
                echoString(en = "More", zh = "多", ja = "多"),
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun HeatmapWeekdayLabel(label: String, size: Dp) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .width(22.dp)
            .height(size),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(
            label,
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

private data class FavoriteHeatmap(
    val weeks: List<FavoriteHeatmapWeek>,
    val activeWeeks: Int,
)

private data class FavoriteHeatmapWeek(
    val monthLabel: String,
    val days: List<FavoriteHeatmapCell>,
)

private data class FavoriteHeatmapCell(
    val isFuture: Boolean,
    val level: Int,
)

private fun buildFavoriteHeatmap(days: List<PlaybackHeatmapDay>): FavoriteHeatmap {
    val today = LocalDate.now()
    val currentWeekStart = today.with(DayOfWeek.MONDAY)
    val firstWeekStart = currentWeekStart.minusWeeks(HomeHeatmapWeeks - 1L)
    val activityByDay = days.associateBy { it.epochDay }
    val maxCount = activityByDay.values.maxOfOrNull { it.playCount }?.coerceAtLeast(1) ?: 1
    val weeks = List(HomeHeatmapWeeks) { weekIndex ->
        val weekStart = firstWeekStart.plusWeeks(weekIndex.toLong())
        FavoriteHeatmapWeek(
            monthLabel = monthLabelForWeek(weekStart, weekIndex),
            days = List(7) { dayIndex ->
                val date = weekStart.plusDays(dayIndex.toLong())
                val count = activityByDay[date.toEpochDay()]?.playCount ?: 0
                FavoriteHeatmapCell(
                    isFuture = date.isAfter(today),
                    level = if (date.isAfter(today)) 0 else heatmapLevel(count, maxCount),
                )
            },
        )
    }
    return FavoriteHeatmap(
        weeks = weeks,
        activeWeeks = weeks.count { week -> week.days.any { it.level > 0 } },
    )
}

private fun monthLabelForWeek(weekStart: LocalDate, weekIndex: Int): String {
    val showLabel = weekIndex == 0 || weekStart.dayOfMonth <= 7
    return if (showLabel) {
        weekStart.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    } else {
        ""
    }
}

private fun heatmapLevel(count: Int, maxCount: Int): Int {
    if (count <= 0) return 0
    val ratio = count.toFloat() / maxCount.toFloat()
    return when {
        ratio >= 0.8f -> 4
        ratio >= 0.55f -> 3
        ratio >= 0.25f -> 2
        else -> 1
    }
}

@Composable
private fun heatmapLevelColor(level: Int): Color =
    if (LocalEchoDarkTheme.current) {
        val accent = MaterialTheme.colorScheme.primary
        when (level) {
            1 -> accent.copy(alpha = 0.18f)
            2 -> accent.copy(alpha = 0.28f)
            3 -> accent.copy(alpha = 0.42f)
            4 -> accent.copy(alpha = 0.58f)
            else -> Color.White.copy(alpha = 0.07f)
        }
    } else {
        when (level) {
            1 -> EchoHomeBlue.copy(alpha = 0.24f)
            2 -> EchoAccent.copy(alpha = 0.44f)
            3 -> EchoAccentDeep.copy(alpha = 0.62f)
            4 -> EchoGlassCyan.copy(alpha = 0.88f)
            else -> Color.White.copy(alpha = 0.72f)
        }
    }

@Composable
private fun EmptyRankingNotice(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (dark) EchoGlassPanel.copy(alpha = 0.28f) else EchoHomeMist.copy(alpha = 0.42f))
            .border(if (dark) echoDarkGlassBorder() else BorderStroke(1.dp, Color.Transparent), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, color = if (dark) scheme.onSurface else RoonInk, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(subtitle, color = if (dark) scheme.onSurfaceVariant else RoonMuted, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun artistReadableDuration(durationMs: Long): String {
    val minutes = (durationMs / 60000L).toInt()
    return if (minutes >= 1) {
        echoString(en = "$minutes min", zh = "$minutes 分钟", ja = "${minutes}分")
    } else {
        formatDuration(durationMs)
    }
}

@Composable
internal fun RecentActivityTabs() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = homePanelColor(0.82f),
        border = homePanelBorder(0.80f),
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RecentActivityTab(label = echoString(en = "Played", zh = "已播放", ja = "再生済み"), selected = true)
            RecentActivityTab(label = echoString(en = "Added", zh = "添加于", ja = "追加日"), selected = false)
        }
    }
}

@Composable
internal fun RecentActivityTab(
    label: String,
    selected: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) scheme.primary.copy(alpha = if (dark) 0.16f else 0.16f) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) scheme.primary else homeBodyColor(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
internal fun RoonRecentActivityCard(
    title: String,
    subtitle: String,
    artworkUri: String?,
    accent: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(126.dp)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box {
            ArtworkTile(
                artworkUri = artworkUri,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                accent = accent,
                showSignal = artworkUri == null,
                cornerRadius = 14.dp,
                elevation = 4.dp,
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(9.dp),
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, Color.White),
            ) {
                Icon(
                    Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = EchoHomeBlue,
                    modifier = Modifier
                        .padding(6.dp)
                        .size(22.dp),
                )
            }
        }
        Text(
            title,
            color = homeTitleColor(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            subtitle,
            color = homeBodyColor(),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun HomeRecommendationsSection(
    tracks: List<EchoTrack>,
    onRefresh: () -> Unit,
    onOpenLibrary: () -> Unit,
    onPlayTrack: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(homePanelBrush())
            .border(homePanelBorder(0.94f), RoundedCornerShape(26.dp))
            .padding(top = 16.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                echoString(en = "Recommended for you", zh = "为你推荐", ja = "あなたへのおすすめ"),
                color = homeTitleColor(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            val dark = LocalEchoDarkTheme.current
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onRefresh),
                shape = RoundedCornerShape(14.dp),
                color = homePanelColor(0.82f),
                border = homePanelBorder(0.80f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, tint = homeBodyColor(), modifier = Modifier.size(16.dp))
                    Text(
                        echoString(en = "Refresh", zh = "刷新", ja = "更新"),
                        color = homeBodyColor(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 18.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(homePanelColor(0.94f))
                    .border(homePanelBorder(0.96f), RoundedCornerShape(18.dp))
                    .clickable(onClick = onOpenLibrary)
                    .padding(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    EchoIconBadge(Icons.Rounded.LibraryMusic)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            echoString(en = "Scan to generate recommendations", zh = "扫描后生成推荐", ja = "スキャンするとおすすめが作られます"),
                            color = homeTitleColor(),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            echoString(
                                en = "Pick a few tracks from your local library to start.",
                                zh = "从本机曲库挑几首开始。",
                                ja = "端末のライブラリから数曲選んで始めましょう。",
                            ),
                            color = homeBodyColor(),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                tracks.forEachIndexed { index, track ->
                    RecommendationCard(
                        track = track,
                        onClick = { onPlayTrack(index) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun RecommendationCard(
    track: EchoTrack,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(118.dp)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        ArtworkTile(
            artworkUri = track.artworkUri,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            accent = EchoAccentDeep,
            showSignal = track.artworkUri == null,
            cornerRadius = 4.dp,
            elevation = 0.dp,
        )
        Text(
            track.title,
            color = homeTitleColor(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${track.artist} · ${formatDuration(track.durationMs)}",
            color = homeBodyColor(),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun RoonListenLaterPanel(onOpenConnect: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(homePanelBrush())
            .border(homePanelBorder(0.96f), RoundedCornerShape(32.dp))
            .padding(horizontal = 22.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            echoString(en = "Listen later", zh = "稍后聆听", ja = "あとで聴く"),
            color = homeTitleColor(),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            echoString(en = "Leave a trail through your local library", zh = "为本机曲库留一条线索", ja = "端末のライブラリに手がかりを残す"),
            color = homeTitleColor(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            echoString(
                en = "Park the albums, artists, and tracks you want, then pick them up later.",
                zh = "把想听的专辑、歌手和曲目先放在这里，稍后继续。",
                ja = "聴きたいアルバム、アーティスト、曲をここに置いて、あとで続けましょう。",
            ),
            color = homeBodyColor(),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Surface(
            modifier = Modifier
                .padding(top = 8.dp)
                .widthIn(min = 230.dp)
                .clickable(onClick = onOpenConnect),
            shape = RoundedCornerShape(28.dp),
            color = EchoHomeBlue,
            contentColor = Color.White,
        ) {
            Text(
                echoString(en = "Connect PC ECHO", zh = "连接 PC ECHO", ja = "PC ECHO に接続"),
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
internal fun HomeTopChrome(onOpenLibrary: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIconButton(
            icon = Icons.Rounded.LibraryMusic,
            description = echoString(en = "Open library", zh = "打开曲库", ja = "ライブラリを開く"),
            onClick = onOpenLibrary,
        )
        GlassSurface(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 46.dp),
            alpha = 0.18f,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.76f), modifier = Modifier.size(20.dp))
                Text(
                    echoString(en = "Search local music...", zh = "搜索本机音乐...", ja = "端末の音楽を検索..."),
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun HomeGreeting(status: EchoPlaybackStatus) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Good Evening",
            color = Color.White.copy(alpha = 0.68f),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            status.track?.artist?.takeIf { it.isNotBlank() } ?: "ECHO Mobile",
            color = Color.White,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (status.track != null) {
                echoString(en = "Not every journey has an ending", zh = "不是所有的旅途都有终点", ja = "すべての旅に終わりがあるわけではない")
            } else {
                echoString(en = "Wake up your local music", zh = "让本机音乐醒过来", ja = "端末の音楽を目覚めさせよう")
            },
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun DailyRecommendationCard(
    status: EchoPlaybackStatus,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 158.dp)
            .shadow(elevation = 14.dp, shape = RoundedCornerShape(24.dp), clip = false)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF2C3A47),
                        Color(0xFF24303B),
                        Color(0xFF1C2730),
                        EchoAccentDeep.copy(alpha = 0.55f),
                    ),
                ),
            ),
    ) {
        AmbientPlanet(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 14.dp, end = 34.dp),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                if (status.track != null) {
                    echoString(en = "Continue playing", zh = "继续播放", ja = "再生を続ける")
                } else {
                    echoString(en = "Daily mix", zh = "每日推荐", ja = "今日のおすすめ")
                },
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                status.track?.title ?: echoString(en = "Discover great music", zh = "发现好音乐", ja = "いい音楽を見つけよう"),
                color = Color.White.copy(alpha = 0.88f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
internal fun HomeModeRibbon(
    repeatMode: EchoRepeatMode,
    shuffleEnabled: Boolean,
    onCycleRepeatMode: () -> Unit,
    onToggleShuffle: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenConnect: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        HomeModeChip(
            icon = if (repeatMode == EchoRepeatMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            label = repeatModeLabel(repeatMode),
            selected = repeatMode != EchoRepeatMode.Off,
            onClick = onCycleRepeatMode,
            modifier = Modifier.weight(1f),
        )
        HomeModeChip(
            icon = Icons.Rounded.Shuffle,
            label = if (shuffleEnabled) {
                echoString(en = "Shuffle", zh = "随机", ja = "シャッフル")
            } else {
                echoString(en = "In order", zh = "顺序", ja = "リスト順")
            },
            selected = shuffleEnabled,
            onClick = onToggleShuffle,
            modifier = Modifier.weight(1f),
        )
        HomeModeChip(
            icon = Icons.Rounded.LibraryMusic,
            label = echoString(en = "Library", zh = "曲库", ja = "ライブラリ"),
            selected = false,
            onClick = onOpenLibrary,
            modifier = Modifier.weight(1f),
        )
        HomeModeChip(
            icon = Icons.Rounded.Devices,
            label = echoString(en = "Handoff", zh = "接力", ja = "ハンドオフ"),
            selected = false,
            onClick = onOpenConnect,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun HomeModeChip(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .heightIn(min = 58.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = if (selected) 0.24f else 0.14f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = if (selected) 0.42f else 0.24f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = label, tint = if (selected) EchoAccent else Color.White.copy(alpha = 0.82f), modifier = Modifier.size(21.dp))
            Text(label, color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun PlaybackQueuePanel(
    status: EchoPlaybackStatus,
    compact: Boolean,
    onOpenLibrary: () -> Unit,
    onOpenConnect: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleShuffle: () -> Unit,
) {
    val hasTrack = status.track != null
    EchoPanel(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(if (compact) 12.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
        ) {
            EchoSectionTitle(
                if (hasTrack) {
                    echoString(en = "Playback queue", zh = "播放队列", ja = "再生キュー")
                } else {
                    echoString(en = "Ready to play", zh = "准备播放", ja = "再生の準備")
                },
                status.track?.album ?: echoString(en = "Queue is empty", zh = "队列为空", ja = "キューは空です"),
            )
            QueuePreviewList(status = status, compact = compact)
            PlaybackModeControls(
                repeatMode = status.repeatMode,
                shuffleEnabled = status.shuffleEnabled,
                onCycleRepeatMode = onCycleRepeatMode,
                onToggleShuffle = onToggleShuffle,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                PlaybackActionCard(
                    icon = Icons.Rounded.LibraryMusic,
                    title = if (hasTrack) {
                        echoString(en = "Back to library", zh = "回到曲库", ja = "ライブラリに戻る")
                    } else {
                        echoString(en = "Choose a track", zh = "选择曲目", ja = "曲を選ぶ")
                    },
                    detail = if (hasTrack) {
                        echoString(en = "Adjust the local queue", zh = "调整本地队列", ja = "ローカルキューを調整")
                    } else {
                        echoString(en = "Start with local music", zh = "从本机音乐开始", ja = "端末の音楽から始める")
                    },
                    onClick = onOpenLibrary,
                    modifier = Modifier.weight(1f),
                )
                PlaybackActionCard(
                    icon = Icons.Rounded.Devices,
                    title = echoString(en = "PC handoff", zh = "PC 接力", ja = "PC ハンドオフ"),
                    detail = if (hasTrack) {
                        echoString(en = "Switch to PC ECHO", zh = "切换到 PC ECHO", ja = "PC ECHO に切り替える")
                    } else {
                        echoString(en = "Pair to play remotely", zh = "配对后远程播放", ja = "ペアリング後にリモート再生")
                    },
                    onClick = onOpenConnect,
                    modifier = Modifier.weight(1f),
                )
            }
            if (!compact) {
                PlaybackHandoffFlow(active = hasTrack)
                EchoPlaceholderLine(
                    if (hasTrack) {
                        echoString(
                            en = "Next: lyrics, repeat, and queue reorder",
                            zh = "下一步补歌词、循环与队列重排",
                            ja = "次は歌詞、リピート、キュー並べ替え",
                        )
                    } else {
                        echoString(
                            en = "Lyrics, repeat, and queue reorder are reserved",
                            zh = "歌词、循环与队列重排位已预留",
                            ja = "歌詞、リピート、キュー並べ替えの枠は用意済みです",
                        )
                    },
                )
            }
        }
    }
}

@Composable
internal fun QueuePreviewList(
    status: EchoPlaybackStatus,
    compact: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        QueuePreviewItem(
            icon = Icons.Rounded.MusicNote,
            label = echoString(en = "Now", zh = "当前", ja = "再生中"),
            title = status.track?.title ?: echoString(en = "Nothing playing", zh = "暂无播放", ja = "再生中の曲はありません"),
            detail = status.track?.artist ?: echoString(en = "Pick a song from your library", zh = "从曲库选择一首歌", ja = "ライブラリから1曲選ぶ"),
            active = status.track != null,
        )
        if (!compact) {
            QueuePreviewItem(
                icon = Icons.Rounded.LibraryMusic,
                label = echoString(en = "Next", zh = "下一首", ja = "次の曲"),
                title = echoString(en = "Smart queue", zh = "智能队列", ja = "スマートキュー"),
                detail = if (status.track != null) {
                    echoString(en = "Continues from the local queue", zh = "跟随本机队列继续播放", ja = "ローカルキューに沿って再生")
                } else {
                    echoString(
                        en = "Upcoming tracks appear after you pick a song",
                        zh = "选歌后显示即将播放",
                        ja = "曲を選ぶと次に再生する曲が表示されます",
                    )
                },
                active = false,
            )
            QueuePreviewItem(
                icon = Icons.Rounded.Devices,
                label = echoString(en = "Handoff", zh = "接力", ja = "ハンドオフ"),
                title = "PC ECHO",
                detail = if (status.track != null) {
                    echoString(en = "Switch to desktop output", zh = "可切换到桌面输出", ja = "デスクトップ出力に切り替えられます")
                } else {
                    echoString(en = "Pair to take over remote playback", zh = "配对后接管远程播放", ja = "ペアリング後にリモート再生を引き継ぎ")
                },
                active = false,
            )
        }
    }
}

@Composable
internal fun PlaybackModeControls(
    repeatMode: EchoRepeatMode,
    shuffleEnabled: Boolean,
    onCycleRepeatMode: () -> Unit,
    onToggleShuffle: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        PlaybackModeButton(
            icon = if (repeatMode == EchoRepeatMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            title = repeatModeLabel(repeatMode),
            detail = echoString(en = "Tap to switch", zh = "点按切换", ja = "タップで切り替え"),
            selected = repeatMode != EchoRepeatMode.Off,
            onClick = onCycleRepeatMode,
            modifier = Modifier.weight(1f),
        )
        PlaybackModeButton(
            icon = Icons.Rounded.Shuffle,
            title = if (shuffleEnabled) {
                echoString(en = "Shuffle on", zh = "随机开启", ja = "シャッフルオン")
            } else {
                echoString(en = "In order", zh = "顺序播放", ja = "リスト順")
            },
            detail = if (shuffleEnabled) {
                echoString(en = "Queue is shuffled", zh = "队列随机", ja = "キューをシャッフル")
            } else {
                echoString(en = "Follows queue order", zh = "按队列顺序", ja = "キューの順に再生")
            },
            selected = shuffleEnabled,
            onClick = onToggleShuffle,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun PlaybackModeButton(
    icon: ImageVector,
    title: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) scheme.primary.copy(alpha = 0.14f) else homePanelColor(0.60f),
        border = if (selected) BorderStroke(1.dp, scheme.primary.copy(alpha = 0.28f)) else homePanelBorder(0.66f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = title,
                tint = if (selected) scheme.primary else scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(
                    detail,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
internal fun QueuePreviewItem(
    icon: ImageVector,
    label: String,
    title: String,
    detail: String,
    active: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (active) scheme.primary.copy(alpha = 0.12f) else homePanelColor(0.56f),
        border = if (active) BorderStroke(1.dp, scheme.primary.copy(alpha = 0.24f)) else homePanelBorder(0.62f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = scheme.primary.copy(alpha = if (active) 0.18f else 0.12f),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.padding(8.dp).size(20.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(label, color = scheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(
                    detail,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
internal fun PlaybackHandoffFlow(active: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = homePanelColor(0.58f),
        border = homePanelBorder(0.64f),
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(echoString(en = "Handoff path", zh = "接力路径", ja = "ハンドオフ経路"), fontWeight = FontWeight.SemiBold)
                Text(
                    if (active) {
                        echoString(en = "Ready to hand off", zh = "可接力", ja = "ハンドオフ可能")
                    } else {
                        echoString(en = "Pick a track first", zh = "待选择曲目", ja = "曲の選択待ち")
                    },
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                HandoffStep("1", echoString(en = "Play on device", zh = "本机播放", ja = "この端末で再生"), selected = true, modifier = Modifier.weight(1f))
                HandoffStep("2", echoString(en = "Connect PC", zh = "连接 PC", ja = "PC に接続"), selected = active, modifier = Modifier.weight(1f))
                HandoffStep("3", echoString(en = "PC output", zh = "PC 输出", ja = "PC 出力"), selected = active, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun HandoffStep(
    number: String,
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) scheme.primary.copy(alpha = 0.14f) else homePanelColor(0.60f),
        border = if (selected) BorderStroke(1.dp, scheme.primary.copy(alpha = 0.24f)) else homePanelBorder(0.62f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(number, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun PlaybackActionCard(
    icon: ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = homePanelColor(0.58f),
        border = homePanelBorder(0.64f),
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                detail,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun NowPlayingHero(
    status: EchoPlaybackStatus,
    compact: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    val heroBrush = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = 0.72f),
            EchoHomeMist.copy(alpha = 0.58f),
            EchoHomeBlue.copy(alpha = 0.12f),
        ),
    )
    if (compact) {
        CompactNowPlayingHero(
            status = status,
            heroBrush = heroBrush,
            onPlayPause = onPlayPause,
            onNext = onNext,
            onPrevious = onPrevious,
        )
        return
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 274.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(heroBrush)
            .border(BorderStroke(1.dp, EchoGlassBorder.copy(alpha = 0.84f)), RoundedCornerShape(26.dp))
            .padding(18.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    echoString(en = "This device", zh = "本机会话", ja = "この端末"),
                    style = MaterialTheme.typography.labelSmall,
                    color = EchoAccentText,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    playbackStateLabel(status.state),
                    style = MaterialTheme.typography.labelMedium,
                    color = homeBodyColor(),
                )
            }
            ArtworkTile(
                artworkUri = status.track?.artworkUri,
                modifier = Modifier
                    .fillMaxWidth(0.44f)
                    .aspectRatio(1f),
                accent = EchoAccent,
                showSignal = true,
                cornerRadius = 24.dp,
                elevation = 18.dp,
            )
            Text(
                status.track?.title ?: echoString(en = "Nothing playing", zh = "暂无播放", ja = "再生中の曲はありません"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = homeTitleColor(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                status.track?.artist ?: echoString(en = "Pick a song from your library", zh = "从曲库选择一首歌", ja = "ライブラリから1曲選ぶ"),
                color = homeBodyColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            PlaybackProgress(status.positionMs, status.durationMs, light = false)
            TransportControls(
                isPlaying = status.isPlaying,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
            )
        }
    }
}

@Composable
internal fun CompactNowPlayingHero(
    status: EchoPlaybackStatus,
    heroBrush: Brush,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(heroBrush)
            .border(BorderStroke(1.dp, EchoGlassBorder.copy(alpha = 0.82f)), RoundedCornerShape(22.dp))
            .padding(14.dp),
    ) {
        val artworkSize = if (maxWidth < 420.dp) 76.dp else 92.dp
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtworkTile(
                artworkUri = status.track?.artworkUri,
                modifier = Modifier.size(artworkSize),
                accent = EchoAccent,
                showSignal = true,
                cornerRadius = 18.dp,
                elevation = 12.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            echoString(en = "This device", zh = "本机会话", ja = "この端末"),
                            style = MaterialTheme.typography.labelSmall,
                            color = EchoAccentText,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            playbackStateLabel(status.state),
                            style = MaterialTheme.typography.labelMedium,
                            color = homeBodyColor(),
                        )
                    }
                    TransportControls(
                        isPlaying = status.isPlaying,
                        onPlayPause = onPlayPause,
                        onNext = onNext,
                        onPrevious = onPrevious,
                    )
                }
                Text(
                    status.track?.title ?: echoString(en = "Nothing playing", zh = "暂无播放", ja = "再生中の曲はありません"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = homeTitleColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    status.track?.artist ?: echoString(en = "Pick a song from your library", zh = "从曲库选择一首歌", ja = "ライブラリから1曲選ぶ"),
                    color = homeBodyColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                PlaybackProgress(status.positionMs, status.durationMs, light = false)
            }
        }
    }
}

@Composable
internal fun HeroMetaRail(status: EchoPlaybackStatus) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = homePanelColor(0.48f),
        border = homePanelBorder(0.58f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactFact(echoString(en = "Output", zh = "输出", ja = "出力"), status.diagnostics.outputRoute, Modifier.weight(1.25f))
            CompactFact(
                echoString(en = "Processing", zh = "处理", ja = "処理"),
                if (status.diagnostics.offloadActive) {
                    echoString(en = "Hardware offload", zh = "硬件直通", ja = "ハードウェア直通")
                } else {
                    echoString(en = "Clear", zh = "清晰", ja = "クリア")
                },
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun CompactFact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun TransportControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    val haptics = rememberEchoHapticPerformer()
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = {
                haptics.tick()
                onPrevious()
            },
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                Icons.Rounded.SkipPrevious,
                contentDescription = echoString(en = "Previous", zh = "上一首", ja = "前の曲"),
                tint = EchoHomeBlue,
                modifier = Modifier.size(28.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(56.dp)
                .shadow(elevation = 10.dp, shape = CircleShape, clip = false)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(EchoHomeBlue, EchoHomeBlueDeep),
                    ),
                )
                .clickable(
                    onClick = {
                        haptics.confirm()
                        onPlayPause()
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = echoString(en = "Play or pause", zh = "播放或暂停", ja = "再生または一時停止"),
                tint = Color.White,
                modifier = Modifier.size(30.dp),
            )
        }
        IconButton(
            onClick = {
                haptics.tick()
                onNext()
            },
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                Icons.Rounded.SkipNext,
                contentDescription = echoString(en = "Next", zh = "下一首", ja = "次の曲"),
                tint = EchoHomeBlue,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
internal fun PlaybackProgress(positionMs: Long, durationMs: Long, light: Boolean = false) {
    val foreground = if (light) Color.White else EchoAccent
    val secondary = if (light) Color.White.copy(alpha = 0.66f) else Color.White.copy(alpha = 0.6f)
    val trackColor = if (light) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.14f)
    val fraction = progressFraction(positionMs, durationMs).coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(trackColor),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(foreground),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(positionMs), color = secondary, style = MaterialTheme.typography.labelSmall)
            Text(formatDuration(durationMs), color = secondary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
internal fun playbackStateLabel(state: EchoPlaybackState): String =
    when (state) {
        EchoPlaybackState.Idle -> echoString(en = "Idle", zh = "空闲", ja = "待機")
        EchoPlaybackState.Loading -> echoString(en = "Loading", zh = "加载中", ja = "読み込み中")
        EchoPlaybackState.Playing -> echoString(en = "Playing", zh = "播放中", ja = "再生中")
        EchoPlaybackState.Paused -> echoString(en = "Paused", zh = "已暂停", ja = "一時停止")
        EchoPlaybackState.Seeking -> echoString(en = "Seeking", zh = "定位中", ja = "シーク中")
        EchoPlaybackState.Buffering -> echoString(en = "Buffering", zh = "缓冲中", ja = "バッファ中")
        EchoPlaybackState.Ended -> echoString(en = "Ended", zh = "已结束", ja = "終了")
        EchoPlaybackState.Stopped -> echoString(en = "Stopped", zh = "已停止", ja = "停止")
        EchoPlaybackState.Error -> echoString(en = "Error", zh = "错误", ja = "エラー")
    }

@Composable
internal fun repeatModeLabel(mode: EchoRepeatMode): String =
    when (mode) {
        EchoRepeatMode.Off -> echoString(en = "Repeat off", zh = "循环关闭", ja = "リピートオフ")
        EchoRepeatMode.All -> echoString(en = "Repeat all", zh = "列表循环", ja = "全曲リピート")
        EchoRepeatMode.One -> echoString(en = "Repeat one", zh = "单曲循环", ja = "1曲リピート")
    }

