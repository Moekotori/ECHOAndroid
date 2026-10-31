package app.echo.android.nfc

import android.content.Intent
import android.nfc.NfcAdapter
import android.provider.Settings
import android.os.Build
import android.content.ActivityNotFoundException
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.echo.android.EchoLaunchActions
import app.echo.android.feature.library.NfcMusicDialog
import app.echo.android.feature.library.NfcMusicPhase
import kotlinx.coroutines.launch

@Composable
internal fun NfcMusicHost(controller: NfcMusicController, onPlay: (String) -> Unit) {
    val state by controller.state.collectAsStateWithLifecycle()
    val request by EchoLaunchActions.nfcMusic.collectAsStateWithLifecycle()
    val play by controller.play.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(request) {
        request?.let { EchoLaunchActions.consumeNfcMusic(); controller.open(it) }
    }
    LaunchedEffect(play) {
        play?.let { controller.consumePlay(); onPlay(it) }
    }
    val reading = controller.reading
    DisposableEffect(activity, lifecycle, controller, reading) {
        val adapter = activity?.let { NfcAdapter.getDefaultAdapter(it) }
        fun enable() {
            controller.refreshHardware()
            if (!reading || activity == null || adapter?.isEnabled != true) return
            try {
                adapter.enableReaderMode(activity, { tag ->
                    (activity as? androidx.lifecycle.LifecycleOwner)?.lifecycleScope?.launch { controller.onTag(tag) }
                }, NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V, null)
            } catch (_: IllegalStateException) { controller.readerFailed() }
            catch (_: SecurityException) { controller.readerFailed() }
        }
        fun disable() {
            if (activity != null && reading) runCatching { adapter?.disableReaderMode(activity) }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> enable()
                Lifecycle.Event.ON_PAUSE -> disable()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) enable()
        onDispose { lifecycle.removeObserver(observer); disable() }
    }
    state?.let {
        NfcMusicDialog(it, onDismiss = controller::dismiss, onRetry = controller::retry,
            onOverwrite = controller::approveOverwrite, onRebind = controller::chooseBinding,
            onQuery = controller::query, onChoose = controller::bind,
            onEnable = {
                val action = if (it.phase == NfcMusicPhase.LaunchBlocked && Build.VERSION.SDK_INT >= 36)
                    NfcAdapter.ACTION_CHANGE_TAG_INTENT_PREFERENCE else Settings.ACTION_NFC_SETTINGS
                try { activity?.startActivity(Intent(action)) }
                catch (_: ActivityNotFoundException) { activity?.startActivity(Intent(Settings.ACTION_SETTINGS)) }
            },
            onPlay = controller::playBound)
    }
}
