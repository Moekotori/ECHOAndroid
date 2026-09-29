package app.echo.android.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.design.EchoArtworkImage
import app.echo.android.design.EchoArtworkSize
import app.echo.android.model.playback.EchoPlaybackStatus
import java.util.Locale

@Composable
internal fun TypePosterCoverPage(status: EchoPlaybackStatus, position: State<Long>, duration: State<Long>,
                                actions: ExpressivePlayerActions, castActive: Boolean,
                                artworkScale: Float, modifier: Modifier = Modifier) {
    val ink = ExpressivePlayerStyle.PosterInk
    val track = status.track
    val fullTitle = track?.title ?: stringResource(R.string.feature_player_not_playing_d72324)
    val (title, edition) = remember(fullTitle) { recordSleeveTitleParts(fullTitle) }
    val lines = remember(title) { posterTitleLines(title) }
    val formats = remember(status.diagnostics) { playbackFormatChips(status.diagnostics, ::formatSampleRate).joinToString(" · ") }
    BoxWithConstraints(modifier.background(ExpressivePlayerStyle.PosterPaper)) {
        val height = maxOf(maxHeight, (770f * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp)
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).height(height)) {
            Column(Modifier.fillMaxWidth().weight(1f)) {
                lines.forEach { line ->
                    PosterTitleLine(line, Modifier.fillMaxWidth().weight(1f))
                }
            }
            Text(track?.artist ?: stringResource(R.string.feature_player_pick_a_song_to_start_68b6af),
                modifier = Modifier.fillMaxWidth().openArtistWhen(track?.id, track?.artist, actions.openArtist),
                color = ink, fontFamily = RecordSleeveStyle.BodyFont, fontSize = 27.sp,
                lineHeight = 32.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            edition?.let { Text(it, color = ink, fontFamily = RecordSleeveStyle.BodyFont,
                fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(3.dp).background(ink))
            ExpressiveProgress(false, track?.id, position, duration, actions.seek)
            Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                EchoArtworkImage(track?.artworkUri, track?.title,
                    Modifier.size(104.dp * artworkScale).clickable(
                        onClickLabel = stringResource(R.string.feature_player_lyrics_b90c97), onClick = actions.lyrics),
                    shape = RectangleShape, sizeClass = EchoArtworkSize.Card)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(track?.album.orEmpty(), color = ink, fontFamily = RecordSleeveStyle.BodyFont,
                        fontSize = 20.sp, lineHeight = 24.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(formats, color = ink, fontFamily = RecordSleeveStyle.BodyFont,
                        fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.7.sp, maxLines = 1,
                        modifier = Modifier.horizontalScroll(rememberScrollState()))
                }
            }
            Spacer(Modifier.height(12.dp))
            ExpressiveTransport(false, status.isPlaying, actions)
            Spacer(Modifier.height(12.dp))
            ExpressiveUtilities(false, status, castActive, actions)
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** Measure visible glyph bounds, then let Android ellipsize overflow at the end. */
@Composable
private fun PosterTitleLine(line: String, modifier: Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val typeface = remember(context) {
        androidx.core.content.res.ResourcesCompat.getFont(context, R.font.poster_anton)
    }
    Box(modifier
        .semantics { text = androidx.compose.ui.text.AnnotatedString(line) }
        .drawWithCache {
            val paint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = typeface
                color = ExpressivePlayerStyle.PosterInk.toArgb()
                textSize = 100f
            }
            val bounds = android.graphics.Rect()
            val sample = line.ifEmpty { "A" }
            paint.getTextBounds(sample, 0, sample.length, bounds)
            val fontSize = (100f * (size.height - 4.dp.toPx()).coerceAtLeast(1f) /
                bounds.height().coerceAtLeast(1)).coerceAtMost(122.sp.toPx())
            paint.textSize = fontSize
            val shown = android.text.TextUtils.ellipsize(
                line, paint, (size.width - 2.dp.toPx()).coerceAtLeast(0f),
                android.text.TextUtils.TruncateAt.END,
            ).toString()
            paint.getTextBounds(shown, 0, shown.length, bounds)
            val baseline = size.height / 2f - (bounds.top + bounds.bottom) / 2f
            onDrawBehind {
                drawContext.canvas.nativeCanvas.drawText(shown, 1.dp.toPx(), baseline, paint)
            }
        })
}

/** Preserve every word while giving a short title the mock's three-line rhythm. */
internal fun posterTitleLines(title: String): List<String> {
    val words = title.trim().uppercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> listOf("")
        words.size <= 2 -> words
        else -> listOf(words[0], words[1], words.drop(2).joinToString(" "))
    }
}
