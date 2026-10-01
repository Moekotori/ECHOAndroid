package app.echo.android.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.echoThemeTokens
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.model.settings.EchoColorTheme
import app.echo.android.model.settings.EchoCustomColors

@Composable
internal fun ThemePaletteSelector(
    selectedId: String,
    customColors: EchoCustomColors,
    onSelect: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    val selected = EchoColorTheme.fromId(selectedId)
    Column(
        modifier = Modifier.settingsSearchAnchor(stringResource(R.string.settings_color_theme)),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.settings_color_theme),
                modifier = Modifier.weight(1f),
                color = scheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            Text(
                colorThemeLabel(selected.id),
                color = scheme.primary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Keep every swatch at least 48 dp wide on small windows and split-screen.
            val columns = ((maxWidth + 4.dp) / 52.dp).toInt().coerceIn(1, 6)
            val rows = remember(columns) { EchoColorTheme.entries.chunked(columns) }
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        row.forEach { theme ->
                            ColorThemeSwatch(
                                theme = theme,
                                customColors = customColors,
                                selected = theme == selected,
                                darkPreview = dark,
                                onSelect = { onSelect(theme.id) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(columns - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun colorThemeLabel(id: String): String =
    stringResource(colorThemeLabelRes(EchoColorTheme.fromId(id)))

@Composable
private fun ColorThemeSwatch(
    theme: EchoColorTheme,
    customColors: EchoCustomColors,
    selected: Boolean,
    darkPreview: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val tokens = remember(theme, customColors, darkPreview) { echoThemeTokens(theme, darkPreview, customColors) }
    val label = stringResource(colorThemeLabelRes(theme))
    Box(
        modifier = modifier
            .height(48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(40.dp).then(
            if (selected) Modifier.border(2.dp, scheme.primary, RectangleShape) else Modifier,
        )) {
            val w = size.width
            val h = size.height
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(tokens.bgTop, tokens.bgMid, tokens.bgBottom),
                ),
            )
            drawCircle(
                color = tokens.accent,
                radius = 5.dp.toPx(),
                center = Offset(w * 0.30f, h * 0.56f),
            )
            drawCircle(
                color = tokens.secondary,
                radius = 3.5.dp.toPx(),
                center = Offset(w * 0.60f, h * 0.56f),
            )
            drawCircle(
                color = tokens.panel.copy(alpha = if (tokens.dark) 0.92f else 0.96f),
                radius = 2.dp.toPx(),
                center = Offset(w * 0.82f, h * 0.56f),
            )
        }
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = scheme.onPrimary,
                modifier = Modifier.align(Alignment.TopCenter).padding(start = 24.dp, top = 4.dp)
                    .background(scheme.primary).size(14.dp).padding(1.dp),
            )
        }
    }
}

private fun colorThemeLabelRes(theme: EchoColorTheme): Int =
    when (theme) {
        EchoColorTheme.Echo -> R.string.settings_color_theme_echo
        EchoColorTheme.Twilight -> R.string.settings_color_theme_twilight
        EchoColorTheme.Rosewood -> R.string.settings_color_theme_rosewood
        EchoColorTheme.Amber -> R.string.settings_color_theme_amber
        EchoColorTheme.Ocean -> R.string.settings_color_theme_ocean
        EchoColorTheme.Graphite -> R.string.settings_color_theme_graphite
        EchoColorTheme.Indigo -> R.string.settings_color_theme_indigo
        EchoColorTheme.Plum -> R.string.settings_color_theme_plum
        EchoColorTheme.Copper -> R.string.settings_color_theme_copper
        EchoColorTheme.Frost -> R.string.settings_color_theme_frost
        EchoColorTheme.Custom -> R.string.settings_color_theme_custom
    }
