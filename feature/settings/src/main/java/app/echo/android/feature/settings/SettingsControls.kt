package app.echo.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import app.echo.android.design.echoClickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import app.echo.android.design.EchoSwitch
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
            .settingsSearchAnchor(title)
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(vertical = if (LocalSettingsCompactMode.current) 10.dp else 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                title,
                color = scheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            TextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
                shape = SettingsShape,
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
    add(SettingsChoiceOption("outfit", stringResource(R.string.settings_font_outfit)))
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
        "outfit" -> stringResource(R.string.settings_font_detail_outfit)
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
    Row(
        modifier = Modifier
            .settingsSearchAnchor(stringResource(R.string.settings_bg_source))
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(vertical = if (LocalSettingsCompactMode.current) 10.dp else 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.settings_bg_source),
                color = scheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                backgroundDetail(mode, uri),
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
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
            .clip(SettingsShape)
            .then(if (enabled) Modifier.echoClickable(onClick = onClick) else Modifier)
            .alpha(if (enabled || selected) 1f else 0.48f)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = accent)
        Text(
            label,
            color = accent,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
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
        EchoSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
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
    disabledLabel: String? = null,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val controlColor = settingsControlColor()
    SettingsRowShell(
        title = title,
        detail = detail,
        modifier = Modifier.echoClickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.55f),
    ) {
        if (enabled && actionLabel == null) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = scheme.onSurfaceVariant,
            )
        } else {
            Text(
                if (enabled) actionLabel.orEmpty() else disabledLabel ?: stringResource(R.string.settings_unavailable),
                color = if (enabled) controlColor else scheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
        }
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
    Column(
        modifier = Modifier.settingsSearchAnchor(title).fillMaxWidth().padding(vertical = if (LocalSettingsCompactMode.current) 10.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Normal,
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
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
}

@Composable
internal fun SettingsOptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    val shape = SettingsShape
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Reserve the mark's width so selecting another option does not reflow the group.
        Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            if (selected) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = settingsControlColor())
        }
        Text(
            label,
            color = if (selected) settingsControlColor() else if (dark) Color.White.copy(alpha = 0.74f) else scheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
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
    preview: (@Composable (Float) -> Unit)? = null,
    onPreviewValueChange: (Float) -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    val controlColor = settingsControlColor()
    var localValue by rememberSaveable(title) { mutableFloatStateOf(value.coerceIn(valueRange)) }
    var dragging by remember(title) { mutableStateOf(false) }
    LaunchedEffect(value, valueRange) { if (!dragging) localValue = value.coerceIn(valueRange) }
    Row(
        modifier = Modifier
            .settingsSearchAnchor(title)
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(vertical = if (LocalSettingsCompactMode.current) 10.dp else 14.dp),
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
                    color = scheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                )
                Text(
                    valueLabel(localValue),
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
            preview?.invoke(localValue)
            Slider(
                value = localValue.coerceIn(valueRange),
                onValueChange = { dragging = true; localValue = it; onPreviewValueChange(it) },
                onValueChangeFinished = { onValueChange(localValue); dragging = false },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = title },
                valueRange = valueRange,
                steps = steps,
                colors = SliderDefaults.colors(
                    thumbColor = controlColor,
                    activeTrackColor = controlColor.copy(alpha = 0.65f),
                    inactiveTrackColor = if (dark) Color.White.copy(alpha = 0.12f) else scheme.outlineVariant.copy(alpha = 0.46f),
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
                thumb = {
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(controlColor),
                    )
                },
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        modifier = Modifier.height(4.dp),
                        colors = SliderDefaults.colors(
                            activeTrackColor = controlColor.copy(alpha = 0.65f),
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SettingsShape)
            .then(modifier)
            .settingsSearchAnchor(title)
            .heightIn(min = if (LocalSettingsCompactMode.current) 56.dp else 64.dp)
            .padding(vertical = if (LocalSettingsCompactMode.current) 10.dp else 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                title,
                color = scheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            if (detail.isNotBlank()) Text(
                detail,
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Box(Modifier.widthIn(max = 96.dp), contentAlignment = Alignment.CenterEnd) { trailing() }
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
