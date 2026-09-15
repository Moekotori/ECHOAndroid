package app.echo.android.connect

import app.echo.android.model.connect.EchoLanRenderer
import app.echo.android.model.connect.EchoLanRendererKind
import app.echo.android.model.connect.EchoRemoteStreamItem

object EchoChromecastCastPolicy {
    const val DefaultReceiverAppId = "CC1AD845"
    const val DefaultPort = 8009
    val SinkMimeTypes = listOf(
        "audio/mpeg",
        "audio/mp3",
        "audio/mp4",
        "audio/aac",
        "audio/wav",
        "audio/x-wav",
        "audio/webm",
    )

    fun canPlay(item: EchoRemoteStreamItem): Boolean = rejectReason(item) == null

    fun rejectReason(renderer: EchoLanRenderer, item: EchoRemoteStreamItem): EchoDlnaRejectReason? {
        if (renderer.kind != EchoLanRendererKind.Chromecast) return EchoDlnaRejectReason.NoAvTransport
        return rejectReason(item)
    }

    fun rejectReason(item: EchoRemoteStreamItem): EchoDlnaRejectReason? {
        if (EchoDlnaCastPolicy.isDsd(item)) return EchoDlnaRejectReason.DsdOnTv
        val mime = EchoDlnaCastPolicy.offeredMime(item)
        val allowed = SinkMimeTypes.any { sink -> EchoDlnaCastPolicy.mimeMatches(mime, sink) }
        return if (allowed) null else EchoDlnaRejectReason.UnsupportedFormat
    }
}
