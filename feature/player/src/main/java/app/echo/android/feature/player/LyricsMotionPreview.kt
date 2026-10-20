package app.echo.android.feature.player

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.model.lyrics.EchoLyricLine
import app.echo.android.model.lyrics.EchoLyricWord
import app.echo.android.model.lyrics.EchoLyrics
import app.echo.android.model.settings.EchoLyricsPageStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** A local demo uses the real renderer even when no playable song or lyric file is loaded. */
@Composable
internal fun LyricsMotionPreview(
    pageStyle: EchoLyricsPageStyle,
    fontFamily: FontFamily?,
    fontScale: Float,
    alignment: String,
    lineSpacing: Float,
    accent: Color,
    highlightEnabled: Boolean,
    highlightIntensity: Float,
    motionMode: String,
) {
    val first = stringResource(R.string.feature_player_lyrics_breathe_naturally_with_playback_c6b1df)
    val second = stringResource(R.string.feature_player_each_line_lifts_with_the_beat_ddb972)
    val lyrics = remember(first, second) {
        EchoLyrics(listOf(first, second, first).mapIndexed { index, text ->
            val start = index * 3200L
            EchoLyricLine(startMs = start, endMs = start + 3200L, text = text,
                // These timings belong only to this synthetic demo, never to the user's lyrics.
                words = text.mapIndexed { offset, char ->
                    EchoLyricWord(start + offset * 3200L / text.length,
                        start + (offset + 1) * 3200L / text.length, char.toString())
                })
        })
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val focused = LocalWindowInfo.current.isWindowFocused
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val position = remember(motionMode, lyrics) { mutableLongStateOf(1200L) }
    LaunchedEffect(position, focused, lightweight, lifecycle) {
        if (!focused) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (lightweight) {
                // Show static focus changes without a continuous animation in the light preset.
                while (isActive) { delay(3200); position.longValue = (position.longValue + 3200L) % 9600L }
            } else {
                val gate = LyricsFrameGate(30)
                var origin = 0L
                val initial = position.longValue
                while (isActive) withFrameNanos { frame ->
                    if (origin == 0L) origin = frame
                    if (gate.accept(frame)) position.longValue = (initial + (frame - origin) / 1_000_000L) % 9600L
                }
            }
        }
    }
    val modifier = Modifier.fillMaxWidth().height(144.dp)
    if (alignment == "vertical") {
        VerticalLyricsList(
            lyrics, position, 9600L, {}, {}, fontFamily, fontScale * 0.6f, lineSpacing,
            accent, MaterialTheme.colorScheme.primary, highlightEnabled, false, highlightIntensity,
            showTranslation = false, showRomanization = false, visible = focused, motionMode = motionMode, modifier = modifier,
            interactive = false,
        )
    } else {
        LyricsLineList(
            lyrics = lyrics, onAdjustOffset = {}, positionMsState = position, onSeek = {},
            lyricsFontFamily = fontFamily, lyricsFontScale = fontScale * 0.6f, lyricAccent = accent,
            lyricsAlignment = alignment, lyricsLineSpacing = lineSpacing,
            lyricsWordHighlightEnabled = highlightEnabled, lyricsEstimatedWordHighlightEnabled = false,
            lyricsWordHighlightIntensity = highlightIntensity, lyricsImmersiveModeEnabled = false,
            lyricsMotionMode = motionMode, showTranslation = false, showRomanization = false,
            focusGlowEnabled = true, animationsVisible = focused, modifier = modifier,
            highlightColor = MaterialTheme.colorScheme.primary, paper = pageStyle == EchoLyricsPageStyle.Paper, durationMs = 9600L,
            interactive = false,
        )
    }
}
