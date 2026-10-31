package app.echo.android.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import kotlin.math.roundToInt

/** Slider drafts feed the shared preview above; the existing callbacks save on release. */
@Composable
internal fun SettingsBackgroundSliders(
    isVideo: Boolean,
    blur: Float,
    brightness: Float,
    glass: Float,
    scale: Float,
    maxBlur: Float,
    onBlurChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onGlassChange: (Float) -> Unit,
    onScaleChange: (Float) -> Unit,
    onBlurPreview: (Float) -> Unit,
    onBrightnessPreview: (Float) -> Unit,
    onGlassPreview: (Float) -> Unit,
    onScalePreview: (Float) -> Unit,
) {
    if (!isVideo) SettingsSliderRow(
        title = stringResource(R.string.settings_blur), valueLabel = { "${it.roundToInt()} dp" },
        value = blur.coerceIn(0f, maxBlur), valueRange = 0f..maxBlur, steps = maxBlur.toInt() - 1,
        onValueChange = onBlurChange, onPreviewValueChange = onBlurPreview, dense = true,
    )
    SettingsSliderRow(
        title = stringResource(R.string.settings_brightness), valueLabel = { "${(it * 100f).roundToInt()}%" },
        value = brightness, valueRange = 0.35f..1.15f, steps = 15,
        onValueChange = onBrightnessChange, onPreviewValueChange = onBrightnessPreview, dense = true,
        neutralValue = 1f,
    )
    SettingsSliderRow(
        title = stringResource(R.string.settings_glass), valueLabel = { "${(it * 100f).roundToInt()}%" },
        value = glass, valueRange = 0.08f..0.90f, steps = 13,
        onValueChange = onGlassChange, onPreviewValueChange = onGlassPreview, dense = true,
    )
    SettingsSliderRow(
        title = stringResource(R.string.settings_scale), valueLabel = { "${(it * 100f).roundToInt()}%" },
        value = scale, valueRange = 1f..1.4f, steps = 15,
        onValueChange = onScaleChange, onPreviewValueChange = onScalePreview, dense = true,
    )
}
