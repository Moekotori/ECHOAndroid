package app.echo.android.data

import app.echo.android.model.playback.EchoEqualizerUserPreset
import app.echo.android.model.playback.EchoEqualizerUserPresets
import org.json.JSONArray
import org.json.JSONObject

object EchoEqualizerUserPresetCodec {
    fun encode(presets: List<EchoEqualizerUserPreset>): String {
        val array = JSONArray()
        presets.mapNotNull(EchoEqualizerUserPresets::normalize).distinctBy { it.id }
            .take(EchoEqualizerUserPresets.MaxCount)
            .forEach { preset ->
            array.put(
                JSONObject().apply {
                    put("id", preset.id)
                    put("name", preset.name)
                    put("updatedAtEpochMs", preset.updatedAtEpochMs)
                    put("parametric", preset.parametric)
                    put("graphicPresetId", preset.graphicPresetId)
                    put("gainsDb", JSONArray(preset.gainsDb))
                    put("preampDb", preset.preampDb.toDouble())
                    if (preset.filters.isNotEmpty()) {
                        put("filters", JSONArray(formatEqualizerFilters(preset.filters)))
                    }
                    preset.sourceLabel?.let { put("sourceLabel", it) }
                    preset.opraEqId?.let { put("opraEqId", it) }
                },
            )
        }
        return array.toString()
    }

    fun decode(raw: String?): List<EchoEqualizerUserPreset> {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return emptyList()
        val array = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                val id = json.optString("id").trim()
                val name = json.optString("name")
                val filtersRaw = json.opt("filters")
                val filters = when (filtersRaw) {
                    is JSONArray -> parseEqualizerFilters(filtersRaw.toString())
                    is String -> parseEqualizerFilters(filtersRaw)
                    else -> emptyList()
                }
                val preset = EchoEqualizerUserPresets.normalize(
                    EchoEqualizerUserPreset(
                        id = id,
                        name = name,
                        updatedAtEpochMs = json.optLong("updatedAtEpochMs", 0L),
                        parametric = json.optBoolean("parametric", false),
                        graphicPresetId = json.optString("graphicPresetId"),
                        gainsDb = json.optJSONArray("gainsDb")?.let { gains ->
                            List(gains.length()) { gainIndex -> gains.optDouble(gainIndex, 0.0).toFloat() }
                        }.orEmpty(),
                        preampDb = json.optDouble("preampDb", 0.0).toFloat(),
                        filters = filters,
                        sourceLabel = json.optString("sourceLabel").trim().takeIf { it.isNotEmpty() },
                        opraEqId = json.optString("opraEqId").trim().takeIf { it.isNotEmpty() },
                    ),
                ) ?: continue
                add(preset)
            }
        }.distinctBy { it.id }.take(EchoEqualizerUserPresets.MaxCount)
    }
}
