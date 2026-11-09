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
import androidx.core.splashscreen.SplashScreenViewProvider
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import app.echo.android.data.EchoSettingsStore
import app.echo.android.model.settings.EchoEffectivePerformanceMode
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Independent of animation frames and the optional system exit callback. */
private const val MaximumSplashMillis = 800L

/**
 * Fade the system splash into the in-app [EchoLaunchSplash] when enabled. A null
 * [launchSplash] hands off directly to the first screen.
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

    var systemSplash: SplashScreenViewProvider? = null
    var timeoutJob: Job? = null
    var settingsJob: Job? = null
    var imageJob: Job? = null
    var startupBackground: Bitmap? = null
    var removed = false
    fun removeSystemSplash() {
        val provider = systemSplash ?: return
        systemSplash = null
        provider.view.animate().setListener(null).cancel()
        provider.view.background = null
        provider.remove()
    }
    lateinit var observer: DefaultLifecycleObserver
    fun finish() {
        if (removed) return
        removed = true
        launchSplash.finish()
        timeoutJob?.cancel()
        settingsJob?.cancel()
        imageJob?.cancel()
        startupBackground = null
        removeSystemSplash()
        activity.lifecycle.removeObserver(observer)
    }
    observer = object : DefaultLifecycleObserver {
        override fun onStop(owner: androidx.lifecycle.LifecycleOwner) = finish()
        override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) = finish()
    }
    activity.lifecycle.addObserver(observer)
    timeoutJob = activity.lifecycleScope.launch {
        // Some launches never dispatch the system exit listener. The in-app overlay
        // must still disappear, even if no animation frames have advanced.
        delay(MaximumSplashMillis)
        finish()
    }

    // Never hold the first frame for settings I/O. Until loaded, assume the lightest mode.
    var mode = EchoEffectivePerformanceMode.Lightweight
    imageJob = startupBackgroundUri?.let { uri ->
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
    settingsJob = activity.lifecycleScope.launch {
        try {
            mode = withContext(Dispatchers.IO) {
                val context = activity.applicationContext
                val performanceMode = EchoSettingsStore(context).readLaunchPerformanceMode()
                val powerSave = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
                performanceMode.resolve(powerSave)
            }
        } catch (_: java.io.IOException) {
            // Settings are optional for this visual transition; keep lightweight mode.
        }
    }
    setOnExitAnimationListener { provider ->
        systemSplash = provider
        if (removed) {
            removeSystemSplash()
            return@setOnExitAnimationListener
        }
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) ||
            !ValueAnimator.areAnimatorsEnabled()
        ) {
            finish()
            return@setOnExitAnimationListener
        }

        // Use what's ready at the first frame. Optional settings and artwork never hold up entry.
        settingsJob.cancel()
        imageJob?.cancel()
        val background = startupBackground
        startupBackground = null
        if (mode.isLightweight && background == null) {
            finish()
            return@setOnExitAnimationListener
        }
        val surface = provider.view
        if (background != null) surface.background = EchoSplashBackgroundDrawable(background)
        launchSplash.start(background, mode.isLightweight)

        surface.animate()
            .alpha(0f)
            .setDuration(if (mode.isLightweight) 120L else 180L)
            .setInterpolator(PathInterpolator(0.4f, 0f, 0.2f, 1f))
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = removeSystemSplash()
                override fun onAnimationCancel(animation: Animator) = removeSystemSplash()
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
