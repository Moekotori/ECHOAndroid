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
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.PowerManager
import android.view.animation.PathInterpolator
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import app.echo.android.data.EchoSettingsStore
import app.echo.android.model.settings.EchoEffectivePerformanceMode
import app.echo.android.model.settings.EchoPerformanceMode
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Longest the system splash waits for the performance mode and startup image before handing off. */
private const val HandOffWaitMillis = 250L

/**
 * Fade the system splash (a plain background) into the in-app [EchoLaunchSplash]. A null
 * [launchSplash] means a restored or animator-less start: the system splash is simply removed.
 */
internal fun SplashScreen.installEchoExitTransition(
    activity: ComponentActivity,
    launchSplash: EchoLaunchSplashState?,
    startupBackgroundUri: String?,
) {
    if (launchSplash == null) {
        setOnExitAnimationListener { it.remove() }
        return
    }

    // Never hold the first frame for settings I/O. Until loaded, assume the lightest mode.
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
                // A revoked or unreadable image falls back to the built-in splash.
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
        activity.lifecycleScope.launch {
            withTimeoutOrNull(HandOffWaitMillis) {
                settingsJob.join()
                imageJob?.join()
            }
            val surface = provider.view
            if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) ||
                !ValueAnimator.areAnimatorsEnabled()
            ) {
                launchSplash.finished = true
                provider.remove()
                return@launch
            }
            val background = startupBackground
            if (background != null) surface.background = EchoSplashBackgroundDrawable(background)
            launchSplash.background = background
            launchSplash.lightweight = mode.isLightweight
            launchSplash.started = true

            // The overlay beneath already shows the same ground; this only dissolves the system layer.
            surface.animate()
                .alpha(0f)
                .setDuration(if (mode.isLightweight) 120L else 180L)
                .setInterpolator(PathInterpolator(0.4f, 0f, 0.2f, 1f))
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) = finish()
                    override fun onAnimationCancel(animation: Animator) = finish()

                    private fun finish() {
                        surface.animate().setListener(null)
                        surface.background = null
                        provider.remove()
                    }
                })
                .start()
        }
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
