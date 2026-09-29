package app.echo.android.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import app.echo.android.design.ArtworkPalette
import app.echo.android.model.playback.EchoPlaybackStatus

internal data class PlayerAppearance(
    val style: String = DefaultPlayerStyle,
    val textScale: Float = 1f,
    val artworkScale: Float = 1f,
) {
    val isRecordSleeve: Boolean get() = style == "record_sleeve"
    val usesFlatSurface: Boolean get() = style != "classic"
    val background get() = when (style) {
        "pixel_handheld" -> ExpressivePlayerStyle.PixelPaper
        "type_poster" -> ExpressivePlayerStyle.PosterPaper
        else -> RecordSleeveStyle.Paper
    }
    val headerInk get() = when (style) {
        "pixel_handheld" -> ExpressivePlayerStyle.PixelInk
        "type_poster" -> ExpressivePlayerStyle.PosterInk
        else -> RecordSleeveStyle.Wine
    }
}

@Composable
internal fun PlayerCoverPage(
    appearance: PlayerAppearance,
    palette: ArtworkPalette,
    presentationExpanded: Boolean,
    status: EchoPlaybackStatus,
    positionMsState: State<Long>,
    durationMsState: State<Long>,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onOpenQueue: () -> Unit,
    onCast: (() -> Unit)?,
    castActive: Boolean,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    isCurrentTrackFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenArtist: ((trackId: String, artistName: String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val actions = ExpressivePlayerActions(onPlayPause, onNext, onPrevious, onSeek, onOpenQueue,
        onCast, onToggleShuffle, onCycleRepeatMode, onToggleFavorite, onOpenLyrics, onOpenArtist)
    // Only cover-page type scales. Lyrics, transport hit targets and the drawer do not.
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * appearance.textScale)) {
        if (appearance.style == "pixel_handheld") {
            PixelHandheldCoverPage(status, positionMsState, durationMsState, actions,
                isCurrentTrackFavorite, castActive, appearance.artworkScale, modifier)
        } else if (appearance.style == "type_poster") {
            TypePosterCoverPage(status, positionMsState, durationMsState, actions,
                castActive, appearance.artworkScale, modifier)
        } else if (appearance.isRecordSleeve) {
            RecordSleeveCoverPage(
                status, positionMsState, durationMsState, onPlayPause, onNext, onPrevious,
                onSeek, onOpenQueue, onCast, castActive, onToggleShuffle, onCycleRepeatMode,
                isCurrentTrackFavorite, onToggleFavorite, onOpenLyrics,
                onOpenArtist = onOpenArtist,
                modifier = modifier, artworkScale = appearance.artworkScale,
            )
        } else {
            ClassicCoverPage(
                status, palette, presentationExpanded, positionMsState, durationMsState,
                onPlayPause, onNext, onPrevious, onSeek, onOpenQueue, onCast, castActive,
                isCurrentTrackFavorite, onToggleFavorite, onOpenLyrics,
                onOpenArtist = onOpenArtist,
                modifier = modifier, artworkScale = appearance.artworkScale,
            )
        }
    }
}

/** Artist name opens that artist's library page. Blank names and missing tracks stay plain text. */
@Composable
internal fun Modifier.openArtistWhen(
    trackId: String?,
    artist: String?,
    onOpenArtist: ((trackId: String, artistName: String) -> Unit)?,
): Modifier {
    val label = stringResource(R.string.feature_player_open_artist)
    val id = trackId?.takeIf { it.isNotBlank() }
    val name = artist?.trim()?.takeIf { it.isNotEmpty() }
    if (onOpenArtist == null || id == null || name == null) return this
    return clickable(role = Role.Button, onClickLabel = label) { onOpenArtist(id, name) }
}
