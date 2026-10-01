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
    heading: @Composable (landscape: Boolean) -> Unit,
    lyrics: @Composable () -> Unit,
    controls: @Composable (landscape: Boolean) -> Unit,
    supportingPane: Boolean = false,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        if (!supportingPane && maxWidth >= 480.dp && maxWidth > maxHeight && maxHeight < 600.dp) {
            // Controls retain their touch areas while lyrics keep at least 196dp at 480dp.
            val controlsWidth = minOf(maxWidth * 0.45f, 320.dp).coerceIn(264.dp, maxWidth - 216.dp)
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
                if (supportingPane) {
                    Column(Modifier.fillMaxWidth().heightIn(max = viewportHeight * 0.35f)
                        .verticalScroll(rememberScrollState())) { controls(false) }
                } else controls(false)
            }
        }
    }
}
