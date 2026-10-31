package app.echo.android.feature.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.design.EchoThemeTokens
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.echoThemeTokens
import app.echo.android.model.settings.EchoColorTheme
import app.echo.android.model.settings.EchoCustomColors

private const val PaletteColumns = 3
private val CardShape = RoundedCornerShape(18.dp)

/**
 * Gallery of themes. Each card is a miniature now-playing page drawn from the theme's own tokens
 * in the mode the app is showing, so what you tap is what you get.
 */
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
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
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
        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EchoColorTheme.entries.chunked(PaletteColumns).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    row.forEach { theme ->
                        ThemePreviewCard(
                            theme = theme,
                            customColors = customColors,
                            selected = theme == selected,
                            dark = dark,
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
private fun ThemePreviewCard(
    theme: EchoColorTheme,
    customColors: EchoCustomColors,
    selected: Boolean,
    dark: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val tokens = remember(theme, dark, customColors) { echoThemeTokens(theme, dark, customColors) }
    val label = stringResource(colorThemeLabelRes(theme))
    val border by animateColorAsState(
        targetValue = if (selected) scheme.primary else scheme.outlineVariant,
        label = "themeCardBorder",
    )
    Column(
        modifier = modifier
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .semantics { contentDescription = label },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.84f)
                .clip(CardShape)
                .border(if (selected) 2.dp else 1.dp, border, CardShape),
        ) {
            Canvas(Modifier.fillMaxSize()) { drawThemePreview(tokens) }
            if (selected) {
                EchoIcon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = scheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(7.dp)
                        .background(scheme.primary, RoundedCornerShape(7.dp))
                        .padding(3.dp)
                        .size(13.dp),
                )
            }
        }
        Text(
            label,
            modifier = Modifier.fillMaxWidth(),
            color = if (selected) scheme.primary else scheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A tiny player: glow, cover, two title bars, progress and a play button. */
private fun DrawScope.drawThemePreview(tokens: EchoThemeTokens) {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(tokens.bgTop, tokens.bgMid, tokens.bgBottom)))
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(tokens.accent.copy(alpha = if (tokens.dark) 0.34f else 0.20f), Color.Transparent),
            center = Offset(w * 0.86f, h * 0.06f),
            radius = w * 0.78f,
        ),
        radius = w * 0.78f,
        center = Offset(w * 0.86f, h * 0.06f),
    )

    val pad = w * 0.12f
    val cover = w - pad * 2f
    val coverSize = Size(cover, cover * 0.74f)
    val coverTop = h * 0.10f
    val coverRadius = CornerRadius(w * 0.09f)
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(tokens.accent, tokens.secondary),
            start = Offset(pad, coverTop),
            end = Offset(pad + coverSize.width, coverTop + coverSize.height),
        ),
        topLeft = Offset(pad, coverTop),
        size = coverSize,
        cornerRadius = coverRadius,
    )
    // A soft highlight keeps the cover from reading as a flat swatch.
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
            startY = coverTop,
            endY = coverTop + coverSize.height * 0.6f,
        ),
        topLeft = Offset(pad, coverTop),
        size = coverSize,
        cornerRadius = coverRadius,
    )

    val barHeight = h * 0.045f
    val titleTop = coverTop + coverSize.height + h * 0.07f
    drawRoundRect(
        color = tokens.heading.copy(alpha = 0.92f),
        topLeft = Offset(pad, titleTop),
        size = Size(cover * 0.62f, barHeight),
        cornerRadius = CornerRadius(barHeight / 2f),
    )
    drawRoundRect(
        color = tokens.muted.copy(alpha = 0.75f),
        topLeft = Offset(pad, titleTop + barHeight * 1.9f),
        size = Size(cover * 0.40f, barHeight * 0.8f),
        cornerRadius = CornerRadius(barHeight / 2f),
    )

    val trackY = h * 0.80f
    val trackHeight = h * 0.028f
    drawRoundRect(
        color = tokens.heading.copy(alpha = 0.18f),
        topLeft = Offset(pad, trackY),
        size = Size(cover, trackHeight),
        cornerRadius = CornerRadius(trackHeight / 2f),
    )
    drawRoundRect(
        color = tokens.accent,
        topLeft = Offset(pad, trackY),
        size = Size(cover * 0.42f, trackHeight),
        cornerRadius = CornerRadius(trackHeight / 2f),
    )
    drawCircle(tokens.accent, radius = trackHeight * 1.35f, center = Offset(pad + cover * 0.42f, trackY + trackHeight / 2f))

    val playRadius = w * 0.085f
    val playCenter = Offset(w / 2f, h * 0.915f)
    drawCircle(tokens.accent, radius = playRadius, center = playCenter)
    val glyph = playRadius * 0.42f
    drawPath(
        Path().apply {
            moveTo(playCenter.x - glyph * 0.6f, playCenter.y - glyph)
            lineTo(playCenter.x + glyph, playCenter.y)
            lineTo(playCenter.x - glyph * 0.6f, playCenter.y + glyph)
            close()
        },
        color = tokens.onAccent,
    )
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
        EchoColorTheme.Forest -> R.string.settings_color_theme_forest
        EchoColorTheme.Aurora -> R.string.settings_color_theme_aurora
        EchoColorTheme.Obsidian -> R.string.settings_color_theme_obsidian
        EchoColorTheme.Custom -> R.string.settings_color_theme_custom
    }
