package app.echo.android.lyrics.overlay

import android.app.Activity
import android.app.Application
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import app.echo.android.data.EchoSettingsStore
import app.echo.android.model.error.EchoErrorLog
import app.echo.android.model.error.EchoErrorSource
import app.echo.android.model.lyrics.EchoLyricDisplaySnapshot
import app.echo.android.model.settings.EchoFloatingLyricsSettings
import app.echo.android.playback.EchoPlaybackProcessRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 系统悬浮窗歌词。进程级单例，由 Application 启动。
 *
 * 只在「开关打开 + 有悬浮窗权限 + 界面不在前台 + 正在播放 + 当前曲目有歌词」时显示；
 * 回到应用内自动隐藏，避免和播放页的歌词重复。数据来自播放服务已有的歌词快照，
 * 这里不额外计时，也不逐帧刷新。
 */
@OptIn(UnstableApi::class)
internal class EchoFloatingLyricsController(
    private val app: Application,
    private val settingsStore: EchoSettingsStore,
) {
    private val windowManager = app.getSystemService(WindowManager::class.java)
    private val power = app.getSystemService(PowerManager::class.java)
    private val keyguard = app.getSystemService(KeyguardManager::class.java)
    private val screenVisible = MutableStateFlow(power.isInteractive && !keyguard.isKeyguardLocked)
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            screenVisible.value = intent.action != Intent.ACTION_SCREEN_OFF &&
                power.isInteractive && !keyguard.isKeyguardLocked
        }
    }
    private val appVisible = MutableStateFlow(false)
    private var startedActivities = 0
    private var view: EchoFloatingLyricsView? = null
    private var params: WindowManager.LayoutParams? = null
    private var settings = EchoFloatingLyricsSettings()

    fun start() {
        app.registerActivityLifecycleCallbacks(VisibilityCallbacks())
        ContextCompat.registerReceiver(app, screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        EchoPlaybackProcessRuntime.scope.launch(Dispatchers.Main.immediate) {
            combine(
                settingsStore.appSettings.map { it.floatingLyrics }.distinctUntilChanged(),
                combine(appVisible, screenVisible) { foreground, screen -> foreground || !screen },
                EchoPlaybackProcessRuntime.surface.map { it.isPlaying }.distinctUntilChanged(),
                EchoPlaybackProcessRuntime.lyricDisplaySnapshot,
            ) { floating, visible, playing, snapshot -> FloatingState(floating, visible, playing, snapshot) }
                .collect(::render)
        }
    }

    private fun render(state: FloatingState) {
        settings = state.settings
        val currentLine = state.snapshot.current?.text?.trim()?.takeIf { it.isNotEmpty() }
        val nextLine = state.snapshot.next?.text?.trim()?.takeIf { it.isNotEmpty() }
        val shownLine = currentLine ?: nextLine.takeIf { state.snapshot.previous == null }
        val shouldShow = state.settings.enabled &&
            !state.appVisible &&
            state.playing &&
            shownLine != null &&
            Settings.canDrawOverlays(app)
        if (!shouldShow) {
            hide()
            return
        }
        val shown = view ?: show(state.settings) ?: return
        shown.bind(
            currentLine = shownLine,
            nextLine = if (currentLine != null) nextLine else null,
            fontScale = state.settings.fontScale,
            accent = CURRENT_LINE_COLOR,
            locked = state.settings.locked,
        )
        params?.let { layout ->
            if (applyLock(layout, state.settings.locked)) {
                runCatching { windowManager.updateViewLayout(shown, layout) }
            }
        }
    }

    private fun show(settings: EchoFloatingLyricsSettings): EchoFloatingLyricsView? {
        val layout = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            BASE_FLAGS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = settings.offsetY.takeIf { it >= 0 } ?: defaultOffsetY()
        }
        applyLock(layout, settings.locked)
        val created = EchoFloatingLyricsView(
            context = app,
            onDrag = { dy -> moveBy(dy) },
            onDragEnd = ::persistOffset,
        )
        return runCatching {
            windowManager.addView(created, layout)
            view = created
            params = layout
            created
        }.onFailure { error ->
            EchoErrorLog.record(EchoErrorSource.Lyrics, "Floating lyrics window failed", throwable = error)
        }.getOrNull()
    }

    private fun hide() {
        val current = view ?: return
        view = null
        params = null
        runCatching { windowManager.removeViewImmediate(current) }
    }

    /**
     * 锁定 = 不接收触摸，点击穿透到下面的应用。Android 12 起，穿透窗口的整体不透明度
     * 必须不高于 0.8，否则系统会拦下触摸，所以锁定时把窗口 alpha 压到 0.8。
     * 返回参数是否有变化。
     */
    private fun applyLock(layout: WindowManager.LayoutParams, locked: Boolean): Boolean {
        val flags = if (locked) BASE_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else BASE_FLAGS
        val alpha = if (locked) LOCKED_WINDOW_ALPHA else 1f
        if (layout.flags == flags && layout.alpha == alpha) return false
        layout.flags = flags
        layout.alpha = alpha
        return true
    }

    private fun moveBy(dy: Int) {
        val shown = view ?: return
        val layout = params ?: return
        val maxY = (app.resources.displayMetrics.heightPixels - shown.height).coerceAtLeast(0)
        layout.y = (layout.y + dy).coerceIn(0, maxY)
        runCatching { windowManager.updateViewLayout(shown, layout) }
    }

    private fun persistOffset() {
        val y = params?.y ?: return
        val updated = settings.copy(offsetY = y)
        settings = updated
        EchoPlaybackProcessRuntime.scope.launch(Dispatchers.IO) {
            runCatching { settingsStore.setFloatingLyrics(updated) }
        }
    }

    private fun defaultOffsetY(): Int = (app.resources.displayMetrics.heightPixels * DEFAULT_OFFSET_FRACTION).toInt()

    private data class FloatingState(
        val settings: EchoFloatingLyricsSettings,
        val appVisible: Boolean,
        val playing: Boolean,
        val snapshot: EchoLyricDisplaySnapshot,
    )

    private inner class VisibilityCallbacks : Application.ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
            startedActivities += 1
            appVisible.value = startedActivities > 0
        }

        override fun onActivityStopped(activity: Activity) {
            startedActivities = (startedActivities - 1).coerceAtLeast(0)
            appVisible.value = startedActivities > 0
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    private companion object {
        const val BASE_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        const val LOCKED_WINDOW_ALPHA = 0.6f
        const val DEFAULT_OFFSET_FRACTION = 0.12f
        val CURRENT_LINE_COLOR = Color.rgb(0xFF, 0xE0, 0x8A)
    }
}
