package app.echo.android.model.playback

import java.util.UUID

enum class EchoEqImportConflict { Add, Skip, Replace }

/** Portable UTF-8 envelope around the existing validated, versioned ECHOEQ1 payloads. */
object EchoEqBundleCodec {
    const val Header = "ECHO-EQ-BUNDLE/1"
    const val MaxBytes = 1024 * 1024
    fun encode(presets: List<EchoEqualizerUserPreset>): String {
        require(presets.size in 1..EchoEqualizerUserPresets.MaxCount)
        return Header + "\n" + presets.joinToString("\n") { requireNotNull(EchoEqualizerShareCodec.encode(it)) } + "\n"
    }
    fun decode(text: String): List<EchoEqualizerUserPreset> {
        require(text.length <= MaxBytes && text.toByteArray(Charsets.UTF_8).size <= MaxBytes)
        val lines = text.removePrefix("\uFEFF").lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        require(lines.firstOrNull() == Header) { "Unsupported bundle format or version" }
        require(lines.size in 2..EchoEqualizerUserPresets.MaxCount + 1)
        return lines.drop(1).map { code ->
            require(code.startsWith(EchoEqualizerShareCodec.Prefix))
            requireNotNull(EchoEqualizerShareCodec.decode(code, UUID.randomUUID().toString())) { "Invalid preset" }
        }
    }
    /** All-or-nothing capacity check, also called inside the preference transaction. */
    fun merge(current: List<EchoEqualizerUserPreset>, imported: List<EchoEqualizerUserPreset>, conflict: EchoEqImportConflict,
        now: Long): List<EchoEqualizerUserPreset> {
        require(imported.size in 1..EchoEqualizerUserPresets.MaxCount)
        val result = current.toMutableList()
        imported.forEach { raw ->
            val preset = requireNotNull(EchoEqualizerUserPresets.normalize(raw)).copy(updatedAtEpochMs = now)
            val index = result.indexOfFirst { it.name.equals(preset.name, ignoreCase = true) }
            when {
                index >= 0 && conflict == EchoEqImportConflict.Skip -> Unit
                index >= 0 && conflict == EchoEqImportConflict.Replace -> result[index] = preset.copy(id = result[index].id)
                else -> {
                    var name = preset.name
                    var suffix = 2
                    while (result.any { it.name.equals(name, ignoreCase = true) }) {
                        val tail = " ($suffix)"; suffix++
                        name = preset.name.take(EchoEqualizerUserPresets.MaxNameLength - tail.length) + tail
                    }
                    result += preset.copy(id = UUID.randomUUID().toString(), name = name)
                }
            }
        }
        require(result.size <= EchoEqualizerUserPresets.MaxCount) { "Preset capacity exceeded" }
        return result
    }
}
