package app.echo.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import app.echo.android.design.EchoIcon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.echoClickable
import app.echo.android.design.echoColor
import app.echo.android.design.toEchoHsl
import app.echo.android.model.settings.EchoCustomColors
import app.echo.android.model.settings.EchoSavedColorTheme
import app.echo.android.model.settings.EchoSavedColorThemeResult
import app.echo.android.model.settings.EchoSavedColorThemes

@Composable
internal fun ThemeCustomEditor(
    selected: Boolean,
    colors: EchoCustomColors,
    savedThemes: List<EchoSavedColorTheme>,
    appliedId: String?,
    onColorsChange: (EchoCustomColors) -> Unit,
    onSave: (String, (EchoSavedColorThemeResult) -> Unit) -> Unit,
    onApply: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var naming by rememberSaveable { mutableStateOf(false) }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    val savedLabel = stringResource(R.string.settings_custom_saved)
    val updatedLabel = stringResource(R.string.settings_custom_updated)
    val fullLabel = stringResource(R.string.settings_custom_full, EchoSavedColorThemes.MaxCount)
    if (!selected && savedThemes.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (selected) {
            Text(
                stringResource(R.string.settings_custom_detail),
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            CustomColorChannel(
                title = stringResource(R.string.settings_custom_accent),
                color = colors.accent,
                initiallyOpen = true,
                onColorChange = {
                    notice = null
                    onColorsChange(colors.copy(accent = it))
                },
            )
            CustomColorChannel(
                title = stringResource(R.string.settings_custom_secondary),
                color = colors.secondary,
                initiallyOpen = false,
                onColorChange = {
                    notice = null
                    onColorsChange(colors.copy(secondary = it))
                },
            )
            CustomColorChannel(
                title = stringResource(R.string.settings_custom_background),
                color = colors.background,
                initiallyOpen = false,
                onColorChange = {
                    notice = null
                    onColorsChange(colors.copy(background = it))
                },
            )
            TextButton(
                shape = SettingsShape,
                onClick = { naming = true },
                modifier = Modifier.settingsSearchAnchor(stringResource(R.string.settings_custom_save)),
            ) {
                Text(stringResource(R.string.settings_custom_save))
            }
        }
        if (savedThemes.isNotEmpty()) {
            Text(
                stringResource(R.string.settings_custom_saved_themes),
                modifier = Modifier.settingsSearchAnchor(stringResource(R.string.settings_custom_saved_themes)),
                color = scheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            savedThemes.forEach { theme ->
                SavedColorThemeRow(
                    theme = theme,
                    active = selected && theme.id == appliedId,
                    onApply = {
                        notice = null
                        onApply(theme.id)
                    },
                    onDelete = { onDelete(theme.id) },
                )
            }
        }
        notice?.let { message ->
            Text(
                message,
                color = if (message == fullLabel) scheme.error else scheme.primary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    if (naming) {
        val initial = savedThemes.firstOrNull { it.id == appliedId }?.name.orEmpty()
        SaveColorThemeDialog(
            initialName = initial,
            onDismiss = { naming = false },
            onConfirm = { name ->
                naming = false
                onSave(name) { result ->
                    notice = when (result) {
                        EchoSavedColorThemeResult.Saved -> savedLabel
                        EchoSavedColorThemeResult.Updated -> updatedLabel
                        EchoSavedColorThemeResult.Full -> fullLabel
                        EchoSavedColorThemeResult.InvalidName -> null
                    }
                }
            },
        )
    }
}

@Composable
private fun CustomColorChannel(
    title: String,
    color: Int,
    initiallyOpen: Boolean,
    onColorChange: (Int) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyOpen) }
    val open = expanded
    val scheme = MaterialTheme.colorScheme
    val hsl = Color(color).toEchoHsl()
    Column(Modifier.settingsSearchAnchor(title)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .echoClickable(role = Role.Button, onClick = { expanded = !open }),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(color)),
            )
            Text(
                title,
                modifier = Modifier.weight(1f),
                color = scheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (open) {
            SettingsSliderRow(
                title = stringResource(R.string.settings_custom_hue),
                valueLabel = { "${it.toInt()}°" },
                value = hsl.hue,
                valueRange = 0f..360f,
                steps = 71,
                onValueChange = { hue ->
                    onColorChange(echoColor(hue, hsl.saturation, hsl.lightness).toArgb())
                },
            )
            SettingsSliderRow(
                title = stringResource(R.string.settings_custom_saturation),
                valueLabel = { "${(it * 100f).toInt()}%" },
                value = hsl.saturation,
                valueRange = 0f..1f,
                steps = 19,
                onValueChange = { saturation ->
                    onColorChange(echoColor(hsl.hue, saturation, hsl.lightness).toArgb())
                },
            )
            SettingsSliderRow(
                title = stringResource(R.string.settings_custom_lightness),
                valueLabel = { "${(it * 100f).toInt()}%" },
                value = hsl.lightness,
                valueRange = 0f..1f,
                steps = 19,
                onValueChange = { lightness ->
                    onColorChange(echoColor(hsl.hue, hsl.saturation, lightness).toArgb())
                },
            )
        }
    }
}

@Composable
private fun SavedColorThemeRow(
    theme: EchoSavedColorTheme,
    active: Boolean,
    onApply: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .echoClickable(onClickLabel = theme.name, role = Role.Button, onClick = onApply),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(theme.colors.background, theme.colors.accent, theme.colors.secondary).forEach { color ->
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Color(color)),
                )
            }
        }
        Text(
            theme.name,
            modifier = Modifier.weight(1f),
            color = if (active) scheme.primary else scheme.onSurface,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (active) {
            EchoIcon(Icons.Rounded.Check, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDelete) {
            EchoIcon(
                Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.settings_custom_delete),
                tint = scheme.error,
            )
        }
    }
}

@Composable
private fun SaveColorThemeDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    val valid = EchoSavedColorThemes.normalizeName(name) != null
    AlertDialog(
        shape = SettingsShape,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_custom_save)) },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it.take(EchoSavedColorThemes.MaxNameLength) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.settings_custom_save_name)) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
                shape = SettingsShape,
            )
        },
        confirmButton = {
            TextButton(
                shape = SettingsShape,
                onClick = { EchoSavedColorThemes.normalizeName(name)?.let(onConfirm) },
                enabled = valid,
            ) {
                Text(stringResource(R.string.settings_custom_save))
            }
        },
        dismissButton = {
            TextButton(shape = SettingsShape, onClick = onDismiss) { Text(stringResource(R.string.error_log_clear_cancel)) }
        },
    )
}
