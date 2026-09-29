package app.echo.android.connect

import app.echo.android.model.connect.EchoRemoteStreamItem

/** 投送到 DLNA / Chromecast 之后，根据渲染器上报的状态推断“该切到哪一首”。纯函数，便于单测。 */
object EchoLanCastFollowPolicy {
    /** 播到离结尾这么近再停下，才算自然播完；更早停下多半是用户在设备上按了停止。 */
    const val END_TOLERANCE_MS = 5_000L

    /** 切歌后这段时间内不再判断“播完”，渲染器此时常常短暂上报 STOPPED / TRANSITIONING。 */
    const val ADVANCE_GRACE_MS = 6_000L

    /**
     * 渲染器通过 SetNextAVTransportURI 自己切到下一首时，TrackURI 会变成下一首的地址。
     * 返回它在队列里的位置；没变化或认不出来时返回 null。
     */
    fun indexForTrackUri(items: List<EchoRemoteStreamItem>, currentIndex: Int, trackUri: String?): Int? {
        val uri = trackUri?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (items.getOrNull(currentIndex)?.streamUrl == uri) return null
        val ahead = (currentIndex + 1 until items.size).firstOrNull { items[it].streamUrl == uri }
        return ahead ?: items.indices.firstOrNull { items[it].streamUrl == uri }
    }

    fun isDlnaPlaying(transportState: String?): Boolean =
        transportState == "PLAYING" || transportState == "TRANSITIONING"

    /** DLNA：上一轮在播、这一轮停了、且停在结尾附近（或时长未知），判为自然播完。 */
    fun dlnaTrackEnded(
        transportState: String?,
        wasPlaying: Boolean,
        pausedByUser: Boolean,
        lastPositionMs: Long,
        durationMs: Long,
    ): Boolean {
        if (!wasPlaying || pausedByUser) return false
        if (transportState != "STOPPED" && transportState != "NO_MEDIA_PRESENT") return false
        return durationMs <= 0L || lastPositionMs >= durationMs - END_TOLERANCE_MS
    }

    /** Chromecast 在播放状态下按经过时间外推进度，不超过时长。 */
    fun extrapolatedPositionMs(
        sampledPositionMs: Long,
        sampledAtElapsedMs: Long,
        nowElapsedMs: Long,
        playing: Boolean,
        durationMs: Long,
    ): Long {
        val base = sampledPositionMs.coerceAtLeast(0L)
        if (!playing) return base
        val position = base + (nowElapsedMs - sampledAtElapsedMs).coerceAtLeast(0L)
        return if (durationMs > 0L) position.coerceAtMost(durationMs) else position
    }
}
