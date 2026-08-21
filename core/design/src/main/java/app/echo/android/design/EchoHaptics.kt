package app.echo.android.design

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

enum class EchoHapticKind {
    Confirm,
    Tick,
}

val LocalEchoHapticsEnabled = staticCompositionLocalOf { true }

fun Context.performEchoHaptic(kind: EchoHapticKind) {
    val vibrator = currentVibrator() ?: return
    if (!vibrator.hasVibrator()) return
    if (Build.VERSION.SDK_INT >= 31) {
        val primitive = when (kind) {
            EchoHapticKind.Confirm -> VibrationEffect.Composition.PRIMITIVE_CLICK
            EchoHapticKind.Tick -> VibrationEffect.Composition.PRIMITIVE_TICK
        }
        if (vibrator.areAllPrimitivesSupported(primitive)) {
            val scale = if (kind == EchoHapticKind.Confirm) 0.72f else 0.42f
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(primitive, scale)
                    .compose(),
            )
            return
        }
    }
    val durationMs = if (kind == EchoHapticKind.Confirm) 18L else 10L
    vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
}

class EchoHapticPerformer(
    private val context: Context,
    private val enabled: Boolean,
) {
    fun confirm() {
        if (enabled) context.performEchoHaptic(EchoHapticKind.Confirm)
    }

    fun tick() {
        if (enabled) context.performEchoHaptic(EchoHapticKind.Tick)
    }
}

@Composable
fun rememberEchoHapticPerformer(): EchoHapticPerformer {
    val context = LocalContext.current
    val enabled = LocalEchoHapticsEnabled.current
    return remember(context, enabled) { EchoHapticPerformer(context, enabled) }
}

private fun Context.currentVibrator(): Vibrator? =
    if (Build.VERSION.SDK_INT >= 31) {
        getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
