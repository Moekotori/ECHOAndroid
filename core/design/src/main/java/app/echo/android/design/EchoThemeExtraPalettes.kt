package app.echo.android.design

import androidx.compose.ui.graphics.Color
import app.echo.android.model.settings.EchoColorTheme

// Light mode keeps the shared paper surfaces; a theme's identity there is its accent pair.
private fun neutralLightPalette(
    id: EchoColorTheme,
    accent: Color,
    accentDeep: Color,
    accentText: Color,
    secondary: Color,
): EchoThemeTokens = themedPalette(
    id = id,
    dark = false,
    accent = accent,
    accentDeep = accentDeep,
    accentText = accentText,
    onAccent = Color.White,
    secondary = secondary,
    heading = Color(0xFF27272D),
    muted = Color(0xFF686870),
    onSurface = Color(0xFF27272D),
    bgTop = EchoLightSurfaces.Background,
    bgMid = EchoLightSurfaces.Background,
    bgBottom = EchoLightSurfaces.Background,
    panel = EchoLightSurfaces.Surface,
    mist = EchoLightSurfaces.Container,
)

/** Moss and lichen over a deep spruce night, with a warm bark secondary. */
internal val ForestLight = neutralLightPalette(
    id = EchoColorTheme.Forest,
    accent = Color(0xFF2F6B43),
    accentDeep = Color(0xFF1F4A2E),
    accentText = Color(0xFF3F8A58),
    secondary = Color(0xFF8A6A3A),
)

internal val ForestDark = themedPalette(
    id = EchoColorTheme.Forest,
    dark = true,
    accent = Color(0xFF7CC38F),
    accentDeep = Color(0xFF4F9A66),
    accentText = Color(0xFFC6EDD0),
    onAccent = Color(0xFF0B2414),
    secondary = Color(0xFFD6B26A),
    heading = Color(0xFFF0FAF2),
    muted = Color(0xFFA9BFAF),
    onSurface = Color(0xFFD6E6DA),
    bgTop = Color(0xFF0E1511),
    bgMid = Color(0xFF14231A),
    bgBottom = Color(0xFF1B2218),
    panel = Color(0xFF1B2A21),
    mist = Color(0xFF131E17),
)

/** Violet dusk that bleeds into teal at the bottom edge, like the sky it is named after. */
internal val AuroraLight = neutralLightPalette(
    id = EchoColorTheme.Aurora,
    accent = Color(0xFF5B3FA6),
    accentDeep = Color(0xFF422C80),
    accentText = Color(0xFF6D52BA),
    secondary = Color(0xFF1F8A78),
)

internal val AuroraDark = themedPalette(
    id = EchoColorTheme.Aurora,
    dark = true,
    accent = Color(0xFFB59CFF),
    accentDeep = Color(0xFF8A6BE0),
    accentText = Color(0xFFE2D7FF),
    onAccent = Color(0xFF1E1240),
    secondary = Color(0xFF6FE0C5),
    heading = Color(0xFFF7F4FF),
    muted = Color(0xFFB7B2CC),
    onSurface = Color(0xFFE2DEF2),
    bgTop = Color(0xFF0C0B17),
    bgMid = Color(0xFF171433),
    bgBottom = Color(0xFF0B2427),
    panel = Color(0xFF1E1B38),
    mist = Color(0xFF131127),
)

/** True-black pages for OLED panels, silver controls and a champagne secondary. */
internal val ObsidianLight = neutralLightPalette(
    id = EchoColorTheme.Obsidian,
    accent = Color(0xFF3A3F4A),
    accentDeep = Color(0xFF252932),
    accentText = Color(0xFF4A505C),
    secondary = Color(0xFF7A6A4A),
)

internal val ObsidianDark = themedPalette(
    id = EchoColorTheme.Obsidian,
    dark = true,
    accent = Color(0xFFE6E8EE),
    accentDeep = Color(0xFF8A8E9A),
    accentText = Color.White,
    onAccent = Color(0xFF0B0C10),
    secondary = Color(0xFFE0B86A),
    heading = Color.White,
    muted = Color(0xFF9A9CA6),
    onSurface = Color(0xFFE8E9EE),
    bgTop = Color.Black,
    bgMid = Color(0xFF050507),
    bgBottom = Color.Black,
    panel = Color(0xFF0F1014),
    mist = Color(0xFF08080B),
)
