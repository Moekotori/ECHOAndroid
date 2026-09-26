package app.echo.android.plugin

import java.io.File
import org.json.JSONObject

internal class PluginStorage(private val file: File) {
    private val values = load()

    fun get(key: String): String? = values[key]

    /** @return a script error code, or null when the value was stored. */
    fun set(key: String, value: String): String? {
        if (!StorageKey.matches(key) || value.length > MaxValueChars) return "invalid"
        if (key !in values && values.size >= MaxKeys) return "too_many"
        val nextBytes = values.entries.sumOf { (storedKey, stored) ->
            if (storedKey == key) key.length + value.length else storedKey.length + stored.length
        } + if (key in values) 0 else key.length + value.length
        if (nextBytes > MaxTotalChars) return "too_many"
        values[key] = value
        if (!persist()) return "failed"
        return null
    }

    private fun load(): LinkedHashMap<String, String> {
        val parsed = runCatching { JSONObject(file.takeIf { it.isFile }?.readText().orEmpty()) }.getOrNull()
            ?: return linkedMapOf()
        val loaded = linkedMapOf<String, String>()
        parsed.keys().forEach { key ->
            val value = parsed.optString(key, "")
            if (StorageKey.matches(key) && value.length <= MaxValueChars && loaded.size < MaxKeys) {
                loaded[key] = value
            }
        }
        return loaded
    }

    private fun persist(): Boolean = runCatching {
        val json = JSONObject()
        values.forEach { (key, value) -> json.put(key, value) }
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(json.toString(), Charsets.UTF_8)
        if (file.exists() && !file.delete()) return false
        temporary.renameTo(file)
    }.getOrDefault(false)

    private companion object {
        val StorageKey = Regex("^[A-Za-z0-9._-]{1,64}$")
        const val MaxKeys = 64
        const val MaxValueChars = 4_096
        const val MaxTotalChars = 64 * 1024
    }
}
