package app.echo.android.design

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings

/** Fixed-size, per-owner effect cache. Hardware queries/builders never run on every drag tick. */
@Suppress("DEPRECATION")
internal class EchoHapticEngine(private val context: Context, private val richFeedbackEnabled: Boolean = true) {
    private val vibrator = context.currentEchoVibrator()?.takeIf { it.hasVibrator() }
    private val effects = arrayOfNulls<VibrationEffect>(EchoHapticKind.entries.size)
    private val touchAttributes = if (Build.VERSION.SDK_INT >= 33)
        VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH) else null
    private val legacyAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()

    fun play(kind: EchoHapticKind) {
        val device = vibrator ?: return
        // USAGE_TOUCH honors the system setting on modern Android without reading Settings per detent.
        if (Build.VERSION.SDK_INT < 33 &&
            Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 0) return
        val effect = effects[kind.ordinal] ?: runCatching { buildEffect(device, kind) }
            .getOrElse { legacyEffect(kind) }.also { effects[kind.ordinal] = it }
        try {
            vibrate(device, effect)
        } catch (_: RuntimeException) {
            // Broken vendor envelope/primitive implementations must not break the playback action.
            val fallback = legacyEffect(kind)
            effects[kind.ordinal] = fallback
            runCatching { vibrate(device, fallback) }
        }
    }

    private fun vibrate(device: Vibrator, effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= 33 && touchAttributes != null) device.vibrate(effect, touchAttributes)
        else device.vibrate(effect, legacyAttributes)
    }

    private fun buildEffect(device: Vibrator, kind: EchoHapticKind): VibrationEffect {
        if (richFeedbackEnabled && Build.VERSION.SDK_INT >= 36 && device.areEnvelopeEffectsSupported()) {
            val info = device.envelopeEffectInfo
            val rise = maxOf(info.minControlPointDurationMillis, 10L)
            val fall = maxOf(info.minControlPointDurationMillis, if (kind == EchoHapticKind.Grab) 24L else 16L)
            // Avoid turning a detent into a long buzz on slow actuators.
            if (info.maxSize >= 2 && rise + fall <= 70L && rise + fall <= info.maxDurationMillis &&
                maxOf(rise, fall) <= info.maxControlPointDurationMillis) {
                val sharpness = when (kind) {
                    EchoHapticKind.Seek -> 0.75f
                    EchoHapticKind.Grab -> 0.25f
                    EchoHapticKind.Drop -> 0.55f
                    EchoHapticKind.Confirm -> 0.45f
                    EchoHapticKind.Tick -> 0.65f
                }
                return VibrationEffect.BasicEnvelopeBuilder().setInitialSharpness(sharpness)
                    .addControlPoint(strength(kind), sharpness, rise)
                    .addControlPoint(0f, sharpness, fall).build()
            }
        }
        if (Build.VERSION.SDK_INT >= 31) {
            val primitive = when (kind) {
                EchoHapticKind.Tick, EchoHapticKind.Seek -> VibrationEffect.Composition.PRIMITIVE_TICK
                else -> VibrationEffect.Composition.PRIMITIVE_CLICK
            }
            if (device.areAllPrimitivesSupported(primitive)) return VibrationEffect.startComposition()
                .addPrimitive(primitive, strength(kind)).compose()
        }
        return legacyEffect(kind)
    }

    private fun legacyEffect(kind: EchoHapticKind): VibrationEffect = VibrationEffect.createOneShot(
        if (kind == EchoHapticKind.Seek || kind == EchoHapticKind.Tick) 8L else 18L,
        VibrationEffect.DEFAULT_AMPLITUDE,
    )

    private fun strength(kind: EchoHapticKind) = when (kind) {
        EchoHapticKind.Confirm -> 0.65f
        EchoHapticKind.Tick -> 0.40f
        EchoHapticKind.Seek -> 0.24f
        EchoHapticKind.Grab -> 0.38f
        EchoHapticKind.Drop -> 0.55f
    }
}
