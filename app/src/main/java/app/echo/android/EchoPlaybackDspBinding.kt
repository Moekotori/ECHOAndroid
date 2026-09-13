package app.echo.android

import androidx.media3.common.util.UnstableApi
import app.echo.android.data.EchoSettingsStore
import app.echo.android.model.playback.EchoDspSettings
import app.echo.android.model.playback.OpraEqBand
import app.echo.android.playback.EchoPlaybackProcessRuntime
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Restore DSP for service/media-button playback as well as activity playback. */
@UnstableApi
internal fun bindPlaybackDspSettings(store: EchoSettingsStore) {
    EchoPlaybackProcessRuntime.scope.launch {
        store.appSettings.map { settings ->
            StoredPlaybackDsp(settings.dsp, settings.equalizerEnabled, settings.equalizerPreset,
                settings.equalizerBandGains, settings.equalizerPreampDb,
                if (settings.equalizerParametric) settings.equalizerFilters else emptyList(),
                settings.equalizerSourceLabel)
        }.distinctUntilChanged().collect { state ->
            EchoPlaybackProcessRuntime.setDspSettings(state.dsp)
            EchoPlaybackProcessRuntime.equalizerController().setConfig(
                state.enabled, state.preset, state.gains, state.preamp, state.filters, state.source,
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
)
