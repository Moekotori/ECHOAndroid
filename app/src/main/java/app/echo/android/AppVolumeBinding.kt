package app.echo.android

import android.content.Context
import android.widget.Toast
import androidx.media3.common.util.UnstableApi
import app.echo.android.data.EchoSettingsStore
import app.echo.android.model.error.EchoErrorLog
import app.echo.android.model.error.EchoErrorSource
import app.echo.android.model.playback.EchoOutputDeviceKind
import app.echo.android.model.playback.EchoVolumeDeviceIdentity
import app.echo.android.playback.EchoPlaybackProcessRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

@UnstableApi
internal fun createAppVolumeController(context: Context, store: EchoSettingsStore): AppVolumeController {
    val runtime = EchoPlaybackProcessRuntime
    val devices = combine(runtime.outputRouteMonitor(context).route, runtime.usbAudioMonitor(context).status) { route, usb ->
        val exclusive = usb.connected && usb.exclusiveEnabled
        val kind = if (exclusive) EchoOutputDeviceKind.Usb else EchoOutputDeviceKind.fromId(route.kind)
        EchoVolumeDeviceIdentity.key(kind, if (exclusive) usb.deviceName else route.deviceName, route.address,
            if (usb.connected) usb.vendorId else null, if (usb.connected) usb.productId else null)
    }.distinctUntilChanged()
    return AppVolumeController(runtime.scope, store.volumeSnapshot, devices,
        save = { value, version -> withContext(Dispatchers.IO) { store.setVolumeSettings(value, version) } },
        apply = runtime::setVolumeSettings,
        onSaveFailure = { error ->
            EchoErrorLog.record(EchoErrorSource.Playback, "App volume could not be saved", throwable = error)
            Toast.makeText(context, context.getString(app.echo.android.feature.settings.R.string.volume_save_failed), Toast.LENGTH_SHORT).show()
        })
}
