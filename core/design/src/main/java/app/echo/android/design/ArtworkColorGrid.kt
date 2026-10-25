package app.echo.android.design

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A small portrait mosaic of the cover's colors, kept vivid enough to paint backgrounds from.
 * Cells are opaque ARGB; the grid is built once per cover and never touches the bitmap again.
 */
@Immutable
class ArtworkColorGrid(val columns: Int, val rows: Int, private val argb: IntArray) {
    init {
        require(columns > 0 && rows > 0 && argb.size == columns * rows) { "grid size mismatch" }
    }

    fun colorAt(column: Int, row: Int): Int =
        argb[row.coerceIn(0, rows - 1) * columns + column.coerceIn(0, columns - 1)]

    /** Average of the cells around normalized (u, v). Setup-time only, not meant for per-frame use. */
    fun sample(u: Float, v: Float): Int {
        val column = (u.coerceIn(0f, 1f) * (columns - 1)).roundToInt()
        val row = (v.coerceIn(0f, 1f) * (rows - 1)).roundToInt()
        var r = 0
        var g = 0
        var b = 0
        var n = 0
        for (dy in -1..1) for (dx in -1..1) {
            val x = column + dx
            val y = row + dy
            if (x !in 0 until columns || y !in 0 until rows) continue
            val c = argb[y * columns + x]
            r += c shr 16 and 0xFF
            g += c shr 8 and 0xFF
            b += c and 0xFF
            n++
        }
        return pack(r / n, g / n, b / n)
    }

    fun map(transform: (Int) -> Int): ArtworkColorGrid =
        ArtworkColorGrid(columns, rows, IntArray(argb.size) { transform(argb[it]) })

    override fun equals(other: Any?): Boolean =
        this === other || (other is ArtworkColorGrid && columns == other.columns && rows == other.rows && argb.contentEquals(other.argb))

    override fun hashCode(): Int = 31 * (31 * columns + rows) + argb.contentHashCode()

    companion object {
        const val Columns = 16
        const val Rows = 32

        /** Boosts saturation and keeps lightness inside a band where white lyrics stay readable. */
        fun fromPixels(pixels: IntArray, columns: Int, rows: Int): ArtworkColorGrid =
            ArtworkColorGrid(columns, rows, IntArray(pixels.size) { vivid(pixels[it]) })

        /** A finer mosaic for styles that draw the cover as shapes of their own (about 18 KB). */
        const val DetailedColumns = 48
        const val DetailedRows = 96

        fun fromBitmap(source: Bitmap, columns: Int = Columns, rows: Int = Rows): ArtworkColorGrid {
            val scaled = Bitmap.createScaledBitmap(source, columns, rows, true)
            val pixels = IntArray(columns * rows)
            scaled.getPixels(pixels, 0, columns, 0, 0, columns, rows)
            if (scaled !== source) scaled.recycle()
            return fromPixels(pixels, columns, rows)
        }

        private fun pack(r: Int, g: Int, b: Int): Int = 0xFF shl 24 or (r shl 16) or (g shl 8) or b

        private fun vivid(color: Int): Int {
            val r = (color shr 16 and 0xFF) / 255f
            val g = (color shr 8 and 0xFF) / 255f
            val b = (color and 0xFF) / 255f
            val top = max(r, max(g, b))
            val delta = top - min(r, min(g, b))
            val hue = when {
                delta == 0f -> 0f
                top == r -> ((g - b) / delta + if (g < b) 6f else 0f)
                top == g -> (b - r) / delta + 2f
                else -> (r - g) / delta + 4f
            }
            val saturation = if (top == 0f) 0f else min(1f, delta / top * 1.3f)
            val value = top.coerceIn(0.32f, 0.82f)
            // HSV -> RGB with the hue in sixths.
            val chroma = value * saturation
            val x = chroma * (1f - abs(hue % 2f - 1f))
            val m = value - chroma
            val (rr, gg, bb) = when (hue.toInt()) {
                0 -> Triple(chroma, x, 0f)
                1 -> Triple(x, chroma, 0f)
                2 -> Triple(0f, chroma, x)
                3 -> Triple(0f, x, chroma)
                4 -> Triple(x, 0f, chroma)
                else -> Triple(chroma, 0f, x)
            }
            return pack(((rr + m) * 255f).roundToInt(), ((gg + m) * 255f).roundToInt(), ((bb + m) * 255f).roundToInt())
        }
    }
}

/**
 * Cover color mosaic for cover-driven backdrops. Returns null while loading, when [enabled] is false
 * or when there is no artwork; callers keep their palette-based surface in that case. A new cover keeps
 * the previous grid until the next one is ready, so the surface never flashes back to a fallback.
 */
@Composable
fun rememberArtworkColorGrid(artworkUri: String?, enabled: Boolean = true, detailed: Boolean = false): ArtworkColorGrid? {
    if (!enabled || artworkUri.isNullOrBlank()) return null
    val context = LocalContext.current
    val rewriteRevision = EchoArtworkUrlRewriteRegistry.revision
    val fetchUri = resolvedArtworkFetchUri(artworkUri)
    val headers = EchoArtworkRequestHeadersRegistry.headersFor(artworkUri)
    val grid by produceState<ArtworkColorGrid?>(null, artworkUri, fetchUri, rewriteRevision, headers, detailed) {
        val loaded = withContext(Dispatchers.IO) {
            loadArtworkSwatch(context, fetchUri ?: artworkUri, sampleSize = if (detailed) 8 else 16, headers = headers)
                ?.let {
                    if (detailed) ArtworkColorGrid.fromBitmap(it, ArtworkColorGrid.DetailedColumns, ArtworkColorGrid.DetailedRows)
                    else ArtworkColorGrid.fromBitmap(it)
                }
        }
        if (loaded != null) value = loaded
    }
    return grid
}
