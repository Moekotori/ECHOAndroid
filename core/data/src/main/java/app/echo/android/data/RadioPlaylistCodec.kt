package app.echo.android.data

import app.echo.android.model.radio.EchoRadioStation
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

object RadioPlaylistCodec {
    fun parse(text: String, baseUrl: String? = null): List<EchoRadioStation> {
        require(text.length <= 4 * 1024 * 1024)
        val raw = text.trim().removePrefix("\uFEFF")
        if ((raw.startsWith("[") && !raw.startsWith("[playlist]", true)) || raw.startsWith("{")) {
            val array = if (raw.startsWith("[")) JSONArray(raw) else JSONObject(raw).getJSONArray("stations")
            require(array.length() <= EchoRadioStation.MaxStations)
            return List(array.length()) { RadioStationJson.decode(array.getJSONObject(it)) }
        }
        val entries = if (raw.startsWith("[playlist]", true)) {
            val fields = raw.lineSequence().filter { it.contains('=') }.associate { it.substringBefore('=').trim().lowercase() to it.substringAfter('=').trim() }
            fields.entries.filter { it.key.matches(Regex("file[0-9]+")) }.sortedBy { it.key.removePrefix("file").toIntOrNull() ?: 0 }
                .map { M3uEntry(it.value, fields["title" + it.key.removePrefix("file")]) }
        } else parseM3u(raw)
        require(entries.size <= EchoRadioStation.MaxStations)
        return entries.mapNotNull { entry ->
            val url = try { if (baseUrl == null) entry.location else URI(baseUrl).resolve(entry.location).toString() } catch (_: Exception) { return@mapNotNull null }
            if (!EchoRadioStation.validUrl(url)) return@mapNotNull null
            EchoRadioStation("import:" + PodcastFeedParser.identity(url).take(24), entry.title?.take(120) ?: URI(url).host, url)
        }.distinctBy { it.url }
    }
    fun export(stations: List<EchoRadioStation>): String = JSONObject().put("stations", JSONArray().apply {
        stations.take(EchoRadioStation.MaxStations).forEach { put(RadioStationJson.encode(it)) }
    }).toString(2)
    fun exportM3u(stations: List<EchoRadioStation>): String = M3uPlaylistCodec.write(stations.map {
        M3uExportTrack(title = it.name.replace('\n', ' ').replace('\r', ' '), artist = "", durationMs = 0, location = it.url)
    })

    // Song M3U parsing normalizes filesystem paths; network playlists must preserve schemes and / roots.
    private fun parseM3u(text: String): List<M3uEntry> {
        val entries = ArrayList<M3uEntry>()
        var title: String? = null
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.startsWith("#EXTINF:", true)) title = line.substringAfter(',', "").trim().take(120).takeIf { it.isNotEmpty() }
            else if (line.isNotEmpty() && !line.startsWith('#')) {
                require(entries.size < EchoRadioStation.MaxStations)
                entries += M3uEntry(line, title)
                title = null
            }
        }
        return entries
    }
}
