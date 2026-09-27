package app.echo.android.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun PlayerAppearanceSettings(
    appearance: PlayerAppearance,
    onPreview: (PlayerAppearance) -> Unit,
    onCommit: () -> Unit,
) {
    PlaybackSettingsSection(
        icon = Icons.Rounded.Palette,
        title = stringResource(R.string.player_appearance_title),
        detail = stringResource(R.string.player_appearance_detail),
        trailing = {
            TextButton(onClick = { onPreview(PlayerAppearance()); onCommit() }) {
                Text(stringResource(R.string.player_appearance_reset))
            }
        },
    ) {
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PlaybackChoiceChip(
                text = stringResource(R.string.player_appearance_record_sleeve),
                selected = appearance.isRecordSleeve,
                onClick = { onPreview(appearance.copy(style = "record_sleeve")); onCommit() },
                modifier = Modifier.weight(1f),
            )
            PlaybackChoiceChip(
                text = stringResource(R.string.player_appearance_classic),
                selected = !appearance.isRecordSleeve,
                onClick = { onPreview(appearance.copy(style = "classic")); onCommit() },
                modifier = Modifier.weight(1f),
            )
        }
        AppearanceScaleSlider(
            label = stringResource(R.string.player_appearance_text_size),
            value = appearance.textScale,
            range = 0.8f..1.2f,
            steps = 7,
            onPreview = { onPreview(appearance.copy(textScale = it)) },
            onCommit = onCommit,
        )
        AppearanceScaleSlider(
            label = stringResource(R.string.player_appearance_cover_size),
            value = appearance.artworkScale,
            range = 0.7f..1f,
            steps = 5,
            onPreview = { onPreview(appearance.copy(artworkScale = it)) },
            onCommit = onCommit,
        )
    }
}

@Composable
private fun AppearanceScaleSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onPreview: (Float) -> Unit,
    onCommit: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.player_appearance_percent, (value * 100).roundToInt()),
                style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            value = value,
            onValueChange = onPreview,
            onValueChangeFinished = onCommit,
            valueRange = range,
            steps = steps,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        )
    }
}
