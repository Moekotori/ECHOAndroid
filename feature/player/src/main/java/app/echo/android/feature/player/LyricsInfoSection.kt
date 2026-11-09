package app.echo.android.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.echoPageSection
import app.echo.android.design.echoTheme
import app.echo.android.model.lyrics.EchoLyricsFormat
import app.echo.android.model.lyrics.EchoLyricsLoadState

@Composable
internal fun LyricsInfoSection(lyricsState: EchoLyricsLoadState) {
    val lyrics = (lyricsState as? EchoLyricsLoadState.Ready)?.lyrics
    val label = when {
        lyrics != null -> {
            val format = lyrics.format
            val source = lyrics.sourceLabel
            remember(format, source) {
                listOfNotNull(format.label(), source?.trim()?.takeIf { it.isNotEmpty() }).joinToString(" · ")
            }
        }
        lyricsState == EchoLyricsLoadState.Loading -> stringResource(R.string.feature_player_reading_local_lyrics_807f08)
        else -> stringResource(R.string.feature_player_no_matching_lyrics_found_7ffc7e)
    }
    val theme = echoTheme()
    Column(
        modifier = Modifier.echoPageSection(0).fillMaxWidth().padding(horizontal = PlaybackRowInset, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = stringResource(R.string.lyrics_info_title),
            modifier = Modifier.semantics { heading() },
            color = theme.heading,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )
        Text(label, color = theme.muted, style = MaterialTheme.typography.bodySmall)
        if (lyrics?.metadata?.get("text_decoding_lossy") == "true") {
            Text(
                stringResource(R.string.lyrics_decoding_lossy),
                color = theme.muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun EchoLyricsFormat.label(): String = when (this) {
    EchoLyricsFormat.Spl -> "SPL"
    EchoLyricsFormat.Lrc -> "LRC"
    EchoLyricsFormat.EnhancedLrc -> "Enhanced LRC"
    EchoLyricsFormat.Ttml -> "TTML"
    EchoLyricsFormat.Srt -> "SRT"
    EchoLyricsFormat.Vtt -> "WebVTT"
    EchoLyricsFormat.Ass -> "ASS/SSA"
    EchoLyricsFormat.Yrc -> "YRC"
    EchoLyricsFormat.Qrc -> "QRC"
    EchoLyricsFormat.Krc -> "KRC"
    EchoLyricsFormat.PlainText -> "Plain"
    EchoLyricsFormat.Sbv -> "SBV"
    EchoLyricsFormat.Sami -> "SAMI"
    EchoLyricsFormat.Json -> "JSON"
    EchoLyricsFormat.Trc -> "TRC"
    EchoLyricsFormat.Snc -> "SNC"
    EchoLyricsFormat.Lrcx -> "LRCX"
}
