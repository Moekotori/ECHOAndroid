package app.echo.android.feature.player

import java.text.BreakIterator
import java.util.Locale

internal data class VerticalLyricGlyph(val text: String, val offset: Int)

/** Preserve supplementary characters and combining marks in both native and legacy rendering. */
internal fun verticalLyricGlyphs(text: String): List<VerticalLyricGlyph> {
    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(text) }
    val result = ArrayList<VerticalLyricGlyph>()
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        result += VerticalLyricGlyph(text.substring(start, end), start)
        start = end
        end = iterator.next()
    }
    return result
}
