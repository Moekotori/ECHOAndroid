package app.echo.android.feature.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Lyrics retain the full viewport height; short windows scroll only the side controls. */
@Composable
internal fun LyricsPageLayout(
    heading: @Composable () -> Unit,
    lyrics: @Composable () -> Unit,
    controls: @Composable (landscape: Boolean) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        if (maxWidth >= 480.dp && maxWidth > maxHeight && maxHeight < 600.dp) {
            val controlsWidth = minOf(maxWidth * 0.45f, 320.dp).coerceAtLeast(264.dp)
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Box(Modifier.weight(1f).fillMaxHeight()) { lyrics() }
                Column(
                    Modifier.width(controlsWidth).fillMaxHeight()
                        .verticalScroll(rememberScrollState()).heightIn(min = viewportHeight),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    heading()
                    controls(true)
                }
            }
        } else {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                heading()
                Box(Modifier.fillMaxWidth().weight(1f)) { lyrics() }
                controls(false)
            }
        }
    }
}
