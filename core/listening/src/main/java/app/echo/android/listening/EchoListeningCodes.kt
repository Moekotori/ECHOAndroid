package app.echo.android.listening

import app.echo.android.model.listening.EchoListeningInvite
import java.util.Base64
import org.json.JSONObject

object EchoListeningCodes {
    private const val PREFIX = "echo-listen:"
    private const val MAX_INPUT = 2048

    fun parse(raw: String): EchoListeningInvite? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_INPUT) return null
        val codeAt = trimmed.indexOf(PREFIX)
        if (codeAt < 0) return invite(trimmed, null, null)
        val encoded = trimmed.substring(codeAt + PREFIX.length).takeWhile { !it.isWhitespace() }
        val json = decodeInvite(encoded) ?: return null
        val server = json.optString("server").trim()
        if (server.isEmpty() || server.length > 512) return null
        val roomId = optional(json, "roomId", 64)?.takeIf { ROOM_ID.matches(it) }
        val invitation = optional(json, "invitation", 128)?.takeIf { INVITATION.matches(it) }
        if (json.has("roomId") && !json.isNull("roomId") && roomId == null) return null
        if (json.has("invitation") && !json.isNull("invitation") && invitation == null) return null
        return invite(server, roomId, invitation)
    }

    fun socketUrl(server: String): String? {
        val trimmed = server.trim().trimEnd('/')
        if (trimmed.isEmpty() || trimmed.length > 512) return null
        val withScheme = when {
            trimmed.startsWith("https://") -> "wss://" + trimmed.removePrefix("https://")
            trimmed.startsWith("http://") -> "ws://" + trimmed.removePrefix("http://")
            trimmed.startsWith("wss://") || trimmed.startsWith("ws://") -> trimmed
            else -> return null
        }
        val schemeEnd = withScheme.indexOf("://")
        val rest = withScheme.substring(schemeEnd + 3)
        if (rest.isEmpty() || rest.startsWith("/") || rest.contains('@')) return null
        val slash = rest.indexOf('/')
        val hostPort = if (slash < 0) rest else rest.substring(0, slash)
        val path = if (slash < 0) "" else rest.substring(slash)
        if (hostPort.isEmpty() || hostPort.any { it.isWhitespace() || it == '#' || it == '?' }) return null
        if (path.any { it == '#' || it == '?' }) return null
        if (withScheme.startsWith("ws://") && !isPrivateHost(hostPort)) return null
        val origin = withScheme.substring(0, schemeEnd + 3) + hostPort
        return when (path) {
            "", "/" -> "$origin/v1/socket"
            "/v1/socket" -> "$origin/v1/socket"
            else -> null
        }
    }

    fun displayServer(socketUrl: String): String = socketUrl.removeSuffix("/v1/socket")

    fun sanitizeName(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.length > 48) return null
        if (trimmed.any { it <= '\u001f' || it == '\u007f' }) return null
        return trimmed
    }

    private fun invite(server: String, roomId: String?, invitation: String?): EchoListeningInvite? {
        if (socketUrl(server) == null) return null
        return EchoListeningInvite(server.trim().trimEnd('/'), roomId, invitation)
    }

    private fun optional(json: JSONObject, name: String, max: Int): String? {
        if (!json.has(name) || json.isNull(name)) return null
        val value = json.optString(name).trim()
        if (value.isEmpty() || value.length > max) return null
        if (value.any { it <= '\u001f' || it == '\u007f' }) return null
        return value
    }

    private fun padBase64Url(encoded: String): String = when (encoded.length % 4) {
        0 -> encoded
        2 -> "$encoded=="
        3 -> "$encoded="
        else -> encoded
    }

    private fun isPrivateHost(hostPort: String): Boolean {
        val host = when {
            hostPort.startsWith("[") -> hostPort.substringAfter("[").substringBefore("]")
            else -> hostPort.substringBefore(":")
        }
        if (host == "localhost" || host == "127.0.0.1" || host == "::1") return true
        val parts = host.split('.')
        if (parts.size != 4) return false
        val numbers = parts.map { it.toIntOrNull() ?: return false }
        if (numbers.any { it !in 0..255 }) return false
        return numbers[0] == 10 ||
            (numbers[0] == 192 && numbers[1] == 168) ||
            (numbers[0] == 172 && numbers[1] in 16..31)
    }

    private val ROOM_ID = Regex("^[A-Za-z0-9-]{1,64}$")
    private val INVITATION = Regex("^[A-Za-z0-9_-]{1,128}$")

    private fun decodeInvite(encoded: String): JSONObject? {
        if (encoded.isEmpty() || encoded.length > MAX_INPUT) return null
        return try {
            val bytes = Base64.getUrlDecoder().decode(padBase64Url(encoded))
            if (bytes.size > 4096) null else JSONObject(String(bytes, Charsets.UTF_8))
        } catch (_: Exception) {
            null
        }
    }
}
