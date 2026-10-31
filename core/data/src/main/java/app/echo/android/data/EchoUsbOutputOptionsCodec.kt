package app.echo.android.data

import app.echo.android.model.playback.EchoUsbBufferMode
import app.echo.android.model.playback.EchoUsbOutputOptions
import org.json.JSONObject

internal object EchoUsbOutputOptionsCodec {
    fun encode(options: EchoUsbOutputOptions): String = options.normalized.let { value ->
        JSONObject().apply {
            put("sampleRateHz", value.sampleRateHz)
            put("bitDepth", value.bitDepth)
            put("channelCount", value.channelCount)
            put("volumePercent", value.volumePercent)
            putOpt("volumeStep", value.volumeStep)
            putOpt("volumeDeviceKey", value.volumeDeviceKey)
            put("deviceVolumeSteps", JSONObject(value.deviceVolumeSteps))
            put("volumeMode", value.volumeMode.id)
            put("bufferMode", value.bufferMode.id)
            put("keepAliveOnPause", value.keepAliveOnPause)
            put("dopDeviceLimits", JSONObject(value.dopDeviceLimits))
        }.toString()
    }

    fun decode(raw: String?): EchoUsbOutputOptions {
        if (raw.isNullOrBlank() || raw.length > 8192) return EchoUsbOutputOptions()
        return runCatching {
            val value = JSONObject(raw)
            EchoUsbOutputOptions(
                sampleRateHz = value.optInt("sampleRateHz", 0), bitDepth = value.optInt("bitDepth", 0),
                channelCount = value.optInt("channelCount", 0), volumePercent = value.optInt("volumePercent", 100),
                volumeMode = app.echo.android.model.playback.EchoUsbVolumeMode.fromId(value.optString("volumeMode")),
                volumeStep = if (value.has("volumeStep") && !value.isNull("volumeStep")) value.optInt("volumeStep", 0) else null,
                volumeDeviceKey = value.optString("volumeDeviceKey").takeIf { it.isNotBlank() },
                deviceVolumeSteps = value.optJSONObject("deviceVolumeSteps")?.let { records ->
                    records.keys().asSequence().take(16).associateWith { records.optInt(it, 0) }
                }.orEmpty(),
                bufferMode = EchoUsbBufferMode.fromId(value.optString("bufferMode")),
                keepAliveOnPause = value.optBoolean("keepAliveOnPause", true),
                dopDeviceLimits = value.optJSONObject("dopDeviceLimits")?.let { limits ->
                    limits.keys().asSequence().take(32).associateWith { limits.optInt(it, 0) }
                }.orEmpty(),
            ).normalized
        }.getOrDefault(EchoUsbOutputOptions())
    }
}
