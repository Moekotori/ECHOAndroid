package app.echo.android.feature.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity

internal val LocalPlayerCoverLandscape = staticCompositionLocalOf { false }

/** Keep each cover style's artwork and controls together when the window is short. */
@Composable
internal fun PlayerCoverLayout(
    minimumPortraitHeight: Dp,
    modifier: Modifier = Modifier,
    minimumDetailsWidth: Dp = 264.dp,
    artwork: @Composable ColumnScope.() -> Unit,
    details: @Composable ColumnScope.() -> Unit,
) {
    // Keep the two scroll positions when resizing crosses the layout breakpoint.
    val portraitScroll = rememberScrollState()
    val detailsScroll = rememberScrollState()
    BoxWithConstraints(modifier) {
        val viewportHeight = maxHeight
        val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        val detailsWidth = playerSidePaneWidth(maxWidth.value, maxHeight.value, fontScale,
            minimumDetailsWidth.value, 144f, 24f, 1.3f / 2.3f)
        if (detailsWidth != null) {
            CompositionLocalProvider(LocalPlayerCoverLandscape provides true) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(
                    Modifier.weight(1f).fillMaxHeight().padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    content = artwork,
                )
                Column(
                    Modifier.width(detailsWidth.dp).fillMaxHeight()
                        .verticalScroll(detailsScroll).heightIn(min = viewportHeight),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    content = details,
                )
            }
            }
        } else {
            Column(
                Modifier.fillMaxWidth().verticalScroll(portraitScroll)
                    .height(maxOf(maxHeight, minimumPortraitHeight)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier.fillMaxWidth().weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    content = artwork,
                )
                details()
            }
        }
    }
}
