package app.echo.android.connect

import android.net.Uri

object EchoPairingParser {
    fun parse(raw: String): EchoRemoteEndpoint? {
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
        if (uri.scheme != "echo") return null
        if (uri.host != "pair") return null
        val endpointHost = uri.getQueryParameter("host")?.takeIf { it.isNotBlank() } ?: return null
        val endpointPort = uri.getQueryParameter("port")?.toIntOrNull() ?: return null
        val token = uri.getQueryParameter("token")?.takeIf { it.length >= 16 } ?: return null
        val name = uri.getQueryParameter("name")?.takeIf { it.isNotBlank() } ?: "ECHO PC"
        return EchoRemoteEndpoint(
            id = "$endpointHost:$endpointPort",
            name = name,
            host = endpointHost,
            port = endpointPort,
            token = token,
        )
    }
}
