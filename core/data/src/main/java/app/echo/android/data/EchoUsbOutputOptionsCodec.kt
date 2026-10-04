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
            put("bufferMode", value.bufferMode.id)
            put("keepAliveOnPause", value.keepAliveOnPause)
        }.toString()
    }

    fun decode(raw: String?): EchoUsbOutputOptions {
        if (raw.isNullOrBlank() || raw.length > 1024) return EchoUsbOutputOptions()
        return runCatching {
            val value = JSONObject(raw)
            EchoUsbOutputOptions(
                sampleRateHz = value.optInt("sampleRateHz", 0), bitDepth = value.optInt("bitDepth", 0),
                channelCount = value.optInt("channelCount", 0), volumePercent = value.optInt("volumePercent", 100),
                bufferMode = EchoUsbBufferMode.fromId(value.optString("bufferMode")),
                keepAliveOnPause = value.optBoolean("keepAliveOnPause", true),
            ).normalized
        }.getOrDefault(EchoUsbOutputOptions())
    }
}
