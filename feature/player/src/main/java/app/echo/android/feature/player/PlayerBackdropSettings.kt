package app.echo.android.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.settings.EchoLyricsBackdrop

internal fun lyricsBackdropLabel(style: EchoLyricsBackdrop): Int = when (style) {
    EchoLyricsBackdrop.Fluid -> R.string.lyrics_backdrop_fluid
    EchoLyricsBackdrop.Mesh -> R.string.lyrics_backdrop_mesh
    EchoLyricsBackdrop.Halftone -> R.string.lyrics_backdrop_halftone
    EchoLyricsBackdrop.Ridge -> R.string.lyrics_backdrop_ridge
    EchoLyricsBackdrop.Vinyl -> R.string.lyrics_backdrop_vinyl
}

/**
 * Backdrop picker shared by the lyrics settings and the song page settings. Only the classic player page
 * paints a backdrop, so on any other page style it says so and offers the switch instead of silently doing nothing.
 */
@Composable
internal fun PlayerBackdropChoices(
    selected: EchoLyricsBackdrop,
    flatSurface: Boolean,
    onSelect: (String) -> Unit,
    onUseClassic: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.lyrics_backdrop_detail),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        EchoLyricsBackdrop.entries.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { option ->
                    PlaybackChoiceChip(text = stringResource(lyricsBackdropLabel(option)),
                        selected = selected == option, onClick = { onSelect(option.id) }, modifier = Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (flatSurface) {
            Text(stringResource(R.string.lyrics_backdrop_needs_classic),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onUseClassic) { Text(stringResource(R.string.lyrics_backdrop_use_classic)) }
        }
    }
}
