package app.echo.android.feature.radio

import app.echo.android.model.radio.*

data class RadioDiscoveryState(
    val query: RadioDirectoryQuery = RadioDirectoryQuery(), val rows: List<EchoRadioDirectoryStation> = emptyList(),
    val hasNext: Boolean = false, val loading: Boolean = false, val failed: Boolean = false,
)

class RadioActions(
    val browse: (RadioDirectoryQuery) -> Unit, val closeDirectory: () -> Unit,
    val play: (EchoRadioStation, String?) -> Unit, val stop: () -> Unit,
    val save: suspend (EchoRadioStation) -> Unit, val delete: suspend (String) -> Unit,
    val favorite: suspend (String) -> Unit, val clear: suspend () -> Unit,
    val importStations: suspend (List<EchoRadioStation>) -> Unit,
    val probe: suspend (String) -> RadioProbeResult,
    val searchSaved: (String, Boolean) -> Unit, val importFile: () -> Unit, val exportFile: () -> Unit, val exportPlaylist: () -> Unit,
    val playClipboard: (String) -> Unit,
    val retrySaved: () -> Unit, val openUrl: (String) -> Unit,
    val openPlayer: () -> Unit = {},
)

