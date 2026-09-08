package app.echo.android

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ImageDecoder
import android.graphics.Movie
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.splashscreen.SplashScreen
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.IOException

/** Original 15-frame GIF, 1870ms: play once at source speed, then fade into the app. */
internal fun SplashScreen.installEchoExitTransition(activity: ComponentActivity, restored: Boolean) {
    val lifecycle = activity.lifecycle
    if (restored || !ValueAnimator.areAnimatorsEnabled()) {
        setOnExitAnimationListener { it.remove() }
        return
    }
    val context = activity.applicationContext
    val artwork = activity.lifecycleScope.async(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.resources, R.raw.echo_startup))
            } else {
                @Suppress("DEPRECATION")
                context.resources.openRawResource(R.raw.echo_startup).use {
                    Movie.decodeStream(it)?.let(::LegacyGifDrawable)
                }
            }
        } catch (_: IOException) {
            null
        }
    }
    setOnExitAnimationListener { provider ->
        activity.lifecycleScope.launch {
            val drawable = artwork.await()
            val surface = provider.view
            val parent = surface as? ViewGroup
            if (drawable == null || parent == null ||
                !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            ) {
                provider.remove()
                return@launch
            }
            val icon = provider.iconView
            val surfacePosition = IntArray(2).also(surface::getLocationOnScreen)
            val iconPosition = IntArray(2).also(icon::getLocationOnScreen)
            val unit = icon.width / if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 192f else 288f
            val x = iconPosition[0] - surfacePosition[0] + icon.width / 2f
            val y = iconPosition[1] - surfacePosition[1] + icon.height / 2f
            val ambience = EchoGifAmbience(context, x, y, unit)
            parent.addView(ambience, ViewGroup.LayoutParams(-1, -1))
            val size = (232f * unit).toInt()
            val image = ImageView(context).apply {
                setImageDrawable(drawable)
                scaleType = ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            }
            parent.addView(image, ViewGroup.LayoutParams(size, size))
            image.x = x - size / 2f
            image.y = y - size / 2f
            image.scaleX = 144f / 232f
            image.scaleY = image.scaleX
            icon.visibility = View.INVISIBLE
            val easing = PathInterpolator(0.22f, 1f, 0.36f, 1f)
            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 2170L
                interpolator = android.view.animation.LinearInterpolator()
            }
            var removed = false
            lateinit var observer: DefaultLifecycleObserver
            fun finish() {
                if (removed) return
                removed = true
                lifecycle.removeObserver(observer)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && drawable is AnimatedImageDrawable) drawable.stop()
                (drawable as? LegacyGifDrawable)?.stop()
                image.setImageDrawable(null)
                parent.removeView(image)
                parent.removeView(ambience)
                provider.remove()
            }
            observer = object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) { animator.cancel(); finish() }
                override fun onDestroy(owner: LifecycleOwner) { animator.cancel(); finish() }
            }
            lifecycle.addObserver(observer)
            animator.addUpdateListener {
                val elapsed = (it.animatedValue as Float) * 2170f
                val arrival = easing.getInterpolation((elapsed / 420f).coerceIn(0f, 1f))
                image.scaleX = (144f + 88f * arrival) / 232f
                image.scaleY = image.scaleX
                ambience.arrival = arrival
                surface.alpha = 1f - easing.getInterpolation(((elapsed - 1870f) / 300f).coerceIn(0f, 1f))
            }
            animator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = finish()
            })
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && drawable is AnimatedImageDrawable) {
                drawable.repeatCount = 0
                drawable.start()
            }
            (drawable as? LegacyGifDrawable)?.start()
            animator.start()
        }
    }
}

/** Warm charcoal, mint/lilac light from the hair, and a soft cardboard-colored floor. */
private class EchoGifAmbience(context: Context, private val x: Float, private val y: Float,
    private val unit: Float) : View(context) {
    private val mint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(-45f, -25f, 190f, intArrayOf(0x344E9E94, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
    }
    private val lilac = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(65f, -5f, 170f, intArrayOf(0x305F538B, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
    }
    private val floor = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 0f, 125f, intArrayOf(0x38DAB886, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
    }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 0f, 85f, intArrayOf(0x66000000, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE8DCCB.toInt()
        textSize = 18f
        textAlign = Paint.Align.CENTER
        typeface = ResourcesCompat.getFont(context, app.echo.android.design.R.font.outfit)
    }
    var arrival = 0f
        set(value) { field = value; invalidate() }
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(unit, unit)
        mint.alpha = (255 * arrival).toInt()
        lilac.alpha = mint.alpha
        floor.alpha = mint.alpha
        shadow.alpha = mint.alpha
        canvas.drawCircle(-45f, -25f, 190f, mint)
        canvas.drawCircle(65f, -5f, 170f, lilac)
        canvas.save()
        canvas.translate(0f, 110f)
        canvas.scale(1f, 0.30f)
        canvas.drawCircle(0f, 0f, 125f, floor)
        canvas.drawCircle(0f, 0f, 85f, shadow)
        canvas.restore()
        text.alpha = (220 * arrival).toInt()
        canvas.drawText("E C H O", 0f, 154f, text)
        canvas.restore()
    }
}

/** API 26/27 fallback only; bounded to the same single original loop. */
@Suppress("DEPRECATION")
private class LegacyGifDrawable(private val movie: Movie) : Drawable(), Runnable {
    private var startedAt = 0L
    private var running = false
    fun start() { startedAt = SystemClock.uptimeMillis(); running = true; invalidateSelf() }
    fun stop() { running = false; unscheduleSelf(this) }
    override fun draw(canvas: Canvas) {
        val duration = movie.duration().coerceAtLeast(1)
        val elapsed = if (running) (SystemClock.uptimeMillis() - startedAt).toInt() else 0
        movie.setTime(elapsed.coerceIn(0, duration - 1))
        canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width().toFloat() / movie.width(), bounds.height().toFloat() / movie.height())
        movie.draw(canvas, 0f, 0f)
        canvas.restore()
        if (running && elapsed < duration) scheduleSelf(this, SystemClock.uptimeMillis() + 16L)
    }
    override fun run() = invalidateSelf()
    override fun getIntrinsicWidth() = movie.width()
    override fun getIntrinsicHeight() = movie.height()
    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
