package app.echo.android.feature.player

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics

internal val LocalTranslationCurrentOnly = staticCompositionLocalOf { false }

/** Keep measured space while hiding non-current translations, including accessibility text. */
internal fun Modifier.lyricTranslationVisibility(visible: Boolean): Modifier =
    if (visible) this else graphicsLayer { alpha = 0f }.clearAndSetSemantics { }
