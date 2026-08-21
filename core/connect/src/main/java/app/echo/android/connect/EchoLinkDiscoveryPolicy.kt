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
            ?: "$endpointHost:$endpointPort"
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
        val without = existing.filterNot { it.id == incoming.id || it.serviceName == incoming.serviceName }
        return without + incoming
    }

    fun removeService(existing: List<EchoLinkLanDevice>, serviceName: String): List<EchoLinkLanDevice> =
        existing.filterNot { it.serviceName == serviceName }

    fun addressLabel(device: EchoLinkLanDevice): String = "${device.host}:${device.port}"

    fun decodeTxt(attributes: Map<String, ByteArray?>): Map<String, String> =
        attributes.mapNotNull { (key, value) ->
            val safeKey = key.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val text = value?.toString(Charsets.UTF_8)?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            safeKey to text
        }.toMap()
}
