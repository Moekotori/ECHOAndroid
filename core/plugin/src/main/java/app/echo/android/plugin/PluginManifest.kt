package app.echo.android.plugin

import org.json.JSONArray
import org.json.JSONObject

data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val entry: String,
    val permissions: List<PluginCapability>,
    val summary: String,
)

internal fun parsePluginManifest(text: String): PluginManifest? {
    val root = runCatching { JSONObject(text.removePrefix("\uFEFF")) }.getOrNull() ?: return null
    if (root.optInt("format", -1) != 1 || root.optInt("api", -1) != 1) return null
    val id = root.optString("id")
    val name = root.optString("name")
    val version = root.optString("version")
    val entry = root.optString("entry")
    if (!PluginPaths.isPluginId(id) || !name.isValidLabel(80) || !version.isValidLabel(32)) return null
    if (!PluginPaths.isScriptEntry(entry)) return null
    val permissions = mutableListOf<PluginCapability>()
    when (val raw = root.opt("permissions")) {
        null, JSONObject.NULL -> Unit
        is JSONArray -> {
            for (index in 0 until raw.length()) {
                val capability = PluginCapability.fromWire(raw.optString(index))
                if (capability != null && capability !in permissions) permissions += capability
            }
        }
        else -> return null
    }
    val summary = root.optString("summary")
    if (summary.length > 200) return null
    return PluginManifest(
        id = id,
        name = name.trim(),
        version = version.trim(),
        entry = entry,
        permissions = permissions,
        summary = summary.trim(),
    )
}

private fun String.isValidLabel(max: Int): Boolean {
    val trimmed = trim()
    return trimmed.isNotEmpty() && trimmed.length <= max && '\n' !in trimmed && '\r' !in trimmed
}
