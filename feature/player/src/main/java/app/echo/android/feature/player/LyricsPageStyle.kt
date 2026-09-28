package app.echo.android.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.LocalEchoTheme
import app.echo.android.design.ArtworkPalette
import app.echo.android.design.echoThemeTokens
import app.echo.android.model.settings.EchoColorTheme
import app.echo.android.model.settings.EchoLyricsPageStyle

internal val LocalLyricsPageStyle = staticCompositionLocalOf { EchoLyricsPageStyle.Mist }

@Composable
internal fun LyricsPageBackdrop(
    artworkUri: String?,
    palette: ArtworkPalette,
    reveal: () -> Float,
    animationsVisible: Boolean,
    modifier: Modifier,
) {
    if (LocalLyricsPageStyle.current == EchoLyricsPageStyle.Paper) {
        Box(modifier.background(LocalEchoTheme.current.bgTop))
    } else {
        Box(modifier) {
            NowPlayingBackdrop(artworkUri, palette, reveal, Modifier.fillMaxSize(), animationsVisible)
            Box(Modifier.fillMaxSize().background(LocalEchoTheme.current.night.copy(alpha = 0.40f)))
        }
    }
}

/** Scope the lyric palette without changing the app theme or the cover page. */
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
    val dark = style == EchoLyricsPageStyle.Mist
    val tokens = remember(dark) { echoThemeTokens(EchoColorTheme.Echo, dark) }
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
    CompositionLocalProvider(
        LocalLyricsPageStyle provides style,
        LocalEchoDarkTheme provides dark,
        LocalEchoTheme provides tokens,
        LocalContentColor provides tokens.onSurface,
    ) {
        MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, content = content)
    }
}

@Composable
internal fun LyricsPageStyleSelector(style: EchoLyricsPageStyle, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().selectableGroup()) {
        Text(stringResource(R.string.lyrics_page_style), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            EchoLyricsPageStyle.entries.forEach { option ->
                val selected = style == option
                Column(
                    Modifier.weight(1f)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(option.id) })
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        stringResource(if (option == EchoLyricsPageStyle.Mist) R.string.lyrics_style_mist else R.string.lyrics_style_paper),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(if (option == EchoLyricsPageStyle.Mist) R.string.lyrics_style_mist_detail else R.string.lyrics_style_paper_detail),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(7.dp))
                    Box(Modifier.fillMaxWidth().height(2.dp).background(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    ))
                }
            }
        }
    }
}
