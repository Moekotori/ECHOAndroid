package app.echo.android.feature.player

import android.os.SystemClock
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.design.ArtworkTile
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.model.lyrics.EchoLyricDisplaySnapshot
import app.echo.android.model.lyrics.EchoLyricLine

@Composable
fun LockLyricsScene(
    title: String,
    artist: String,
    artworkUri: String?,
    snapshot: EchoLyricDisplaySnapshot,
    wordHighlightEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val current = snapshot.current
    val karaoke = wordHighlightEnabled &&
        !lightweight &&
        snapshot.isPlaying &&
        current != null &&
        current.words.isNotEmpty()
    val position = rememberLockLyricsPosition(snapshot, interpolate = karaoke)
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x66000000), Color.Transparent, Color(0x99000000)),
                    ),
                ),
        )
        Column(
            Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ArtworkTile(
                    artworkUri = artworkUri,
                    modifier = Modifier
                        .fillMaxWidth(0.42f)
                        .height(148.dp),
                    accent = MaterialTheme.colorScheme.primary,
                    cornerRadius = 20.dp,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = title.ifBlank { stringResource(R.string.lock_lyrics_idle_title) },
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                if (artist.isNotBlank()) {
                    Text(
                        text = artist,
                        color = Color.White.copy(alpha = 0.68f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                LockLyricSideLine(snapshot.previous)
                LockLyricCurrentLine(
                    line = current,
                    karaoke = karaoke,
                    position = position,
                )
                LockLyricSideLine(snapshot.next)
            }
            Text(
                text = stringResource(R.string.lock_lyrics_hint),
                color = Color.White.copy(alpha = 0.38f),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun LockLyricSideLine(line: EchoLyricLine?) {
    Text(
        text = line?.text.orEmpty(),
        color = Color.White.copy(alpha = if (line == null) 0f else 0.38f),
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LockLyricCurrentLine(
    line: EchoLyricLine?,
    karaoke: Boolean,
    position: State<Long>,
) {
    val style = MaterialTheme.typography.headlineMedium.copy(
        fontSize = 34.sp,
        lineHeight = 42.sp,
        fontWeight = FontWeight.Bold,
    )
    if (line == null) {
        Text(
            text = stringResource(R.string.lock_lyrics_waiting),
            color = Color.White.copy(alpha = 0.55f),
            style = style,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    KaraokeLyricText(
        line = line,
        active = true,
        enabled = karaoke,
        position = position,
        color = Color.White,
        intensity = 1f,
        style = style,
        weight = FontWeight.Bold,
        align = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun rememberLockLyricsPosition(
    snapshot: EchoLyricDisplaySnapshot,
    interpolate: Boolean,
): State<Long> {
    val state = remember { mutableLongStateOf(snapshot.positionMs) }
    LaunchedEffect(
        snapshot.trackId,
        snapshot.currentStartMs,
        snapshot.positionMs,
        snapshot.publishedAtElapsedRealtimeMs,
        snapshot.isPlaying,
        snapshot.speed,
        interpolate,
    ) {
        state.longValue = snapshot.positionMs
        if (!interpolate || !snapshot.isPlaying) return@LaunchedEffect
        while (true) {
            state.longValue = snapshot.interpolatedPositionMs(SystemClock.elapsedRealtime())
            withInfiniteAnimationFrameNanos { }
        }
    }
    return state
}
