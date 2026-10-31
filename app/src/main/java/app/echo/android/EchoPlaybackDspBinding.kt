package app.echo.android

import androidx.media3.common.util.UnstableApi
import app.echo.android.data.EchoSettingsStore
import app.echo.android.model.playback.EchoDspSettings
import app.echo.android.model.playback.EchoMsebState
import app.echo.android.model.playback.OpraEqBand
import app.echo.android.playback.EchoPlaybackProcessRuntime
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Restore DSP for service/media-button playback as well as activity playback. */
@UnstableApi
internal fun bindPlaybackDspSettings(store: EchoSettingsStore, applicationContextForAudioProfiles: android.content.Context) {
    EchoPlaybackProcessRuntime.scope.launch {
        store.appSettings.map { it.volume }.distinctUntilChanged()
            .collect(EchoPlaybackProcessRuntime::setVolumeSettings)
    }
    EchoPlaybackProcessRuntime.scope.launch {
        store.appSettings.map { it.channelBalance }.distinctUntilChanged()
            .collect { EchoPlaybackProcessRuntime.channelBalanceController().setState(it) }
    }
    EchoPlaybackProcessRuntime.scope.launch {
        kotlinx.coroutines.flow.combine(
            EchoPlaybackProcessRuntime.outputRouteMonitor(applicationContextForAudioProfiles).route,
            EchoPlaybackProcessRuntime.usbAudioMonitor(applicationContextForAudioProfiles).status.map { it.deviceName }.distinctUntilChanged(),
            store.appSettings.map { Triple(it.outputAudioProfileKeys, it.usbExclusiveEnabled, it.usbBitPerfectEnabled) }.distinctUntilChanged(),
        ) { route, usbName, settings ->
            Triple(app.echo.android.model.playback.EchoOutputDspPolicy.deviceKey(
                app.echo.android.model.playback.EchoOutputDeviceKind.fromId(route.kind),
                if (route.kind == app.echo.android.model.playback.EchoOutputDeviceKind.Usb.id) usbName ?: route.deviceName else route.deviceName),
                settings.second && settings.third, settings.first)
        }.distinctUntilChanged().collect { (key, bypassed, keys) ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                store.activateOutputAudioProfile(if (bypassed) "" else key)
                if (!bypassed && key !in keys && key.substringBefore(':') !in keys) {
                    val settings = store.appSettings.first()
                    val presetId = app.echo.android.model.playback.EchoOutputDspPolicy.resolvePresetId(settings.equalizerDevicePresetIds, key)
                    settings.equalizerUserPresets.firstOrNull { it.id == presetId }?.let { store.applyEqualizerUserPreset(it) }
                }
            }
        }
    }
    EchoPlaybackProcessRuntime.scope.launch {
        store.appSettings.map { settings ->
            StoredPlaybackDsp(settings.dsp, settings.equalizerEnabled, settings.equalizerPreset,
                settings.equalizerBandGains, settings.equalizerPreampDb,
                if (settings.equalizerParametric) settings.equalizerFilters else emptyList(),
                settings.equalizerSourceLabel, settings.mseb)
        }.distinctUntilChanged().collect { state ->
            EchoPlaybackProcessRuntime.setDspSettings(state.dsp)
            EchoPlaybackProcessRuntime.equalizerController().setConfig(
                state.enabled, state.preset, state.gains, state.preamp, state.filters, state.source,
                mseb = state.mseb,
            )
        }
    }
}

private data class StoredPlaybackDsp(
    val dsp: EchoDspSettings,
    val enabled: Boolean,
    val preset: String,
    val gains: List<Float>,
    val preamp: Float,
    val filters: List<OpraEqBand>,
    val source: String?,
    val mseb: EchoMsebState,
)
