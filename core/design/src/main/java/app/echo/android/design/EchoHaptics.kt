package app.echo.android.design

import android.content.Context
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import app.echo.android.model.platform.EchoPlatformCapabilities

enum class EchoHapticKind {
    Confirm,
    Tick,
    Seek,
    Grab,
    Drop,
}

val LocalEchoHapticsEnabled = staticCompositionLocalOf { true }

fun Context.performEchoHaptic(kind: EchoHapticKind) {
    EchoHapticEngine(applicationContext).play(kind)
}

class EchoHapticPerformer(
    context: Context,
    private val enabled: Boolean,
    private val continuousFeedbackEnabled: Boolean = true,
) {
    private val appContext = context.applicationContext
    private val engine by lazy(LazyThreadSafetyMode.NONE) {
        EchoHapticEngine(appContext, richFeedbackEnabled = continuousFeedbackEnabled)
    }
    private val seekGate = EchoSeekHapticGate()

    fun confirm() {
        if (enabled) engine.play(EchoHapticKind.Confirm)
    }

    fun tick() {
        if (enabled) engine.play(EchoHapticKind.Tick)
    }

    fun grab() { if (enabled) engine.play(EchoHapticKind.Grab) }
    fun drop() { if (enabled) engine.play(EchoHapticKind.Drop) }

    /** Called only by pointer gestures, never by playback-clock updates. */
    fun seek(fraction: Float) {
        if (enabled && continuousFeedbackEnabled && seekGate.shouldPulse(fraction, SystemClock.uptimeMillis()))
            engine.play(EchoHapticKind.Seek)
    }

    /** Queue rows have their own detents, independent of the number of tracks. */
    fun seekStep(step: Int) {
        if (enabled && continuousFeedbackEnabled && seekGate.shouldPulseStep(step, SystemClock.uptimeMillis()))
            engine.play(EchoHapticKind.Seek)
    }

    fun endSeek(committed: Boolean) {
        seekGate.reset()
        if (committed) drop()
    }
}

@Composable
fun rememberEchoHapticPerformer(): EchoHapticPerformer {
    val context = LocalContext.current.applicationContext
    val enabled = LocalEchoHapticsEnabled.current
    val continuous = !LocalEchoEffectivePerformanceMode.current.isLightweight
    return remember(context, enabled, continuous) { EchoHapticPerformer(context, enabled, continuous) }
}

internal fun Context.currentEchoVibrator(): Vibrator? =
    if (Build.VERSION.SDK_INT >= EchoPlatformCapabilities.HapticPrimitivesSdk) {
        getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
