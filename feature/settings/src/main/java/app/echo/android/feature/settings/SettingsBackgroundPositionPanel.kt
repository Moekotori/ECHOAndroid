package app.echo.android.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import kotlin.math.abs
import kotlin.math.roundToInt

/** Keep the viewfinder and its controls together; effect sliders remain in the advanced section. */
@Composable
internal fun SettingsBackgroundPositionPanel(
    scale: Float,
    positionX: Float,
    positionY: Float,
    onScalePreview: (Float) -> Unit,
    onScaleChange: (Float) -> Unit,
    onPositionPreview: (Float, Float) -> Unit,
    onPositionChange: (Float, Float) -> Unit,
    preview: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(modifier = Modifier.settingsSearchAnchor(stringResource(R.string.settings_background_position_title)),
        color = scheme.surfaceContainerLow, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.settings_background_position_title),
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                TextButton(
                    onClick = { onPositionPreview(0f, 0f); onPositionChange(0f, 0f) },
                    enabled = abs(positionX) > .005f || abs(positionY) > .005f,
                    colors = ButtonDefaults.textButtonColors(contentColor = scheme.onSurface),
                ) {
                    EchoIcon(Icons.Rounded.CenterFocusStrong, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.settings_background_position_center))
                }
            }
            preview()
            SettingsSliderRow(
                title = stringResource(R.string.settings_scale), valueLabel = { "${(it * 100f).roundToInt()}%" },
                value = scale, valueRange = 1f..1.4f, steps = 0, neutralValue = 1f,
                onValueChange = onScaleChange, onPreviewValueChange = onScalePreview, dense = true,
            )
            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = .5f))
            SettingsSliderRow(
                title = stringResource(R.string.settings_background_position_x),
                valueLabel = { positionLabel(it, R.string.settings_background_position_left, R.string.settings_background_position_right) },
                value = positionX, valueRange = -1f..1f, steps = 0, neutralValue = 0f,
                onValueChange = { onPositionChange(it, positionY) },
                onPreviewValueChange = { onPositionPreview(it, positionY) }, dense = true,
            )
            SettingsSliderRow(
                title = stringResource(R.string.settings_background_position_y),
                valueLabel = { positionLabel(it, R.string.settings_background_position_up, R.string.settings_background_position_down) },
                value = positionY, valueRange = -1f..1f, steps = 0, neutralValue = 0f,
                onValueChange = { onPositionChange(positionX, it) },
                onPreviewValueChange = { onPositionPreview(positionX, it) }, dense = true,
            )
            Text(stringResource(R.string.settings_background_position_detail),
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun positionLabel(value: Float, negative: Int, positive: Int): String {
    val percent = (abs(value) * 100).roundToInt()
    return if (percent == 0) stringResource(R.string.settings_background_position_center)
        else stringResource(if (value < 0f) negative else positive, percent)
}
