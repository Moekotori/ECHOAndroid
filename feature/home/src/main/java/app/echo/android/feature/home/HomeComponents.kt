package app.echo.android.feature.home

import app.echo.android.feature.home.R as L10nR
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import app.echo.android.design.echoClickable
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
import app.echo.android.design.EchoColors
import app.echo.android.design.echoAccentColor
import app.echo.android.design.echoOnAccentColor
import app.echo.android.design.EchoIconBadge
import app.echo.android.design.EchoPanel
import app.echo.android.design.EchoPlaceholderLine
import app.echo.android.design.EchoSectionTitle
import app.echo.android.design.AmbientPlanet
import app.echo.android.design.GlassIconButton
import app.echo.android.design.GlassSurface
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.echoDarkGlassBorder
import app.echo.android.design.rememberEchoHapticPerformer
import app.echo.android.design.formatDuration
import app.echo.android.design.progressFraction
import app.echo.android.design.echoTheme
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.ArtistSummary
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.LibraryScanProgress
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
        echoTheme().panel.copy(alpha = (lightAlpha * 0.58f).coerceIn(0.42f, 0.62f))
    } else {
        Color.White.copy(alpha = lightAlpha.coerceIn(0.95f, 1.00f))
    }
}

@Composable
private fun homePanelBorder(lightAlpha: Float = 0.94f): BorderStroke {
    return BorderStroke(
        1.dp,
        if (LocalEchoDarkTheme.current) echoTheme().glassBorder else echoTheme().softLine.copy(alpha = lightAlpha.coerceIn(0.74f, 0.96f)),
    )
}

@Composable
private fun homeTitleColor(): Color =
    if (LocalEchoDarkTheme.current) Color.White.copy(alpha = 0.92f) else echoTheme().heading

@Composable
internal fun homeBodyColor(): Color =
    if (LocalEchoDarkTheme.current) Color.White.copy(alpha = 0.66f) else echoTheme().muted.copy(alpha = 0.94f)

@Composable
private fun homePanelBrush(): Brush {
    return if (LocalEchoDarkTheme.current) {
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.035f),
                echoTheme().panel.copy(alpha = 0.56f),
                echoTheme().ink.copy(alpha = 0.62f),
            ),
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 1.00f),
                Color(0xFFF7F5F6),
                echoTheme().mist.copy(alpha = 0.76f),
            ),
        )
    }
}

@Composable
internal fun LibraryOverview(
    trackCount: Int,
    albumCount: Int,
    artistCount: Int,
    scanState: LibraryScanProgress = LibraryScanProgress(),
    onOpenLibrary: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(homePanelBrush())
            .border(homePanelBorder(), RoundedCornerShape(22.dp))
            .padding(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                LibraryMetric(stringResource(L10nR.string.feature_home_songs_107b60), trackCount.toString(), Modifier.weight(1f))
                LibraryMetric(stringResource(L10nR.string.feature_home_albums_e68c2b), albumCount.toString(), Modifier.weight(1f))
                LibraryMetric(stringResource(L10nR.string.feature_home_artists_e168aa), artistCount.toString(), Modifier.weight(1f))
            }
            if (scanState.isScanning) {
                HomeLibraryScanHint(scanState = scanState, onOpenLibrary = onOpenLibrary)
            }
        }
    }
}

@Composable
private fun HomeLibraryScanHint(
    scanState: LibraryScanProgress,
    onOpenLibrary: () -> Unit,
) {
    val progress = scanState.totalCount?.let { total -> "${scanState.scannedCount}/$total" }
        ?: scanState.scannedCount.toString()
    val detail = scanState.currentTitle?.takeIf { it.isNotBlank() } ?: progress
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .echoClickable(onClick = onOpenLibrary)
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.LibraryMusic,
            contentDescription = null,
            tint = echoAccentColor(),
            modifier = Modifier.size(18.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = stringResource(L10nR.string.feature_home_scanning_library_d0b14c),
                color = homeTitleColor(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (detail == progress) progress else "$progress · $detail",
                color = homeBodyColor(),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
                    .echoClickable { onOpenSearch() },
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
                        stringResource(L10nR.string.feature_home_search_local_music_albums_and_artists_443a4f),
                        color = homeBodyColor(),
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
            .echoClickable { onClick(result) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (result.artworkUri.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(if (result.type == SearchResultType.Artist) CircleShape else RoundedCornerShape(10.dp))
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
                    .clip(if (result.type == SearchResultType.Artist) CircleShape else RoundedCornerShape(10.dp)),
                accent = echoAccentColor(),
                showSignal = false,
                cornerRadius = if (result.type == SearchResultType.Artist) 18.dp else 10.dp,
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
                shape = RoundedCornerShape(28.dp),
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
                stringResource(L10nR.string.feature_home_recent_activity_581ef8),
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
                            stringResource(L10nR.string.feature_home_nothing_played_yet_988bfc)
                        } else {
                            stringResource(L10nR.string.feature_home_no_new_albums_yet_ac7085)
                        },
                        subtitle = if (selectedMode == RecentActivityMode.Played) {
                            stringResource(L10nR.string.feature_home_appears_after_you_play_an_album_26effb)
                        } else {
                            stringResource(L10nR.string.feature_home_appears_after_you_scan_your_library_5ae1b0)
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
                label = stringResource(L10nR.string.feature_home_played_ef0258),
                selected = selectedMode == RecentActivityMode.Played,
                onClick = { onSelect(RecentActivityMode.Played) },
            )
            RecentActivityModeTab(
                label = stringResource(L10nR.string.feature_home_added_930006),
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
            .echoClickable(onClick = onClick)
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
                stringResource(L10nR.string.feature_home_played_ef0258),
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
    val artistLabel = album.albumArtist ?: album.artist ?: stringResource(L10nR.string.feature_home_unknown_artist_85ee30)
    Column(
        modifier = Modifier
            .width(RecentActivityAlbumCardWidth)
            .height(RecentActivityAlbumCardHeight)
            .echoClickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ArtworkTile(
            artworkUri = album.artworkUri,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            accent = echoAccentColor(),
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
    return stringResource(L10nR.string.feature_home_month_day_000137, (month).toString(), (day).toString())
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
            .echoClickable(onClick = onClick),
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
                tint = echoAccentColor(),
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
    title: String = stringResource(L10nR.string.feature_home_no_albums_yet_93b5cb),
    subtitle: String = stringResource(L10nR.string.feature_home_appears_after_you_scan_your_library_5ae1b0),
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .echoClickable(onClick = onClick),
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
                tint = echoAccentColor(),
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
                shape = RoundedCornerShape(32.dp),
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
                    stringResource(L10nR.string.feature_home_recent_activity_581ef8),
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
                title = status.track?.title ?: stringResource(L10nR.string.feature_home_local_music_a88e59),
                subtitle = status.track?.artist ?: stringResource(L10nR.string.feature_home_pick_from_your_library_24e9be),
                artworkUri = status.track?.artworkUri,
                accent = echoAccentColor(),
                onClick = if (status.track != null) onPlayPause else onOpenLibrary,
            )
            RoonRecentActivityCard(
                title = stringResource(L10nR.string.feature_home_daily_mix_cb64bd),
                subtitle = stringResource(L10nR.string.feature_home_from_your_local_library_266631),
                artworkUri = null,
                accent = EchoColors.Brass,
                onClick = onOpenLibrary,
            )
            RoonRecentActivityCard(
                title = "PC ECHO",
                subtitle = stringResource(L10nR.string.feature_home_desktop_handoff_playback_399554),
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
                stringResource(L10nR.string.feature_home_recommended_for_you_8335d9),
                color = homeTitleColor(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
            )
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .echoClickable(enabled = albums.isNotEmpty(), onClick = onRefresh)
                    .alpha(if (albums.isEmpty()) 0.42f else 1f),
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
                        stringResource(L10nR.string.feature_home_refresh_828c69),
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
                    EmptyRecentAlbumsCard(
                        title = stringResource(L10nR.string.feature_home_no_recommendations_yet_040329),
                        subtitle = stringResource(L10nR.string.feature_home_play_or_favorite_albums_to_fill_this_row_cf6bca),
                        onClick = onOpenLibrary,
                    )
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
    val artistLabel = album.albumArtist ?: album.artist ?: stringResource(L10nR.string.feature_home_unknown_artist_85ee30)
    Column(
        modifier = Modifier
            .width(136.dp)
            .echoClickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        ArtworkTile(
            artworkUri = album.artworkUri,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            accent = echoTheme().accentDeep,
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
            stringResource(L10nR.string.feature_home_artist_ranking_80100b),
            color = scheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
        )
        if (artists.isEmpty()) {
            EmptyRankingNotice(
                title = stringResource(L10nR.string.feature_home_no_ranking_data_yet_810ddb),
                subtitle = stringResource(L10nR.string.feature_home_appears_after_you_play_an_artist_8a9384),
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
            .echoClickable(onClick = onClick)
            .background(
                if (rank == 1) {
                    Brush.horizontalGradient(
                        listOf(
                            scheme.primary.copy(alpha = if (dark) 0.12f else 0.12f),
                            if (dark) Color.White.copy(alpha = 0.025f) else echoTheme().accentDeep.copy(alpha = 0.10f),
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
                stringResource(L10nR.string.feature_home_artist_trackcount_tracks_durationlabel_3431c6, (artist.trackCount).toString(), (durationLabel).toString()),
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
                                listOf(scheme.primary.copy(alpha = 0.86f), scheme.primary.copy(alpha = 0.42f)),
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
                stringResource(L10nR.string.feature_home_artist_albumcount_coerceatleast_0_albums_3a30a7, (artist.albumCount.coerceAtLeast(0)).toString()),
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
            stringResource(L10nR.string.feature_home_albums_you_like_95a2b9),
            color = scheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 18.dp),
        )
        if (albums.isEmpty()) {
            HomeLibraryNotice(
                title = stringResource(L10nR.string.feature_home_no_favorite_albums_yet_7c8d3b),
                subtitle = stringResource(L10nR.string.feature_home_star_an_album_on_the_player_to_see_edd836),
                onClick = onOpenLibrary,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(albums.take(4), key = { it.albumKey }) { album ->
                    RecommendedAlbumCard(album = album, onClick = { onOpenAlbum(album) })
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
            .background(if (dark) echoTheme().panel.copy(alpha = 0.38f) else echoTheme().mist.copy(alpha = 0.52f))
            .border(
                BorderStroke(
                    1.dp,
                    if (dark) echoTheme().glassBorder else echoTheme().glassBorder,
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
                stringResource(L10nR.string.feature_home_listening_heatmap_last_12_weeks_943587),
                color = scheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(L10nR.string.feature_home_heatmap_activeweeks_weeks_active_79d6b5, (heatmap.activeWeeks).toString()),
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
                stringResource(L10nR.string.feature_home_less_37b004),
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
                stringResource(L10nR.string.feature_home_more_8d2e9a),
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
private fun heatmapLevelColor(level: Int): Color {
    val accent = MaterialTheme.colorScheme.primary
    val dark = LocalEchoDarkTheme.current
    return when (level) {
        1 -> accent.copy(alpha = if (dark) 0.18f else 0.20f)
        2 -> accent.copy(alpha = if (dark) 0.28f else 0.36f)
        3 -> accent.copy(alpha = if (dark) 0.42f else 0.56f)
        4 -> accent.copy(alpha = if (dark) 0.58f else 0.82f)
        else -> if (dark) Color.White.copy(alpha = 0.07f) else echoTheme().mist
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
            .background(if (dark) echoTheme().panel.copy(alpha = 0.28f) else echoTheme().mist.copy(alpha = 0.42f))
            .border(if (dark) echoDarkGlassBorder() else BorderStroke(1.dp, Color.Transparent), RoundedCornerShape(18.dp))
            .echoClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, color = if (dark) scheme.onSurface else echoTheme().heading, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(subtitle, color = if (dark) scheme.onSurfaceVariant else echoTheme().muted, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun artistReadableDuration(durationMs: Long): String {
    val minutes = (durationMs / 60000L).toInt()
    return if (minutes >= 1) {
        stringResource(L10nR.string.feature_home_minutes_min_c3e281, (minutes).toString())
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
            RecentActivityTab(label = stringResource(L10nR.string.feature_home_played_ef0258), selected = true)
            RecentActivityTab(label = stringResource(L10nR.string.feature_home_added_930006), selected = false)
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
            .echoClickable(onClick = onClick),
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
                    tint = echoAccentColor(),
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
                stringResource(L10nR.string.feature_home_recommended_for_you_8335d9),
                color = homeTitleColor(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            val dark = LocalEchoDarkTheme.current
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .echoClickable(onClick = onRefresh),
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
                        stringResource(L10nR.string.feature_home_refresh_828c69),
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
                    .echoClickable(onClick = onOpenLibrary)
                    .padding(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    EchoIconBadge(Icons.Rounded.LibraryMusic)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            stringResource(L10nR.string.feature_home_scan_to_generate_recommendations_16383a),
                            color = homeTitleColor(),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(L10nR.string.feature_home_pick_a_few_tracks_from_your_local_library_21141c),
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
            .echoClickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        ArtworkTile(
            artworkUri = track.artworkUri,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            accent = echoTheme().accentDeep,
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
            stringResource(L10nR.string.feature_home_listen_later_d52083),
            color = homeTitleColor(),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(L10nR.string.feature_home_leave_a_trail_through_your_local_library_298736),
            color = homeTitleColor(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(L10nR.string.feature_home_park_the_albums_artists_and_tracks_you_want_b2f171),
            color = homeBodyColor(),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Surface(
            modifier = Modifier
                .padding(top = 8.dp)
                .widthIn(min = 230.dp)
                .echoClickable(onClick = onOpenConnect),
            shape = RoundedCornerShape(28.dp),
            color = echoAccentColor(),
            contentColor = echoOnAccentColor(),
        ) {
            Text(
                stringResource(L10nR.string.feature_home_connect_pc_echo_bcfead),
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
            description = stringResource(L10nR.string.feature_home_open_library_eba8cb),
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
                Icon(Icons.Rounded.Search, contentDescription = null, tint = homeBodyColor(), modifier = Modifier.size(20.dp))
                Text(
                    stringResource(L10nR.string.feature_home_search_local_music_b9c195),
                    color = homeBodyColor(),
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
                stringResource(L10nR.string.feature_home_not_every_journey_has_an_ending_53606e)
            } else {
                stringResource(L10nR.string.feature_home_wake_up_your_local_music_a2c609)
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
            .echoClickable(onClick = onClick)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF2C2B31),
                        Color(0xFF242328),
                        Color(0xFF1C1B20),
                        echoTheme().accentDeep.copy(alpha = 0.55f),
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
                    stringResource(L10nR.string.feature_home_continue_playing_6faf22)
                } else {
                    stringResource(L10nR.string.feature_home_daily_mix_cb64bd)
                },
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                status.track?.title ?: stringResource(L10nR.string.feature_home_discover_great_music_c63445),
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
                stringResource(L10nR.string.feature_home_shuffle_957ce0)
            } else {
                stringResource(L10nR.string.feature_home_in_order_951ca0)
            },
            selected = shuffleEnabled,
            onClick = onToggleShuffle,
            modifier = Modifier.weight(1f),
        )
        HomeModeChip(
            icon = Icons.Rounded.LibraryMusic,
            label = stringResource(L10nR.string.feature_home_library_848e9b),
            selected = false,
            onClick = onOpenLibrary,
            modifier = Modifier.weight(1f),
        )
        HomeModeChip(
            icon = Icons.Rounded.Devices,
            label = stringResource(L10nR.string.feature_home_handoff_2f82d3),
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
            .echoClickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = if (selected) 0.24f else 0.14f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = if (selected) 0.42f else 0.24f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = label, tint = if (selected) echoAccentColor() else Color.White.copy(alpha = 0.82f), modifier = Modifier.size(21.dp))
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
                    stringResource(L10nR.string.feature_home_playback_queue_d2e6c0)
                } else {
                    stringResource(L10nR.string.feature_home_ready_to_play_c4764f)
                },
                status.track?.album ?: stringResource(L10nR.string.feature_home_queue_is_empty_1b4178),
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
                        stringResource(L10nR.string.feature_home_back_to_library_f16b77)
                    } else {
                        stringResource(L10nR.string.feature_home_choose_a_track_abb9c5)
                    },
                    detail = if (hasTrack) {
                        stringResource(L10nR.string.feature_home_adjust_the_local_queue_f4499e)
                    } else {
                        stringResource(L10nR.string.feature_home_start_with_local_music_9875f9)
                    },
                    onClick = onOpenLibrary,
                    modifier = Modifier.weight(1f),
                )
                PlaybackActionCard(
                    icon = Icons.Rounded.Devices,
                    title = stringResource(L10nR.string.feature_home_pc_handoff_c867f1),
                    detail = if (hasTrack) {
                        stringResource(L10nR.string.feature_home_switch_to_pc_echo_5ab6a6)
                    } else {
                        stringResource(L10nR.string.feature_home_pair_to_play_remotely_14a7b9)
                    },
                    onClick = onOpenConnect,
                    modifier = Modifier.weight(1f),
                )
            }
            if (!compact) {
                PlaybackHandoffFlow(active = hasTrack)
                EchoPlaceholderLine(
                    if (hasTrack) {
                        stringResource(L10nR.string.feature_home_next_lyrics_repeat_and_queue_reorder_f8bcaf)
                    } else {
                        stringResource(L10nR.string.feature_home_lyrics_repeat_and_queue_reorder_are_reserved_6d2c34)
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
            label = stringResource(L10nR.string.feature_home_now_124d95),
            title = status.track?.title ?: stringResource(L10nR.string.feature_home_nothing_playing_b290fe),
            detail = status.track?.artist ?: stringResource(L10nR.string.feature_home_pick_a_song_from_your_library_320636),
            active = status.track != null,
        )
        if (!compact) {
            QueuePreviewItem(
                icon = Icons.Rounded.LibraryMusic,
                label = stringResource(L10nR.string.feature_home_next_d67904),
                title = stringResource(L10nR.string.feature_home_smart_queue_814a45),
                detail = if (status.track != null) {
                    stringResource(L10nR.string.feature_home_continues_from_the_local_queue_2f45cb)
                } else {
                    stringResource(L10nR.string.feature_home_upcoming_tracks_appear_after_you_pick_a_song_7e7129)
                },
                active = false,
            )
            QueuePreviewItem(
                icon = Icons.Rounded.Devices,
                label = stringResource(L10nR.string.feature_home_handoff_2f82d3),
                title = "PC ECHO",
                detail = if (status.track != null) {
                    stringResource(L10nR.string.feature_home_switch_to_desktop_output_c19745)
                } else {
                    stringResource(L10nR.string.feature_home_pair_to_take_over_remote_playback_5e3e4c)
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
            detail = stringResource(L10nR.string.feature_home_tap_to_switch_47d6a3),
            selected = repeatMode != EchoRepeatMode.Off,
            onClick = onCycleRepeatMode,
            modifier = Modifier.weight(1f),
        )
        PlaybackModeButton(
            icon = Icons.Rounded.Shuffle,
            title = if (shuffleEnabled) {
                stringResource(L10nR.string.feature_home_shuffle_on_c7c5c4)
            } else {
                stringResource(L10nR.string.feature_home_in_order_47b60a)
            },
            detail = if (shuffleEnabled) {
                stringResource(L10nR.string.feature_home_queue_is_shuffled_45cabc)
            } else {
                stringResource(L10nR.string.feature_home_follows_queue_order_0926fb)
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
        modifier = modifier.echoClickable(onClick = onClick),
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
                Text(stringResource(L10nR.string.feature_home_handoff_path_d70cad), fontWeight = FontWeight.SemiBold)
                Text(
                    if (active) {
                        stringResource(L10nR.string.feature_home_ready_to_hand_off_2e2884)
                    } else {
                        stringResource(L10nR.string.feature_home_pick_a_track_first_bcdca7)
                    },
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                HandoffStep("1", stringResource(L10nR.string.feature_home_play_on_device_4765ae), selected = true, modifier = Modifier.weight(1f))
                HandoffStep("2", stringResource(L10nR.string.feature_home_connect_pc_724ba0), selected = active, modifier = Modifier.weight(1f))
                HandoffStep("3", stringResource(L10nR.string.feature_home_pc_output_e48933), selected = active, modifier = Modifier.weight(1f))
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
            .echoClickable(onClick = onClick),
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
    val dark = LocalEchoDarkTheme.current
    val scheme = MaterialTheme.colorScheme
    val heroBrush = Brush.linearGradient(
        if (dark) {
            listOf(
                Color.White.copy(alpha = 0.04f),
                echoTheme().panel.copy(alpha = 0.56f),
                echoTheme().ink.copy(alpha = 0.62f),
                scheme.primary.copy(alpha = 0.10f),
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.72f),
                echoTheme().mist.copy(alpha = 0.58f),
                scheme.primary.copy(alpha = 0.10f),
            )
        },
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
            .border(BorderStroke(1.dp, echoTheme().glassBorder.copy(alpha = 0.84f)), RoundedCornerShape(26.dp))
            .padding(18.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(L10nR.string.feature_home_this_device_8463b7),
                    style = MaterialTheme.typography.labelSmall,
                    color = echoAccentColor(),
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
                accent = echoAccentColor(),
                showSignal = true,
                cornerRadius = 24.dp,
                elevation = 18.dp,
            )
            Text(
                status.track?.title ?: stringResource(L10nR.string.feature_home_nothing_playing_b290fe),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = homeTitleColor(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                status.track?.artist ?: stringResource(L10nR.string.feature_home_pick_a_song_from_your_library_320636),
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
            .border(BorderStroke(1.dp, echoTheme().glassBorder.copy(alpha = 0.82f)), RoundedCornerShape(22.dp))
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
                accent = echoAccentColor(),
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
                            stringResource(L10nR.string.feature_home_this_device_8463b7),
                            style = MaterialTheme.typography.labelSmall,
                            color = echoAccentColor(),
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
                    status.track?.title ?: stringResource(L10nR.string.feature_home_nothing_playing_b290fe),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = homeTitleColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    status.track?.artist ?: stringResource(L10nR.string.feature_home_pick_a_song_from_your_library_320636),
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
            CompactFact(stringResource(L10nR.string.feature_home_output_bb8fcf), status.diagnostics.outputRoute, Modifier.weight(1.25f))
            CompactFact(
                stringResource(L10nR.string.feature_home_processing_4f0a68),
                if (status.diagnostics.offloadActive) {
                    stringResource(L10nR.string.feature_home_hardware_offload_205b4d)
                } else {
                    stringResource(L10nR.string.feature_home_clear_e6e947)
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
                contentDescription = stringResource(L10nR.string.feature_home_previous_af0264),
                tint = echoAccentColor(),
                modifier = Modifier.size(28.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(56.dp)
                .shadow(elevation = 10.dp, shape = CircleShape, clip = false)
                .clip(CircleShape)
                .background(echoAccentColor())
                .echoClickable(
                    onClick = {
                        haptics.confirm()
                        onPlayPause()
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = stringResource(L10nR.string.feature_home_play_or_pause_37a70f),
                tint = echoOnAccentColor(),
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
                contentDescription = stringResource(L10nR.string.feature_home_next_d67904),
                tint = echoAccentColor(),
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
internal fun PlaybackProgress(positionMs: Long, durationMs: Long, light: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    val foreground = if (light) Color.White else echoAccentColor()
    val secondary = when {
        light -> Color.White.copy(alpha = 0.70f)
        dark -> Color.White.copy(alpha = 0.62f)
        else -> scheme.onSurfaceVariant
    }
    val trackColor = when {
        light -> Color.White.copy(alpha = 0.18f)
        dark -> Color.White.copy(alpha = 0.12f)
        else -> scheme.outlineVariant.copy(alpha = 0.90f)
    }
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
        EchoPlaybackState.Idle -> stringResource(L10nR.string.feature_home_idle_3e0cc6)
        EchoPlaybackState.Loading -> stringResource(L10nR.string.feature_home_loading_a4ce8b)
        EchoPlaybackState.Playing -> stringResource(L10nR.string.feature_home_playing_d86b54)
        EchoPlaybackState.Paused -> stringResource(L10nR.string.feature_home_paused_3d8ed2)
        EchoPlaybackState.Seeking -> stringResource(L10nR.string.feature_home_seeking_ea77e5)
        EchoPlaybackState.Buffering -> stringResource(L10nR.string.feature_home_buffering_24542e)
        EchoPlaybackState.Ended -> stringResource(L10nR.string.feature_home_ended_6503ae)
        EchoPlaybackState.Stopped -> stringResource(L10nR.string.feature_home_stopped_6b0d06)
        EchoPlaybackState.Error -> stringResource(L10nR.string.feature_home_error_ad4bd6)
    }

@Composable
internal fun repeatModeLabel(mode: EchoRepeatMode): String =
    when (mode) {
        EchoRepeatMode.Off -> stringResource(L10nR.string.feature_home_repeat_off_e1d801)
        EchoRepeatMode.All -> stringResource(L10nR.string.feature_home_repeat_all_b8cce1)
        EchoRepeatMode.One -> stringResource(L10nR.string.feature_home_repeat_one_3df94f)
    }


@Composable
internal fun HomeLibraryNotice(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .echoClickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.LibraryMusic, contentDescription = null,
            tint = echoAccentColor(), modifier = Modifier.size(28.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = homeTitleColor(), style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold)
            Text(subtitle, color = homeBodyColor(), style = MaterialTheme.typography.bodySmall)
        }
    }
}
