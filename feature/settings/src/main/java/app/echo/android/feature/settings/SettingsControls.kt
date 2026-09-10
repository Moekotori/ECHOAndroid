package app.echo.android.feature.settings

import app.echo.android.design.echoAnimateContentSize
import androidx.compose.foundation.background
import app.echo.android.design.echoClickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.settings.EchoBackgroundStyle
import app.echo.android.model.settings.EchoAppLanguage
import app.echo.android.model.settings.EchoEffectivePerformanceMode
import app.echo.android.model.settings.EchoPerformanceMode
import kotlin.math.roundToInt

@Composable
internal fun SettingsTextInputRow(
    title: String,
    value: String,
    placeholder: String,
    secret: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                title,
                color = scheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun backgroundDetail(mode: String, uri: String?): String {
    val fileName = uri?.substringAfterLast('/')?.takeLast(28)
    return when {
        mode == "image" && !fileName.isNullOrBlank() -> stringResource(R.string.settings_bg_image, fileName)
        mode == "video" && !fileName.isNullOrBlank() -> stringResource(R.string.settings_bg_video, fileName)
        else -> stringResource(R.string.settings_bg_default)
    }
}

internal data class SettingsChoiceOption(
    val value: String,
    val label: String,
)

@Composable
internal fun languageOptions(): List<SettingsChoiceOption> =
    listOf(SettingsChoiceOption(EchoAppLanguage.System, stringResource(R.string.settings_language_system))) +
        EchoAppLanguage.supported.map { SettingsChoiceOption(it.id, it.nativeName) }

@Composable
internal fun languageDetail(mode: String): String =
    EchoAppLanguage.languageOrNull(mode)?.nativeName
        ?: stringResource(R.string.settings_language_detail_system)

@Composable
internal fun performanceModeOptions(): List<SettingsChoiceOption> = listOf(
    SettingsChoiceOption(EchoPerformanceMode.Auto.id, stringResource(R.string.settings_perf_auto)),
    SettingsChoiceOption(EchoPerformanceMode.Balanced.id, stringResource(R.string.settings_perf_balanced)),
    SettingsChoiceOption(EchoPerformanceMode.Lightweight.id, stringResource(R.string.settings_perf_lightweight)),
    SettingsChoiceOption(EchoPerformanceMode.HighPerformance.id, stringResource(R.string.settings_perf_high)),
)

@Composable
internal fun performanceModeDetail(mode: String, effectiveMode: String): String {
    val effectiveLabel = when (EchoEffectivePerformanceMode.entries.firstOrNull { it.id == effectiveMode }) {
        EchoEffectivePerformanceMode.Lightweight -> stringResource(R.string.settings_perf_effective_light)
        EchoEffectivePerformanceMode.HighPerformance -> stringResource(R.string.settings_perf_effective_high)
        else -> stringResource(R.string.settings_perf_effective_balanced)
    }
    return when (EchoPerformanceMode.fromId(mode)) {
        EchoPerformanceMode.Auto -> stringResource(R.string.settings_perf_detail_auto, effectiveLabel)
        EchoPerformanceMode.Balanced -> stringResource(R.string.settings_perf_detail_balanced)
        EchoPerformanceMode.Lightweight -> stringResource(R.string.settings_perf_detail_lightweight)
        EchoPerformanceMode.HighPerformance -> stringResource(R.string.settings_perf_detail_high)
    }
}

@Composable
internal fun fontOptions(importedFontUri: String?): List<SettingsChoiceOption> = buildList {
    add(SettingsChoiceOption("system", stringResource(R.string.settings_font_system)))
    add(SettingsChoiceOption("serif", stringResource(R.string.settings_font_serif)))
    add(SettingsChoiceOption("monospace", stringResource(R.string.settings_font_mono)))
    add(
        SettingsChoiceOption(
            "imported",
            if (importedFontUri.isNullOrBlank()) {
                stringResource(R.string.settings_font_import)
            } else {
                stringResource(R.string.settings_font_imported)
            },
        ),
    )
}

@Composable
internal fun fontDetail(mode: String, importedFontUri: String?): String =
    when (mode) {
        "outfit" -> stringResource(R.string.settings_font_detail_system)
        "serif" -> stringResource(R.string.settings_font_detail_serif)
        "monospace" -> stringResource(R.string.settings_font_detail_mono)
        "imported" -> importedFontUri?.substringAfterLast('/')?.takeLast(28)?.let {
            stringResource(R.string.settings_font_detail_imported, it)
        } ?: stringResource(R.string.settings_font_detail_pick)
        else -> stringResource(R.string.settings_font_detail_system)
    }

internal fun formatMinuteOfDay(value: Int): String {
    val minuteOfDay = value.coerceIn(0, 23 * 60 + 59)
    val hour = minuteOfDay / 60
    val minute = minuteOfDay % 60
    return "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
}

internal fun Float.roundToQuarterHour(): Int =
    ((this / 15f).roundToInt() * 15).coerceIn(0, 23 * 60 + 59)

@Composable
internal fun SettingsSectionCard(
    title: String,
    collapsible: Boolean = false,
    expanded: Boolean = true,
    onExpandedChange: (Boolean) -> Unit = {},
    persistentContent: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    val animateSize = !LocalEchoEffectivePerformanceMode.current.isLightweight
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(settingsPanelColor())
            .then(if (animateSize) Modifier.echoAnimateContentSize() else Modifier)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = if (collapsible) {
                Modifier
                    .fillMaxWidth()
                    .echoClickable { onExpandedChange(!expanded) }
                    .padding(vertical = 2.dp)
            } else {
                Modifier.fillMaxWidth()
            },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                color = if (dark) Color.White.copy(alpha = 0.96f) else scheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (collapsible) {
                Icon(
                    imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = if (dark) Color.White.copy(alpha = 0.72f) else scheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        persistentContent()
        if (expanded) {
            content()
        }
    }
}

@Composable
internal fun SettingsDisclosureRow(
    title: String,
    detail: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    val dark = LocalEchoDarkTheme.current
    val scheme = MaterialTheme.colorScheme
    SettingsRowShell(
        title = title,
        detail = detail,
        modifier = Modifier.echoClickable { onExpandedChange(!expanded) },
    ) {
        Icon(
            imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            tint = if (dark) Color.White.copy(alpha = 0.62f) else scheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
internal fun SettingsBackgroundSourceRow(
    mode: String,
    uri: String?,
    onPickImageBackground: () -> Unit,
    onPickVideoBackground: () -> Unit,
    onClearCustomBackground: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.settings_bg_source),
                color = if (dark) Color.White.copy(alpha = 0.94f) else scheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                backgroundDetail(mode, uri),
                color = if (dark) Color.White.copy(alpha = 0.70f) else scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                BackgroundSourceAction(
                    label = stringResource(R.string.settings_bg_image_label),
                    selected = mode == "image" && !uri.isNullOrBlank(),
                    enabled = true,
                    modifier = Modifier.weight(1f),
                    onClick = onPickImageBackground,
                )
                BackgroundSourceAction(
                    label = stringResource(R.string.settings_bg_video_label),
                    selected = mode == "video" && !uri.isNullOrBlank(),
                    enabled = true,
                    modifier = Modifier.weight(1f),
                    onClick = onPickVideoBackground,
                )
                BackgroundSourceAction(
                    label = stringResource(R.string.settings_bg_default_label),
                    selected = uri.isNullOrBlank(),
                    enabled = !uri.isNullOrBlank(),
                    modifier = Modifier.weight(1f),
                    onClick = onClearCustomBackground,
                )
            }
        }
    }
}

@Composable
internal fun BackgroundSourceAction(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val dark = LocalEchoDarkTheme.current
    val accent = if (selected) settingsControlColor() else if (dark) Color.White.copy(alpha = 0.74f) else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(settingsRowColor(selected))
            .then(if (enabled) Modifier.echoClickable(onClick = onClick) else Modifier)
            .alpha(if (enabled || selected) 1f else 0.48f)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = accent,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun SettingsSwitchRow(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    SettingsRowShell(
        title = title, detail = detail,
        modifier = Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch,
            onValueChange = onCheckedChange).alpha(if (enabled) 1f else 0.5f),
    ) {
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
internal fun SettingsInfoRow(title: String, detail: String) {
    SettingsRowShell(title = title, detail = detail, trailing = {})
}

@Composable
internal fun SettingsActionRow(
    title: String,
    detail: String,
    enabled: Boolean = true,
    actionLabel: String? = null,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val controlColor = settingsControlColor()
    val resolvedActionLabel = actionLabel ?: stringResource(R.string.settings_enter)
    SettingsRowShell(
        title = title,
        detail = detail,
        modifier = if (enabled) Modifier.echoClickable(onClick = onClick) else Modifier,
    ) {
        Text(
            if (enabled) resolvedActionLabel else stringResource(R.string.settings_closed),
            color = if (enabled) controlColor else if (LocalEchoDarkTheme.current) Color.White.copy(alpha = 0.58f) else scheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsChoiceGroupRow(
    title: String,
    detail: String,
    options: List<SettingsChoiceOption>,
    selectedValue: String,
    onOptionSelected: (String) -> Unit,
) {
    SettingsRowShell(title = title, detail = detail, trailing = {})
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            SettingsOptionChip(
                label = option.label,
                selected = selectedValue == option.value,
                onClick = { onOptionSelected(option.value) },
            )
        }
    }
}

@Composable
internal fun SettingsOptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(settingsRowColor(selected))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) settingsControlColor() else if (dark) Color.White.copy(alpha = 0.74f) else scheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SettingsSliderRow(
    title: String,
    valueLabel: @Composable (Float) -> String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    val controlColor = settingsControlColor()
    var localValue by rememberSaveable { mutableFloatStateOf(value) }
    var dragging by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(value) { if (!dragging) localValue = value }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    color = if (dark) Color.White.copy(alpha = 0.94f) else scheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    valueLabel(localValue),
                    color = if (dark) Color.White.copy(alpha = 0.72f) else scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
            Slider(
                value = localValue,
                onValueChange = { dragging = true; localValue = it },
                onValueChangeFinished = { onValueChange(localValue); dragging = false },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = title },
                valueRange = valueRange,
                steps = steps,
                colors = SliderDefaults.colors(
                    thumbColor = controlColor.copy(alpha = if (dark) 0.92f else 0.78f),
                    activeTrackColor = controlColor.copy(alpha = if (dark) 0.46f else 0.40f),
                    inactiveTrackColor = if (dark) Color.White.copy(alpha = 0.12f) else scheme.outlineVariant.copy(alpha = 0.46f),
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
                thumb = {
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(controlColor.copy(alpha = if (dark) 0.92f else 0.72f)),
                    )
                },
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        modifier = Modifier.height(4.dp),
                        colors = SliderDefaults.colors(
                            activeTrackColor = controlColor.copy(alpha = if (dark) 0.38f else 0.34f),
                            inactiveTrackColor = if (dark) Color.White.copy(alpha = 0.10f) else scheme.outlineVariant.copy(alpha = 0.40f),
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent,
                        ),
                    )
                },
            )
        }
    }
}

@Composable
internal fun SettingsRowShell(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                title,
                color = if (dark) Color.White.copy(alpha = 0.94f) else scheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                detail,
                color = if (dark) Color.White.copy(alpha = 0.70f) else scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        trailing()
    }
}

@Composable
internal fun usbExclusiveDetail(status: EchoPlaybackStatus): String {
    val diagnostics = status.diagnostics
    return when {
        diagnostics.usbBitPerfectActive -> stringResource(R.string.settings_usb_bit_perfect)
        diagnostics.usbAudioHasIsochronousOut -> stringResource(
            R.string.settings_usb_iso,
            diagnostics.usbAudioEndpointSummary ?: "iso OUT",
        )
        diagnostics.usbHostPermissionGranted -> stringResource(R.string.settings_usb_granted)
        diagnostics.usbHostPermissionPending -> stringResource(R.string.settings_usb_pending)
        diagnostics.usbBitPerfectSupported -> stringResource(R.string.settings_usb_supported)
        diagnostics.usbConnected -> stringResource(R.string.settings_usb_mixer)
        else -> stringResource(R.string.settings_usb_fallback)
    }
}

@Composable
internal fun usbExclusiveTestDetail(status: EchoPlaybackStatus, result: String): String {
    val diagnostics = status.diagnostics
    return when {
        !diagnostics.usbConnected -> stringResource(R.string.settings_usb_not_detected)
        !diagnostics.usbHostPermissionGranted -> stringResource(R.string.settings_usb_need_permission)
        else -> result
    }
}

@Composable
internal fun backgroundStyleLabel(style: EchoBackgroundStyle): String = stringResource(
    when (style) {
        EchoBackgroundStyle.Natural -> R.string.settings_bg_style_natural
        EchoBackgroundStyle.Soft -> R.string.settings_bg_style_soft
        EchoBackgroundStyle.Airy -> R.string.settings_bg_style_airy
        EchoBackgroundStyle.Dreamy -> R.string.settings_bg_style_dreamy
        EchoBackgroundStyle.Cinematic -> R.string.settings_bg_style_cinematic
        EchoBackgroundStyle.Focus -> R.string.settings_bg_style_focus
    },
)
