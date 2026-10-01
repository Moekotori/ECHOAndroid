package app.echo.android.feature.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import app.echo.android.design.EchoIcon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun RemoteTransportControls(
    isPlaying: Boolean,
    enabled: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onPrevious, Modifier.size(56.dp), enabled = enabled) {
            EchoIcon(Icons.Rounded.SkipPrevious,
                stringResource(R.string.feature_connect_previous_on_pc_a0f0a7), Modifier.size(30.dp))
        }
        FilledIconButton(onPlayPause, Modifier.size(80.dp), enabled = enabled, shape = CircleShape) {
            EchoIcon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                stringResource(if (isPlaying) R.string.feature_connect_pause_pc_003bcf else R.string.feature_connect_play_on_pc_aa41d1),
                Modifier.size(38.dp))
        }
        IconButton(onNext, Modifier.size(56.dp), enabled = enabled) {
            EchoIcon(Icons.Rounded.SkipNext,
                stringResource(R.string.feature_connect_next_on_pc_303358), Modifier.size(30.dp))
        }
    }
}
