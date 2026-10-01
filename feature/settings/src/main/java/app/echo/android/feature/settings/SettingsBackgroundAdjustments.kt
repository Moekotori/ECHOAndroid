package app.echo.android.feature.settings

import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import kotlin.math.roundToInt

/** Slider drafts only change the local preview. The existing callbacks save on release. */
@Composable
internal fun SettingsBackgroundAdjustments(
    uri: String,
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
) {
    var draftBlur by remember(uri, blur, maxBlur) { mutableFloatStateOf(blur.coerceIn(0f, maxBlur)) }
    var draftBrightness by remember(uri, brightness) { mutableFloatStateOf(brightness) }
    var draftGlass by remember(uri, glass) { mutableFloatStateOf(glass) }
    var draftScale by remember(uri, scale) { mutableFloatStateOf(scale) }
    SettingsBackgroundPreview(uri, isVideo, draftBlur, draftBrightness, draftGlass, draftScale, appliedBlur = blur)
    if (!isVideo) SettingsSliderRow(
        title = stringResource(R.string.settings_blur), valueLabel = { "${it.roundToInt()} dp" },
        value = blur.coerceIn(0f, maxBlur), valueRange = 0f..maxBlur, steps = maxBlur.toInt() - 1,
        onValueChange = onBlurChange, onPreviewValueChange = { draftBlur = it },
    )
    SettingsSliderRow(
        title = stringResource(R.string.settings_brightness), valueLabel = { "${(it * 100f).roundToInt()}%" },
        value = brightness, valueRange = 0.35f..1.15f, steps = 15,
        onValueChange = onBrightnessChange, onPreviewValueChange = { draftBrightness = it },
    )
    SettingsSliderRow(
        title = stringResource(R.string.settings_glass), valueLabel = { "${(it * 100f).roundToInt()}%" },
        value = glass, valueRange = 0.08f..0.90f, steps = 13,
        onValueChange = onGlassChange, onPreviewValueChange = { draftGlass = it },
    )
    SettingsSliderRow(
        title = stringResource(R.string.settings_scale), valueLabel = { "${(it * 100f).roundToInt()}%" },
        value = scale, valueRange = 1f..1.4f, steps = 15,
        onValueChange = onScaleChange, onPreviewValueChange = { draftScale = it },
    )
}
