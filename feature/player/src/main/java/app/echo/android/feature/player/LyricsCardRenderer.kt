package app.echo.android.feature.player

import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import app.echo.android.model.library.EchoTrack

internal enum class LyricsCardStyle { Paper, Midnight, Artwork }

/** A single 1080 x 1350 export, with the same bitmap used for the on-screen preview. */
internal object LyricsCardRenderer {
    fun render(track: EchoTrack, lines: List<String>, artwork: Bitmap?, style: LyricsCardStyle): Bitmap {
        require(lines.isNotEmpty() && lines.size <= 6 && lines.sumOf { it.length } <= 1000)
        val bitmap = Bitmap.createBitmap(1080, 1350, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val background = when (style) {
            LyricsCardStyle.Paper -> Color.rgb(239, 237, 231)
            LyricsCardStyle.Midnight -> Color.rgb(24, 33, 45)
            LyricsCardStyle.Artwork -> artworkColor(artwork)
        }
        val ink = if (style == LyricsCardStyle.Paper) Color.rgb(38, 43, 47) else Color.rgb(247, 245, 238)
        canvas.drawColor(background)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink }
        if (artwork != null) canvas.drawBitmap(artwork, null, RectF(88f, 88f, 252f, 252f), paint)
        paint.textSize = 28f; paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        canvas.drawText("ECHO", if (artwork == null) 88f else 292f, 126f, paint)
        val text = lines.joinToString("\n\n")
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink; typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        var size = 64f
        var layout: StaticLayout
        do {
            textPaint.textSize = size
            layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, 904)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
                .setLineSpacing(8f, 1.12f).build()
            if (layout.height <= 750 || size <= 18f) break
            size -= 2f
        } while (true)
        canvas.save(); canvas.translate(88f, 340f + (750 - layout.height).coerceAtLeast(0) / 2f)
        layout.draw(canvas); canvas.restore()
        paint.alpha = 70; paint.strokeWidth = 2f
        canvas.drawLine(88f, 1180f, 992f, 1180f, paint)
        paint.alpha = 255
        drawLabel(canvas, track.title, 88f, 1218f, ink, 32f)
        drawLabel(canvas, track.artist, 88f, 1268f, ink, 24f)
        return bitmap
    }

    private fun drawLabel(canvas: Canvas, text: String, x: Float, y: Float, color: Int, size: Float) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = size; typeface = Typeface.DEFAULT }
        val clipped = android.text.TextUtils.ellipsize(text, paint, 904f, android.text.TextUtils.TruncateAt.END)
        canvas.drawText(clipped.toString(), x, y, paint)
    }

    private fun artworkColor(bitmap: Bitmap?): Int {
        if (bitmap == null) return Color.rgb(39, 49, 61)
        var r = 0; var g = 0; var b = 0
        for (y in 0..7) for (x in 0..7) {
            val pixel = bitmap.getPixel(x * (bitmap.width - 1) / 7, y * (bitmap.height - 1) / 7)
            r += Color.red(pixel); g += Color.green(pixel); b += Color.blue(pixel)
        }
        return Color.rgb((r / 64 * .28).toInt(), (g / 64 * .28).toInt(), (b / 64 * .28).toInt())
    }
}
