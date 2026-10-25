package app.echo.android.model.radio

/** Public directory hit; saving copies name/url into [EchoRadioStation]. */
data class EchoRadioDirectoryStation(
    val id: String,
    val name: String,
    val url: String,
    val country: String? = null,
    val tags: String? = null,
    val bitrateKbps: Int? = null,
    val codec: String? = null,
    val artworkUrl: String? = null,
    val countryCode: String = "", val language: String = "", val homepage: String? = null,
) {
    fun toStation(): EchoRadioStation = EchoRadioStation(id = id, name = name, url = url, artworkUrl = artworkUrl,
        genre = tags.orEmpty(), codec = codec, bitrateKbps = bitrateKbps, countryCode = countryCode, language = language, homepage = homepage)
}

data class RadioDirectoryQuery(
    val name: String = "", val countryCode: String = "", val language: String = "",
    val tag: String = "", val page: Int = 0,
)

data class RadioDirectoryPage(val stations: List<EchoRadioDirectoryStation>, val hasNext: Boolean)

data class EchoRadioDirectorySearch(
    val query: String = "",
    val stations: List<EchoRadioDirectoryStation> = emptyList(),
    val loading: Boolean = false,
    val failed: Boolean = false,
) {
    val active: Boolean
        get() = query.isNotEmpty() || loading || failed || stations.isNotEmpty()
}
