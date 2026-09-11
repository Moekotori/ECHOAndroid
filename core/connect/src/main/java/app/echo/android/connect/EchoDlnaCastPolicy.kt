package app.echo.android.connect

import app.echo.android.model.connect.EchoLanRenderer
import app.echo.android.model.connect.EchoLanRendererKind
import app.echo.android.model.connect.EchoRemoteStreamItem

enum class EchoDlnaRejectReason(val code: String) {
    Chromecast("chromecast_unsupported"),
    NoAvTransport("no_avtransport"),
    UnsupportedFormat("unsupported_format"),
    DsdOnTv("dsd_not_supported"),
}

object EchoDlnaCastPolicy {
    val TvSinkMimeTypes = listOf("audio/mpeg", "audio/wav")
    val StreamerSinkMimeTypes = listOf(
        "audio/mpeg",
        "audio/wav",
        "audio/flac",
        "audio/x-dsf",
        "audio/x-dff",
        "audio/x-dsd",
    )

    private val tvName = Regex(
        """tv|bravia|webos|roku|chromecast|android tv|google tv|samsung|lg tv|tcl|hisense|xiaomi tv|mi tv|电视|電視|テレビ""",
        RegexOption.IGNORE_CASE,
    )

    fun looksLikeTv(renderer: EchoLanRenderer): Boolean = looksLikeTv(
        name = renderer.name,
        manufacturer = renderer.manufacturer,
        model = renderer.model,
    )

    fun looksLikeTv(name: String?, manufacturer: String? = null, model: String? = null): Boolean {
        val text = listOf(name, manufacturer, model)
            .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
            .joinToString(" ")
        return text.isNotEmpty() && tvName.containsMatchIn(text)
    }

    fun defaultSinkMimeTypes(renderer: EchoLanRenderer): List<String> =
        if (looksLikeTv(renderer)) TvSinkMimeTypes else StreamerSinkMimeTypes

    fun sinkMimeTypes(renderer: EchoLanRenderer): List<String> {
        val advertised = renderer.sinkMimeTypes.map(::normalizeMime).filter { it.isNotEmpty() }.distinct()
        return advertised.ifEmpty { defaultSinkMimeTypes(renderer) }
    }

    fun offeredMime(item: EchoRemoteStreamItem): String {
        val explicit = item.audio?.mimeType?.trim()?.takeIf { it.isNotEmpty() }
        if (explicit != null) return normalizeMime(explicit)
        val codec = item.audio?.codec?.trim()?.lowercase()
        return when (codec) {
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "aac", "m4a", "alac" -> "audio/mp4"
            "ogg", "opus" -> "audio/ogg"
            "dsd", "dsf" -> "audio/x-dsf"
            "dff" -> "audio/x-dff"
            else -> normalizeMime(EchoLinkCastPolicy.mimeTypeForUri(item.streamUrl))
        }
    }

    fun isDsd(item: EchoRemoteStreamItem): Boolean {
        val codec = item.audio?.codec?.trim()?.lowercase().orEmpty()
        val mime = offeredMime(item)
        return codec == "dsd" || mime == "audio/x-dsf" || mime == "audio/x-dff" || mime == "audio/x-dsd"
    }

    fun canPlay(renderer: EchoLanRenderer, item: EchoRemoteStreamItem): Boolean =
        rejectReason(renderer, item) == null

    fun rejectReason(renderer: EchoLanRenderer, item: EchoRemoteStreamItem): EchoDlnaRejectReason? {
        if (renderer.kind == EchoLanRendererKind.Chromecast) return EchoDlnaRejectReason.Chromecast
        if (renderer.avTransport == null) return EchoDlnaRejectReason.NoAvTransport
        val dsd = isDsd(item)
        val tv = looksLikeTv(renderer)
        if (dsd && tv) return EchoDlnaRejectReason.DsdOnTv
        val mime = offeredMime(item)
        val sinks = sinkMimeTypes(renderer)
        if (sinks.none { sink -> mimeMatches(mime, sink) }) return EchoDlnaRejectReason.UnsupportedFormat
        return null
    }

    fun mimeMatches(offered: String, sink: String): Boolean {
        val left = normalizeMime(offered)
        val right = normalizeMime(sink)
        if (left == right) return true
        if (left == "audio/mp4" && (right == "audio/aac" || right == "audio/x-m4a")) return true
        if (right == "audio/mp4" && (left == "audio/aac" || left == "audio/x-m4a")) return true
        return false
    }

    private fun normalizeMime(mimeType: String): String = EchoDlnaDidl.normalizeMime(mimeType)
}
