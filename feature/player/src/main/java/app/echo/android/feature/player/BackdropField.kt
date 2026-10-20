package app.echo.android.feature.player

import android.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import app.echo.android.design.ArtworkColorGrid

/**
 * One decorative surface painted behind the player. Implementations build their paints and shaders once
 * and reuse them on every frame; [elapsedMs] is decoration time only and never playback position.
 * [pulse] is a 0..1 decorative kick that decays after each lyric line change; fields may ignore it.
 */
internal interface BackdropField {
    fun draw(canvas: Canvas, width: Float, height: Float, elapsedMs: Long, opacity: Float, pulse: Float)
}

/** Pulls every cover color toward the page color so the surface follows the app theme. */
internal fun ArtworkColorGrid.washedToward(anchor: Color, amount: Float): ArtworkColorGrid =
    map { lerp(Color(it), anchor, amount).toArgb() }

/** Light-weight mode never advances the clock, so animated fields start from a pleasant, non-empty frame. */
internal const val BackdropStillOffsetMs = 12_000L
