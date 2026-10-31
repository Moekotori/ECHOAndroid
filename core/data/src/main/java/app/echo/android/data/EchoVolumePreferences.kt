package app.echo.android.data

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.echo.android.model.playback.EchoVolumeSettings
import app.echo.android.model.playback.EchoVolumeDeviceIdentity
import app.echo.android.model.playback.EchoVolumeDeviceLevel
import org.json.JSONObject

/** Restore metadata is local to this settings store and is not included in portable backups. */
data class EchoVolumeSnapshot(val volume: EchoVolumeSettings, val restoreVersion: Long = 0L)

internal object EchoVolumePreferences {
    val Key = stringPreferencesKey("app_volume_v1")
    private val RestoreVersion = longPreferencesKey("app_volume_restore_version")

    fun read(preferences: Preferences): EchoVolumeSettings = decode(preferences[Key])

    fun snapshot(preferences: Preferences) = EchoVolumeSnapshot(read(preferences), preferences[RestoreVersion] ?: 0L)

    fun write(preferences: MutablePreferences, volume: EchoVolumeSettings, expectedRestoreVersion: Long? = null): Boolean {
        if (expectedRestoreVersion != null && (preferences[RestoreVersion] ?: 0L) != expectedRestoreVersion) return false
        preferences[Key] = encode(volume)
        return true
    }

    fun restore(preferences: MutablePreferences, volume: EchoVolumeSettings) {
        preferences[Key] = encode(volume)
        preferences[RestoreVersion] = (preferences[RestoreVersion] ?: 0L) + 1L
    }

    fun encode(value: EchoVolumeSettings): String = value.normalized.let {
        JSONObject().put("levelDb", it.levelDb).put("gainDb", it.gainDb)
            .put("limitDb", it.limitDb).put("stepDb", it.stepDb).put("muted", it.muted)
            .put("rememberPerDevice", it.rememberPerDevice)
            .put("deviceKey", it.deviceKey ?: JSONObject.NULL)
            .put("devices", JSONObject().apply {
                it.deviceLevels.forEach { (key, level) -> put(key,
                    JSONObject().put("levelDb", level.levelDb).put("gainDb", level.gainDb)) }
            }).toString()
    }

    fun decode(value: String?): EchoVolumeSettings {
        if (value == null || value.length > 32768) return EchoVolumeSettings()
        return runCatching {
            val json = JSONObject(value)
            val devices = json.optJSONObject("devices")
            val levels = devices?.keys()?.asSequence()?.filter(EchoVolumeDeviceIdentity::validKey)
                ?.take(EchoVolumeDeviceIdentity.MaxDevices)?.mapNotNull { key ->
                    devices.optJSONObject(key)?.let { level -> key to EchoVolumeDeviceLevel(
                        level.optDouble("levelDb", 0.0).toFloat(), level.optDouble("gainDb", 0.0).toFloat()) }
                }?.toMap().orEmpty()
            EchoVolumeSettings(
                json.optDouble("levelDb", 0.0).toFloat(),
                json.optDouble("gainDb", 0.0).toFloat(),
                json.optDouble("limitDb", 30.0).toFloat(),
                json.optDouble("stepDb", 0.5).toFloat(),
                json.optBoolean("muted", false),
                json.optString("deviceKey").takeUnless { it.isBlank() || it == "null" },
                levels,
                json.optBoolean("rememberPerDevice", false),
            ).normalized
        }.getOrDefault(EchoVolumeSettings())
    }
}
