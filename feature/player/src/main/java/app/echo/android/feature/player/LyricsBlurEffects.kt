package app.echo.android.feature.player

import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import kotlin.math.roundToInt

/** Shared by the visible rows of one list and released with that list. */
internal class LyricsBlurEffects(private val maxRadiusPx: Float) {
    // Small, fixed steps keep the transition continuous without constructing an effect per frame.
    private val effects = Array<RenderEffect?>(33) { step ->
        if (step == 0) null else {
            val radius = maxRadiusPx * step / 32f
            BlurEffect(radius, radius, TileMode.Decal)
        }
    }

    fun at(radiusPx: Float): RenderEffect? = effects[
        ((radiusPx / maxRadiusPx) * 32f).roundToInt().coerceIn(0, 32)
    ]
}
