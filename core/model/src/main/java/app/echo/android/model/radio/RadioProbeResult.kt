package app.echo.android.model.radio

data class RadioProbeResult(
    val stations: List<EchoRadioStation> = emptyList(), val name: String? = null,
    val description: String? = null, val genre: String? = null, val homepage: String? = null,
    val codec: String? = null, val bitrateKbps: Int? = null,
)
