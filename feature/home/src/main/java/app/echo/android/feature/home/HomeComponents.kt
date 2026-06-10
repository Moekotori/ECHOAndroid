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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalConfiguration
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
import app.echo.android.design.EchoGlassViolet
import app.echo.android.design.EchoHomeBlue
import app.echo.android.design.EchoHomeBlueDeep
import app.echo.android.design.EchoHomeMist
import app.echo.android.design.EchoIconBadge
import app.echo.android.design.EchoMetricTile
import app.echo.android.design.EchoPanel
import app.echo.android.design.EchoPlaceholderLine
import app.echo.android.design.EchoSectionTitle
import app.echo.android.design.EchoSegmentChip
import app.echo.android.design.EchoSoftLine
import app.echo.android.design.AmbientPlanet
import app.echo.android.design.GlassIconButton
import app.echo.android.design.GlassSurface
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.RoonInk
import app.echo.android.design.RoonMuted
import app.echo.android.design.echoDarkGlassBorder
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

private const val HomeHeatmapWeeks = 12

@Composable
private fun homePanelColor(lightAlpha: Float = 0.90f): Color {
    return if (LocalEchoDarkTheme.current) {
        EchoGlassInk.copy(alpha = lightAlpha.coerceIn(0.86f, 0.94f))
    } else {
        Color.White.copy(alpha = lightAlpha.coerceIn(0.95f, 1.00f))
    }
}

@Composable
private fun homePanelBorder(lightAlpha: Float = 0.94f): BorderStroke {
    return BorderStroke(
        1.dp,
        if (LocalEchoDarkTheme.current) Color.White.copy(alpha = 0.14f) else EchoSoftLine.copy(alpha = lightAlpha.coerceIn(0.74f, 0.96f)),
    )
}

@Composable
private fun homeTitleColor(): Color =
    if (LocalEchoDarkTheme.current) Color.White else RoonInk

@Composable
private fun homeBodyColor(): Color =
    if (LocalEchoDarkTheme.current) Color.White.copy(alpha = 0.88f) else RoonMuted.copy(alpha = 0.94f)

@Composable
private fun homePanelBrush(): Brush {
    return if (LocalEchoDarkTheme.current) {
        Brush.linearGradient(
            listOf(
                EchoGlassPanel.copy(alpha = 0.88f),
                EchoGlassInk.copy(alpha = 0.94f),
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
        Text(value, color = homeTitleColor(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, color = homeBodyColor(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun RoonHomeHeader(
    status: EchoPlaybackStatus,
    compact: Boolean,
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
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = homePanelColor(0.94f),
                border = homePanelBorder(0.88f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = homeBodyColor(), modifier = Modifier.size(20.dp))
                    Text("搜索本机音乐、专辑、歌手", color = homeBodyColor(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (false) {
                Text(
                text = status.track?.title ?: "让本机音乐醒过来",
                color = homeBodyColor(),
                style = MaterialTheme.typography.titleMedium,
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
            .padding(top = 20.dp, bottom = 20.dp),
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
                "最近活动",
                color = homeTitleColor(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            RecentActivityTabs(
                selectedMode = selectedMode,
                onSelect = { selectedMode = it },
            )
        }
        LazyRow(
            modifier = Modifier.height(if (displayAlbums.isEmpty()) 220.dp else 256.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            if (displayAlbums.isEmpty()) {
                item {
                    RecentActivityEmptyAlbumCard(
                        title = if (selectedMode == RecentActivityMode.Played) "\u6682\u65e0\u5df2\u64ad\u653e" else "\u6682\u65e0\u65b0\u589e\u4e13\u8f91",
                        subtitle = if (selectedMode == RecentActivityMode.Played) "\u64ad\u653e\u4e13\u8f91\u540e\u663e\u793a" else "\u626b\u63cf\u66f2\u5e93\u540e\u663e\u793a",
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
                label = "\u5df2\u64ad\u653e",
                selected = selectedMode == RecentActivityMode.Played,
                onClick = { onSelect(RecentActivityMode.Played) },
            )
            RecentActivityModeTab(
                label = "\u6dfb\u52a0\u4e8e",
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
internal fun RecentPlayedAlbumsTab() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, EchoGlassBorder),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(EchoAccentDeep.copy(alpha = 0.16f))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "已播放",
                color = EchoAccentText,
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
    val artistLabel = album.albumArtist ?: album.artist ?: "未知艺人"
    Column(
        modifier = Modifier
            .width(150.dp)
            .height(256.dp)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            recentAlbumSubtitle(album, mode, artistLabel),
            color = homeBodyColor(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun recentAlbumSubtitle(
    album: AlbumSummary,
    mode: RecentActivityMode,
    artistLabel: String,
): String {
    val dateLabel = album.addedAtSeconds.takeIf { it > 0L }?.let(::formatAlbumDate)
    return if (mode == RecentActivityMode.Added && dateLabel != null) {
        "$artistLabel \u00b7 $dateLabel"
    } else {
        artistLabel
    }
}

private fun formatAlbumDate(seconds: Long): String {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = seconds * 1000L
    }
    return "${calendar.get(Calendar.MONTH) + 1}\u6708${calendar.get(Calendar.DAY_OF_MONTH)}\u65e5"
}

@Composable
private fun RecentActivityEmptyAlbumCard(
    title: String,
    subtitle: String,
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
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            subtitle,
            color = homeBodyColor(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun EmptyRecentAlbumsCard(
    title: String = "\u6682\u65e0\u4e13\u8f91",
    subtitle: String = "\u626b\u63cf\u66f2\u5e93\u540e\u663e\u793a",
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
            "暂无专辑",
            color = homeTitleColor(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            "扫描曲库后显示",
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
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White,
                        EchoHomeMist,
                        Color.White.copy(alpha = 0.92f),
                    ),
                ),
            )
            .border(BorderStroke(1.dp, EchoGlassBorder), RoundedCornerShape(32.dp))
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
                    "最近活动",
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
                title = status.track?.title ?: "本地音乐",
                subtitle = status.track?.artist ?: "从曲库选择",
                artworkUri = status.track?.artworkUri,
                accent = EchoAccent,
                onClick = if (status.track != null) onPlayPause else onOpenLibrary,
            )
            RoonRecentActivityCard(
                title = "每日推荐",
                subtitle = "按你的本机曲库",
                artworkUri = null,
                accent = EchoColors.Brass,
                onClick = onOpenLibrary,
            )
            RoonRecentActivityCard(
                title = "PC ECHO",
                subtitle = "桌面接力播放",
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
                "为你推荐",
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
                    Text("刷新", color = homeBodyColor(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
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
    val artistLabel = album.albumArtist ?: album.artist ?: "未知艺人"
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
            "\u827a\u4eba\u6392\u884c\u699c",
            color = scheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
        )
        if (artists.isEmpty()) {
            EmptyRankingNotice(
                title = "\u6682\u65e0\u6392\u884c\u6570\u636e",
                subtitle = "\u64ad\u653e\u827a\u4eba\u6b4c\u66f2\u540e\u663e\u793a",
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .background(
                if (rank == 1) {
                    Brush.horizontalGradient(
                        listOf(
                            EchoAccent.copy(alpha = 0.12f),
                            EchoAccentDeep.copy(alpha = 0.10f),
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
                "${artist.trackCount} \u9996 \u00b7 ${artistReadableDuration(artist.durationMs)}",
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
                    .background(if (dark) Color.White.copy(alpha = 0.16f) else scheme.surfaceVariant.copy(alpha = 0.52f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0.08f, 1f))
                        .height(4.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(Brush.horizontalGradient(listOf(EchoAccent, EchoAccentDeep))),
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(99.dp),
            color = homePanelColor(0.78f),
            border = homePanelBorder(0.76f),
        ) {
            Text(
                "${artist.albumCount.coerceAtLeast(0)} \u4e13\u8f91",
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
            "\u4f60\u559c\u6b22\u7684\u4e13\u8f91",
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
                    title = "\u6682\u65e0\u504f\u597d\u4e13\u8f91",
                    subtitle = "\u591a\u542c\u51e0\u5f20\u4e13\u8f91\u540e\u663e\u793a",
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
    val heatmap = remember(days) { buildFavoriteHeatmap(days) }
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
                "近 12 周播放热力图",
                color = scheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${heatmap.activeWeeks} 周活跃",
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
                        HeatmapWeekdayLabel("一", cellSize)
                        HeatmapWeekdayLabel("", cellSize)
                        HeatmapWeekdayLabel("三", cellSize)
                        HeatmapWeekdayLabel("", cellSize)
                        HeatmapWeekdayLabel("五", cellSize)
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
                "少",
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
                "多",
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
        weekStart.month.getDisplayName(TextStyle.SHORT, Locale.CHINA)
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
    when (level) {
        1 -> EchoHomeBlue.copy(alpha = 0.24f)
        2 -> EchoAccent.copy(alpha = 0.44f)
        3 -> EchoAccentDeep.copy(alpha = 0.62f)
        4 -> EchoGlassCyan.copy(alpha = 0.88f)
        else -> if (LocalEchoDarkTheme.current) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.72f)
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

private fun artistReadableDuration(durationMs: Long): String {
    val minutes = (durationMs / 60000L).toInt()
    return if (minutes >= 1) "$minutes \u5206\u949f" else formatDuration(durationMs)
}

@Composable
internal fun RecentActivityTabs() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, EchoGlassBorder),
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RecentActivityTab(label = "已播放", selected = true)
            RecentActivityTab(label = "添加于", selected = false)
        }
    }
}

@Composable
internal fun RecentActivityTab(
    label: String,
    selected: Boolean,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) EchoAccentDeep.copy(alpha = 0.16f) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) EchoAccentText else RoonMuted,
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
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White,
                        EchoHomeMist,
                        Color.White.copy(alpha = 0.92f),
                    ),
                ),
            )
            .border(BorderStroke(1.dp, EchoGlassBorder), RoundedCornerShape(26.dp))
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
                "为你推荐",
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
                color = if (dark) EchoGlassPanel.copy(alpha = 0.62f) else Color.White.copy(alpha = 0.94f),
                border = if (dark) BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)) else BorderStroke(1.dp, EchoGlassBorder),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, tint = homeBodyColor(), modifier = Modifier.size(16.dp))
                    Text("刷新", color = homeBodyColor(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
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
                        Text("扫描后生成推荐", color = homeTitleColor(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("从本机曲库挑几首开始。", color = homeBodyColor(), style = MaterialTheme.typography.bodySmall)
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
            "稍后聆听",
            color = homeTitleColor(),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "为本机曲库留一条线索",
            color = homeTitleColor(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "把想听的专辑、歌手和曲目先放在这里，稍后继续。",
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
                "连接 PC ECHO",
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
            description = "打开曲库",
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
                Text("搜索本机音乐...", color = Color.White.copy(alpha = 0.72f), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            if (status.track != null) "不是所有的旅途都有终点" else "让本机音乐醒过来",
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
                if (status.track != null) "继续播放" else "每日推荐",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                status.track?.title ?: "发现好音乐",
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
            label = if (shuffleEnabled) "随机" else "顺序",
            selected = shuffleEnabled,
            onClick = onToggleShuffle,
            modifier = Modifier.weight(1f),
        )
        HomeModeChip(
            icon = Icons.Rounded.LibraryMusic,
            label = "曲库",
            selected = false,
            onClick = onOpenLibrary,
            modifier = Modifier.weight(1f),
        )
        HomeModeChip(
            icon = Icons.Rounded.Devices,
            label = "接力",
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
                if (hasTrack) "播放队列" else "准备播放",
                status.track?.album ?: "队列为空",
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
                    title = if (hasTrack) "回到曲库" else "选择曲目",
                    detail = if (hasTrack) "调整本地队列" else "从本机音乐开始",
                    onClick = onOpenLibrary,
                    modifier = Modifier.weight(1f),
                )
                PlaybackActionCard(
                    icon = Icons.Rounded.Devices,
                    title = "PC 接力",
                    detail = if (hasTrack) "切换到 PC ECHO" else "配对后远程播放",
                    onClick = onOpenConnect,
                    modifier = Modifier.weight(1f),
                )
            }
            if (!compact) {
                PlaybackHandoffFlow(active = hasTrack)
                EchoPlaceholderLine(if (hasTrack) "下一步补歌词、循环与队列重排" else "歌词、循环与队列重排位已预留")
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
            label = "当前",
            title = status.track?.title ?: "暂无播放",
            detail = status.track?.artist ?: "从曲库选择一首歌",
            active = status.track != null,
        )
        if (!compact) {
            QueuePreviewItem(
                icon = Icons.Rounded.LibraryMusic,
                label = "下一首",
                title = "智能队列",
                detail = if (status.track != null) "跟随本机队列继续播放" else "选歌后显示即将播放",
                active = false,
            )
            QueuePreviewItem(
                icon = Icons.Rounded.Devices,
                label = "接力",
                title = "PC ECHO",
                detail = if (status.track != null) "可切换到桌面输出" else "配对后接管远程播放",
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
            detail = "点按切换",
            selected = repeatMode != EchoRepeatMode.Off,
            onClick = onCycleRepeatMode,
            modifier = Modifier.weight(1f),
        )
        PlaybackModeButton(
            icon = Icons.Rounded.Shuffle,
            title = if (shuffleEnabled) "随机开启" else "顺序播放",
            detail = if (shuffleEnabled) "队列随机" else "按队列顺序",
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
                Text("接力路径", fontWeight = FontWeight.SemiBold)
                Text(
                    if (active) "可接力" else "待选择曲目",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                HandoffStep("1", "本机播放", selected = true, modifier = Modifier.weight(1f))
                HandoffStep("2", "连接 PC", selected = active, modifier = Modifier.weight(1f))
                HandoffStep("3", "PC 输出", selected = active, modifier = Modifier.weight(1f))
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
                    "本机会话",
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
                status.track?.title ?: "暂无播放",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = homeTitleColor(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                status.track?.artist ?: "从曲库选择一首歌",
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
                            "本机会话",
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
                    status.track?.title ?: "暂无播放",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = homeTitleColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    status.track?.artist ?: "从曲库选择一首歌",
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
            CompactFact("输出", status.diagnostics.outputRoute, Modifier.weight(1.25f))
            CompactFact("处理", if (status.diagnostics.offloadActive) "硬件直通" else "清晰", Modifier.weight(1f))
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
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(44.dp)) {
            Icon(
                Icons.Rounded.SkipPrevious,
                contentDescription = "上一首",
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
                .clickable(onClick = onPlayPause),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = "播放或暂停",
                tint = Color.White,
                modifier = Modifier.size(30.dp),
            )
        }
        IconButton(onClick = onNext, modifier = Modifier.size(44.dp)) {
            Icon(
                Icons.Rounded.SkipNext,
                contentDescription = "下一首",
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

internal fun playbackStateLabel(state: EchoPlaybackState): String =
    when (state) {
        EchoPlaybackState.Idle -> "空闲"
        EchoPlaybackState.Loading -> "加载中"
        EchoPlaybackState.Playing -> "播放中"
        EchoPlaybackState.Paused -> "已暂停"
        EchoPlaybackState.Seeking -> "定位中"
        EchoPlaybackState.Buffering -> "缓冲中"
        EchoPlaybackState.Ended -> "已结束"
        EchoPlaybackState.Stopped -> "已停止"
        EchoPlaybackState.Error -> "错误"
    }

internal fun repeatModeLabel(mode: EchoRepeatMode): String =
    when (mode) {
        EchoRepeatMode.Off -> "循环关闭"
        EchoRepeatMode.All -> "列表循环"
        EchoRepeatMode.One -> "单曲循环"
    }

