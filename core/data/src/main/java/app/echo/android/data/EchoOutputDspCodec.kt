package app.echo.android.data

import app.echo.android.model.playback.EchoOutputDspPolicy
import org.json.JSONObject

object EchoOutputDspCodec {
    fun encode(bindings: Map<String, String>): String {
        val json = JSONObject()
        bindings.entries
            .filter { it.key.isNotBlank() && it.value.isNotBlank() }
            .take(EchoOutputDspPolicy.MaxBindings)
            .forEach { json.put(it.key, it.value) }
        return json.toString()
    }

    fun decode(raw: String?): Map<String, String> {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return emptyMap()
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return emptyMap()
        val keys = json.keys().asSequence().toList()
        return keys.mapNotNull { key ->
            val id = json.optString(key).trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            key.trim().takeIf { it.isNotEmpty() }?.let { it to id }
        }.take(EchoOutputDspPolicy.MaxBindings).toMap()
    }
}
