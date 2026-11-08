package app.echo.android.data

import app.echo.android.model.settings.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONObject

data class DecodedAppearancePack(val appearance: EchoAppearancePack, val background: ByteArray? = null, val font: ByteArray? = null)

object EchoAppearancePackCodec {
    const val MaxPackageBytes = 12 * 1024 * 1024
    private val limits = mapOf("appearance.json" to 32_768, "background" to 8 * 1024 * 1024, "font" to 4 * 1024 * 1024)

    fun encode(pack: DecodedAppearancePack): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun entry(name: String, bytes: ByteArray) {
                require(bytes.size <= limits.getValue(name))
                zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
            }
            entry("appearance.json", toJson(pack.appearance).toString().toByteArray(Charsets.UTF_8))
            pack.background?.let { entry("background", it) }
            pack.font?.let { entry("font", it) }
        }
        return out.toByteArray().also { require(it.size <= MaxPackageBytes) }
    }

    fun decode(bytes: ByteArray): DecodedAppearancePack {
        require(bytes.size <= MaxPackageBytes)
        val entries = linkedMapOf<String, ByteArray>()
        var total = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val max = limits[entry.name] ?: error("Unknown appearance pack entry")
                require(!entry.isDirectory && !entries.containsKey(entry.name))
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = zip.read(buffer)
                    if (count < 0) break
                    total += count
                    require(out.size() + count <= max && total <= MaxPackageBytes)
                    out.write(buffer, 0, count)
                }
                entries[entry.name] = out.toByteArray()
            }
        }
        val json = JSONObject(entries.getValue("appearance.json").toString(Charsets.UTF_8))
        require(json.getString("format") == "echo-android-appearance" && json.getInt("version") == 1)
        val appearance = fromJson(json)
        require(appearance.fontFamily != "imported" || entries.containsKey("font"))
        return DecodedAppearancePack(appearance, entries["background"], entries["font"])
    }

    internal fun toJson(s: EchoAppearancePack): JSONObject = JSONObject()
        .put("format", "echo-android-appearance").put("version", 1).put("name", s.name.take(48))
        .put("playerStyle", s.playerStyle).put("textScale", s.textScale).put("artworkScale", s.artworkScale)
        .put("lyricsBackdrop", s.lyricsBackdrop).put("fontFamily", s.fontFamily).put("fontScale", s.fontScale)
        .put("alignment", s.alignment).put("lineSpacing", s.lineSpacing).put("backgroundDim", s.backgroundDim)
        .put("wordStyle", s.wordStyle).put("wordIntensity", s.wordIntensity).put("colorTheme", s.colorTheme)
        .put("accent", s.colors.accent).put("secondary", s.colors.secondary).put("backgroundColor", s.colors.background)
        .put("backgroundBlur", s.backgroundBlur).put("backgroundBrightness", s.backgroundBrightness).put("backgroundGlass", s.backgroundGlass)
        .put("showTranslation", s.showTranslation).put("showRomanization", s.showRomanization)
        .put("colorMode", s.colorMode).put("wordHighlightEnabled", s.wordHighlightEnabled).put("estimatedWordHighlightEnabled", s.estimatedWordHighlightEnabled)
        .put("translationScale", s.translationScale).put("romanizationScale", s.romanizationScale)
        .put("backgroundScale", s.backgroundScale).put("backgroundPositionX", s.backgroundPositionX).put("backgroundPositionY", s.backgroundPositionY)
        .put("dynamicColorEnabled", s.dynamicColorEnabled)

    private fun fromJson(j: JSONObject): EchoAppearancePack {
        fun choice(key: String, default: String, allowed: List<String>): String = j.optString(key, default).also { require(it in allowed) }
        fun number(key: String, default: Float, range: ClosedFloatingPointRange<Float>): Float =
            j.optDouble(key, default.toDouble()).toFloat().also { require(it.isFinite() && it in range) }
        val d = EchoAppearancePack()
        val name = j.optString("name", "ECHO").trim().also { require(it.length in 1..48 && it.none(Char::isISOControl)) }
        return EchoAppearancePack(name,
            choice("playerStyle", d.playerStyle, EchoPlayerPageStyle.entries.map { it.id }),
            number("textScale", 1f, 0.8f..1.2f), number("artworkScale", 1f, 0.7f..1f),
            choice("lyricsBackdrop", d.lyricsBackdrop, EchoLyricsBackdrop.entries.map { it.id }),
            choice("fontFamily", d.fontFamily, listOf("system", "outfit", "serif", "monospace", "imported")),
            number("fontScale", 1f, 0.5f..1.28f), choice("alignment", "start", listOf("start", "center", "end")),
            number("lineSpacing", 1f, 0.5f..1.38f), number("backgroundDim", 0f, 0f..0.78f),
            choice("wordStyle", d.wordStyle, EchoLyricsWordHighlightStyle.entries.map { it.id }),
            number("wordIntensity", 1f, 0.45f..1.35f), choice("colorTheme", d.colorTheme, EchoColorTheme.entries.map { it.id }),
            EchoCustomColors(j.optInt("accent", d.colors.accent), j.optInt("secondary", d.colors.secondary), j.optInt("backgroundColor", d.colors.background)).normalized(),
            number("backgroundBlur", 24f, 0f..80f), number("backgroundBrightness", 0.88f, 0.35f..1.15f), number("backgroundGlass", 0.42f, 0.08f..0.9f),
            j.optBoolean("showTranslation", true), j.optBoolean("showRomanization", true),
            colorMode = choice("colorMode", "white", listOf("white", "warm", "blue", "violet", "mint")),
            wordHighlightEnabled = j.optBoolean("wordHighlightEnabled", true), estimatedWordHighlightEnabled = j.optBoolean("estimatedWordHighlightEnabled"),
            translationScale = number("translationScale", 1f, 0.5f..2f), romanizationScale = number("romanizationScale", 1f, 0.5f..2f),
            backgroundScale = number("backgroundScale", 1.05f, 1f..1.4f), backgroundPositionX = number("backgroundPositionX", 0f, -1f..1f),
            backgroundPositionY = number("backgroundPositionY", 0f, -1f..1f), dynamicColorEnabled = j.optBoolean("dynamicColorEnabled"))
    }
}
