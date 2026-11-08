package app.echo.android.data

import org.json.JSONArray
import org.json.JSONObject

internal fun decodeSubsonicConnections(raw: String?): List<SubsonicEndpoint> = runCatching {
    if (raw.isNullOrBlank()) return emptyList()
    require(raw.length <= 128_000)
    JSONArray(raw).objectList().take(32).map { row ->
        SubsonicEndpoint(row.getString("url"), row.getString("user"), row.getString("password"))
    }.filter { it.normalizedBaseUrl.isNotBlank() && it.username.isNotBlank() && it.password.isNotBlank() }
}.getOrDefault(emptyList())

internal fun encodeSubsonicConnections(connections: List<SubsonicEndpoint>): String = JSONArray().apply {
    connections.distinctBy { it.sourceId }.take(32).forEach { server -> put(JSONObject()
        .put("url", server.normalizedBaseUrl).put("user", server.username).put("password", server.password)) }
}.toString()

internal fun savedSubsonicConnection(connections: List<SubsonicEndpoint>, url: String?, username: String?): SubsonicEndpoint? {
    if (url.isNullOrBlank() || username.isNullOrBlank()) return null
    val base = normalizeSubsonicBaseUrl(url)
    return connections.firstOrNull { it.normalizedBaseUrl == base && it.username.trim() == username.trim() }
}
