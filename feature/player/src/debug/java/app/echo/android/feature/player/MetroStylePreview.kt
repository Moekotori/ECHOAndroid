package app.echo.android.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.design.ArtworkPalette
import app.echo.android.design.EchoMobileTheme
import app.echo.android.model.lyrics.EchoLyricLine
import app.echo.android.model.lyrics.EchoLyrics
import app.echo.android.model.lyrics.EchoLyricsLoadState
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoTrackRef
import app.echo.android.model.settings.EchoLyricsPageStyle
import app.echo.android.model.settings.EchoPlayerPageStyle

/** Debug-only, fixed-data previews of the production composables. No audio service or library needed. */
@Preview(name = "Metro · song", widthDp = 393, heightDp = 852, locale = "zh")
@Composable
fun MetroStylePreview(lyrics: Boolean = false, artworkUri: String? = null) {
    var lyricPage by remember { mutableStateOf(lyrics) }
    val position = remember { mutableLongStateOf(102_000L) }
    val duration = remember { mutableLongStateOf(240_000L) }
    var playing by remember { mutableStateOf(false) }
    var favorite by remember { mutableStateOf(true) }
    val track = remember(artworkUri) {
        EchoTrackRef("metro-preview", "", "沿着光的方向", "ECHO Studio", "City Lines · 2026", artworkUri, durationMs = 240_000L)
    }
    val status = EchoPlaybackStatus(track = track, isPlaying = playing)
    val actions = ExpressivePlayerActions({ playing = !playing }, {}, {}, { position.longValue = it }, {}, null,
        {}, {}, { favorite = !favorite }, { lyricPage = true })
    EchoMobileTheme {
        CompositionLocalProvider(LocalPlayerPageStyle provides EchoPlayerPageStyle.MetroLine) {
            LyricsPageTheme(EchoLyricsPageStyle.Paper) {
                Column(Modifier.fillMaxSize().background(MetroStyle.Paper).safeDrawingPadding().padding(horizontal = 24.dp)) {
                    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Text("04 / ECHO", Modifier.weight(1f), color = MetroStyle.Muted, fontSize = 11.sp, letterSpacing = 1.sp)
                        Text("歌曲", Modifier.clickable { lyricPage = false }.padding(vertical = 12.dp),
                            color = if (!lyricPage) MetroStyle.Ink else MetroStyle.Muted,
                            fontSize = 13.sp, fontWeight = if (!lyricPage) FontWeight.Bold else FontWeight.Normal)
                        Text("歌词", Modifier.clickable { lyricPage = true }.padding(vertical = 12.dp),
                            color = if (lyricPage) MetroStyle.Ink else MetroStyle.Muted,
                            fontSize = 13.sp, fontWeight = if (lyricPage) FontWeight.Bold else FontWeight.Normal)
                    }
                    if (!lyricPage) MetroCoverPage(status, position, duration, actions, favorite, false, 1f, false,
                        Modifier.fillMaxWidth().weight(1f))
                    else NowPlayingLyricsPage(
                        status = status, lyricsState = EchoLyricsLoadState.Ready(previewLyrics),
                        showLyricsControlDeck = false, lyricsFontFamily = null, lyricsFontScale = 0.92f,
                        lyricsColorMode = "white", lyricsAlignment = "start", lyricsLineSpacing = 1f,
                        lyricsBackgroundDim = 0f, lyricsWordHighlightEnabled = true,
                        lyricsEstimatedWordHighlightEnabled = false, lyricsWordHighlightIntensity = 1f,
                        lyricsImmersiveModeEnabled = false, lyricsMotionMode = "calm",
                        lyricsShowTranslation = true, lyricsShowRomanization = false, lyricsFocusGlowEnabled = false,
                        onPlayPause = actions.playPause, onNext = {}, onPrevious = {}, onSeek = actions.seek,
                        onOpenQueue = {}, positionMsState = position, durationMsState = duration,
                        onImportLyrics = {}, onAdjustLyricsOffset = {}, onResetLyricsOffset = {}, onOpenLyricsSettings = {},
                        lyricsPageStyle = EchoLyricsPageStyle.Paper, palette = ArtworkPalette.Default,
                        animationsVisible = false, modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
            }
        }
    }
}

@Preview(name = "Metro · lyrics", widthDp = 393, heightDp = 852, locale = "zh")
@Composable
fun MetroLyricsStylePreview() = MetroStylePreview(lyrics = true)

private val previewLyrics = EchoLyrics(lines = listOf(
    EchoLyricLine(0, text = "清晨的风，穿过街巷"),
    EchoLyricLine(18_000, text = "每一次出发，都有方向"),
    EchoLyricLine(54_000, text = "窗外的光，慢慢铺开", translation = "Light unfolds beyond the window."),
    EchoLyricLine(90_000, text = "把城市的回声，留在这一站", translation = "Let the city echo, here at this stop."),
    EchoLyricLine(128_000, text = "沿着光的方向，继续向前", translation = "Follow the light, and keep moving."),
    EchoLyricLine(180_000, text = "下一站，听见新的远方"),
    EchoLyricLine(225_000, text = "直到夜色，轻轻落下"),
))
