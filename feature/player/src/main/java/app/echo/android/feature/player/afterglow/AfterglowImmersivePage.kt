package app.echo.android.feature.player.afterglow

import app.echo.android.design.EchoIcon

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.feature.player.LyricsLineList
import app.echo.android.feature.player.LyricsScrubber
import app.echo.android.feature.player.LyricsTransportControls
import app.echo.android.feature.player.R
import app.echo.android.model.lyrics.EchoLyricsLoadState
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.settings.EchoLyricsPageStyle
import kotlinx.coroutines.delay

private class AfterglowChrome {
    var visible by mutableStateOf(false)
    var touchedAt by mutableLongStateOf(0L)
    var held by mutableStateOf(false)
    var menuOpen by mutableStateOf(false)
    fun touch() { touchedAt = SystemClock.uptimeMillis() }
    fun toggle() { visible = !visible; touch() }
}

/** A full playback surface. Chrome overlays the scene and never reserves lyric layout space. */
@Composable
internal fun AfterglowImmersivePage(
    status: EchoPlaybackStatus, lyricsState: EchoLyricsLoadState, style: EchoLyricsPageStyle,
    position: State<Long>, duration: State<Long>, fontFamily: FontFamily?, fontScale: Float,
    lineSpacing: Float, motionMode: String, showTranslation: Boolean, showRomanization: Boolean,
    wordHighlight: Boolean, estimatedHighlight: Boolean, highlightIntensity: Float,
    visible: Boolean, overlayBlocking: Boolean, onDismiss: () -> Unit, onSettings: () -> Unit,
    onImport: () -> Unit, onAdjustOffset: (Long) -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit,
    onSeek: (Long) -> Unit, onQueue: () -> Unit, onCast: (() -> Unit)?, castActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val chrome = remember(status.track?.id, style) { AfterglowChrome() }
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val showLabel = stringResource(R.string.afterglow_show_controls)
    val hideLabel = stringResource(R.string.afterglow_hide_controls)
    var palette by remember(style) { mutableStateOf(AfterglowPalette.forStyle(style, 0)) }
    LaunchedEffect(chrome.visible, chrome.touchedAt, chrome.held, chrome.menuOpen, overlayBlocking) {
        if (chrome.visible && !chrome.held && !chrome.menuOpen && !overlayBlocking) {
            delay(4500); chrome.visible = false
        }
    }
    LaunchedEffect(visible) { if (!visible) chrome.visible = false }
    AfterglowSystemBars(chrome.visible || overlayBlocking)
    BackHandler(enabled = !overlayBlocking, onBack = onDismiss)
    val controls: @Composable (@Composable () -> Unit) -> Unit = { sceneControls ->
        AfterglowChromeOverlay(chrome.visible, lightweight, status, position, duration,
            onDismiss, { chrome.touch(); onSettings() }, onPlayPause, onNext, onPrevious,
            onSeek, onQueue, onCast, castActive, sceneControls)
    }
    Box(modifier.fillMaxSize().background(Color(palette.background))
        .semantics { onClick(label = if (chrome.visible) hideLabel else showLabel) { chrome.toggle(); true } }
        .pointerInput(chrome) { detectTapGestures(onTap = { chrome.toggle() }) }
        .pointerInput(chrome) {
            awaitPointerEventScope {
                while (true) {
                    val pressed = awaitPointerEvent(PointerEventPass.Initial).changes.any { it.pressed }
                    if (pressed != chrome.held) { chrome.held = pressed; chrome.touch() }
                }
            }
        }) {
        val ordinaryLyrics: @Composable () -> Unit = {
            val lyrics = (lyricsState as? EchoLyricsLoadState.Ready)?.lyrics
            if (lyrics != null) LyricsLineList(lyrics = lyrics, durationMs = duration.value,
                onAdjustOffset = onAdjustOffset, positionMsState = position, onSeek = onSeek,
                lyricsFontFamily = fontFamily, lyricsFontScale = fontScale,
                lyricAccent = Color(palette.foreground), highlightColor = Color(palette.highlightInk),
                paper = false, lyricsAlignment = "center", lyricsLineSpacing = lineSpacing,
                lyricsWordHighlightEnabled = wordHighlight, lyricsEstimatedWordHighlightEnabled = estimatedHighlight,
                lyricsWordHighlightIntensity = highlightIntensity,
                lyricsImmersiveModeEnabled = true, lyricsMotionMode = motionMode,
                showTranslation = showTranslation, showRomanization = showRomanization,
                focusGlowEnabled = false, animationsVisible = visible,
                modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp))
            else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Text(status.track?.title.orEmpty(), color = Color(palette.foreground), style = MaterialTheme.typography.headlineSmall)
                    if (chrome.visible) TextButton(onClick = onImport) { Text(stringResource(R.string.feature_player_import_lyrics_e7494e)) }
                }
            }
            controls({})
        }
        if (lyricsState is EchoLyricsLoadState.Ready) {
            AfterglowStage(lyricsState.lyrics, style, status.track?.id, status.track?.title.orEmpty(),
                position, duration.value, status.isPlaying, status.playbackSpeed, visible, fontFamily,
                fontScale, lineSpacing, motionMode, showTranslation, showRomanization, wordHighlight,
                estimatedHighlight, highlightIntensity, onSeek, Modifier.fillMaxSize(), ordinaryLyrics,
                controlsLayer = controls, onControlsInteraction = chrome::touch,
                onControlsMenu = { chrome.menuOpen = it; chrome.touch() }, onStagePalette = { palette = it })
        } else ordinaryLyrics()
    }
}

@Composable
private fun AfterglowChromeOverlay(
    shown: Boolean, lightweight: Boolean, status: EchoPlaybackStatus,
    position: State<Long>, duration: State<Long>, onDismiss: () -> Unit, onSettings: () -> Unit,
    onPlayPause: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit, onSeek: (Long) -> Unit,
    onQueue: () -> Unit, onCast: (() -> Unit)?, castActive: Boolean, sceneControls: @Composable () -> Unit,
) {
    val fadeMs = if (lightweight) 0 else 160
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
    val shortLandscape = maxWidth >= 480.dp && maxWidth > maxHeight && maxHeight < 600.dp
    val controlsMaxHeight = maxOf(96.dp, maxHeight - 88.dp)
    AnimatedVisibility(shown, modifier = Modifier.align(Alignment.TopCenter), enter = fadeIn(tween(fadeMs)), exit = fadeOut(tween(fadeMs))) {
        Row(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent)))
            .statusBarsPadding().padding(horizontal = 12.dp, vertical = if (shortLandscape) 6.dp else 18.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDismiss) { EchoIcon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.feature_player_close_player_d23966), tint = Color.White) }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(status.track?.title.orEmpty(), color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(status.track?.artist.orEmpty(), color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onSettings) { EchoIcon(Icons.Rounded.MoreHoriz, stringResource(R.string.feature_player_lyrics_settings_843bc9), tint = Color.White) }
        }
    }
    AnimatedVisibility(shown, modifier = Modifier.align(if (shortLandscape) Alignment.BottomEnd else Alignment.BottomCenter), enter = fadeIn(tween(fadeMs)), exit = fadeOut(tween(fadeMs))) {
        Column(Modifier.then(if (shortLandscape) Modifier.width(320.dp).heightIn(max = controlsMaxHeight) else Modifier.fillMaxWidth())
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))))
            .navigationBarsPadding().then(if (shortLandscape) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(horizontal = 22.dp, vertical = if (shortLandscape) 8.dp else 20.dp)) {
            sceneControls()
            Spacer(Modifier.height(12.dp))
            LyricsScrubber(status.track?.id, position, duration, onSeek)
            LyricsTransportControls(status.isPlaying, onPlayPause, onNext, onPrevious, onQueue, onSettings, onCast, castActive,
                landscape = shortLandscape)
        }
    }
    }
}
