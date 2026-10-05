package app.echo.android.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.roundToInt

@Composable
internal fun SettingsAccessibilitySystemContent(isActive: Boolean) {
    val context = LocalContext.current
    val touchExploration = rememberSettingsTouchExploration(isActive)
    val systemFontPercent = (LocalConfiguration.current.fontScale * 100f).roundToInt()
    var launchFailed by remember { mutableStateOf(false) }
    val openSettings: (String) -> Unit = { action ->
        launchFailed = !context.openAccessibilitySystemSettings(action)
    }
    SettingsSectionCard(title = stringResource(R.string.accessibility_system)) {
        SettingsActionRow(
            title = stringResource(R.string.accessibility_screen_reader),
            detail = stringResource(when (touchExploration) {
                true -> R.string.accessibility_screen_reader_on
                false -> R.string.accessibility_screen_reader_off
                null -> R.string.accessibility_screen_reader_unknown
            }),
            onClick = { openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS) },
        )
        SettingsActionRow(
            title = stringResource(R.string.accessibility_system_display),
            detail = stringResource(R.string.accessibility_system_display_detail, systemFontPercent),
            onClick = { openSettings(Settings.ACTION_DISPLAY_SETTINGS) },
        )
        if (launchFailed) {
            Text(
                stringResource(R.string.accessibility_system_unavailable),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** Listen only while this page is active and resumed; refreshing needs no polling. */
@Composable
private fun rememberSettingsTouchExploration(active: Boolean): Boolean? {
    val context = LocalContext.current.applicationContext
    val manager = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var enabled by remember(manager) { mutableStateOf<Boolean?>(null) }
    DisposableEffect(manager, lifecycle, active) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled = it }
        var listening = false
        fun refresh() { enabled = manager?.isTouchExplorationEnabled }
        fun start() {
            if (!active || listening || manager == null) return
            listening = manager.addTouchExplorationStateChangeListener(listener)
            refresh()
        }
        fun stop() {
            if (listening) manager?.removeTouchExplorationStateChangeListener(listener)
            listening = false
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> start()
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> stop()
                else -> Unit
            }
        }
        if (active) refresh()
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) start()
        onDispose {
            stop()
            lifecycle.removeObserver(observer)
        }
    }
    return enabled
}

private fun Context.openAccessibilitySystemSettings(action: String): Boolean = try {
    startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}
