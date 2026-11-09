package app.echo.android.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.model.lyrics.EchoLyrics
import app.echo.android.model.lyrics.EchoLyricsFormat

@Composable
internal fun LyricsInfoRow(
    lyrics: EchoLyrics,
    modifier: Modifier = Modifier,
    color: Color = OnArtMuted,
) {
    val format = lyrics.format
    val encoding = lyrics.metadata["text_encoding"]?.takeIf { it.isNotBlank() && it.length <= 40 }
    val source = lyrics.sourceLabel?.trim()?.takeIf { it.isNotEmpty() }
    val label = remember(format, encoding, source) {
        listOfNotNull(format.label(), encoding, source).joinToString(" · ")
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EchoIcon(PlayerControlIcons.Lyrics, contentDescription = null, Modifier.size(16.dp), tint = color)
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun EchoLyricsFormat.label(): String = when (this) {
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
