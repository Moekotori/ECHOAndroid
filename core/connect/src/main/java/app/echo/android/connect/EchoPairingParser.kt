package app.echo.android.connect

import android.net.Uri
import app.echo.android.model.connect.EchoRemoteEndpoint
import java.net.URI
import java.util.Locale

object EchoPairingParser {
    const val DefaultPort = 26789

    fun parse(raw: String): EchoRemoteEndpoint? {
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
        if (uri.scheme != "echo") return null
        if (uri.host != "pair") return null
        val endpointHost = uri.getQueryParameter("host")?.takeIf { it.isNotBlank() } ?: return null
        val endpointPort = uri.getQueryParameter("port")?.toIntOrNull() ?: DefaultPort
        val token = uri.getQueryParameter("token")?.takeIf { it.length >= 16 } ?: return null
        val name = uri.getQueryParameter("name")?.takeIf { it.isNotBlank() } ?: "ECHO PC"
        val scheme = uri.getQueryParameter("scheme")
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it == "http" || it == "https" }
            ?: "http"
        return EchoRemoteEndpoint(
            id = "$endpointHost:$endpointPort",
            name = name,
            host = endpointHost,
            port = endpointPort,
            token = token,
            scheme = scheme,
        )
    }

    fun parseManual(address: String, token: String, name: String = "PC ECHO"): EchoRemoteEndpoint? {
        val rawAddress = address.trim().trimEnd('/').takeIf { it.isNotBlank() } ?: return null
        parse(rawAddress)?.let { endpoint ->
            val overrideToken = token.trim().takeIf { it.length >= 8 }
            return if (overrideToken == null) endpoint else endpoint.copy(token = overrideToken)
        }
        val safeToken = token.trim().takeIf { it.length >= 8 } ?: return null
        val normalized = if (rawAddress.contains("://")) rawAddress else "http://$rawAddress"
        val uri = runCatching { URI(normalized) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT)?.takeIf { it == "http" || it == "https" } ?: return null
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
        val port = uri.port.takeIf { it > 0 } ?: DefaultPort
        return EchoRemoteEndpoint(
            id = "$host:$port",
            name = name,
            host = host,
            port = port,
            token = safeToken,
            scheme = scheme,
        )
    }
}
