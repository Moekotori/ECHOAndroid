package app.echo.android.feature.player

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.model.lyrics.EchoLyrics

/** Synced, non-blank lines in start order: the only lines the groove can place. */
internal fun grooveLines(lyrics: EchoLyrics): List<GrooveLine> =
    lyrics.lines.filter { it.startMs >= 0L && it.text.isNotBlank() }
        .sortedBy { it.startMs }
        .map { GrooveLine(it.startMs, it.text.trim()) }

/**
 * The lyric body of the groove spiral style. Lines are cut into the groove and the record turns them under the
 * stylus; the current line's translation sits under the record. The caller supplies heading and transport.
 */
@Composable
internal fun GrooveSpiralLyrics(
    lyrics: EchoLyrics,
    position: State<Long>,
    durationMs: Long,
    artworkUri: String?,
    playing: Boolean,
    showTranslation: Boolean,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val lines = remember(lyrics) { grooveLines(lyrics) }
    val translations = remember(lyrics) {
        lyrics.lines.filter { it.startMs >= 0L && it.text.isNotBlank() }.sortedBy { it.startMs }.map { it.translation?.trim().orEmpty() }
    }
    val text = remember(lines, position, durationMs) { GrooveText.Lyrics(lines, position, durationMs) }
    val current by remember(lines, position) {
        derivedStateOf { lines.indexOfLast { it.startMs <= position.value } }
    }
    val spinning = playing && visible && !LocalEchoEffectivePerformanceMode.current.isLightweight
    BoxWithConstraints(modifier) {
        val captionHeight = 64.dp
        val side = minOf(maxWidth, (maxHeight - captionHeight).coerceAtLeast(0.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            GrooveDisc(artworkUri, text, spinning, Modifier.size(side))
            Spacer(Modifier.height(8.dp))
            val caption = if (showTranslation) translations.getOrNull(current).orEmpty() else ""
            Text(
                caption, Modifier.fillMaxWidth().height(captionHeight - 8.dp), color = GrooveSpiralStyle.Dim,
                fontFamily = FontFamily.Serif, fontSize = 15.sp, lineHeight = 22.sp, textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
