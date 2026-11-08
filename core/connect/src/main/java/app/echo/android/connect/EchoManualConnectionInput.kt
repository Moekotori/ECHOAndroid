package app.echo.android.connect

import app.echo.android.model.connect.EchoRemoteEndpoint
import app.echo.android.model.connect.EchoSavedPcEndpoint

data class EchoManualConnection(val address: String, val token: String)

/** Builds the existing connect request without moving credentials to another origin. */
object EchoManualConnectionInput {
    /** Restore paired capabilities only for the same origin and the same access token. */
    fun endpointForConnection(
        address: String,
        token: String,
        savedPcs: List<EchoSavedPcEndpoint> = emptyList(),
        currentPc: EchoSavedPcEndpoint? = null,
    ): EchoRemoteEndpoint? {
        val endpoint = EchoPairingParser.parseManual(address, token) ?: return null
        if (endpoint.needsV2PairExchange || endpoint.token.isBlank()) return endpoint
        val saved = sequenceOf(currentPc).filterNotNull().plus(savedPcs.asSequence()).firstOrNull { candidate ->
            val known = EchoPairingParser.parseManual(candidate.address, candidate.token.orEmpty())
            known != null && !known.needsV2PairExchange && known.token == endpoint.token &&
                known.scheme.equals(endpoint.scheme, ignoreCase = true) &&
                known.host.equals(endpoint.host, ignoreCase = true) && known.port == endpoint.port
        }
        return endpoint.copy(supportsV2Events = saved?.supportsV2Events == true)
    }

    fun validPort(value: String): Boolean = value.isBlank() ||
        value.trim().toIntOrNull()?.let { it in 1..65535 } == true

    fun resolve(
        address: String,
        port: String,
        savedPcs: List<EchoSavedPcEndpoint> = emptyList(),
        currentPc: EchoSavedPcEndpoint? = null,
    ): EchoManualConnection? {
        val raw = address.trim()
        // A pairing capability owns its destination, protocol and credentials.
        EchoPairingParser.parse(raw)?.let { return EchoManualConnection(raw, "") }
        if (!validPort(port)) return null
        val parsed = EchoPairingParser.parseManual(raw) ?: return null
        val selectedPort = port.trim().toIntOrNull() ?: parsed.port
        val target = parsed.copy(port = selectedPort)
        fun credentialFrom(saved: EchoSavedPcEndpoint): String? {
            val token = saved.token?.takeIf { it.isNotBlank() } ?: return null
            val known = EchoPairingParser.parseManual(saved.address, token) ?: return null
            return known.token.takeIf {
                known.scheme.equals(target.scheme, ignoreCase = true) &&
                    known.host.equals(target.host, ignoreCase = true) && known.port == target.port
            }
        }
        val credential = currentPc?.let(::credentialFrom) ?: savedPcs.firstNotNullOfOrNull(::credentialFrom)
        return EchoManualConnection(origin(target), credential.orEmpty())
    }

    private fun origin(endpoint: EchoRemoteEndpoint): String {
        val host = if (':' in endpoint.host) "[${endpoint.host}]" else endpoint.host
        return "${endpoint.scheme}://$host:${endpoint.port}"
    }
}
