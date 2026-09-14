package app.echo.android.data

import app.echo.android.model.radio.EchoRadioDirectoryStation
import app.echo.android.model.radio.EchoRadioStation
import org.json.JSONArray
import org.json.JSONObject

internal object RadioBrowserParser {
    fun parseStations(body: String): List<EchoRadioDirectoryStation> {
        val array = JSONArray(body)
        if (array.length() > RadioBrowserPolicy.MaxResults * 4) {
            throw IllegalArgumentException("Radio directory returned too many rows")
        }
        val seenUrls = HashSet<String>()
        val stations = ArrayList<EchoRadioDirectoryStation>(minOf(array.length(), RadioBrowserPolicy.MaxResults))
        for (index in 0 until array.length()) {
            if (stations.size >= RadioBrowserPolicy.MaxResults) break
            val item = array.optJSONObject(index) ?: continue
            val station = parseStation(item) ?: continue
            if (!seenUrls.add(station.url)) continue
            stations += station
        }
        return stations
    }

    fun parseStation(item: JSONObject): EchoRadioDirectoryStation? {
        if (item.has("lastcheckok") && item.optInt("lastcheckok", 1) == 0) return null
        val id = item.optString("stationuuid").trim().take(64).takeIf { it.isNotEmpty() } ?: return null
        val name = item.optString("name").trim().replace("\n", " ").take(120).takeIf { it.isNotEmpty() }
            ?: return null
        val resolved = item.optString("url_resolved").trim()
        val fallback = item.optString("url").trim()
        val url = when {
            EchoRadioStation.validUrl(resolved) -> resolved.trim()
            EchoRadioStation.validUrl(fallback) -> fallback.trim()
            else -> return null
        }
        val country = item.optString("country").trim().takeIf { it.isNotEmpty() }
            ?: item.optString("countrycode").trim().takeIf { it.isNotEmpty() }
        val tags = item.optString("tags").split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString(", ")
            .takeIf { it.isNotEmpty() }
            ?.take(80)
        val bitrate = item.optInt("bitrate", 0).takeIf { it > 0 }
        val codec = item.optString("codec").trim().uppercase().takeIf { it.isNotEmpty() }?.take(16)
        return EchoRadioDirectoryStation(
            id = id,
            name = name,
            url = url,
            country = country?.take(80),
            tags = tags,
            bitrateKbps = bitrate,
            codec = codec,
        )
    }
}
