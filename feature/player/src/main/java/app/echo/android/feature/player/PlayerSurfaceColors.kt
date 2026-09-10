package app.echo.android.feature.player

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.echo.android.design.LocalEchoDarkTheme

// Shared foregrounds for the artwork-tinted player surface and its controls.
internal val OnArt: Color
    @Composable get() = if (LocalEchoDarkTheme.current) Color.White else MaterialTheme.colorScheme.onSurface
internal val OnArtMuted: Color
    @Composable get() = OnArt.copy(alpha = 0.84f)
internal val OnArtFaint: Color
    @Composable get() = OnArt.copy(alpha = 0.42f)
internal val OnArtChip: Color
    @Composable get() = OnArt.copy(alpha = if (LocalEchoDarkTheme.current) 0.24f else 0.08f)
