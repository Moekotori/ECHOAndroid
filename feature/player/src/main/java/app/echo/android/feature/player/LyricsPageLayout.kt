package app.echo.android.feature.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity

/** Lyrics retain the full viewport height; short windows scroll only the side controls. */
@Composable
internal fun LyricsPageLayout(
    heading: @Composable (landscape: Boolean) -> Unit,
    lyrics: @Composable () -> Unit,
    controls: @Composable (landscape: Boolean) -> Unit,
    supportingPane: Boolean = false,
) {
    val controlsScroll = rememberScrollState()
    val headingScroll = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        val controlsWidth = playerSidePaneWidth(maxWidth.value, maxHeight.value, fontScale,
            264f, 216f * fontScale, 20f, 0.45f)
        if (!supportingPane && controlsWidth != null) {
            // Controls retain touch areas and font scaling while lyrics keep a readable pane.
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Box(Modifier.weight(1f).fillMaxHeight()) { lyrics() }
                Column(
                    Modifier.width(minOf(controlsWidth, 320f * fontScale).dp).fillMaxHeight()
                        .verticalScroll(controlsScroll).heightIn(min = viewportHeight),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    heading(true)
                    controls(true)
                }
            }
        } else {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                val compact = supportingPane || viewportHeight < 600.dp
                if (compact) {
                    // Large text must not consume the lyrics viewport when split panes no longer fit.
                    Column(Modifier.fillMaxWidth().heightIn(max = viewportHeight * 0.25f)
                        .verticalScroll(headingScroll)) { heading(false) }
                } else heading(false)
                Box(Modifier.fillMaxWidth().weight(1f)) { lyrics() }
                if (compact) {
                    // Enlarged text can prevent a side-by-side layout even in landscape.
                    // Bound and scroll the controls so the lyrics retain a visible viewport.
                    Column(Modifier.fillMaxWidth().heightIn(max = viewportHeight * 0.35f)
                        .verticalScroll(controlsScroll)) { controls(false) }
                } else controls(false)
            }
        }
    }
}
