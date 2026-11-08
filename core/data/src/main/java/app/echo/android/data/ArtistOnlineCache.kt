package app.echo.android.data

import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Called only under the repository mutex on Dispatchers.IO; bounded to 96 x 512 KiB. */
internal class ArtistOnlineCache(private val directory: File, private val maxBytes: Int = 524288, private val maxEntries: Int = 96) {
    data class Entry(val value: JSONObject?, val age: Long)
    private fun file(key: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(directory, "$digest.json")
    }
    fun read(key: String): Entry? = runCatching {
        val file = file(key)
        if (file.length() !in 1..maxBytes.toLong()) return null
        val json = JSONObject(file.readText())
        val age = System.currentTimeMillis() - json.getLong("at")
        if (age < 0 || !json.has("value")) null else Entry(json.optJSONObject("value"), age)
    }.getOrNull()

    fun write(key: String, value: JSONObject?) {
        runCatching {
            directory.mkdirs()
            val body = JSONObject().put("at", System.currentTimeMillis()).put("value", value ?: JSONObject.NULL).toString()
            if (body.toByteArray().size > maxBytes) return
            val destination = file(key)
            val temporary = File(directory, "${destination.name}.tmp")
            temporary.writeText(body)
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            directory.listFiles()?.filter { it.extension == "json" }?.sortedByDescending { it.lastModified() }
                ?.drop(maxEntries)?.forEach { it.delete() }
        }
    }
}
