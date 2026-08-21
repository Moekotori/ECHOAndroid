package app.echo.android.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.echo.android.model.settings.EchoWidthSizeClass

val LocalEchoWidthSizeClass = staticCompositionLocalOf { EchoWidthSizeClass.Compact }

val LocalEchoContentMaxWidth = staticCompositionLocalOf { EchoContentMaxWidth }

@Composable
fun rememberEchoWidthSizeClass(): EchoWidthSizeClass {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return remember(widthDp) { EchoWidthSizeClass.fromWidthDp(widthDp) }
}

fun EchoWidthSizeClass.contentMaxWidth(): Dp = contentMaxWidthDp().dp
