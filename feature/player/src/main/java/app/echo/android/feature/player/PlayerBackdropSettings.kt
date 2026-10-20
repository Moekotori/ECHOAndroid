package app.echo.android.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
    EchoLyricsBackdrop.Iso -> R.string.lyrics_backdrop_iso
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
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.lyrics_backdrop_detail),
            modifier = Modifier.padding(horizontal = 6.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsOptionGrid(options = lyricsBackdropOptions(), selectedId = selected.id, onSelect = onSelect)
        if (flatSurface) {
            Text(stringResource(R.string.lyrics_backdrop_needs_classic),
                modifier = Modifier.padding(horizontal = 6.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onUseClassic) { Text(stringResource(R.string.lyrics_backdrop_use_classic)) }
        }
    }
}
