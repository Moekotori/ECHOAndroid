package app.echo.android.data

import app.echo.android.model.playback.EchoMsebParameter
import app.echo.android.model.playback.EchoMsebState
import org.json.JSONArray
import org.json.JSONObject

internal object EchoMsebCodec {
    fun encode(state: EchoMsebState): String = JSONObject().apply {
        put("enabled", state.enabled)
        put("values", JSONArray(state.normalized.values))
    }.toString()

    fun decode(raw: String?): EchoMsebState {
        if (raw.isNullOrBlank() || raw.length > 2048) return EchoMsebState()
        return runCatching {
            val json = JSONObject(raw)
            val values = json.optJSONArray("values")
            EchoMsebState(
                enabled = json.optBoolean("enabled", false),
                values = EchoMsebParameter.entries.map { values?.optDouble(it.ordinal, 0.0)?.toFloat() ?: 0f },
            ).normalized
        }.getOrDefault(EchoMsebState())
    }
}
