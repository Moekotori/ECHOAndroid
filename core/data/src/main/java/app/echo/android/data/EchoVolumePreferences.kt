package app.echo.android.data

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import app.echo.android.model.playback.EchoVolumeSettings
import org.json.JSONObject

internal object EchoVolumePreferences {
    val Key = stringPreferencesKey("app_volume_v1")

    fun read(preferences: Preferences): EchoVolumeSettings = decode(preferences[Key])

    fun encode(value: EchoVolumeSettings): String = value.normalized.let {
        JSONObject().put("levelDb", it.levelDb).put("gainDb", it.gainDb)
            .put("limitDb", it.limitDb).put("stepDb", it.stepDb).put("muted", it.muted).toString()
    }

    fun decode(value: String?): EchoVolumeSettings {
        if (value == null || value.length > 1024) return EchoVolumeSettings()
        return runCatching {
            val json = JSONObject(value)
            EchoVolumeSettings(
                json.optDouble("levelDb", 0.0).toFloat(),
                json.optDouble("gainDb", 0.0).toFloat(),
                json.optDouble("limitDb", 30.0).toFloat(),
                json.optDouble("stepDb", 0.5).toFloat(),
                json.optBoolean("muted", false),
            ).normalized
        }.getOrDefault(EchoVolumeSettings())
    }
}
