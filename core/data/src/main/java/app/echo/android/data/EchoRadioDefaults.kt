package app.echo.android.data

import app.echo.android.model.radio.EchoRadioStation
import org.json.JSONArray
import org.json.JSONObject

/** Defaults are applied only when creating storage or migrating the legacy array. */
internal object EchoRadioDefaults {
    private val station = EchoRadioStation(
        id = "echo-official-radio",
        name = "ECHO Radio",
        url = "https://echonext.moe/radio/stream",
    )

    fun initialize(stations: List<EchoRadioStation>): List<EchoRadioStation> =
        if (stations.size >= EchoRadioStation.MaxStations ||
            stations.any { it.id == station.id || it.url.trimEnd('/') == station.url }
        ) stations else listOf(station) + stations

    // An object distinguishes initialized storage from legacy arrays, including an empty list
    // after the user deletes the default. Keep initialization and stations in one atomic write.
    fun encode(stations: List<EchoRadioStation>): String {
        val entries = JSONArray()
        stations.forEach { station ->
            entries.put(JSONObject().put("id", station.id).put("name", station.name).put("url", station.url))
        }
        return JSONObject().put("stations", entries).toString()
    }
}
