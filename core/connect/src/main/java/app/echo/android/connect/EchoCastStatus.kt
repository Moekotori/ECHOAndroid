package app.echo.android.connect

import org.json.JSONObject

/** Chromecast 接收端上报的播放状态。字段缺失时为 null，由调用方沿用上一次的值。 */
data class EchoCastMediaStatus(
    val mediaSessionId: Int?,
    val playerState: String?,
    val idleReason: String?,
    val positionMs: Long?,
    val durationMs: Long?,
) {
    val isPlaying: Boolean get() = playerState == "PLAYING" || playerState == "BUFFERING"
    val finished: Boolean get() = playerState == "IDLE" && idleReason == "FINISHED"
}

internal object EchoCastStatusParser {
    /** MEDIA_STATUS 的 status 是数组；空数组表示会话已结束，返回 IDLE。 */
    fun media(payload: String): EchoCastMediaStatus? {
        val json = runCatching { JSONObject(payload) }.getOrNull() ?: return null
        if (json.optString("type") != "MEDIA_STATUS") return null
        val array = json.optJSONArray("status") ?: return null
        if (array.length() == 0) {
            return EchoCastMediaStatus(null, "IDLE", null, null, null)
        }
        val status = array.optJSONObject(0) ?: return null
        val media = status.optJSONObject("media")
        return EchoCastMediaStatus(
            mediaSessionId = status.optInt("mediaSessionId", -1).takeIf { it >= 0 },
            playerState = status.optString("playerState").takeIf { it.isNotBlank() },
            idleReason = status.optString("idleReason").takeIf { it.isNotBlank() },
            positionMs = status.optDouble("currentTime", Double.NaN).toMillisOrNull(),
            durationMs = media?.optDouble("duration", Double.NaN)?.toMillisOrNull(),
        )
    }

    /** RECEIVER_STATUS 里的设备音量，0..1。 */
    fun receiverVolume(payload: String): Float? {
        val json = runCatching { JSONObject(payload) }.getOrNull() ?: return null
        if (json.optString("type") != "RECEIVER_STATUS") return null
        val level = json.optJSONObject("status")?.optJSONObject("volume")?.optDouble("level", Double.NaN)
            ?: return null
        return level.takeIf { it.isFinite() }?.toFloat()?.coerceIn(0f, 1f)
    }

    private fun Double.toMillisOrNull(): Long? =
        takeIf { it.isFinite() && it >= 0.0 }?.let { (it * 1000.0).toLong() }
}
