package app.echo.android.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.LocalEchoTheme
import app.echo.android.design.ArtworkPalette
import app.echo.android.model.settings.EchoLyricsPageStyle
import app.echo.android.model.settings.EchoPlayerPageStyle

internal val LocalLyricsPageStyle = staticCompositionLocalOf { EchoLyricsPageStyle.Mist }

@Composable
internal fun LyricsPageBackdrop(
    artworkUri: String?,
    palette: ArtworkPalette,
    reveal: () -> Float,
    animationsVisible: Boolean,
    modifier: Modifier,
) {
    if (LocalLyricsPageStyle.current == EchoLyricsPageStyle.Paper || LocalLyricsPageStyle.current.isAfterglow) {
        Box(modifier.background(LocalEchoTheme.current.bgTop))
    } else {
        Box(modifier) {
            NowPlayingBackdrop(artworkUri, palette, reveal, Modifier.fillMaxSize(), animationsVisible)
            Box(Modifier.fillMaxSize().background(LocalEchoTheme.current.night.copy(alpha = 0.40f)))
        }
    }
}

/** Both pages share the selected palette; nested previews inherit the same choice. */
@Composable
internal fun LyricsPageTheme(
    style: EchoLyricsPageStyle,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }
    val shared = LocalPlayerPageStyle.current
    val pageStyle = if (shared.lyricsPreset == style) shared else EchoPlayerPageStyle.fromId(style.id)
    val dark = !pageStyle.isLight
    val tokens = remember(pageStyle) { playerPageTokens(pageStyle) }
    val scheme = remember(tokens) {
        val base = if (dark) darkColorScheme() else lightColorScheme()
        base.copy(
            primary = tokens.accent,
            onPrimary = tokens.onAccent,
            primaryContainer = tokens.panel,
            onPrimaryContainer = tokens.onSurface,
            secondary = tokens.accent,
            onSecondary = tokens.onAccent,
            background = tokens.bgTop,
            onBackground = tokens.onSurface,
            surface = tokens.surface,
            onSurface = tokens.onSurface,
            surfaceVariant = tokens.panel,
            onSurfaceVariant = tokens.muted,
            outline = tokens.outline,
            outlineVariant = tokens.outlineVariant,
            surfaceTint = tokens.accent,
        )
    }
    val inheritedTypography = MaterialTheme.typography
    val typography = remember(pageStyle, inheritedTypography) {
        val display = pageStyle.displayFont
        val body = pageStyle.bodyFont
        inheritedTypography.copy(
            titleLarge = inheritedTypography.titleLarge.copy(fontFamily = display ?: inheritedTypography.titleLarge.fontFamily),
            titleMedium = inheritedTypography.titleMedium.copy(fontFamily = display ?: inheritedTypography.titleMedium.fontFamily),
            titleSmall = inheritedTypography.titleSmall.copy(fontFamily = display ?: inheritedTypography.titleSmall.fontFamily),
            bodyLarge = inheritedTypography.bodyLarge.copy(fontFamily = body ?: inheritedTypography.bodyLarge.fontFamily),
            bodyMedium = inheritedTypography.bodyMedium.copy(fontFamily = body ?: inheritedTypography.bodyMedium.fontFamily),
            bodySmall = inheritedTypography.bodySmall.copy(fontFamily = body ?: inheritedTypography.bodySmall.fontFamily),
        )
    }
    CompositionLocalProvider(
        LocalLyricsPageStyle provides style,
        LocalEchoDarkTheme provides dark,
        LocalEchoTheme provides tokens,
        LocalContentColor provides tokens.onSurface,
    ) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
