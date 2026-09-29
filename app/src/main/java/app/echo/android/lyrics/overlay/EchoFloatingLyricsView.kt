package app.echo.android.lyrics.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 悬浮歌词的内容：当前行和下一行。用普通 View 而不是 ComposeView，
 * 挂在 WindowManager 上时不需要额外的 Lifecycle / SavedState 宿主，开销也更小。
 */
@SuppressLint("ViewConstructor")
internal class EchoFloatingLyricsView(
    context: Context,
    private val onDrag: (dy: Int) -> Unit,
    private val onDragEnd: () -> Unit,
) : LinearLayout(context) {
    private val density = resources.displayMetrics.density
    private val current = lyricText(bold = true)
    private val next = lyricText(bold = false).apply { alpha = 0.72f }
    private var lastRawY = 0f
    private var dragging = false

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val horizontal = (16 * density).toInt()
        val vertical = (8 * density).toInt()
        setPadding(horizontal, vertical, horizontal, vertical)
        addView(current, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(next, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun bind(currentLine: String?, nextLine: String?, fontScale: Float, accent: Int, locked: Boolean) {
        current.text = currentLine.orEmpty()
        next.text = nextLine.orEmpty()
        next.visibility = if (nextLine.isNullOrBlank()) GONE else VISIBLE
        current.setTextColor(accent)
        current.setTextSize(TypedValue.COMPLEX_UNIT_SP, CURRENT_SP * fontScale)
        next.setTextSize(TypedValue.COMPLEX_UNIT_SP, NEXT_SP * fontScale)
        // 未锁定时给一块半透明底，提示“可以拖动”；锁定后完全透明，只留文字阴影。
        background = if (locked) {
            null
        } else {
            GradientDrawable().apply {
                cornerRadius = 16 * density
                setColor(Color.argb(0x66, 0, 0, 0))
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastRawY = event.rawY
                dragging = true
            }
            MotionEvent.ACTION_MOVE -> if (dragging) {
                val dy = (event.rawY - lastRawY).toInt()
                if (dy != 0) {
                    lastRawY = event.rawY
                    onDrag(dy)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) {
                dragging = false
                onDragEnd()
            }
        }
        return true
    }

    private fun lyricText(bold: Boolean): TextView = TextView(context).apply {
        gravity = Gravity.CENTER_HORIZONTAL
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        setTextColor(Color.WHITE)
        setShadowLayer(6 * density, 0f, 1.5f * density, Color.argb(0xCC, 0, 0, 0))
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private companion object {
        const val CURRENT_SP = 18f
        const val NEXT_SP = 14f
    }
}
