package app.echo.android.data

import app.echo.android.model.connect.EchoSavedPcEndpoint
import org.json.JSONArray
import org.json.JSONObject

object EchoSavedPcCodec {
    fun encode(endpoints: List<EchoSavedPcEndpoint>): String {
        val array = JSONArray()
        endpoints.distinctBy { it.id }.forEach { endpoint ->
            array.put(
                JSONObject()
                    .put("address", endpoint.address)
                    .put("name", endpoint.name)
                    .put("supportsV2Events", endpoint.supportsV2Events),
            )
        }
        return array.toString()
    }

    fun decode(raw: String?): List<EchoSavedPcEndpoint> {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return emptyList()
        val array = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                val address = json.optString("address").trim().trimEnd('/')
                if (address.isBlank()) continue
                add(
                    EchoSavedPcEndpoint(
                        address = address,
                        name = json.optString("name").trim().ifBlank { address },
                        supportsV2Events = json.optBoolean("supportsV2Events", false),
                    ),
                )
            }
        }.distinctBy { it.id }
    }

    fun upsert(
        current: List<EchoSavedPcEndpoint>,
        endpoint: EchoSavedPcEndpoint,
    ): List<EchoSavedPcEndpoint> {
        val next = current.filterNot { it.id == endpoint.id } + endpoint
        return next.takeLast(MaxSavedPcs)
    }

    fun remove(current: List<EchoSavedPcEndpoint>, address: String): List<EchoSavedPcEndpoint> {
        val id = address.trim().trimEnd('/').lowercase()
        return current.filterNot { it.id == id }
    }

    const val MaxSavedPcs = 8
}
