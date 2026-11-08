package app.echo.android.feature.player

import androidx.compose.runtime.staticCompositionLocalOf
import app.echo.android.model.settings.EchoLyricsOptions

internal data class LyricsSecondaryText(val translation: Float = 1f, val romanization: Float = 1f)
internal val LocalLyricsSecondaryText = staticCompositionLocalOf { LyricsSecondaryText() }
internal fun secondaryLyricScale(value: Float) = EchoLyricsOptions.secondaryScale(value)
