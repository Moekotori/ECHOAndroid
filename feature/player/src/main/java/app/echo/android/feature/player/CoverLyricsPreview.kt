package app.echo.android.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.model.lyrics.EchoLyrics
import app.echo.android.model.settings.EchoPlayerPageStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Scoped to the cover so each appearance can use the same adaptive lyrics slot. */
internal val LocalCoverLyricsPreview = compositionLocalOf<(@Composable () -> Unit)?> { null }

@Composable
internal fun CoverLyricsPreview(
    trackId: String,
    lyrics: EchoLyrics,
    position: State<Long>,
    onOpenLyrics: () -> Unit,
) {
    // Replacing the song or document must clear the old rows while the new index is prepared.
    key(trackId, lyrics) {
        val timeline by produceState<CoverLyricsTimeline?>(null) {
            value = withContext(Dispatchers.Default) { CoverLyricsTimeline(lyrics.lines).takeUnless { it.isEmpty } }
        }
        timeline?.let { CoverLyricsRows(it, position, onOpenLyrics) }
    }
}

@Composable
private fun CoverLyricsRows(timeline: CoverLyricsTimeline, position: State<Long>, onOpenLyrics: () -> Unit) {
    // Only lyric boundaries invalidate the rows; the player owns the progress clock.
    val active by remember(timeline, position) {
        derivedStateOf(structuralEqualityPolicy()) { timeline.activeAt(position.value) }
    }
    val context by remember(timeline, position) {
        derivedStateOf(structuralEqualityPolicy()) { timeline.contextAt(position.value) }
    }
    val anchor = if (active >= 0) active else context
    val rows = remember(timeline, anchor) { timeline.rowsAround(anchor) }
    val pageStyle = LocalPlayerPageStyle.current
    val ink = if (pageStyle == EchoPlayerPageStyle.Classic || pageStyle.lyricsPreset.isAfterglow) {
        OnArt
    } else {
        remember(pageStyle) { playerPageTokens(pageStyle).ink }
    }
    val textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 19.sp)
    val rowHeight = with(LocalDensity.current) { 21.sp.toDp() }
    Column(
        Modifier.fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.feature_player_lyrics_b90c97),
                onClick = onOpenLyrics,
            )
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        rows.forEachIndexed { index, line ->
            Box(Modifier.fillMaxWidth().height(rowHeight), contentAlignment = Alignment.CenterStart) {
                if (line != null) {
                    val highlighted = index == 1 && active >= 0
                    Text(
                        line,
                        color = if (highlighted) ink else ink.copy(alpha = 0.64f),
                        style = textStyle,
                        fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
