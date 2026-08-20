package app.echo.android.connect

import app.echo.android.model.connect.EchoProtocolVersion
import app.echo.android.model.connect.EchoRemoteEndpoint
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

object EchoPairingParser {
    const val DefaultPort = 26789

    fun parse(raw: String): EchoRemoteEndpoint? {
        val trimmed = raw.trim().takeIf { it.isNotEmpty() } ?: return null
        parseRemotePage(trimmed)?.let { return it }
        return parseEchoPairUri(trimmed)
    }

    fun parseManual(address: String, token: String, name: String = "PC ECHO"): EchoRemoteEndpoint? {
        val rawAddress = address.trim().trimEnd('/').takeIf { it.isNotBlank() } ?: return null
        parse(rawAddress)?.let { endpoint ->
            val overrideToken = token.trim().takeIf { it.length >= 8 }
            return if (overrideToken == null) endpoint else endpoint.copy(
                token = overrideToken,
                pairingId = null,
                pairingSecret = null,
            )
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

    private fun parseRemotePage(raw: String): EchoRemoteEndpoint? {
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        if (scheme != "http" && scheme != "https") {
            return null
        }
        val fragment = uri.rawFragment ?: uri.fragment ?: return null
        val pairValue = queryParams(fragment)["pair"]?.takeIf { it.isNotBlank() } ?: return null
        return parseEchoPairUri(pairValue)
    }

    private fun parseEchoPairUri(raw: String): EchoRemoteEndpoint? {
        val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
        if (uri.scheme != "echo") {
            return null
        }
        if (uri.host != "pair" && uri.authority != "pair") {
            return null
        }
        val params = queryParams(uri.rawQuery ?: uri.query)
        val endpointHost = params["host"]?.takeIf { it.isNotBlank() } ?: return null
        val endpointPort = params["port"]?.toIntOrNull() ?: DefaultPort
        val name = params["name"]?.takeIf { it.isNotBlank() } ?: "ECHO PC"
        val scheme = params["scheme"]
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it == "http" || it == "https" }
            ?: "http"
        val pairingId = params["pairingId"]?.takeIf { it.isNotBlank() }
        val pairingSecret = params["secret"]?.takeIf { it.isNotBlank() }
        val token = params["token"]?.takeIf { it.length >= 16 }
        val version = params["version"]?.toIntOrNull()
        if (pairingId != null && pairingSecret != null) {
            return EchoRemoteEndpoint(
                id = "$endpointHost:$endpointPort",
                name = name,
                host = endpointHost,
                port = endpointPort,
                token = token ?: pairingSecret,
                scheme = scheme,
                protocolVersion = EchoProtocolVersion(version ?: 2, 0),
                pairingId = pairingId,
                pairingSecret = pairingSecret,
            )
        }
        if (token == null) {
            return null
        }
        return EchoRemoteEndpoint(
            id = "$endpointHost:$endpointPort",
            name = name,
            host = endpointHost,
            port = endpointPort,
            token = token,
            scheme = scheme,
            protocolVersion = EchoProtocolVersion(version ?: 1, 0),
        )
    }

    private fun queryParams(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) {
            return emptyMap()
        }
        return rawQuery.split('&').mapNotNull { part ->
            if (part.isBlank()) {
                return@mapNotNull null
            }
            val separator = part.indexOf('=')
            if (separator <= 0) {
                return@mapNotNull null
            }
            val key = decodeComponent(part.substring(0, separator))
            val value = decodeComponent(part.substring(separator + 1))
            if (key.isBlank()) null else key to value
        }.toMap()
    }

    private fun decodeComponent(value: String): String =
        runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }.getOrDefault(value)
}
