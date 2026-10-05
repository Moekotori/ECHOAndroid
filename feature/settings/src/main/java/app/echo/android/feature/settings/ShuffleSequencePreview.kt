package app.echo.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.model.playback.EchoShuffleMode
import app.echo.android.model.playback.EchoShuffleOptions

/** A compact illustrative sequence, never a preview of the user's real queue. */
@Composable
internal fun ShuffleSequencePreview(options: EchoShuffleOptions) {
    val scheme = MaterialTheme.colorScheme
    val sequence = if (options.mode == EchoShuffleMode.PureRandom && options.recentTrackCount == 0)
        listOf("A", "C", "A", "D") else listOf("A", "C", "B", "D")
    val description = stringResource(R.string.settings_shuffle_example_accessible, sequence.joinToString(" → "))
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.settings_shuffle_example), style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant)
        Row(
            Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            sequence.forEachIndexed { index, track ->
                if (index > 0) EchoIcon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(14.dp),
                    tint = scheme.onSurfaceVariant.copy(alpha = 0.5f))
                Text(track, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        Text(
            stringResource(when {
                options.mode == EchoShuffleMode.PseudoRandom -> R.string.settings_shuffle_example_pseudo
                options.recentTrackCount == 0 -> R.string.settings_shuffle_example_pure
                else -> R.string.settings_shuffle_example_recent
            }),
            style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
        )
    }
}
