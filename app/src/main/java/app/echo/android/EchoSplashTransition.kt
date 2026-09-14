package app.echo.android

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.os.PowerManager
import android.view.animation.PathInterpolator
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import app.echo.android.data.EchoSettingsStore
import app.echo.android.model.settings.EchoEffectivePerformanceMode
import app.echo.android.model.settings.EchoPerformanceMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Continue the system splash in place, without a second image or a decoded GIF timeline. */
internal fun SplashScreen.installEchoExitTransition(activity: ComponentActivity, restored: Boolean) {
    if (restored || !ValueAnimator.areAnimatorsEnabled()) {
        setOnExitAnimationListener { it.remove() }
        return
    }

    // Never hold the first frame for settings I/O. Until loaded, use the lightest transition.
    var mode = EchoEffectivePerformanceMode.Lightweight
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
        val lifecycle = activity.lifecycle
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) ||
            !ValueAnimator.areAnimatorsEnabled()
        ) {
            provider.remove()
            return@setOnExitAnimationListener
        }

        val surface = provider.view
        val icon = provider.iconView
        val lightweight = mode.isLightweight
        // Keep the hand-off rhythm independent of whether settings finished loading.
        // Zero slope at both ends avoids a sudden kick from the stationary system icon.
        val duration = 420L
        val easing = PathInterpolator(0.4f, 0f, 0.2f, 1f)
        var removed = false
        lateinit var observer: DefaultLifecycleObserver
        fun finish() {
            if (removed) return
            removed = true
            lifecycle.removeObserver(observer)
            surface.animate().setListener(null).cancel()
            icon.animate().cancel()
            provider.remove()
        }
        observer = object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) = finish()
            override fun onDestroy(owner: LifecycleOwner) = finish()
        }
        lifecycle.addObserver(observer)

        // Animate the existing icon: identical position, crop and scale at the hand-off.
        // View properties avoid per-frame layout, Compose recomposition and bitmap decoding.
        if (!lightweight) {
            icon.animate()
                .scaleX(1.025f)
                .scaleY(1.025f)
                .setStartDelay(0L)
                .setDuration(duration)
                .setInterpolator(easing)
                .start()
        }
        // Reveal the first frame immediately, with the same easing as the icon so
        // motion and opacity settle together instead of feeling like two transitions.
        surface.animate()
            .alpha(0f)
            .setStartDelay(0L)
            .setDuration(duration)
            .setInterpolator(easing)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = finish()
                override fun onAnimationCancel(animation: Animator) = finish()
            })
            .start()
    }
}
