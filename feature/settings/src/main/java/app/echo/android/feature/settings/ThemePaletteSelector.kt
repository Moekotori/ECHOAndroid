package app.echo.android.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.echoThemeTokens
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.model.settings.EchoColorTheme

private const val PaletteColumns = 5

@Composable
internal fun ThemePaletteSelector(
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    val selected = EchoColorTheme.fromId(selectedId)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.settings_color_theme),
            color = scheme.onSurface,
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            colorThemeLabel(selected.id),
            color = scheme.primary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EchoColorTheme.entries.chunked(PaletteColumns).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { theme ->
                        ColorThemeSwatch(
                            theme = theme,
                            selected = theme == selected,
                            darkPreview = dark,
                            onSelect = { onSelect(theme.id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(PaletteColumns - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        Text(
            stringResource(R.string.settings_color_theme_detail),
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
internal fun colorThemeLabel(id: String): String =
    stringResource(colorThemeLabelRes(EchoColorTheme.fromId(id)))

@Composable
private fun ColorThemeSwatch(
    theme: EchoColorTheme,
    selected: Boolean,
    darkPreview: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val tokens = remember(theme, darkPreview) { echoThemeTokens(theme, darkPreview) }
    val label = stringResource(colorThemeLabelRes(theme))
    val shape = RoundedCornerShape(14.dp)
    Canvas(
        modifier = modifier
            .height(52.dp)
            .clip(shape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) scheme.primary else scheme.outlineVariant,
                shape = shape,
            )
            .semantics { contentDescription = label }
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(3.dp),
    ) {
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
            center = Offset(w * 0.32f, h * 0.52f),
        )
        drawCircle(
            color = tokens.secondary,
            radius = 3.5.dp.toPx(),
            center = Offset(w * 0.62f, h * 0.52f),
        )
        drawCircle(
            color = tokens.panel.copy(alpha = if (tokens.dark) 0.92f else 0.96f),
            radius = 2.5.dp.toPx(),
            center = Offset(w * 0.82f, h * 0.52f),
        )
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
    }
