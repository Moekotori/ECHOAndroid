package app.echo.android.feature.player

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import app.echo.android.model.lyrics.EchoLyricWord
import java.text.BreakIterator
import java.util.Locale

internal class LyricWordShape(
    val first: Int,
    val endExclusive: Int,
    val glyphs: IntArray,
) {
    val isEmpty: Boolean get() = first >= endExclusive
}

/** Match timed text without requiring providers to preserve inter-word whitespace. */
internal fun lyricWordShapes(text: String, words: List<EchoLyricWord>): List<LyricWordShape> {
    val boundaries = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(text) }
    var cursor = 0
    val shapes = ArrayList<LyricWordShape>(words.size)
    for (word in words) {
        var start = -1
        var end = cursor
        for (character in word.text) {
            if (character.isWhitespace()) continue
            while (cursor < text.length && text[cursor].isWhitespace()) cursor++
            // A genuinely different transcription must not highlight unrelated characters.
            if (cursor >= text.length || text[cursor] != character) return emptyList()
            if (start < 0) start = cursor
            cursor++
            end = cursor
        }
        if (start < 0) {
            shapes += LyricWordShape(cursor, cursor, IntArray(0))
            continue
        }
        val glyphs = (start until end).filter { !text[it].isWhitespace() && boundaries.isBoundary(it) }.toIntArray()
        shapes += LyricWordShape(start, end, glyphs)
    }
    if ((cursor until text.length).any { !text[it].isWhitespace() }) return emptyList()
    return shapes
}

internal fun lyricWordEndMs(words: List<EchoLyricWord>, index: Int, lineEndMs: Long?): Long? {
    val word = words[index]
    return word.endMs ?: words.getOrNull(index + 1)?.startMs ?: lineEndMs
}

internal fun lyricWordFraction(startMs: Long, endMs: Long?, now: Long): Float = when {
    now < startMs -> 0f
    endMs == null || endMs <= startMs -> 1f
    else -> ((now - startMs).toFloat() / (endMs - startMs)).coerceIn(0f, 1f)
}

/** Coverage of one glyph inside a partially lit word. Earlier glyphs are 1, later glyphs are 0. */
internal fun lyricGlyphPart(fraction: Float, glyphCount: Int, glyphIndex: Int): Float {
    if (glyphCount <= 0 || fraction <= 0f || glyphIndex !in 0 until glyphCount) return 0f
    if (fraction >= 1f) return 1f
    return (fraction * glyphCount - glyphIndex).coerceIn(0f, 1f)
}

/**
 * Fully lit words, plus the single word still wiping.
 * [partialWord] is -1 when the highlight is steady (not started, between words, or finished).
 */
internal data class KaraokeHighlightPlan(
    val completedWords: Int,
    val partialWord: Int,
    val partialFraction: Float,
)

internal fun planKaraokeHighlight(
    words: List<EchoLyricWord>,
    lineEndMs: Long?,
    now: Long,
): KaraokeHighlightPlan {
    var completed = 0
    while (completed < words.size) {
        val fraction = lyricWordFraction(
            startMs = words[completed].startMs,
            endMs = lyricWordEndMs(words, completed, lineEndMs),
            now = now,
        )
        if (fraction < 1f) {
            return KaraokeHighlightPlan(
                completedWords = completed,
                partialWord = if (fraction > 0f) completed else -1,
                partialFraction = if (fraction > 0f) fraction else 0f,
            )
        }
        completed++
    }
    return KaraokeHighlightPlan(completedWords = completed, partialWord = -1, partialFraction = 0f)
}

/**
 * Completed words keep their glyph outlines. A draw rebuilds only when the plan or layout changes,
 * and then only appends newly finished words plus the glyph still in progress.
 */
internal class KaraokeHighlightClip {
    private val completed = Path()
    private val frame = Path()
    private var layout: TextLayoutResult? = null
    private var builtWords = 0
    private var cachedPlan: KaraokeHighlightPlan? = null
    private var visible = false

    fun prepare(
        result: TextLayoutResult,
        words: List<EchoLyricWord>,
        lineEndMs: Long?,
        shapes: List<LyricWordShape>,
        now: Long,
    ): Path? {
        val plan = planKaraokeHighlight(words, lineEndMs, now)
        if (layout === result && plan == cachedPlan) return if (visible) frame else null
        if (layout !== result || plan.completedWords < builtWords) {
            completed.rewind()
            builtWords = 0
            layout = result
        }
        while (builtWords < plan.completedWords && builtWords < shapes.size) {
            val shape = shapes[builtWords]
            if (!shape.isEmpty) completed.addPath(result.getPathForRange(shape.first, shape.endExclusive))
            builtWords++
        }
        frame.rewind()
        var ink = false
        if (builtWords > 0) {
            frame.addPath(completed)
            ink = true
        }
        val partial = plan.partialWord
        if (partial in shapes.indices && plan.partialFraction > 0f) {
            if (addPartial(result, shapes[partial], plan.partialFraction)) ink = true
        }
        cachedPlan = plan
        visible = ink
        return if (ink) frame else null
    }

    private fun addPartial(result: TextLayoutResult, shape: LyricWordShape, fraction: Float): Boolean {
        val glyphs = shape.glyphs
        if (glyphs.isEmpty()) return false
        var ink = false
        for (glyphIndex in glyphs.indices) {
            val part = lyricGlyphPart(fraction, glyphs.size, glyphIndex)
            if (part <= 0f) continue
            val bounds = result.getBoundingBox(glyphs[glyphIndex])
            val rtl = result.getBidiRunDirection(glyphs[glyphIndex]) == ResolvedTextDirection.Rtl
            val width = bounds.width * part
            frame.addRect(
                if (rtl) Rect(bounds.right - width, bounds.top, bounds.right, bounds.bottom)
                else Rect(bounds.left, bounds.top, bounds.left + width, bounds.bottom),
            )
            ink = true
        }
        return ink
    }
}
