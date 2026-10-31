package app.echo.android

import android.content.Context
import androidx.media3.common.util.UnstableApi
import app.echo.android.data.EchoSettingsStore
import app.echo.android.model.playback.EchoUsbFineVolume
import app.echo.android.playback.EchoPlaybackProcessRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Device events only. No progress polling or device I/O in this binding. */
@UnstableApi
internal fun bindUsbFineVolumeSettings(context: Context, store: EchoSettingsStore) {
    EchoPlaybackProcessRuntime.scope.launch {
        EchoPlaybackProcessRuntime.usbAudioMonitor(context).status.map {
            if (it.connected) EchoUsbFineVolume.deviceKey(it.vendorId, it.productId) else null
        }.distinctUntilChanged().collect { key ->
            withContext(Dispatchers.IO) { store.activateUsbVolumeDevice(key) }
        }
    }
    EchoPlaybackProcessRuntime.scope.launch {
        store.appSettings.map { it.usbOutputOptions }.distinctUntilChanged()
            .collect(EchoPlaybackProcessRuntime::setUsbOutputOptions)
    }
}
