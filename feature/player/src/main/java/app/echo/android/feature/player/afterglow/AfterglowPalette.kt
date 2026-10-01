package app.echo.android.feature.player.afterglow

import app.echo.android.model.settings.EchoLyricsPageStyle

/** Colourways translated from echosteam's jizuraSignatureLooks; the chrome stays in the lyric theme. */
internal data class AfterglowPalette(val background: Int, val foreground: Int, val accent: Int, val secondary: Int) {
    // Pastel scenery can be darker than its sky; keep the lyric wipe readable over the ridges too.
    val highlightInk: Int = if ((foreground ushr 16 and 255) < (background ushr 16 and 255))
        mixAfterglow(accent, foreground, 0.6f) else accent
    companion object {
        private val mist = arrayOf(
            AfterglowPalette(0xFFEAE4F6.toInt(), 0xFF3A2C5E.toInt(), 0xFF8E6FD6.toInt(), 0xFFF29DB4.toInt()),
            AfterglowPalette(0xFFDCE9F1.toInt(), 0xFF22405A.toInt(), 0xFF3F8FC4.toInt(), 0xFFF2A88D.toInt()),
            AfterglowPalette(0xFFFBE6EA.toInt(), 0xFF5A2E45.toInt(), 0xFFD6577E.toInt(), 0xFFF2B45E.toInt()),
        )
        private val night = arrayOf(
            AfterglowPalette(0xFF0E1A38.toInt(), 0xFFF4F1FF.toInt(), 0xFFF2C66B.toInt(), 0xFF7FB4FF.toInt()),
            AfterglowPalette(0xFF1A1440.toInt(), 0xFFF6F0FF.toInt(), 0xFFFF8FC4.toInt(), 0xFF8FD8FF.toInt()),
            AfterglowPalette(0xFF08262E.toInt(), 0xFFE8FFF8.toInt(), 0xFF5CE6C2.toInt(), 0xFFF6D07A.toInt()),
        )
        fun forStyle(style: EchoLyricsPageStyle, variant: Int): AfterglowPalette {
            val palettes = if (style == EchoLyricsPageStyle.AfterglowNight) night else mist
            return palettes[Math.floorMod(variant, palettes.size)]
        }
    }
}
