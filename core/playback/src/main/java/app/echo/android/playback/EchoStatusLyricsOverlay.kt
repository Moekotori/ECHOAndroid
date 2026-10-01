package app.echo.android.playback

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import app.echo.android.model.error.EchoErrorLog
import app.echo.android.model.error.EchoErrorSource
import app.echo.android.model.settings.EchoLyricsOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Service-owned compact, touch-through window. Android may place it below the system status bar. */
@UnstableApi
internal class EchoStatusLyricsOverlay(private val context: Context, scope: CoroutineScope) : AutoCloseable {
    private val windows = context.getSystemService(WindowManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private val visible = MutableStateFlow(power.isInteractive && !keyguard.isKeyguardLocked)
    private var view: TextView? = null
    private var layout: WindowManager.LayoutParams? = null
    private var lastOptions: EchoLyricsOptions? = null
    private var lastText: String? = null
    private var failedOptions: EchoLyricsOptions? = null
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            failedOptions = null
            visible.value = intent.action != Intent.ACTION_SCREEN_OFF &&
                power.isInteractive && !keyguard.isKeyguardLocked
            if (intent.action == Intent.ACTION_CONFIGURATION_CHANGED) {
                lastOptions?.let { options ->
                    lastOptions = null
                    render(options, lastText, visible.value)
                }
            }
        }
    }
    private val job: Job

    init {
        ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        job = scope.launch {
            combine(EchoPlaybackProcessRuntime.lyricsOptions, EchoPlaybackProcessRuntime.lyricDisplaySnapshot, visible) {
                    options, snapshot, visible ->
                Triple(options, EchoStatusLyricPolicy.text(snapshot, options.statusHideTranslation), visible)
            }.collect { (options, text, visible) -> render(options, text, visible) }
        }
    }

    private fun render(options: EchoLyricsOptions, text: String?, visible: Boolean) {
        if (!options.statusOverlayEnabled || !visible || text == null || !Settings.canDrawOverlays(context)) {
            if (!options.statusOverlayEnabled) failedOptions = null
            hide()
            return
        }
        if (failedOptions == options) return
        val shown = view ?: TextView(context).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(Color.WHITE)
            setShadowLayer(3f * resources.displayMetrics.density, 0f, 0f, Color.BLACK)
        }.also { created ->
            val params = WindowManager.LayoutParams(1, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                // Together with the locked desktop window (0.6), combined opacity stays <= 0.8.
                alpha = 0.5f
            }
            applyLayout(params, options)
            if (runCatching { windows.addView(created, params) }.onFailure {
                    failedOptions = options
                    EchoErrorLog.record(EchoErrorSource.Lyrics, "Status lyrics window failed", throwable = it)
                }.isFailure) return
            view = created
            layout = params
        }
        if (lastOptions != options) {
            shown.textSize = options.statusFontSp
            layout?.let {
                applyLayout(it, options)
                if (runCatching { windows.updateViewLayout(shown, it) }.isFailure) {
                    failedOptions = options
                    hide()
                    return
                }
            }
            lastOptions = options
        }
        if (lastText != text) {
            shown.text = text
            lastText = text
        }
    }

    private fun applyLayout(params: WindowManager.LayoutParams, options: EchoLyricsOptions) {
        val metrics = context.resources.displayMetrics
        params.width = (options.statusWidthDp * metrics.density).toInt().coerceAtMost(metrics.widthPixels)
        params.x = (options.statusOffsetXDp * metrics.density).toInt().coerceIn(0, (metrics.widthPixels - params.width).coerceAtLeast(0))
        params.y = (options.statusOffsetYDp * metrics.density).toInt()
    }

    private fun hide() {
        view?.let { runCatching { windows.removeViewImmediate(it) } }
        view = null
        layout = null
        lastOptions = null
        lastText = null
    }

    override fun close() {
        job.cancel()
        context.unregisterReceiver(receiver)
        hide()
    }
}
