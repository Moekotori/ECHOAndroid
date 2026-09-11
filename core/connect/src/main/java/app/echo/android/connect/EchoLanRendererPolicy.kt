package app.echo.android.connect

import app.echo.android.model.connect.EchoDlnaService
import app.echo.android.model.connect.EchoLanRenderer
import app.echo.android.model.connect.EchoLanRendererKind
import java.net.URI

data class EchoSsdpAdvertisement(
    val location: String,
    val usn: String?,
    val st: String?,
    val nt: String?,
)

object EchoLanRendererPolicy {
    const val SsdpHost = "239.255.255.250"
    const val SsdpPort = 1900
    const val MaxDevices = 24
    const val MaxDescriptionBytes = 32 * 1024
    const val ChromecastServiceType = "_googlecast._tcp."
    val SearchTargets = listOf(
        "urn:schemas-upnp-org:device:MediaRenderer:1",
        "urn:schemas-upnp-org:service:AVTransport:1",
    )

    fun searchMessage(st: String, mxSeconds: Int = 2): String = buildString {
        append("M-SEARCH * HTTP/1.1\r\n")
        append("HOST: $SsdpHost:$SsdpPort\r\n")
        append("MAN: \"ssdp:discover\"\r\n")
        append("MX: ${mxSeconds.coerceIn(1, 5)}\r\n")
        append("ST: $st\r\n")
        append("\r\n")
    }

    fun parseResponse(raw: String): EchoSsdpAdvertisement? {
        val lines = raw.replace("\r\n", "\n").split('\n')
        val start = lines.firstOrNull()?.uppercase().orEmpty()
        if (!start.contains("HTTP/") || (!start.contains("200") && !start.startsWith("NOTIFY"))) {
            return null
        }
        val headers = mutableMapOf<String, String>()
        lines.drop(1).forEach { line ->
            val index = line.indexOf(':')
            if (index <= 0) return@forEach
            headers[line.substring(0, index).trim().lowercase()] = line.substring(index + 1).trim()
        }
        val location = headers["location"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (!isLanHttpUrl(location)) return null
        val st = headers["st"]
        val nt = headers["nt"]
        val usn = headers["usn"]
        if (!isRendererAdvertisement(st, nt, usn)) return null
        return EchoSsdpAdvertisement(location = location, usn = usn, st = st, nt = nt)
    }

    fun isRendererAdvertisement(st: String?, nt: String?, usn: String?): Boolean {
        val haystack = listOfNotNull(st, nt, usn).joinToString(" ").lowercase()
        if (haystack.isBlank()) return false
        return "mediarenderer" in haystack || "avtransport" in haystack
    }

    fun isLanHttpUrl(url: String): Boolean {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        if (host == "127.0.0.1" || host.equals("localhost", true) || host.equals("::1", true)) return false
        if (host.endsWith(".local", ignoreCase = true)) return true
        val parts = host.split('.')
        if (parts.size == 4) {
            val first = parts[0].toIntOrNull() ?: return false
            val second = parts[1].toIntOrNull() ?: return false
            if (parts.any { it.toIntOrNull()?.let { n -> n in 0..255 } != true }) return false
            return first == 10 || first == 192 && second == 168 || first == 172 && second in 16..31
        }
        val lower = host.lowercase()
        return lower.startsWith("fd") || lower.startsWith("fe80:")
    }

    fun parseDescription(xml: String, location: String? = null): EchoUPnPDeviceInfo? {
        val body = xml.trim().takeIf { it.isNotEmpty() } ?: return null
        val devices = xmlBlocks(body, "device")
        val deviceBlock = devices.find { block ->
            xmlTag(block, "deviceType").orEmpty().contains("MediaRenderer", ignoreCase = true)
        } ?: devices.firstOrNull() ?: body
        val lower = deviceBlock.lowercase()
        val renderer = "device:mediarenderer" in lower
        val avTransport = "service:avtransport" in lower || "service:avtransport" in body.lowercase()
        if (!renderer && !avTransport) return null
        if (!renderer && "device:mediaserver" in lower) return null
        val name = xmlTag(deviceBlock, "friendlyName")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val base = xmlTag(body, "URLBase")?.trim()?.takeIf { it.isNotEmpty() }
            ?: location?.trim()?.takeIf { it.isNotEmpty() }
            ?: ""
        return EchoUPnPDeviceInfo(
            name = name,
            manufacturer = xmlTag(deviceBlock, "manufacturer")?.trim()?.takeIf { it.isNotEmpty() },
            model = xmlTag(deviceBlock, "modelName")?.trim()?.takeIf { it.isNotEmpty() }
                ?: xmlTag(deviceBlock, "modelNumber")?.trim()?.takeIf { it.isNotEmpty() },
            avTransport = parseService(deviceBlock, "AVTransport", base)
                ?: parseService(body, "AVTransport", base),
            renderingControl = parseService(deviceBlock, "RenderingControl", base)
                ?: parseService(body, "RenderingControl", base),
            connectionManager = parseService(deviceBlock, "ConnectionManager", base)
                ?: parseService(body, "ConnectionManager", base),
        )
    }

    fun rendererFromDescription(
        location: String,
        xml: String,
        usn: String?,
    ): EchoLanRenderer? {
        val info = parseDescription(xml, location) ?: return null
        val uri = runCatching { URI(location.trim()) }.getOrNull() ?: return null
        val host = uri.host?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val port = uri.port.takeIf { it > 0 } ?: if (uri.scheme.equals("https", true)) 443 else 80
        val id = usn?.substringBefore("::")?.trim()?.takeIf { it.isNotEmpty() }
            ?: "dlna:$host:$port:${info.name}"
        return EchoLanRenderer(
            id = id,
            name = info.name,
            host = host,
            port = port,
            kind = EchoLanRendererKind.Dlna,
            manufacturer = info.manufacturer,
            model = info.model,
            location = location,
            avTransport = info.avTransport,
            renderingControl = info.renderingControl,
            connectionManager = info.connectionManager,
        )
    }

    fun absoluteUrl(raw: String?, base: String): String? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val resolved = runCatching {
            if (base.isBlank()) URI(value).toString() else URI(base).resolve(value).toString()
        }.getOrNull() ?: return null
        return resolved.takeIf(::isLanHttpUrl)
    }

    fun chromecastFromResolved(
        serviceName: String,
        host: String?,
        port: Int,
        txt: Map<String, String>,
    ): EchoLanRenderer? {
        val endpointHost = host?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val name = txt["fn"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: txt["md"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: serviceName.trim().takeIf { it.isNotEmpty() }
            ?: return null
        val id = txt["id"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: "cast:$endpointHost:$port"
        return EchoLanRenderer(
            id = id,
            name = name,
            host = endpointHost,
            port = port.takeIf { it > 0 } ?: 8009,
            kind = EchoLanRendererKind.Chromecast,
            manufacturer = txt["ve"]?.trim()?.takeIf { it.isNotEmpty() },
            model = txt["md"]?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    fun upsert(existing: List<EchoLanRenderer>, incoming: EchoLanRenderer): List<EchoLanRenderer> {
        val without = existing.filterNot { device ->
            device.id == incoming.id ||
                (
                    device.host.equals(incoming.host, ignoreCase = true) &&
                        device.kind == incoming.kind &&
                        device.name.equals(incoming.name, ignoreCase = true)
                    )
        }
        return (without + incoming).take(MaxDevices)
    }

    private fun parseService(xml: String, name: String, base: String): EchoDlnaService? {
        val block = xmlBlocks(xml, "service").find { service ->
            xmlTag(service, "serviceType").orEmpty().contains(":$name:", ignoreCase = true)
        } ?: return null
        val serviceType = xmlTag(block, "serviceType")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val controlUrl = absoluteUrl(xmlTag(block, "controlURL"), base) ?: return null
        return EchoDlnaService(serviceType = serviceType, controlUrl = controlUrl)
    }

    private fun xmlBlocks(xml: String, tag: String): List<String> {
        val regex = Regex(
            "<(?:[\\w.-]+:)?$tag(?:\\s[^>]*)?>[\\s\\S]*?</(?:[\\w.-]+:)?$tag>",
            setOf(RegexOption.IGNORE_CASE),
        )
        return regex.findAll(xml).map { it.value }.toList()
    }

    private fun xmlTag(xml: String, tag: String): String? {
        val regex = Regex(
            "<(?:[\\w.-]+:)?$tag(?:\\s[^>]*)?>([^<]*)</(?:[\\w.-]+:)?$tag>",
            setOf(RegexOption.IGNORE_CASE),
        )
        return regex.find(xml)?.groupValues?.getOrNull(1)?.replace("&amp;", "&")
            ?.replace("&lt;", "<")
            ?.replace("&gt;", ">")
            ?.replace("&quot;", "\"")
    }
}

data class EchoUPnPDeviceInfo(
    val name: String,
    val manufacturer: String? = null,
    val model: String? = null,
    val avTransport: EchoDlnaService? = null,
    val renderingControl: EchoDlnaService? = null,
    val connectionManager: EchoDlnaService? = null,
)
