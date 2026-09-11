package app.echo.android.connect

import app.echo.android.model.connect.EchoDlnaService
import app.echo.android.model.connect.EchoLanRenderer
import app.echo.android.model.connect.EchoRemoteStreamItem
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class EchoDlnaClient(
    private val transport: EchoDlnaTransport = EchoDlnaHttpTransport(),
) {
    fun protocolInfoSinks(renderer: EchoLanRenderer): List<String> {
        val service = renderer.connectionManager ?: return emptyList()
        val xml = invoke(service, "GetProtocolInfo", emptyMap())
        return EchoDlnaSoap.parseSinkMimeTypes(xml)
    }

    fun play(
        renderer: EchoLanRenderer,
        item: EchoRemoteStreamItem,
        positionMs: Long = 0L,
        next: EchoRemoteStreamItem? = null,
    ) {
        val av = requireAvTransport(renderer)
        EchoDlnaCastPolicy.rejectReason(renderer, item)?.let { reason ->
            throw EchoDlnaException(415, reason.code)
        }
        val mime = EchoDlnaCastPolicy.offeredMime(item)
        invoke(
            av,
            "SetAVTransportURI",
            mapOf(
                "InstanceID" to "0",
                "CurrentURI" to item.streamUrl,
                "CurrentURIMetaData" to didl(item, mime),
            ),
        )
        if (next != null && EchoDlnaCastPolicy.canPlay(renderer, next)) {
            runCatching {
                invoke(
                    av,
                    "SetNextAVTransportURI",
                    mapOf(
                        "InstanceID" to "0",
                        "NextURI" to next.streamUrl,
                        "NextURIMetaData" to didl(next, EchoDlnaCastPolicy.offeredMime(next)),
                    ),
                )
            }
        }
        invoke(av, "Play", mapOf("InstanceID" to "0", "Speed" to "1"))
        if (positionMs > 0L) {
            runCatching { seek(renderer, positionMs) }
        }
    }

    fun pause(renderer: EchoLanRenderer) {
        invoke(requireAvTransport(renderer), "Pause", mapOf("InstanceID" to "0"))
    }

    fun resume(renderer: EchoLanRenderer) {
        invoke(requireAvTransport(renderer), "Play", mapOf("InstanceID" to "0", "Speed" to "1"))
    }

    fun stop(renderer: EchoLanRenderer) {
        invoke(requireAvTransport(renderer), "Stop", mapOf("InstanceID" to "0"))
    }

    fun seek(renderer: EchoLanRenderer, positionMs: Long) {
        invoke(
            requireAvTransport(renderer),
            "Seek",
            mapOf(
                "InstanceID" to "0",
                "Unit" to "REL_TIME",
                "Target" to EchoDlnaSoap.formatDlnaTime(positionMs),
            ),
        )
    }

    fun transportState(renderer: EchoLanRenderer): String? {
        val xml = invoke(requireAvTransport(renderer), "GetTransportInfo", mapOf("InstanceID" to "0"))
        return EchoDlnaSoap.parseTransportState(xml)
    }

    private fun didl(item: EchoRemoteStreamItem, mime: String): String = EchoDlnaDidl.build(
        id = item.id,
        streamUrl = item.streamUrl,
        title = item.title,
        artist = item.artist,
        album = item.album,
        artworkUrl = item.artworkUrl,
        mimeType = mime,
        durationMs = item.durationMs,
    )

    private fun requireAvTransport(renderer: EchoLanRenderer): EchoDlnaService =
        renderer.avTransport ?: throw EchoDlnaException(400, "no_avtransport")

    private fun invoke(service: EchoDlnaService, action: String, args: Map<String, String>): String =
        transport.post(service.controlUrl, service.serviceType, action, args)
}

class EchoDlnaHttpTransport(
    private val connectTimeoutMs: Int = 6_000,
    private val readTimeoutMs: Int = 6_000,
) : EchoDlnaTransport {
    override fun post(
        controlUrl: String,
        serviceType: String,
        action: String,
        args: Map<String, String>,
    ): String {
        if (!EchoLanRendererPolicy.isLanHttpUrl(controlUrl)) {
            throw EchoDlnaException(400, "control_url_must_be_lan")
        }
        val body = EchoDlnaSoap.envelope(serviceType, action, args)
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        val connection = (URL(controlUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = false
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
            setRequestProperty("Content-Length", bytes.size.toString())
            setRequestProperty("SOAPAction", EchoDlnaSoap.soapAction(serviceType, action))
        }
        return try {
            connection.outputStream.use { it.write(bytes) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use { it.readBytes().toString(StandardCharsets.UTF_8) }.orEmpty()
            if (status !in 200..299) {
                val fault = EchoDlnaSoap.faultMessage(text)
                throw EchoDlnaException(status, fault ?: "soap_failed")
            }
            text
        } finally {
            connection.disconnect()
        }
    }
}
