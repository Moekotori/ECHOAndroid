package app.echo.android.model.playback

import java.net.URLDecoder
import java.net.URLEncoder

enum class EchoMsebAdjustmentRange(val limit: Float) {
    Fine(20f), Standard(40f), Wide(100f);
    companion object { fun fromId(id: String?) = entries.firstOrNull { it.name == id } ?: Wide }
}

data class EchoMsebUserPreset(val id: String, val name: String, val values: List<Float>)

/** A sound preset never changes whether processing is enabled or the editor's range. */
object EchoMsebUserPresets {
    const val MaxCount = 24
    const val MaxNameLength = 40
    const val MaxImportBytes = 32 * 1024
    fun normalize(preset: EchoMsebUserPreset): EchoMsebUserPreset? {
        val name = preset.name.replace(Regex("[\\p{Cntrl}]"), " ").trim().take(MaxNameLength)
        if (preset.id.isBlank() || preset.id.length > 80 || name.isBlank() ||
            preset.values.size != EchoMsebParameter.entries.size || preset.values.any { !it.isFinite() }) return null
        return preset.copy(name = name, values = preset.values.map { it.coerceIn(-100f, 100f) })
    }

    fun encode(presets: List<EchoMsebUserPreset>): String = "ECHOMSEB1\n" +
        presets.mapNotNull(::normalize).distinctBy { it.id }.take(MaxCount).joinToString("\n") {
            URLEncoder.encode(it.name, "UTF-8") + "\t" + it.values.joinToString(",")
        }

    fun decode(text: String): List<EchoMsebUserPreset> {
        require(text.length <= MaxImportBytes && text.toByteArray(Charsets.UTF_8).size <= MaxImportBytes)
        val lines = text.lineSequence().toList()
        require(lines.firstOrNull()?.trim() == "ECHOMSEB1") { "Invalid MSEB file" }
        val rows = lines.drop(1).filter { it.isNotBlank() }
        require(rows.isNotEmpty() && rows.size <= MaxCount) { "Invalid preset count" }
        return rows.mapIndexed { index, row ->
            val cells = row.split('\t')
            require(cells.size == 2)
            val values = cells[1].split(',').map { it.toFloatOrNull() ?: error("Invalid value") }
            requireNotNull(normalize(EchoMsebUserPreset("import-$index", URLDecoder.decode(cells[0], "UTF-8"), values)))
        }
    }

    /** Imports add copies, with deterministic names; saved presets are never silently replaced. */
    fun merge(current: List<EchoMsebUserPreset>, incoming: List<EchoMsebUserPreset>, newId: () -> String): List<EchoMsebUserPreset> {
        require(current.size + incoming.size <= MaxCount) { "Preset capacity reached" }
        val result = current.toMutableList()
        incoming.forEach { candidate ->
            val preset = requireNotNull(normalize(candidate))
            var name = preset.name
            var suffix = 2
            while (result.any { it.name.equals(name, true) }) {
                val ending = " ($suffix)"
                name = preset.name.take(MaxNameLength - ending.length) + ending
                suffix++
            }
            result.add(preset.copy(id = newId(), name = name))
        }
        return result
    }
}
