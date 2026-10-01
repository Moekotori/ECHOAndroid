package app.echo.android.feature.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Keep each cover style's artwork and controls together when the window is short. */
@Composable
internal fun PlayerCoverLayout(
    minimumPortraitHeight: Dp,
    modifier: Modifier = Modifier,
    minimumDetailsWidth: Dp = 264.dp,
    artwork: @Composable ColumnScope.() -> Unit,
    details: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier) {
        val viewportHeight = maxHeight
        if (maxWidth >= 480.dp && maxWidth > maxHeight && maxHeight < 600.dp) {
            val detailsWidth = maxOf((maxWidth - 24.dp) * (1.3f / 2.3f), minimumDetailsWidth)
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    content = artwork,
                )
                Column(
                    Modifier.width(detailsWidth).fillMaxHeight()
                        .verticalScroll(rememberScrollState()).heightIn(min = viewportHeight),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    content = details,
                )
            }
        } else {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
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
