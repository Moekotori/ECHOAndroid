package app.echo.android.feature.player.afterglow

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Full-bleed scene; controls and system chrome appear together and restore the host on exit. */
@Composable
internal fun AfterglowSystemBars(controlsVisible: Boolean) {
    val view = LocalView.current
    val window = view.context.afterglowActivity()?.window ?: return
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val oldStatus = controller.isAppearanceLightStatusBars
        val oldNavigation = controller.isAppearanceLightNavigationBars
        val oldBehavior = controller.systemBarsBehavior
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.isAppearanceLightStatusBars = oldStatus
            controller.isAppearanceLightNavigationBars = oldNavigation
            controller.systemBarsBehavior = oldBehavior
        }
    }
    LaunchedEffect(window, view, controlsVisible) {
        val controller = WindowCompat.getInsetsController(window, view)
        if (controlsVisible) controller.show(WindowInsetsCompat.Type.systemBars())
        else controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}

private tailrec fun Context.afterglowActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.afterglowActivity()
    else -> null
}
