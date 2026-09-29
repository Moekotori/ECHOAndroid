package app.echo.android

import app.echo.android.connect.EchoLinkCastSourceTrack
import app.echo.android.model.playback.EchoPlaybackState
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoTrackRef

/**
 * 投送到 DLNA / Chromecast 时，远端设备的播放状态。进度单独放在
 * [EchoLinkSession.castPosition]，避免每秒的进度刷新让整棵界面重组。
 */
data class EchoLanCastPlayback(
    val rendererName: String,
    val index: Int,
    val track: EchoLinkCastSourceTrack,
    val isPlaying: Boolean,
    val durationMs: Long,
    /** 0..1；设备不支持读取音量时为 null。 */
    val volume: Float? = null,
)

/** 把远端状态套到本地的 [EchoPlaybackStatus] 上，播放页和迷你播放器不用区分来源。 */
internal fun EchoPlaybackStatus.withLanCast(cast: EchoLanCastPlayback, positionMs: Long): EchoPlaybackStatus =
    copy(
        state = if (cast.isPlaying) EchoPlaybackState.Playing else EchoPlaybackState.Paused,
        isPlaying = cast.isPlaying,
        track = EchoTrackRef(
            id = cast.track.id,
            uri = cast.track.uri,
            title = cast.track.title,
            artist = cast.track.artist,
            album = cast.track.album,
            artworkUri = cast.track.artworkUri,
            durationMs = cast.durationMs.takeIf { it > 0L } ?: cast.track.durationMs,
            sampleRateHz = cast.track.sampleRateHz,
            sourceId = cast.track.sourceId,
        ),
        positionMs = positionMs,
        durationMs = cast.durationMs.takeIf { it > 0L } ?: cast.track.durationMs,
    )
