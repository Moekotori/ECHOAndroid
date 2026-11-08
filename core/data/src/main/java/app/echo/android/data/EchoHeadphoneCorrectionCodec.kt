package app.echo.android.data

import app.echo.android.model.playback.*
import org.json.JSONObject

internal object EchoHeadphoneCorrectionCodec {
    fun encode(value: EchoHeadphoneCorrectionSettings): String = JSONObject()
        .put("enabled", value.enabled).put("id", value.id).put("label", value.label).put("preampDb", value.preampDb)
        .put("filters", org.json.JSONArray(formatEqualizerFilters(value.filters))).toString()
    fun decode(raw: String?): EchoHeadphoneCorrectionSettings {
        if (raw.isNullOrBlank() || raw.length > 65536) return EchoHeadphoneCorrectionSettings()
        return runCatching {
            val j = JSONObject(raw)
            val bands = parseEqualizerFilters(j.optJSONArray("filters")?.toString())
            require(bands.size <= 32 && bands.all { it.dynamicMode == EchoEqDynamicMode.Off })
            val valid = if (bands.isEmpty()) emptyList() else requireNotNull(EchoParametricEq.sanitize(bands))
            EchoHeadphoneCorrectionSettings(j.optBoolean("enabled"), j.optString("id").takeIf { it.isNotBlank() },
                j.optString("label").takeIf { it.isNotBlank() }, j.optDouble("preampDb", 0.0).toFloat().coerceIn(-60f, 24f), valid)
        }.getOrDefault(EchoHeadphoneCorrectionSettings())
    }
}
