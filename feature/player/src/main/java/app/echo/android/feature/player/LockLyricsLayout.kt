package app.echo.android.feature.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Keep the current lyric readable without letting artwork consume a short window. */
@Composable
internal fun LockLyricsLayout(
    metadata: @Composable (artworkSize: Dp) -> Unit,
    lyrics: @Composable () -> Unit,
    hint: @Composable () -> Unit,
) {
    val metadataScroll = rememberScrollState()
    val lyricsScroll = rememberScrollState()
    BoxWithConstraints(Modifier.widthIn(max = 1120.dp).fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp)) {
        val height = maxHeight
        val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        val sideWidth = playerSidePaneWidth(maxWidth.value, height.value, scale, 200f, 240f * scale, 32f, .36f)
        if (sideWidth != null) {
            Row(Modifier.widthIn(max = 1120.dp).fillMaxSize().align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Column(Modifier.width(sideWidth.dp).fillMaxHeight().verticalScroll(metadataScroll)
                    .heightIn(min = height), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    metadata(minOf(148.dp, height * .42f))
                }
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    LockLyricsViewport(lyricsScroll, Modifier.weight(1f).fillMaxWidth()) {
                        lyrics()
                    }
                    hint()
                }
            }
        } else {
            Column(Modifier.widthIn(max = 560.dp).fillMaxSize().align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.fillMaxWidth().heightIn(max = height * .42f).verticalScroll(metadataScroll),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    metadata(minOf(148.dp, height * .24f))
                }
                LockLyricsViewport(lyricsScroll, Modifier.weight(1f).fillMaxWidth()) {
                    lyrics()
                }
                hint()
            }
        }
    }
}

@Composable
private fun LockLyricsViewport(scroll: ScrollState, modifier: Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier) {
        Column(Modifier.fillMaxWidth().verticalScroll(scroll).heightIn(min = maxHeight),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            content()
        }
    }
}
