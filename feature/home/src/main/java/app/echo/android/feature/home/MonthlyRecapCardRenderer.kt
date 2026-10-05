package app.echo.android.feature.home

import android.graphics.*
import android.text.TextPaint
import android.text.TextUtils
import app.echo.android.model.playback.EchoMonthlyRecap

internal enum class MonthlyRecapCardStyle { Paper, Midnight }

internal data class MonthlyRecapCardLabels(val month: String, val title: String, val minutes: String,
    val totals: String, val albums: String, val artists: String, val hours: String, val basis: String)

/** One fixed-size bitmap is shared by preview, document export and sharing. */
internal object MonthlyRecapCardRenderer {
    fun render(report: EchoMonthlyRecap, covers: List<Bitmap?>, style: MonthlyRecapCardStyle,
        labels: MonthlyRecapCardLabels): Bitmap {
        val bitmap = Bitmap.createBitmap(1080, 1350, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val dark = style == MonthlyRecapCardStyle.Midnight
        val ink = if (dark) Color.rgb(242, 244, 246) else Color.rgb(36, 36, 43)
        val muted = if (dark) Color.rgb(162, 174, 186) else Color.rgb(100, 101, 109)
        canvas.drawColor(if (dark) Color.rgb(23, 33, 43) else Color.rgb(245, 245, 243))
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ink }
        fun label(text: String, x: Float, y: Float, size: Float, width: Float = 912f, secondary: Boolean = false) {
            paint.color = if (secondary) muted else ink; paint.textSize = size
            paint.typeface = Typeface.create("sans-serif", if (size >= 44f) Typeface.BOLD else Typeface.NORMAL)
            canvas.drawText(TextUtils.ellipsize(text, paint, width, TextUtils.TruncateAt.END).toString(), x, y, paint)
        }
        label("ECHO", 84f, 94f, 27f)
        label(labels.month, 84f, 160f, 52f)
        label(labels.title, 84f, 207f, 25f, secondary = true)
        label(labels.minutes, 84f, 337f, 70f)
        label(labels.totals, 84f, 383f, 25f, secondary = true)
        label(labels.hours, 84f, 458f, 25f)
        val max = report.playsByHour.maxOrNull()?.coerceAtLeast(1) ?: 1
        for (hour in 0..23) {
            val value = report.playsByHour.getOrNull(hour)?.coerceAtLeast(0) ?: 0
            val height = value.toFloat() / max * 92
            paint.color = ink; paint.alpha = if (value == max) 230 else 110
            canvas.drawRoundRect(84f + hour * 38f, 566f - height.coerceAtLeast(3f), 111f + hour * 38f, 566f, 5f, 5f, paint)
        }
        paint.alpha = 255
        listOf(0, 6, 12, 18, 23).forEach { label("%02d".format(it), 84f + it * 38, 599f, 20f, secondary = true) }
        label(labels.albums, 84f, 668f, 30f)
        report.topAlbums.take(3).forEachIndexed { index, entry ->
            val x = 84f + index * 312f
            paint.color = muted; paint.alpha = 35
            canvas.drawRoundRect(x, 696f, x + 260, 956f, 14f, 14f, paint)
            paint.alpha = 255
            covers.getOrNull(index)?.let { cover ->
                val edge = minOf(cover.width, cover.height)
                val left = (cover.width - edge) / 2; val top = (cover.height - edge) / 2
                canvas.drawBitmap(cover, Rect(left, top, left + edge, top + edge), RectF(x, 696f, x + 260, 956f), paint)
            }
            label(entry.title, x, 996f, 28f, 260f)
            label(entry.subtitle.orEmpty(), x, 1030f, 21f, 260f, secondary = true)
        }
        label(labels.artists, 84f, 1088f, 30f)
        label(report.newArtists.take(3).joinToString(" · ") { it.title }.ifEmpty { "—" }, 84f, 1138f, 27f)
        paint.color = muted; paint.alpha = 75; paint.strokeWidth = 1f
        canvas.drawLine(84f, 1190f, 996f, 1190f, paint); paint.alpha = 255
        label(labels.basis, 84f, 1238f, 20f, secondary = true)
        label("ECHO · ${report.year}.${"%02d".format(report.month)}", 84f, 1283f, 19f, secondary = true)
        return bitmap
    }
}
