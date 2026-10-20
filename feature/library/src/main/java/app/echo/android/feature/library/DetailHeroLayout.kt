package app.echo.android.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.echo.android.design.LocalEchoLayout

/** Compact detail headers leave room for songs in short windows, including narrow split panes. */
@Composable
internal fun DetailHeroLayout(
    artworkSize: Dp = 208.dp,
    centered: Boolean = false,
    artwork: @Composable (Modifier) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val compact = LocalEchoLayout.current.compactHeader && LocalEchoLayout.current.heightDp < 600f
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (compact && maxWidth >= 400.dp * fontScale) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically) {
                artwork(Modifier.size(112.dp))
                Column(Modifier.weight(1f), content = content)
            }
        } else {
            Column(Modifier.fillMaxWidth(),
                horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
                artwork(Modifier.align(Alignment.CenterHorizontally).padding(top = if (centered && !compact) 6.dp else 4.dp)
                    .size(if (compact) 104.dp else artworkSize))
                Spacer(Modifier.height(if (compact) 12.dp else if (centered) 20.dp else 24.dp))
                content()
            }
        }
    }
}
