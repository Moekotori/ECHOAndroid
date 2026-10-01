package app.echo.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.echo.android.design.LocalEchoCustomBackgroundActive

internal val SettingsShape = RoundedCornerShape(8.dp)
internal val SettingsContentInset = 16.dp

internal val SettingsShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = SettingsShape,
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

@Composable
internal fun SettingsStyle(compactMode: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(shapes = SettingsShapes) {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onSurface,
            LocalSettingsCompactMode provides compactMode,
            LocalEchoCustomBackgroundActive provides false,
        ) {
            // Keep the wallpaper covered while settings content enters or changes pages.
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                content()
            }
        }
    }
}
