package app.echo.android.playback

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.widget.Toast
import androidx.media3.common.util.UnstableApi
import app.echo.android.model.playback.*

/** Rechecks the live service session at click time; no session IDs are persisted. */
@UnstableApi
object EchoSystemAudioEffects {
    fun open(context: Context, pipeline: EchoAudioPipelineState, localPlayback: Boolean = true): Boolean {
        val runtime = EchoPlaybackProcessRuntime
        val decoder = pipeline.diagnostics.value.decoder
        val session = pipeline.systemEffectsSession?.invoke()
        val eligible = EchoPlaybackDiagnostics(decoder = decoder,
            usbExclusiveEnabled = runtime.usbExclusiveEnabled, usbBitPerfectEnabled = runtime.usbBitPerfectEnabled)
            .systemEffectsAvailability(localPlayback) == EchoSystemEffectsAvailability.Available
        if (!eligible || session == null || session != decoder.audioSessionId) {
            Toast.makeText(context, R.string.effects_session_changed, Toast.LENGTH_SHORT).show()
            return false
        }
        val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
            .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, session)
            .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.effects_panel_missing, Toast.LENGTH_SHORT).show(); false
        } catch (_: SecurityException) {
            Toast.makeText(context, R.string.effects_panel_blocked, Toast.LENGTH_SHORT).show(); false
        }
    }
}
