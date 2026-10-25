package app.echo.android.feature.player

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import app.echo.android.design.rememberEchoWindowLayout

@Composable
internal fun playerDialogWindowSizing(): PlayerDialogSizing {
    val density = LocalDensity.current
    val window = rememberEchoWindowLayout()
    val safeDrawing = WindowInsets.safeDrawing
    val insets = (safeDrawing.getTop(density) + safeDrawing.getBottom(density)) / density.density
    return playerDialogSizing(window.heightDp, insets, density.fontScale)
}
