package app.echo.android.feature.library

import androidx.compose.runtime.*
import app.echo.android.model.library.ArtistOnlineInfoLoader
import app.echo.android.model.library.ArtistSetlistMatcher
import kotlinx.coroutines.CancellationException

internal val LocalArtistOnlineInfoLoader = staticCompositionLocalOf<ArtistOnlineInfoLoader?> { null }
internal val LocalArtistSetlistMatcher = staticCompositionLocalOf<ArtistSetlistMatcher?> { null }
internal val LocalArtistSetlistPlayer = staticCompositionLocalOf<((List<String>) -> Unit)?> { null }

@Composable
fun ArtistOnlineInfoProvider(
    loader: ArtistOnlineInfoLoader,
    matcher: ArtistSetlistMatcher? = null,
    onPlayMatched: ((List<String>) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalArtistOnlineInfoLoader provides loader,
        LocalArtistSetlistMatcher provides matcher,
        LocalArtistSetlistPlayer provides onPlayMatched,
        content = content,
    )
}

internal class ArtistOnlineState<T> {
    var value: T? by mutableStateOf(null)
    var loading by mutableStateOf(false)
    var failed by mutableStateOf(false)
    var attempt by mutableIntStateOf(0)
    var completedAttempt = -1
}

/** Requests are cancelled when their page leaves the viewport, and never run from pager precomposition. */
@Composable
internal fun <T> rememberArtistOnlineState(key: Any, active: Boolean, load: suspend (Boolean) -> T?): ArtistOnlineState<T> {
    val state = remember(key) { ArtistOnlineState<T>() }
    val currentLoad by rememberUpdatedState(load)
    LaunchedEffect(key, active, state.attempt) {
        if (!active || state.completedAttempt == state.attempt) return@LaunchedEffect
        state.loading = true
        state.failed = false
        try {
            state.value = currentLoad(state.attempt > 0)
            state.completedAttempt = state.attempt
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (failure: Exception) {
            android.util.Log.w("ArtistOnlineInfo", "Artist lookup failed", failure)
            state.failed = true
            state.completedAttempt = state.attempt
        } finally { state.loading = false }
    }
    return state
}
