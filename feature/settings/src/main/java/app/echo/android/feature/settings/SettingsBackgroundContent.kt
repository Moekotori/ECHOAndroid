package app.echo.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.backgroundMaxBlur
import app.echo.android.model.settings.EchoBackgroundMode
import app.echo.android.model.settings.EchoBackgroundStyle

@Composable
internal fun SettingsBackgroundContent(
    mode: String,
    uri: String?,
    startupUri: String?,
    blur: Float,
    brightness: Float,
    glass: Float,
    scale: Float,
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onClear: () -> Unit,
    onPickStartup: () -> Unit,
    onClearStartup: () -> Unit,
    onBlurChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onGlassChange: (Float) -> Unit,
    onScaleChange: (Float) -> Unit,
    onStyleChange: (EchoBackgroundStyle) -> Unit,
    showHeader: Boolean = true,
) {
    val performance = LocalEchoEffectivePerformanceMode.current
    val maxBlur = performance.backgroundMaxBlur
    val isVideo = mode == EchoBackgroundMode.Video
    val hasCustom = mode != EchoBackgroundMode.Default && !uri.isNullOrBlank()
    val videoDisabled = isVideo && performance.isLightweight
    val adjustable = hasCustom && !videoDisabled

    var draftBlur by remember(uri, blur, maxBlur) { mutableFloatStateOf(blur.coerceIn(0f, maxBlur)) }
    var draftBrightness by remember(uri, brightness) { mutableFloatStateOf(brightness) }
    var draftGlass by remember(uri, glass) { mutableFloatStateOf(glass) }
    var draftScale by remember(uri, scale) { mutableFloatStateOf(scale) }

    SettingsSectionCard(title = stringResource(R.string.settings_section_background), showHeader = showHeader) {
        Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (adjustable && uri != null) {
                SettingsBackgroundPreview(uri, isVideo, draftBlur, draftBrightness, draftGlass, draftScale, appliedBlur = blur)
            } else {
                BackgroundEmptyTile(
                    if (videoDisabled) stringResource(R.string.settings_bg_video_disabled) else backgroundDetail(mode, uri),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SettingsSegmentedControl(
                    segments = listOf(
                        SettingsSegment(
                            stringResource(R.string.settings_bg_image_label),
                            selected = mode == EchoBackgroundMode.Image && !uri.isNullOrBlank(),
                            onClick = onPickImage,
                        ),
                        SettingsSegment(
                            stringResource(R.string.settings_bg_video_label),
                            selected = isVideo && !uri.isNullOrBlank(),
                            onClick = onPickVideo,
                        ),
                        SettingsSegment(
                            stringResource(R.string.settings_bg_default_label),
                            selected = uri.isNullOrBlank(),
                            enabled = !uri.isNullOrBlank(),
                            onClick = onClear,
                        ),
                    ),
                    modifier = Modifier.settingsSearchAnchor(stringResource(R.string.settings_bg_source)),
                )
                if (hasCustom && !videoDisabled) Text(
                    backgroundDetail(mode, uri),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            if (adjustable) {
                val selectedStyle = EchoBackgroundStyle.entries.firstOrNull {
                    it.matches(blur, brightness, glass, scale, maxBlur, isVideo = isVideo)
                }
                SettingsChipGroup(
                    title = stringResource(R.string.settings_bg_style),
                    detail = if (selectedStyle == null) stringResource(R.string.settings_bg_style_custom) else null,
                    options = EchoBackgroundStyle.entries.map { SettingsChoiceOption(it.id, backgroundStyleLabel(it)) },
                    selectedValue = selectedStyle?.id.orEmpty(),
                    onOptionSelected = { id ->
                        EchoBackgroundStyle.entries.firstOrNull { it.id == id }?.let(onStyleChange)
                    },
                )
                Text(
                    stringResource(
                        if (isVideo) R.string.settings_bg_style_video_detail else R.string.settings_bg_style_detail,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BackgroundAdjustments(
                    isVideo, blur, brightness, glass, scale, maxBlur,
                    onBlurChange, onBrightnessChange, onGlassChange, onScaleChange,
                    onBlurPreview = { draftBlur = it },
                    onBrightnessPreview = { draftBrightness = it },
                    onGlassPreview = { draftGlass = it },
                    onScalePreview = { draftScale = it },
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            SettingsStartupBackgroundCard(uri = startupUri, onPick = onPickStartup, onClear = onClearStartup)
        }
    }
}

/** Sliders stay folded until asked for, unless search is jumping to one of them. */
@Composable
private fun BackgroundAdjustments(
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
    val adjustmentsTitle = stringResource(R.string.settings_section_background_adjustments)
    val sliderTitles = listOf(
        adjustmentsTitle,
        stringResource(R.string.settings_blur),
        stringResource(R.string.settings_brightness),
        stringResource(R.string.settings_glass),
        stringResource(R.string.settings_scale),
    )
    var expanded by rememberSaveable { mutableStateOf(false) }
    val searching = LocalSettingsSearchFocus.current?.title in sliderTitles
    Column {
        SettingsDisclosureRow(
            title = adjustmentsTitle,
            detail = "",
            expanded = expanded || searching,
            onExpandedChange = { expanded = it },
        )
        if (expanded || searching) SettingsBackgroundSliders(
            isVideo, blur, brightness, glass, scale, maxBlur,
            onBlurChange, onBrightnessChange, onGlassChange, onScaleChange,
            onBlurPreview, onBrightnessPreview, onGlassPreview, onScalePreview,
        )
    }
}

@Composable
private fun BackgroundEmptyTile(text: String) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxWidth().height(96.dp)
            .background(scheme.surfaceContainerLow, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            EchoIcon(Icons.Rounded.Image, null, Modifier.size(26.dp), tint = scheme.onSurfaceVariant)
            Text(
                text,
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}
