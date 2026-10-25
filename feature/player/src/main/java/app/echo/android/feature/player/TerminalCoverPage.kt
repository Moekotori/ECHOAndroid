package app.echo.android.feature.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.design.EchoArtworkImage
import app.echo.android.design.EchoArtworkSize
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.displayAlbum
import app.echo.android.design.displayArtist
import app.echo.android.design.displayTrack
import app.echo.android.design.formatDuration
import app.echo.android.model.playback.EchoPlaybackStatus
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/** Song details as a console session: a boxed `echo --now`, block progress, a bar display and command buttons. */
@Composable
internal fun TerminalCoverPage(
    status: EchoPlaybackStatus,
    position: State<Long>,
    duration: State<Long>,
    actions: ExpressivePlayerActions,
    isFavorite: Boolean,
    castActive: Boolean,
    artworkScale: Float,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    val track = status.track
    val green = TerminalStyle.Green
    val formats = remember(status.diagnostics) {
        playbackFormatChips(status.diagnostics, ::formatSampleRate).joinToString(" · ")
    }
    val lyricsLabel = stringResource(R.string.feature_player_lyrics_b90c97)
    PlayerCoverLayout(
        minimumPortraitHeight = (700f * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp,
        modifier = modifier,
        artwork = {
            Column(Modifier.fillMaxWidth().weight(1f)) {
                TerminalWindowBar("echo@android: ~/now-playing")
                TerminalPrompt("echo --now")
                Row(
                    Modifier.fillMaxWidth().border(1.dp, green).padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.size(116.dp * artworkScale).border(1.dp, green)) {
                        EchoArtworkImage(
                            track?.artworkUri, track?.title,
                            Modifier.fillMaxWidth().weight(1f).phosphor()
                                .clickable(onClickLabel = lyricsLabel, onClick = actions.lyrics),
                            RectangleShape, EchoArtworkSize.Card,
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        TerminalKv("title", track?.let { displayTrack(it.title) } ?: stringResource(R.string.feature_player_not_playing_d72324))
                        TerminalKv("artist", track?.let { displayArtist(it.artist) }.orEmpty(),
                            Modifier.openArtistWhen(track?.id, track?.artist, actions.openArtist))
                        TerminalKv("album", track?.let { displayAlbum(it.album) }.orEmpty())
                        if (formats.isNotEmpty()) TerminalKv("fmt", formats)
                    }
                }
            }
        },
        details = {
            TerminalPrompt("progress")
            ThemedSeekBar(
                trackKey = track?.id, position = position, duration = duration, onSeek = actions.seek,
                height = 28.dp, inset = 0.dp,
                times = { elapsed, total, fraction ->
                    Text(
                        "${formatDuration(elapsed)} / ${formatDuration(total)} · ${(fraction * 100).roundToInt()}%",
                        color = green.copy(alpha = 0.55f), fontFamily = TerminalStyle.Mono, fontSize = 12.sp,
                    )
                },
            ) { fraction, _, enabled ->
                val cells = 24
                val gap = 3.dp.toPx()
                val cell = (size.width - gap * (cells - 1)) / cells
                val filled = (fraction * cells).toInt()
                val barHeight = 16.dp.toPx()
                val top = (size.height - barHeight) / 2f
                for (i in 0 until cells) {
                    val color = if (enabled && i < filled) green else green.copy(alpha = 0.2f)
                    drawRect(color, Offset(i * (cell + gap), top), Size(cell, barHeight))
                }
            }
            TerminalPrompt("spectrum")
            TerminalSpectrum(running = animate && status.isPlaying)
            TerminalPrompt("ctl")
            TerminalButtons(status, isFavorite, castActive, actions)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
                Text("$ ", color = green.copy(alpha = 0.5f), fontFamily = TerminalStyle.Mono, fontSize = 12.5.sp)
                Text("▌", color = green, fontFamily = TerminalStyle.Mono, fontSize = 12.5.sp)
            }
        },
    )
}

@Composable
private fun TerminalKv(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth()) {
        Text(label, Modifier.width(52.dp), color = TerminalStyle.Green.copy(alpha = 0.5f),
            fontFamily = TerminalStyle.Mono, fontSize = 12.5.sp, maxLines = 1)
        Text(value, Modifier.weight(1f), color = TerminalStyle.Green, fontFamily = TerminalStyle.Mono,
            fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Decorative bars. They move only while a track plays, the page is visible and the effective mode is not lightweight. */
@Composable
private fun TerminalSpectrum(running: Boolean) {
    val moving = running && !LocalEchoEffectivePerformanceMode.current.isLightweight
    val clock = rememberLoopClock(moving, 3)
    Canvas(Modifier.fillMaxWidth().height(56.dp)) {
        val bars = 26
        val gap = 3.dp.toPx()
        val barWidth = (size.width - gap * (bars - 1)) / bars
        val t = clock.value
        for (i in 0 until bars) {
            val level = if (moving) 0.12f + 0.88f * abs(sin((t * (1 + i % 3) + i * 0.137f) * 2f * PI.toFloat())) else 0.12f
            val h = size.height * level
            drawRect(TerminalStyle.Green, Offset(i * (barWidth + gap), size.height - h), Size(barWidth, h))
        }
    }
}
