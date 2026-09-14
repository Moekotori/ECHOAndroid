package app.echo.android.feature.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.echo.android.design.EchoMotion
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.model.playback.EchoTrackRef

/** Only track identity starts a transition; progress and metadata updates do not. */
@Composable
internal fun NowPlayingTrackTransition(
    track: EchoTrackRef?,
    previousRequestedFrom: String?,
    modifier: Modifier = Modifier,
    artwork: Boolean = false,
    content: @Composable (EchoTrackRef?) -> Unit,
) {
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    AnimatedContent(
        targetState = track,
        contentKey = { it?.id },
        modifier = modifier,
        contentAlignment = Alignment.Center,
        transitionSpec = {
            if (lightweight || initialState == null || targetState == null) {
                EchoMotion.stateChange()
            } else {
                val direction = if (initialState?.id == previousRequestedFrom) -1 else 1
                // Keep fades, travel and scale on interruptible springs. Rapid skips can
                // redirect the in-flight layers without restarting a timed fade.
                val enterMs = if (artwork) 560 else 480
                val exitMs = if (artwork) 460 else 380
                val distance = if (artwork) 5 else 18
                ContentTransform(
                    targetContentEnter = fadeIn(EchoMotion.silkFloat(enterMs)) +
                        slideInHorizontally(EchoMotion.silkOffset(enterMs)) { direction * it / distance } +
                        scaleIn(initialScale = if (artwork) 0.94f else 1f,
                            animationSpec = EchoMotion.silkFloat(enterMs)),
                    initialContentExit = fadeOut(EchoMotion.silkFloat(exitMs)) +
                        slideOutHorizontally(EchoMotion.silkOffset(exitMs)) { -direction * it / (distance * 2) } +
                        scaleOut(targetScale = if (artwork) 0.96f else 1f,
                            animationSpec = EchoMotion.silkFloat(exitMs)),
                    // Keep the incoming cover above the outgoing one, including reversals.
                    targetContentZIndex = 1f,
                    // Album/lyric rows can change height. Ease that change rather than
                    // abruptly pushing the scrubber and transport controls up or down.
                    sizeTransform = if (artwork) null else SizeTransform(clip = false) { _, _ ->
                        EchoMotion.silkSize(480)
                    },
                )
            }
        },
        label = if (artwork) "track-artwork" else "track-info",
    ) { displayedTrack ->
        content(displayedTrack)
    }
}
