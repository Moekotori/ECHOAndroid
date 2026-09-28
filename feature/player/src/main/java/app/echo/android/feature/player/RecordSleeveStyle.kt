package app.echo.android.feature.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat

/** The selected record-sleeve treatment is local to the cover page. */
internal object RecordSleeveStyle {
    val Paper = Color(0xFFFAF7F2)
    val Ink = Color(0xFF242830)
    val Wine = Color(0xFF78364B)
    val Rule = Color(0xFFB49A9F)
    val Track = Color(0xFFD5C4C7)

    // Static instances avoid Android's variable-font default-axis fallback.
    // Cormorant Garamond wght=500 / Outfit wght=400, from Google Fonts upstream.
    // Licenses ship in assets/fonts; these resources do not alter the global font.
    val TitleFont = FontFamily(
        Font(
            R.font.record_sleeve_serif,
            weight = FontWeight.Medium,
        ),
    )

    val BodyFont = FontFamily(
        Font(
            R.font.record_sleeve_sans,
            weight = FontWeight.Normal,
        ),
    )
}

/** Keep system icons readable on paper, then restore the host's appearance. */
@Composable
internal fun RecordSleeveSystemBars(enabled: Boolean, darkIcons: Boolean = true) {
    val view = LocalView.current
    DisposableEffect(view, enabled, darkIcons) {
        val window = view.context.activity()?.window
        if (!enabled || window == null) return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, view)
        val statusWasLight = controller.isAppearanceLightStatusBars
        val navigationWasLight = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = darkIcons
        controller.isAppearanceLightNavigationBars = darkIcons
        onDispose {
            controller.isAppearanceLightStatusBars = statusWasLight
            controller.isAppearanceLightNavigationBars = navigationWasLight
        }
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
