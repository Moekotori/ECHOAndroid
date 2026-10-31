package app.echo.android.data

import app.echo.android.model.playback.EchoMsebUserPreset
import app.echo.android.model.playback.EchoMsebUserPresets
import org.json.JSONArray
import org.json.JSONObject

internal object EchoMsebPresetCodec {
    fun encode(presets: List<EchoMsebUserPreset>): String = JSONArray().apply {
        presets.mapNotNull(EchoMsebUserPresets::normalize).distinctBy { it.id }
            .take(EchoMsebUserPresets.MaxCount).forEach {
                put(JSONObject().put("id", it.id).put("name", it.name).put("values", JSONArray(it.values)))
            }
    }.toString()

    fun decode(raw: String?): List<EchoMsebUserPreset> {
        if (raw.isNullOrBlank() || raw.length > EchoMsebUserPresets.MaxImportBytes) return emptyList()
        return runCatching {
            val rows = JSONArray(raw)
            (0 until minOf(rows.length(), EchoMsebUserPresets.MaxCount)).mapNotNull { i ->
                val row = rows.optJSONObject(i) ?: return@mapNotNull null
                val values = row.optJSONArray("values") ?: return@mapNotNull null
                if (values.length() != 10) return@mapNotNull null
                EchoMsebUserPresets.normalize(EchoMsebUserPreset(row.optString("id"), row.optString("name"),
                    List(10) { values.optDouble(it, Double.NaN).toFloat() }))
            }.distinctBy { it.id }
        }.getOrDefault(emptyList())
    }
}
