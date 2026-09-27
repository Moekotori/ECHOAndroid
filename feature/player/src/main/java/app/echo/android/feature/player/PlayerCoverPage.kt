package app.echo.android.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import app.echo.android.design.ArtworkPalette
import app.echo.android.model.playback.EchoPlaybackStatus

internal data class PlayerAppearance(
    val style: String = "record_sleeve",
    val textScale: Float = 1f,
    val artworkScale: Float = 1f,
) {
    val isRecordSleeve: Boolean get() = style != "classic"
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
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // Only cover-page type scales. Lyrics, transport hit targets and the drawer do not.
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * appearance.textScale)) {
        if (appearance.isRecordSleeve) {
            RecordSleeveCoverPage(
                status, positionMsState, durationMsState, onPlayPause, onNext, onPrevious,
                onSeek, onOpenQueue, onCast, castActive, onToggleShuffle, onCycleRepeatMode,
                isCurrentTrackFavorite, onToggleFavorite, onOpenLyrics,
                modifier = modifier, artworkScale = appearance.artworkScale,
            )
        } else {
            ClassicCoverPage(
                status, palette, presentationExpanded, positionMsState, durationMsState,
                onPlayPause, onNext, onPrevious, onSeek, onOpenQueue, onCast, castActive,
                isCurrentTrackFavorite, onToggleFavorite, onOpenLyrics,
                modifier = modifier, artworkScale = appearance.artworkScale,
            )
        }
    }
}
