package app.echo.android.model.playback

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** PC EqPreset / EqSavePresetRequest JSON, with complete audible band parameters. */
object EchoEqPcJsonCodec {
    fun decode(text: String): List<EchoEqualizerUserPreset> {
        require(text.toByteArray(Charsets.UTF_8).size <= EchoEqBundleCodec.MaxBytes)
        val value = text.trimStart().removePrefix("\uFEFF")
        val root = if (value.startsWith("[")) JSONArray(value) else JSONObject(value)
        val rows = when (root) {
            is JSONArray -> root
            is JSONObject -> root.optJSONArray("presets") ?: JSONArray().put(root.optJSONObject("preset") ?: root)
            else -> error("Invalid EQ JSON")
        }
        require(rows.length() in 1..EchoEqualizerUserPresets.MaxCount)
        return List(rows.length()) { index ->
            val row = rows.getJSONObject(index)
            val state = row.optJSONObject("state") ?: row
            val bands = state.getJSONArray("bands")
            require(bands.length() in 1..31)
            val filters = List(bands.length()) { i ->
                val band = bands.getJSONObject(i)
                val scope = band.optString("scope", "stereo").also { require(EchoEqBandScope.entries.any { v -> v.id == it }) }
                val dynamic = band.optString("dynamicMode", "off").also { require(EchoEqDynamicMode.entries.any { v -> v.id == it }) }
                val type = EchoEqFilterType.normalize(band.optString("filterType", "peaking"))
                require(type in EchoParametricEq.AllowedTypes && type != EchoEqFilterType.BandPass)
                OpraEqBand(type, band.getDouble("frequencyHz").toFloat(), band.optDouble("gainDb", 0.0).toFloat(),
                    band.optDouble("q", 1.0).toFloat(), if (EchoParametricEq.isPass(type)) 12f else null,
                    enabled = band.optBoolean("enabled", true), scope = EchoEqBandScope.fromId(scope), dynamicMode = EchoEqDynamicMode.fromId(dynamic),
                    dynamicThresholdDb = band.optDouble("dynamicThresholdDb", -24.0).toFloat(), dynamicRatio = band.optDouble("dynamicRatio", 2.0).toFloat(),
                    dynamicAttackMs = band.optDouble("dynamicAttackMs", 5.0).toFloat(), dynamicReleaseMs = band.optDouble("dynamicReleaseMs", 80.0).toFloat())
            }
            requireNotNull(EchoEqualizerUserPresets.normalize(EchoEqualizerUserPreset(
                UUID.randomUUID().toString(), row.optString("name", "Imported EQ"), parametric = true,
                preampDb = state.optDouble("preampDb", 0.0).toFloat(), filters = filters,
                options = EchoEqOptions(state.optDouble("strengthPercent", 100.0).toFloat(), state.optDouble("tiltDb", 0.0).toFloat()))))
        }
    }

    fun encode(presets: List<EchoEqualizerUserPreset>): String {
        require(presets.size in 1..EchoEqualizerUserPresets.MaxCount)
        val rows = presets.map { input ->
            val preset = requireNotNull(EchoEqualizerUserPresets.normalize(input))
            val filters = if (preset.parametric) preset.filters else EchoEqualizerPresets.preciseFrequenciesHz.mapIndexed { i, f ->
                OpraEqBand(EchoEqFilterType.PeakDip, f, preset.gainsDb.getOrElse(i) { 0f }, EchoEqualizerPresets.GraphicQ, null)
            }
            require(filters.size <= 31 && filters.none { it.normalizedType() == EchoEqFilterType.BandPass ||
                (EchoParametricEq.isPass(it.type) && EchoParametricEq.snapSlope(it.slope) != 12f) ||
                it.frequencyHz !in 20f..20000f || it.gainDb !in -24f..12f ||
                (it.q ?: 1f) !in .1f..(if (it.normalizedType() == EchoEqFilterType.BandStop) 24f else 12f) }) { "Use ECHO bundle for legacy filters" }
            require(preset.preampDb in -12f..6f) { "Use ECHO bundle for extended preamp" }
            JSONObject().put("id", preset.id).put("name", preset.name).put("preampDb", preset.preampDb)
                .put("strengthPercent", preset.options.strengthPercent).put("tiltDb", preset.options.tiltDb)
                .put("bands", JSONArray().apply { filters.forEach { band -> put(JSONObject()
                    .put("filterType", when (band.normalizedType()) {
                        EchoEqFilterType.LowShelf -> "lowShelf"; EchoEqFilterType.HighShelf -> "highShelf"
                        EchoEqFilterType.LowPass -> "lowPass"; EchoEqFilterType.HighPass -> "highPass"
                        EchoEqFilterType.BandStop -> "notch"; else -> "peaking" })
                    .put("frequencyHz", band.frequencyHz).put("gainDb", band.gainDb).put("q", band.q ?: 1f)
                    .put("enabled", band.enabled).put("scope", band.scope.id).put("dynamicMode", band.dynamicMode.id)
                    .put("dynamicThresholdDb", band.dynamicThresholdDb).put("dynamicRatio", band.dynamicRatio)
                    .put("dynamicAttackMs", band.dynamicAttackMs).put("dynamicReleaseMs", band.dynamicReleaseMs)) } })
        }
        return (if (rows.size == 1) rows.single().toString(2) else JSONArray(rows).toString(2)).also {
            require(it.toByteArray(Charsets.UTF_8).size <= EchoEqBundleCodec.MaxBytes)
        }
    }
}
