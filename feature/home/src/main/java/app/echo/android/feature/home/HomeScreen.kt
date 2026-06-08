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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Menu
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.ArtworkTile
import app.echo.android.design.EchoAccent
import app.echo.android.design.EchoAccentDeep
import app.echo.android.design.EchoAccentText
import app.echo.android.design.EchoColors
import app.echo.android.design.EchoGlassBorder
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
import app.echo.android.design.RoonInk
import app.echo.android.design.RoonMuted
import app.echo.android.design.formatDuration
import app.echo.android.design.progressFraction
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.playback.EchoPlaybackState
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoRepeatMode

@Composable
fun HomeScreen(
    status: EchoPlaybackStatus,
    trackCount: Int,
    albumCount: Int,
    artistCount: Int,
    recentAlbums: List<AlbumSummary>,
    recommendedAlbums: List<AlbumSummary>,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleShuffle: () -> Unit,
    onRefreshRecommendations: () -> Unit,
    onOpenAlbum: (AlbumSummary) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenConnect: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val compactViewport = configuration.screenHeightDp < 620 ||
        configuration.screenWidthDp > configuration.screenHeightDp
    val scrollState = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(scrollState),
        ) {
            RoonHomeHeader(
                status = status,
                compact = compactViewport,
                onOpenLibrary = onOpenLibrary,
            )
            Box(Modifier.padding(horizontal = 24.dp)) {
                LibraryOverview(
                    trackCount = trackCount,
                    albumCount = albumCount,
                    artistCount = artistCount,
                )
            }
            Spacer(Modifier.height(if (compactViewport) 14.dp else 22.dp))
            RoonRecentActivitySection(
                albums = recentAlbums,
                onOpenAlbum = onOpenAlbum,
                onOpenLibrary = onOpenLibrary,
            )
            Spacer(Modifier.height(if (compactViewport) 14.dp else 20.dp))
            HomeAlbumRecommendationsSection(
                albums = recommendedAlbums,
                onRefresh = onRefreshRecommendations,
                onOpenLibrary = onOpenLibrary,
                onOpenAlbum = onOpenAlbum,
            )
            Spacer(Modifier.height(if (compactViewport) 180.dp else 212.dp))
        }
    }
}

