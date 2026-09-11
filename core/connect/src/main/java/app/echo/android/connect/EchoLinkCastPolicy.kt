package app.echo.android.connect

import app.echo.android.model.connect.EchoRemoteTrack
import app.echo.android.model.library.LibrarySource
import app.echo.android.model.playback.EchoLinkPlaybackUri
import java.security.SecureRandom

data class EchoLinkCastSourceTrack(
    val id: String,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUri: String? = null,
    val durationMs: Long = 0L,
    val mimeType: String? = null,
    val sourceId: String? = null,
    val sampleRateHz: Int? = null,
    val bitDepth: Int? = null,
    val channelCount: Int? = null,
    val codec: String? = null,
)

sealed interface EchoLinkCastPlan {
    data class HandoffPcLibrary(
        val tracks: List<EchoRemoteTrack>,
        val startIndex: Int,
    ) : EchoLinkCastPlan

    data class LocalHttp(
        val tracks: List<EchoLinkCastSourceTrack>,
        val startIndex: Int,
    ) : EchoLinkCastPlan

    data class Blocked(
        val reason: EchoLinkCastBlockReason,
    ) : EchoLinkCastPlan
}

enum class EchoLinkCastBlockReason {
    EmptyQueue,
    UnsupportedSource,
}

object EchoLinkCastPolicy {
    const val MaxQueueSize = 50
    const val PathPrefix = "/echo-link/cast/"
    const val ArtworkPathPrefix = "/echo-link/cast-art/"
    const val MaxConnections = 4
    const val TokenLength = 32
    const val IdleTimeoutMs = 20L * 60L * 1000L
    const val CopyBufferBytes = 128 * 1024
    const val SendBufferBytes = 256 * 1024
    const val HeaderSniffBytes = 256
    const val QualityOriginal = "original"

    fun kind(id: String, uri: String, sourceId: String?): EchoLinkCastKind {
        if (EchoLinkPlaybackUri.trackId(id, uri) != null) return EchoLinkCastKind.PcLibrary
        if (isLocalFileUri(uri) || isRemoteHttpUri(uri) || isLocalSource(sourceId)) {
            return EchoLinkCastKind.PhoneServed
        }
        return EchoLinkCastKind.Unsupported
    }

    fun plan(tracks: List<EchoLinkCastSourceTrack>, startIndex: Int): EchoLinkCastPlan {
        if (tracks.isEmpty()) return EchoLinkCastPlan.Blocked(EchoLinkCastBlockReason.EmptyQueue)
        val start = startIndex.coerceIn(0, tracks.lastIndex)
        val startKind = kind(tracks[start].id, tracks[start].uri, tracks[start].sourceId)
        if (startKind == EchoLinkCastKind.Unsupported) {
            return EchoLinkCastPlan.Blocked(EchoLinkCastBlockReason.UnsupportedSource)
        }
        val run = tracks.drop(start).takeWhile { track ->
            kind(track.id, track.uri, track.sourceId) == startKind
        }.take(MaxQueueSize)
        if (run.isEmpty()) return EchoLinkCastPlan.Blocked(EchoLinkCastBlockReason.EmptyQueue)
        return when (startKind) {
            EchoLinkCastKind.PcLibrary -> EchoLinkCastPlan.HandoffPcLibrary(
                tracks = run.map { track ->
                    EchoRemoteTrack(
                        id = EchoLinkPlaybackUri.trackId(track.id, track.uri),
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        artworkUrl = track.artworkUri,
                        durationMs = track.durationMs,
                    )
                },
                startIndex = 0,
            )
            EchoLinkCastKind.PhoneServed -> EchoLinkCastPlan.LocalHttp(tracks = run, startIndex = 0)
            EchoLinkCastKind.Unsupported -> EchoLinkCastPlan.Blocked(EchoLinkCastBlockReason.UnsupportedSource)
        }
    }

    fun isLocalFileUri(uri: String): Boolean {
        val trimmed = uri.trim()
        return trimmed.startsWith("content:", ignoreCase = true) ||
            trimmed.startsWith("file:", ignoreCase = true)
    }

    fun isRemoteHttpUri(uri: String): Boolean {
        val trimmed = uri.trim()
        return trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
    }

    fun isLocalSource(sourceId: String?): Boolean {
        val id = sourceId?.trim()?.lowercase().orEmpty()
        return id == LibrarySource.MediaStore.id || id == LibrarySource.Saf.id
    }

    fun isCastToken(token: String): Boolean =
        token.length in 16..64 && token.all { ch -> ch.isLetterOrDigit() || ch == '-' || ch == '_' }

    fun newToken(): String {
        val bytes = ByteArray(TokenLength / 2)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { byte -> "%02x".format(byte) }
    }

    fun pickLanIpv4(hosts: Iterable<String?>): String? {
        val cleaned = hosts.mapNotNull { host -> host?.trim()?.takeIf { it.isNotEmpty() } }
        return cleaned.firstOrNull(::isPrivateIpv4)
            ?: cleaned.firstOrNull { host -> isIpv4(host) && !isLoopbackIpv4(host) }
    }

    fun advertisedBaseUrl(host: String, port: Int): String {
        val hostPart = if (':' in host && !host.startsWith('[')) "[$host]" else host
        return "http://$hostPart:$port"
    }

    fun streamUrl(host: String, port: Int, token: String): String =
        advertisedBaseUrl(host, port) + PathPrefix + token

    fun artworkUrl(host: String, port: Int, token: String): String =
        advertisedBaseUrl(host, port) + ArtworkPathPrefix + token

    fun imageMimeType(uri: String): String {
        val ext = uri.substringBefore('?').substringAfterLast('.', "").lowercase()
        return when (ext) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "image/jpeg"
        }
    }

    fun trackCount(plan: EchoLinkCastPlan): Int = when (plan) {
        is EchoLinkCastPlan.HandoffPcLibrary -> plan.tracks.size
        is EchoLinkCastPlan.LocalHttp -> plan.tracks.size
        is EchoLinkCastPlan.Blocked -> 0
    }

    fun isDsd(codec: String?): Boolean =
        codec?.equals("dsd", ignoreCase = true) == true

    fun mimeTypeForUri(uri: String, explicit: String? = null): String {
        explicit?.trim()?.takeIf { it.isNotEmpty() && it != "application/octet-stream" }?.let { return it }
        val path = uri.substringBefore('?').substringAfterLast('/')
        val ext = path.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "aac", "m4a" -> "audio/mp4"
            "ogg", "oga" -> "audio/ogg"
            "opus" -> "audio/ogg"
            "aiff", "aif" -> "audio/aiff"
            "alac" -> "audio/mp4"
            "dsf" -> "audio/x-dsf"
            "dff", "dsd" -> "audio/x-dff"
            "wv" -> "audio/x-wavpack"
            "ape" -> "audio/x-ape"
            "tak" -> "audio/x-tak"
            "pcm" -> "audio/pcm"
            else -> "application/octet-stream"
        }
    }

    fun contentRangeTotal(header: String?): Long? {
        val raw = header?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val total = raw.substringAfterLast('/', "").trim()
        if (total.isEmpty() || total == "*") return null
        return total.toLongOrNull()?.takeIf { it > 0L }
    }

    fun parseRange(header: String?, totalLength: Long?): Pair<Long, Long?>? {
        val raw = header?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (!raw.startsWith("bytes=", ignoreCase = true)) return null
        val spec = raw.removePrefix("bytes=").trim()
        if (spec.startsWith('-')) return null
        val startText = spec.substringBefore('-')
        val endText = spec.substringAfter('-', "")
        val start = startText.toLongOrNull() ?: return null
        if (start < 0L) return null
        val end = endText.takeIf { it.isNotEmpty() }?.toLongOrNull()
        if (end != null && end < start) return null
        if (totalLength != null && start >= totalLength) return null
        val boundedEnd = when {
            end == null -> totalLength?.minus(1L)
            totalLength == null -> end
            else -> minOf(end, totalLength - 1L)
        }
        return start to boundedEnd
    }

    fun isUnsupportedRemoteStreamCommand(error: Throwable): Boolean {
        val code = (error as? EchoLinkHttpException)?.statusCode ?: return false
        return code == 400 || code == 404 || code == 405 || code == 501
    }

    fun peerMatches(allowedHost: String?, remoteHost: String?): Boolean {
        val allowed = normalizeHost(allowedHost) ?: return true
        val remote = normalizeHost(remoteHost) ?: return true
        if (allowed.equals(remote, ignoreCase = true)) return true
        // Hostname allow-lists cannot be compared to a TCP peer IP; the path token is the gate.
        if (!isIpv4(allowed) && !looksLikeIpv6(allowed)) return true
        return false
    }

    fun normalizeHost(host: String?): String? {
        var value = host?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (value.startsWith('[') && value.endsWith(']') && value.length > 2) {
            value = value.substring(1, value.length - 1)
        }
        value = value.substringBefore('%')
        val mapped = value.lowercase()
        return if (mapped.startsWith("::ffff:")) mapped.removePrefix("::ffff:") else value
    }

    private fun isIpv4(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false
        return parts.all { part -> part.toIntOrNull()?.let { it in 0..255 } == true }
    }

    private fun isLoopbackIpv4(host: String): Boolean = host == "127.0.0.1"

    private fun isPrivateIpv4(host: String): Boolean {
        if (!isIpv4(host) || isLoopbackIpv4(host)) return false
        val parts = host.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) return false
        val first = parts[0]
        val second = parts[1]
        return first == 10 ||
            first == 192 && second == 168 ||
            first == 172 && second in 16..31
    }

    private fun looksLikeIpv6(host: String): Boolean = host.contains(':') && !isIpv4(host)
}

enum class EchoLinkCastKind {
    PcLibrary,
    PhoneServed,
    Unsupported,
}
