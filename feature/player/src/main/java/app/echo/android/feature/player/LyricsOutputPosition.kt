package app.echo.android.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import app.echo.android.model.lyrics.EchoLyricsOutputTiming

/** Apply after interpolation so the zero-position clamp cannot advance the first lyric. */
@Composable
internal fun rememberLyricsOutputPosition(position: State<Long>, delayMs: Int, speed: Float): State<Long> =
    remember(position, delayMs, speed) {
        derivedStateOf { EchoLyricsOutputTiming.positionMs(position.value, delayMs, speed) }
    }
