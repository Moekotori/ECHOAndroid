package app.echo.android.data

import app.echo.android.model.playback.EchoUsbBufferMode
import app.echo.android.model.playback.EchoUsbOutputOptions
import app.echo.android.model.playback.EchoUsbVolumeMode
import org.json.JSONObject

internal object EchoUsbOutputOptionsCodec {
    fun encode(options: EchoUsbOutputOptions): String = options.normalized.let { value ->
        JSONObject().apply {
            put("volumePercent", value.volumePercent)
            putOpt("volumeStep", value.volumeStep)
            putOpt("volumeDeviceKey", value.volumeDeviceKey)
            put("deviceVolumeSteps", JSONObject(value.deviceVolumeSteps))
            put("volumeMode", value.volumeMode.id)
            put("bufferMode", value.bufferMode.id)
            put("keepAliveOnPause", value.keepAliveOnPause)
            put("keepAliveByDevice", JSONObject(value.keepAliveByDevice))
            put("dopDeviceLimits", JSONObject(value.dopDeviceLimits))
        }.toString()
    }

    fun decode(raw: String?): EchoUsbOutputOptions {
        if (raw.isNullOrBlank() || raw.length > 8192) return EchoUsbOutputOptions()
        return runCatching {
            val value = JSONObject(raw)
            // USB format is negotiated automatically, including when restoring legacy settings.
            EchoUsbOutputOptions(
                volumePercent = value.optInt("volumePercent", 100),
                volumeMode = EchoUsbVolumeMode.fromId(value.optString("volumeMode")),
                volumeStep = if (value.has("volumeStep") && !value.isNull("volumeStep")) value.optInt("volumeStep", 0) else null,
                volumeDeviceKey = value.optString("volumeDeviceKey").takeIf { it.isNotBlank() },
                deviceVolumeSteps = value.optJSONObject("deviceVolumeSteps")?.let { records ->
                    records.keys().asSequence().take(16).associateWith { records.optInt(it, 0) }
                }.orEmpty(),
                bufferMode = EchoUsbBufferMode.fromId(value.optString("bufferMode")),
                keepAliveOnPause = value.optBoolean("keepAliveOnPause", true),
                keepAliveByDevice = value.optJSONObject("keepAliveByDevice")?.let { records ->
                    records.keys().asSequence().take(EchoUsbOutputOptions.MAX_KEEP_ALIVE_DEVICES)
                        .mapNotNull { key -> (records.opt(key) as? Boolean)?.let { key to it } }.toMap()
                }.orEmpty(),
                dopDeviceLimits = value.optJSONObject("dopDeviceLimits")?.let { limits ->
                    limits.keys().asSequence().take(32).associateWith { limits.optInt(it, 0) }
                }.orEmpty(),
            ).normalized
        }.getOrDefault(EchoUsbOutputOptions())
    }
}
