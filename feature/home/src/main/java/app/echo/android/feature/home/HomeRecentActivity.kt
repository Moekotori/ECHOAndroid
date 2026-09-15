package app.echo.android.feature.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.ArtworkTile
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.echoAccentColor
import app.echo.android.design.echoClickable
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.EchoTrack
import app.echo.android.feature.home.R as L10nR

@Composable
internal fun RoonRecentActivitySection(
    recentPlayedAlbums: List<AlbumSummary>,
    recentlyAddedAlbums: List<AlbumSummary>,
    recentPlayedTracks: List<EchoTrack> = emptyList(),
    onOpenAlbum: (AlbumSummary) -> Unit,
    onOpenLibrary: () -> Unit,
    onPlayTrack: (EchoTrack) -> Unit = {},
) {
    var selectedMode by rememberSaveable { mutableStateOf(RecentActivityMode.Played) }
    val albums = when (selectedMode) {
        RecentActivityMode.Played -> recentPlayedAlbums
        RecentActivityMode.Added -> recentlyAddedAlbums
        RecentActivityMode.Tracks -> emptyList()
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
            .padding(top = 4.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(L10nR.string.feature_home_recent_activity_581ef8),
                color = homeTitleColor(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            RecentActivityTabs(
                selectedMode = selectedMode,
                onSelect = { selectedMode = it },
            )
        }
        if (selectedMode == RecentActivityMode.Tracks) {
            if (recentPlayedTracks.isEmpty()) {
                HomeLibraryNotice(
                    title = stringResource(L10nR.string.feature_home_nothing_played_yet_988bfc),
                    subtitle = stringResource(L10nR.string.feature_home_appears_after_you_play_an_album_26effb),
                    onClick = onOpenLibrary,
                )
            } else {
                LazyRow(
                    contentPadding = PaddingValues(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(recentPlayedTracks, key = { it.id }) { track ->
                        RecentTrackCard(track = track, onClick = { onPlayTrack(track) })
                    }
                }
            }
        } else if (displayAlbums.isEmpty()) {
            HomeLibraryNotice(
                title = stringResource(
                    if (selectedMode == RecentActivityMode.Played) L10nR.string.feature_home_nothing_played_yet_988bfc
                    else L10nR.string.feature_home_no_new_albums_yet_ac7085,
                ),
                subtitle = stringResource(
                    if (selectedMode == RecentActivityMode.Played) L10nR.string.feature_home_appears_after_you_play_an_album_26effb
                    else L10nR.string.feature_home_appears_after_you_scan_your_library_5ae1b0,
                ),
                onClick = onOpenLibrary,
            )
        } else {
            Crossfade(targetState = displayMode,
                animationSpec = tween(if (LocalEchoEffectivePerformanceMode.current.isLightweight) 0 else 180),
                label = "recent-activity",
            ) { mode ->
                val visibleAlbums = if (mode == RecentActivityMode.Added) recentlyAddedAlbums else recentPlayedAlbums
                if (visibleAlbums.size == 1) {
                    SingleRecentAlbum(visibleAlbums.first(), onOpenAlbum)
                } else {
                    LazyRow(
                        modifier = Modifier.heightIn(min = RecentActivityAlbumCardHeight),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(visibleAlbums, key = { it.albumKey }) { album ->
                            RecentAlbumCard(album = album, mode = mode, onClick = { onOpenAlbum(album) })
                        }
                    }
                }
            }
        }
    }
}

internal enum class RecentActivityMode {
    Played,
    Added,
    Tracks,
}

internal val RecentActivityAlbumCardHeight = 202.dp
internal val RecentActivityEmptyCardWidth = 126.dp
internal val RecentActivityEmptyCardHeight = 184.dp

@Composable
internal fun RecentActivityTabs(
    selectedMode: RecentActivityMode,
    onSelect: (RecentActivityMode) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        border = null,
    ) {
        Row(
            modifier = Modifier.selectableGroup(),
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
            RecentActivityModeTab(
                label = stringResource(L10nR.string.feature_home_tracks_9c2e11),
                selected = selectedMode == RecentActivityMode.Tracks,
                onClick = { onSelect(RecentActivityMode.Tracks) },
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
    val fill by animateColorAsState(
        if (selected) scheme.primary.copy(alpha = 0.13f) else Color.Transparent,
        animationSpec = tween(if (LocalEchoEffectivePerformanceMode.current.isLightweight) 0 else 180),
        label = "recent-tab",
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(fill)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) scheme.primary else homeBodyColor(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
private fun SingleRecentAlbum(album: AlbumSummary, onOpen: (AlbumSummary) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .echoClickable { onOpen(album) }.padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkTile(album.artworkUri, modifier = Modifier.size(112.dp),
            accent = echoAccentColor(), cornerRadius = 16.dp, elevation = 0.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(album.title, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(album.albumArtist ?: album.artist ?: stringResource(L10nR.string.feature_home_unknown_artist_85ee30),
                style = MaterialTheme.typography.bodyMedium, color = homeBodyColor(),
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RecentTrackCard(track: EchoTrack, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(126.dp).echoClickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ArtworkTile(
            track.artworkUri,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            accent = echoAccentColor(),
            cornerRadius = 16.dp,
            elevation = 0.dp,
        )
        Text(
            track.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            track.artist,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = homeBodyColor(),
        )
    }
}
