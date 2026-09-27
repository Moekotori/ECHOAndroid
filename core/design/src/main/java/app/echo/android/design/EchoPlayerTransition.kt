package app.echo.android.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

private val LocalPlayerSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
private val LocalPlayerVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }
private val LocalPlayerExpanded = compositionLocalOf { false }
private data class PlayerArtworkKey(val trackId: String)

/** Reuse the expanded image cache while the thumbnail is being drawn at hero size on return. */
@Composable
fun echoMiniPlayerArtworkSize(): EchoArtworkSize =
    if (!LocalEchoEffectivePerformanceMode.current.isLightweight &&
        LocalPlayerSharedScope.current?.isTransitionActive == true) EchoArtworkSize.Hero
    else EchoArtworkSize.Thumbnail

/** The overlay is owned by the screen shell, never by either player or the playback engine. */
@Composable
fun EchoPlayerTransitionRoot(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    SharedTransitionLayout(modifier) {
        CompositionLocalProvider(
            LocalPlayerSharedScope provides this,
            LocalPlayerExpanded provides expanded,
        ) {
            Box(Modifier.fillMaxSize(), content = content)
        }
    }
}

/** Shared-element animations participate in this visibility transition, including its disposal. */
@Composable
fun EchoExpandedPlayer(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(if (lightweight) 90 else 320, easing = EchoMotion.Silk)),
        exit = fadeOut(tween(if (lightweight) 90 else 280, easing = EchoMotion.SilkExit)),
    ) {
        CompositionLocalProvider(LocalPlayerVisibilityScope provides this, content = content)
    }
}

/** Track identity pairs the existing images. Unmatched artwork and lyrics-only openings just fade. */
fun Modifier.echoSharedPlayerArtwork(trackId: String?, expandedArtwork: Boolean): Modifier = composed {
    val scope = LocalPlayerSharedScope.current
    val visibility = LocalPlayerVisibilityScope.current
    val expanded = LocalPlayerExpanded.current
    if (scope == null || trackId == null || LocalEchoEffectivePerformanceMode.current.isLightweight) {
        this
    } else with(scope) {
        val state = rememberSharedContentState(PlayerArtworkKey(trackId))
        val bounds = BoundsTransform { _, _ -> EchoMotion.silkRect(520) }
        if (expandedArtwork && visibility != null) {
            sharedElement(state, visibility, boundsTransform = bounds, zIndexInOverlay = 1f)
        } else if (!expandedArtwork) {
            sharedElementWithCallerManagedVisibility(
                state, visible = !expanded, boundsTransform = bounds, zIndexInOverlay = 1f,
            )
        } else this@composed
    }
}
