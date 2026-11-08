package app.echo.android.design

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/** Set by [EchoPageContent] when its pages may hand a shared cover to each other. */
internal val LocalEchoPageSharedScope = compositionLocalOf<SharedTransitionScope?> { null }

/** The page currently being composed, so a cover knows which enter/exit it belongs to. */
internal val LocalEchoPageVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

private data class EchoCoverKey(val kind: String, val id: String)

private val CoverBounds = BoundsTransform { _, _ -> EchoMotion.silkRect(EchoMotion.PageMs + 20) }

/**
 * Lets a cover on one page fly to the same cover on the next. [kind] and [id] pair the two ends
 * (for example "album" and the album key). Place it before the size modifiers. Without a page
 * scope, in lightweight mode, or when the other end is not composed, the cover simply travels
 * with its page. Use each pair once per page: a repeated key would make the flight ambiguous.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.echoSharedCover(kind: String, id: String): Modifier {
    val scope = LocalEchoPageSharedScope.current
    val visibility = LocalEchoPageVisibilityScope.current
    if (scope == null || visibility == null || LocalEchoEffectivePerformanceMode.current.isLightweight) return this
    val owner = this
    return with(scope) {
        val state = rememberSharedContentState(EchoCoverKey(kind, id))
        owner.sharedElement(state, visibility, boundsTransform = CoverBounds, zIndexInOverlay = 1f)
    }
}
