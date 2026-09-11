package app.echo.android.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import app.echo.android.model.settings.EchoColorTheme
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

@Immutable
data class EchoThemeTokens(
    val id: String,
    val dark: Boolean,
    val accent: Color,
    val accentDeep: Color,
    val accentText: Color,
    val onAccent: Color,
    val secondary: Color,
    val heading: Color,
    val muted: Color,
    val onSurface: Color,
    val bgTop: Color,
    val bgMid: Color,
    val bgBottom: Color,
    val night: Color,
    val ink: Color,
    val panel: Color,
    val mist: Color,
    val glassBorder: Color,
    val softLine: Color,
    val glassWash: Color,
    val outline: Color,
    val outlineVariant: Color,
    val onSurfaceVariant: Color,
    val surface: Color,
)

fun echoThemeTokens(theme: EchoColorTheme, dark: Boolean): EchoThemeTokens =
    when (theme) {
        EchoColorTheme.Echo -> if (dark) EchoDefaultDark else EchoDefaultLight
        EchoColorTheme.Twilight -> if (dark) TwilightDark else TwilightLight
        EchoColorTheme.Rosewood -> if (dark) RosewoodDark else RosewoodLight
        EchoColorTheme.Amber -> if (dark) AmberDark else AmberLight
        EchoColorTheme.Ocean -> if (dark) OceanDark else OceanLight
        EchoColorTheme.Graphite -> if (dark) GraphiteDark else GraphiteLight
        EchoColorTheme.Indigo -> if (dark) IndigoDark else IndigoLight
        EchoColorTheme.Plum -> if (dark) PlumDark else PlumLight
        EchoColorTheme.Copper -> if (dark) CopperDark else CopperLight
        EchoColorTheme.Frost -> if (dark) FrostDark else FrostLight
    }

fun echoStartupWindowColor(theme: EchoColorTheme, dark: Boolean): Int =
    if (theme == EchoColorTheme.Echo) {
        if (dark) EchoDefaultStartupDarkArgb else EchoDefaultStartupLightArgb
    } else {
        echoThemeTokens(theme, dark).night.toArgb()
    }

internal fun contrastRatio(foreground: Color, background: Color): Float {
    val lighter = max(relativeLuminance(foreground), relativeLuminance(background))
    val darker = min(relativeLuminance(foreground), relativeLuminance(background))
    return (lighter + 0.05f) / (darker + 0.05f)
}

internal fun relativeLuminance(color: Color): Float {
    fun channel(value: Float): Double {
        val v = value.toDouble()
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }
    return (0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)).toFloat()
}

private const val EchoDefaultStartupDarkArgb = 0xFF080B12.toInt()
private const val EchoDefaultStartupLightArgb = 0xFFF1F1F3.toInt()

private val EchoDefaultDark = EchoThemeTokens(
    id = EchoColorTheme.Echo.id,
    dark = true,
    accent = Color(0xFFD3A9B5),
    accentDeep = Color(0xFF9B5B6A),
    accentText = Color(0xFFE4C4CC),
    onAccent = Color(0xFF251B20),
    secondary = Color(0xFFD7675D),
    heading = Color.White,
    muted = Color(0xFFBCBBC2),
    onSurface = Color(0xFFF3F1F2),
    bgTop = Color(0xFF17171B),
    bgMid = Color(0xFF1D1D21),
    bgBottom = Color(0xFF151519),
    night = Color(0xFF17171B),
    ink = Color(0xFF202126),
    panel = Color(0xFF2A2B30),
    mist = Color(0xFFF4F1F2),
    glassBorder = Color.White.copy(alpha = 0.08f),
    softLine = Color(0xFFDCD8DA),
    glassWash = Color(0xFF2D2E33),
    outline = Color(0xFF5D5C63),
    outlineVariant = Color(0xFF3A3A40),
    onSurfaceVariant = Color(0xFFBCBBC2),
    surface = Color(0xFF222327),
)

private val EchoDefaultLight = EchoThemeTokens(
    id = EchoColorTheme.Echo.id,
    dark = false,
    accent = Color(0xFF925568),
    accentDeep = Color(0xFF9B5B6A),
    accentText = Color(0xFF925568),
    onAccent = Color.White,
    secondary = Color(0xFF76616A),
    heading = Color(0xFF25242A),
    muted = Color(0xFF6D6D73),
    onSurface = Color(0xFF29252A),
    bgTop = Color(0xFFFCF9F6),
    bgMid = Color(0xFFF8F3F1),
    bgBottom = Color(0xFFF0E8E7),
    night = Color(0xFFF8F5F3),
    ink = Color(0xFFFFFBFA),
    panel = Color(0xFFF5EFEC),
    mist = Color(0xFFF4F1F2),
    glassBorder = Color(0xFFE6E3E5),
    softLine = Color(0xFFDCD8DA),
    glassWash = Color(0xFFF6F4F5),
    outline = Color(0xFF93848A),
    outlineVariant = Color(0xFFDED2D5),
    onSurfaceVariant = Color(0xFF6E6268),
    surface = Color(0xFFFFFBFA),
)

private val TwilightLight = themedPalette(
    id = EchoColorTheme.Twilight,
    dark = false,
    accent = Color(0xFFA83E37),
    accentDeep = Color(0xFF7A2C27),
    accentText = Color(0xFFDF6B5F),
    onAccent = Color.White,
    secondary = Color(0xFF4E8F8A),
    heading = Color(0xFF352321),
    muted = Color(0xFF765D57),
    onSurface = Color(0xFF4F3833),
    bgTop = Color(0xFFFFF4EF),
    bgMid = Color(0xFFF3D7CF),
    bgBottom = Color(0xFFEFE5F2),
    panel = Color(0xFFFFFCF9),
    mist = Color(0xFFFBE9E4),
)

private val TwilightDark = themedPalette(
    id = EchoColorTheme.Twilight,
    dark = true,
    accent = Color(0xFFE2776D),
    accentDeep = Color(0xFFC45A52),
    accentText = Color(0xFFFFD0CA),
    onAccent = Color(0xFF2B1513),
    secondary = Color(0xFF8FD4CE),
    heading = Color(0xFFFFF7F4),
    muted = Color(0xFFD2B9B2),
    onSurface = Color(0xFFF3E3DE),
    bgTop = Color(0xFF151012),
    bgMid = Color(0xFF211719),
    bgBottom = Color(0xFF171320),
    panel = Color(0xFF271E21),
    mist = Color(0xFF1F181B),
)

private val RosewoodLight = themedPalette(
    id = EchoColorTheme.Rosewood,
    dark = false,
    accent = Color(0xFF66312D),
    accentDeep = Color(0xFF4A221F),
    accentText = Color(0xFF8F4D48),
    onAccent = Color.White,
    secondary = Color(0xFF8B6A3E),
    heading = Color(0xFF35211F),
    muted = Color(0xFF7E6662),
    onSurface = Color(0xFF5C4240),
    bgTop = Color(0xFFFBF3EE),
    bgMid = Color(0xFFEAD3C7),
    bgBottom = Color(0xFFF0DFE7),
    panel = Color(0xFFFFF8F4),
    mist = Color(0xFFEFDCD3),
)

private val RosewoodDark = themedPalette(
    id = EchoColorTheme.Rosewood,
    dark = true,
    accent = Color(0xFFD4827B),
    accentDeep = Color(0xFFB56660),
    accentText = Color(0xFFF3B8AE),
    onAccent = Color(0xFF2F1210),
    secondary = Color(0xFFD2A45C),
    heading = Color(0xFFFFF0EB),
    muted = Color(0xFFC7AAA3),
    onSurface = Color(0xFFEBD1CB),
    bgTop = Color(0xFF140F10),
    bgMid = Color(0xFF251717),
    bgBottom = Color(0xFF201821),
    panel = Color(0xFF2A1D1D),
    mist = Color(0xFF1E1516),
)

private val AmberLight = themedPalette(
    id = EchoColorTheme.Amber,
    dark = false,
    accent = Color(0xFF624015),
    accentDeep = Color(0xFF4A2F10),
    accentText = Color(0xFF9A6A24),
    onAccent = Color.White,
    secondary = Color(0xFF6A4B3A),
    heading = Color(0xFF33291E),
    muted = Color(0xFF7A6A59),
    onSurface = Color(0xFF584737),
    bgTop = Color(0xFFFBF7EE),
    bgMid = Color(0xFFEAD9BB),
    bgBottom = Color(0xFFF3E7D4),
    panel = Color(0xFFFFFAF1),
    mist = Color(0xFFEFE1C9),
)

private val AmberDark = themedPalette(
    id = EchoColorTheme.Amber,
    dark = true,
    accent = Color(0xFFD4A64C),
    accentDeep = Color(0xFFB8873A),
    accentText = Color(0xFFF5D78F),
    onAccent = Color(0xFF221405),
    secondary = Color(0xFFB88763),
    heading = Color(0xFFFFF3D8),
    muted = Color(0xFFC3AD8D),
    onSurface = Color(0xFFEAD9BD),
    bgTop = Color(0xFF11100E),
    bgMid = Color(0xFF211A12),
    bgBottom = Color(0xFF2A2118),
    panel = Color(0xFF2A241C),
    mist = Color(0xFF1D1914),
)

private val OceanLight = themedPalette(
    id = EchoColorTheme.Ocean,
    dark = false,
    accent = Color(0xFF1D526A),
    accentDeep = Color(0xFF143B4D),
    accentText = Color(0xFF2F7390),
    onAccent = Color.White,
    secondary = Color(0xFF596B9A),
    heading = Color(0xFF202D3A),
    muted = Color(0xFF607486),
    onSurface = Color(0xFF415363),
    bgTop = Color(0xFFF4F8FB),
    bgMid = Color(0xFFD8E8EF),
    bgBottom = Color(0xFFDCE3F2),
    panel = Color(0xFFFBFDFF),
    mist = Color(0xFFE5EEF4),
)

private val OceanDark = themedPalette(
    id = EchoColorTheme.Ocean,
    dark = true,
    accent = Color(0xFF68B4D4),
    accentDeep = Color(0xFF3E8AAB),
    accentText = Color(0xFFC2EAFF),
    onAccent = Color(0xFF0C2531),
    secondary = Color(0xFF9AA7E8),
    heading = Color(0xFFEDF7FF),
    muted = Color(0xFFA9BFCE),
    onSurface = Color(0xFFD4E5EF),
    bgTop = Color(0xFF0F151B),
    bgMid = Color(0xFF132332),
    bgBottom = Color(0xFF17203A),
    panel = Color(0xFF1E2A34),
    mist = Color(0xFF16212B),
)

private val GraphiteLight = themedPalette(
    id = EchoColorTheme.Graphite,
    dark = false,
    accent = Color(0xFF1F5D55),
    accentDeep = Color(0xFF16443E),
    accentText = Color(0xFF2F7F73),
    onAccent = Color.White,
    secondary = Color(0xFF496A9F),
    heading = Color(0xFF1F292B),
    muted = Color(0xFF637174),
    onSurface = Color(0xFF3F5053),
    bgTop = Color(0xFFF5F7F8),
    bgMid = Color(0xFFDFE6E8),
    bgBottom = Color(0xFFD8F3EC),
    panel = Color(0xFFFBFCFC),
    mist = Color(0xFFE8EEF0),
)

private val GraphiteDark = themedPalette(
    id = EchoColorTheme.Graphite,
    dark = true,
    accent = Color(0xFF5EC4B5),
    accentDeep = Color(0xFF3E9A8D),
    accentText = Color(0xFFB2EFE6),
    onAccent = Color(0xFF0B2824),
    secondary = Color(0xFF86A8E7),
    heading = Color(0xFFEDF6F5),
    muted = Color(0xFFA8BCBA),
    onSurface = Color(0xFFD4E3E1),
    bgTop = Color(0xFF101416),
    bgMid = Color(0xFF182123),
    bgBottom = Color(0xFF10241F),
    panel = Color(0xFF20292B),
    mist = Color(0xFF171F21),
)

private val IndigoLight = themedPalette(
    id = EchoColorTheme.Indigo,
    dark = false,
    accent = Color(0xFF0D3659),
    accentDeep = Color(0xFF09263F),
    accentText = Color(0xFF174F7F),
    onAccent = Color.White,
    secondary = Color(0xFFB06D1F),
    heading = Color(0xFF10283A),
    muted = Color(0xFF536B78),
    onSurface = Color(0xFF314655),
    bgTop = Color(0xFFEAF1ED),
    bgMid = Color(0xFFC5D6DC),
    bgBottom = Color(0xFFE4D4B4),
    panel = Color(0xFFFBFBF3),
    mist = Color(0xFFD6E1DF),
)

private val IndigoDark = themedPalette(
    id = EchoColorTheme.Indigo,
    dark = true,
    accent = Color(0xFF4AA6DD),
    accentDeep = Color(0xFF2E7FB3),
    accentText = Color(0xFFC5E7FF),
    onAccent = Color(0xFF041B2F),
    secondary = Color(0xFFD59B43),
    heading = Color(0xFFEDF8FF),
    muted = Color(0xFFABC1D1),
    onSurface = Color(0xFFD8E8F4),
    bgTop = Color(0xFF07101A),
    bgMid = Color(0xFF0A2540),
    bgBottom = Color(0xFF211D15),
    panel = Color(0xFF172B40),
    mist = Color(0xFF0D1D2E),
)

private val PlumLight = themedPalette(
    id = EchoColorTheme.Plum,
    dark = false,
    accent = Color(0xFF5C2946),
    accentDeep = Color(0xFF431D33),
    accentText = Color(0xFF823F65),
    onAccent = Color.White,
    secondary = Color(0xFF665084),
    heading = Color(0xFF3B2030),
    muted = Color(0xFF786170),
    onSurface = Color(0xFF59404F),
    bgTop = Color(0xFFF8F1F5),
    bgMid = Color(0xFFD8B9CA),
    bgBottom = Color(0xFFBCA8C8),
    panel = Color(0xFFFFFAFD),
    mist = Color(0xFFEADDE5),
)

private val PlumDark = themedPalette(
    id = EchoColorTheme.Plum,
    dark = true,
    accent = Color(0xFFD47AA9),
    accentDeep = Color(0xFFB55A8A),
    accentText = Color(0xFFFFD3E9),
    onAccent = Color(0xFF321022),
    secondary = Color(0xFFAA8AD1),
    heading = Color(0xFFFFF3FA),
    muted = Color(0xFFC7AABC),
    onSurface = Color(0xFFEDDBE6),
    bgTop = Color(0xFF120912),
    bgMid = Color(0xFF2A1122),
    bgBottom = Color(0xFF1D1730),
    panel = Color(0xFF301B2B),
    mist = Color(0xFF1B0E19),
)

private val CopperLight = themedPalette(
    id = EchoColorTheme.Copper,
    dark = false,
    accent = Color(0xFF6E3B25),
    accentDeep = Color(0xFF522B1B),
    accentText = Color(0xFF9B5939),
    onAccent = Color.White,
    secondary = Color(0xFF386A76),
    heading = Color(0xFF2C2B2A),
    muted = Color(0xFF716861),
    onSurface = Color(0xFF504944),
    bgTop = Color(0xFFF5F1EB),
    bgMid = Color(0xFFD8C7B9),
    bgBottom = Color(0xFFC8D8DC),
    panel = Color(0xFFFCFAF6),
    mist = Color(0xFFE7DDD3),
)

private val CopperDark = themedPalette(
    id = EchoColorTheme.Copper,
    dark = true,
    accent = Color(0xFFD28A5F),
    accentDeep = Color(0xFFB56C43),
    accentText = Color(0xFFFFD2B5),
    onAccent = Color(0xFF2E160B),
    secondary = Color(0xFF6FA6B5),
    heading = Color(0xFFFFF5ED),
    muted = Color(0xFFBFB1A7),
    onSurface = Color(0xFFE9DFD7),
    bgTop = Color(0xFF10151D),
    bgMid = Color(0xFF1D2935),
    bgBottom = Color(0xFF4E3025),
    panel = Color(0xFF202B34),
    mist = Color(0xFF131B23),
)

private val FrostLight = themedPalette(
    id = EchoColorTheme.Frost,
    dark = false,
    accent = Color(0xFF163F70),
    accentDeep = Color(0xFF102E53),
    accentText = Color(0xFF245F9E),
    onAccent = Color.White,
    secondary = Color(0xFF7F3E70),
    heading = Color(0xFF142234),
    muted = Color(0xFF546A80),
    onSurface = Color(0xFF34495F),
    bgTop = Color(0xFFEAF2FB),
    bgMid = Color(0xFFAAC2DF),
    bgBottom = Color(0xFFD4C0DC),
    panel = Color(0xFFFBFDFF),
    mist = Color(0xFFD9E5F2),
)

private val FrostDark = themedPalette(
    id = EchoColorTheme.Frost,
    dark = true,
    accent = Color(0xFF5C8FD3),
    accentDeep = Color(0xFF3D6FB3),
    accentText = Color(0xFFD1E4FF),
    onAccent = Color(0xFF07182D),
    secondary = Color(0xFFC06A9E),
    heading = Color(0xFFF5F9FF),
    muted = Color(0xFFB1C4DC),
    onSurface = Color(0xFFDEEBFB),
    bgTop = Color(0xFF080D15),
    bgMid = Color(0xFF101B2C),
    bgBottom = Color(0xFF201426),
    panel = Color(0xFF182434),
    mist = Color(0xFF0E1724),
)

private fun themedPalette(
    id: EchoColorTheme,
    dark: Boolean,
    accent: Color,
    accentDeep: Color,
    accentText: Color,
    onAccent: Color,
    secondary: Color,
    heading: Color,
    muted: Color,
    onSurface: Color,
    bgTop: Color,
    bgMid: Color,
    bgBottom: Color,
    panel: Color,
    mist: Color,
): EchoThemeTokens {
    val surface = if (dark) bgMid else panel
    val night = bgTop
    val ink = if (dark) bgMid else panel
    return EchoThemeTokens(
        id = id.id,
        dark = dark,
        accent = accent,
        accentDeep = accentDeep,
        accentText = accentText,
        onAccent = onAccent,
        secondary = secondary,
        heading = heading,
        muted = muted,
        onSurface = onSurface,
        bgTop = bgTop,
        bgMid = bgMid,
        bgBottom = bgBottom,
        night = night,
        ink = ink,
        panel = panel,
        mist = mist,
        glassBorder = if (dark) Color.White.copy(alpha = 0.10f) else lerp(mist, accent, 0.16f),
        softLine = if (dark) Color.White.copy(alpha = 0.08f) else lerp(mist, heading, 0.14f),
        glassWash = if (dark) lerp(panel, secondary, 0.12f) else lerp(mist, secondary, 0.10f),
        outline = lerp(onSurface, surface, if (dark) 0.58f else 0.42f),
        outlineVariant = lerp(onSurface, surface, if (dark) 0.78f else 0.72f),
        onSurfaceVariant = muted,
        surface = surface,
    )
}
