package app.echo.android

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.PowerManager
import android.view.animation.PathInterpolator
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import app.echo.android.data.EchoSettingsStore
import app.echo.android.model.settings.EchoEffectivePerformanceMode
import app.echo.android.model.settings.EchoPerformanceMode
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Continue the system splash in place, optionally drawing a still image behind its icon. */
internal fun SplashScreen.installEchoExitTransition(
    activity: ComponentActivity,
    restored: Boolean,
    startupBackgroundUri: String?,
) {
    if (restored || !ValueAnimator.areAnimatorsEnabled()) {
        setOnExitAnimationListener { it.remove() }
        return
    }

    // Never hold the first frame for settings I/O. Until loaded, use the lightest transition.
    var mode = EchoEffectivePerformanceMode.Lightweight
    var startupBackground: Bitmap? = null
    val imageJob = startupBackgroundUri?.let { uri ->
        activity.lifecycleScope.launch {
            try {
                val request = ImageRequest.Builder(activity.applicationContext)
                    .data(Uri.parse(uri))
                    .size(1280, 1280)
                    .bitmapConfig(Bitmap.Config.RGB_565)
                    .allowHardware(false)
                    .crossfade(false)
                    .build()
                val result = activity.applicationContext.imageLoader.execute(request)
                startupBackground = (result as? SuccessResult)?.drawable
                    ?.let { it as? BitmapDrawable }
                    ?.bitmap
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A revoked or unreadable image falls back to the built-in splash color.
            }
        }
    }
    val settingsJob = activity.lifecycleScope.launch {
        mode = withContext(Dispatchers.IO) {
            val context = activity.applicationContext
            val settings = EchoSettingsStore(context).appSettings.first()
            val powerSave = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
            EchoPerformanceMode.fromId(settings.performanceMode).resolve(powerSave)
        }
    }
    setOnExitAnimationListener { provider ->
        settingsJob.cancel()
        imageJob?.cancel()
        val lifecycle = activity.lifecycle
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) ||
            !ValueAnimator.areAnimatorsEnabled()
        ) {
            provider.remove()
            return@setOnExitAnimationListener
        }

        val surface = provider.view
        val icon = provider.iconView
        startupBackground?.let { surface.background = EchoSplashBackgroundDrawable(it) }
        startupBackground = null
        val lightweight = mode.isLightweight
        val duration = if (lightweight) 120L else 360L
        val easing = PathInterpolator(0.4f, 0f, 0.2f, 1f)
        val signal = if (!lightweight && icon is ImageView) {
            (activity.getDrawable(R.drawable.echo_splash_reveal) as? AnimatedVectorDrawable)
                ?.also { icon.setImageDrawable(it) }
        } else null
        var removed = false
        lateinit var observer: DefaultLifecycleObserver
        fun finish() {
            if (removed) return
            removed = true
            lifecycle.removeObserver(observer)
            surface.animate().setListener(null).cancel()
            icon.animate().cancel()
            signal?.stop()
            surface.background = null
            provider.remove()
        }
        observer = object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) = finish()
            override fun onDestroy(owner: LifecycleOwner) = finish()
        }
        lifecycle.addObserver(observer)

        // One outward signal pulse, then a soft hand-off. No loops or minimum splash hold.
        signal?.start()
        val iconExit = icon.animate()
            .alpha(0f)
            .setStartDelay(if (lightweight) 0L else 80L)
            .setDuration(if (lightweight) 120L else 280L)
            .setInterpolator(easing)
        if (!lightweight) {
            iconExit.scaleX(1.06f).scaleY(1.06f)
        }
        iconExit.start()
        surface.animate()
            .alpha(0f)
            .setStartDelay(if (lightweight) 0L else 100L)
            .setDuration(if (lightweight) duration else duration - 100L)
            .setInterpolator(easing)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = finish()
                override fun onAnimationCancel(animation: Animator) = finish()
            })
            .start()
    }
}

private class EchoSplashBackgroundDrawable(private val bitmap: Bitmap) : Drawable() {
    private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dimPaint = Paint().apply { color = android.graphics.Color.argb(32, 0, 0, 0) }
    private val source = Rect()

    override fun draw(canvas: Canvas) {
        val target = bounds
        if (target.isEmpty) return
        val sourceRatio = bitmap.width.toFloat() / bitmap.height
        val targetRatio = target.width().toFloat() / target.height()
        if (sourceRatio > targetRatio) {
            val croppedWidth = (bitmap.height * targetRatio).toInt().coerceAtLeast(1)
            val left = (bitmap.width - croppedWidth) / 2
            source.set(left, 0, left + croppedWidth, bitmap.height)
        } else {
            val croppedHeight = (bitmap.width / targetRatio).toInt().coerceAtLeast(1)
            val top = (bitmap.height - croppedHeight) / 2
            source.set(0, top, bitmap.width, top + croppedHeight)
        }
        canvas.drawBitmap(bitmap, source, target, imagePaint)
        canvas.drawRect(target, dimPaint)
    }

    override fun setAlpha(alpha: Int) { imagePaint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { imagePaint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Required by Drawable")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}
