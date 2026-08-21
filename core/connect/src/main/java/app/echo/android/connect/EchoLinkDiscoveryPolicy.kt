package app.echo.android.connect

import app.echo.android.model.connect.EchoLinkLanDevice

object EchoLinkDiscoveryPolicy {
    const val ServiceType = "_echo-link._tcp."

    fun deviceFromResolved(
        serviceName: String,
        host: String?,
        port: Int,
        txt: Map<String, String>,
        defaultPort: Int = EchoPairingParser.DefaultPort,
    ): EchoLinkLanDevice? {
        val endpointHost = host?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val endpointPort = port.takeIf { it > 0 } ?: defaultPort
        if (endpointPort <= 0) return null
        val name = txt["name"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: serviceName.trim().takeIf { it.isNotEmpty() }
            ?: "PC ECHO"
        val deviceId = txt["deviceId"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: hostPortKey(endpointHost, endpointPort)
        val version = txt["version"]?.toIntOrNull()?.takeIf { it > 0 } ?: 1
        return EchoLinkLanDevice(
            id = deviceId,
            name = name,
            host = endpointHost,
            port = endpointPort,
            version = version,
            requiresPairing = true,
            serviceName = serviceName,
        )
    }

    fun upsertDevice(existing: List<EchoLinkLanDevice>, incoming: EchoLinkLanDevice): List<EchoLinkLanDevice> {
        val without = existing.filterNot { device ->
            device.id == incoming.id ||
                device.serviceName == incoming.serviceName ||
                (
                    device.host.equals(incoming.host, ignoreCase = true) &&
                        device.port == incoming.port
                    )
        }
        return without + incoming
    }

    fun removeService(existing: List<EchoLinkLanDevice>, serviceName: String): List<EchoLinkLanDevice> =
        existing.filterNot { it.serviceName == serviceName }

    fun addressLabel(device: EchoLinkLanDevice): String = hostPortKey(device.host, device.port)

    fun pickHost(hosts: Iterable<String?>): String? {
        val cleaned = hosts.mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
        if (cleaned.isEmpty()) return null
        return cleaned.firstOrNull(::isIpv4)
            ?: cleaned.firstOrNull { host -> !isLinkLocalIpv6(host) }
            ?: cleaned.first()
    }

    fun addressMatchesDevice(address: String?, device: EchoLinkLanDevice): Boolean {
        val parsed = parseLanHostPort(address) ?: return false
        return parsed.first.equals(device.host, ignoreCase = true) && parsed.second == device.port
    }

    fun tokenAfterSelecting(
        device: EchoLinkLanDevice,
        savedAddress: String?,
        savedToken: String?,
    ): String {
        val token = savedToken?.trim().orEmpty()
        if (token.isEmpty()) return ""
        return if (addressMatchesDevice(savedAddress, device)) token else ""
    }

    fun parseLanHostPort(raw: String?): Pair<String, Int>? {
        val trimmed = raw?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: return null
        val hostPort = trimmed
            .substringAfter("://", trimmed)
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
        if (hostPort.isBlank()) return null
        if (hostPort.startsWith('[')) {
            val close = hostPort.indexOf(']')
            if (close <= 1) return null
            val host = hostPort.substring(1, close).trim().takeIf { it.isNotEmpty() } ?: return null
            val after = hostPort.substring(close + 1)
            val port = when {
                after.isEmpty() -> EchoPairingParser.DefaultPort
                after.startsWith(':') -> after.drop(1).toIntOrNull() ?: return null
                else -> return null
            }
            if (port <= 0) return null
            return host to port
        }
        if (hostPort.contains('[')) return null
        val host = hostPort.substringBefore(':').trim().takeIf { it.isNotEmpty() } ?: return null
        val portPart = hostPort.substringAfter(':', missingDelimiterValue = "")
        val port = if (portPart.isEmpty()) {
            EchoPairingParser.DefaultPort
        } else {
            portPart.toIntOrNull() ?: return null
        }
        if (port <= 0) return null
        return host to port
    }

    private fun isIpv4(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false
        return parts.all { part -> part.toIntOrNull()?.let { it in 0..255 } == true }
    }

    private fun isLinkLocalIpv6(host: String): Boolean {
        val normalized = host.lowercase()
        return normalized.startsWith("fe80:") || normalized.contains('%')
    }

    private fun looksLikeIpv6(host: String): Boolean =
        host.contains(':') && !isIpv4(host)

    private fun hostPortKey(host: String, port: Int): String =
        if (looksLikeIpv6(host)) "[$host]:$port" else "$host:$port"

    fun decodeTxt(attributes: Map<String, ByteArray?>): Map<String, String> =
        attributes.mapNotNull { (key, value) ->
            val safeKey = key.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val text = value?.toString(Charsets.UTF_8)?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            safeKey to text
        }.toMap()
}
