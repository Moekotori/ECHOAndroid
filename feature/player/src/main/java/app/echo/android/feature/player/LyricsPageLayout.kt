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
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        val controlsMinimum = 264.dp * fontScale
        val shortLandscape = maxWidth > maxHeight && maxHeight < 600.dp
        if (!supportingPane && maxWidth >= controlsMinimum + 216.dp + 20.dp &&
            (shortLandscape || maxWidth >= 720.dp * fontScale)) {
            // Controls retain touch areas and font scaling while lyrics keep a readable pane.
            val controlsWidth = minOf(maxWidth * 0.45f, 320.dp * fontScale)
                .coerceIn(controlsMinimum, maxWidth - 216.dp - 20.dp)
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Box(Modifier.weight(1f).fillMaxHeight()) { lyrics() }
                Column(
                    Modifier.width(controlsWidth).fillMaxHeight()
                        .verticalScroll(rememberScrollState()).heightIn(min = viewportHeight),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    heading(true)
                    controls(true)
                }
            }
        } else {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                heading(false)
                Box(Modifier.fillMaxWidth().weight(1f)) { lyrics() }
                if (supportingPane || viewportHeight < 600.dp) {
                    // Enlarged text can prevent a side-by-side layout even in landscape.
                    // Bound and scroll the controls so the lyrics retain a visible viewport.
                    Column(Modifier.fillMaxWidth().heightIn(max = viewportHeight * 0.35f)
                        .verticalScroll(rememberScrollState())) { controls(false) }
                } else controls(false)
            }
        }
    }
}
