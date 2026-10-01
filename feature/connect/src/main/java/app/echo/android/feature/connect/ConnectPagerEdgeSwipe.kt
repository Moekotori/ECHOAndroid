package app.echo.android.feature.connect

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.echo.android.design.echoPagerEdgeSwipe

/** Only pager edge overflow can navigate to the adjacent main page. */
@Composable
internal fun Modifier.connectPagerEdgeSwipe(
    pagerState: PagerState,
    onSwipeToLibrary: () -> Unit,
    onSwipeToDiagnostics: () -> Unit,
): Modifier = echoPagerEdgeSwipe(pagerState, onSwipeToLibrary, onSwipeToDiagnostics)
