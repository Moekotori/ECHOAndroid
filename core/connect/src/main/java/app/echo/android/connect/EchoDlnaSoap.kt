package app.echo.android.connect

class EchoDlnaException(
    val statusCode: Int,
    message: String,
) : Exception(message)

fun interface EchoDlnaTransport {
    fun post(controlUrl: String, serviceType: String, action: String, args: Map<String, String>): String
}

object EchoDlnaSoap {
    fun envelope(serviceType: String, action: String, args: Map<String, String>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
        append("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" ")
        append("s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">")
        append("<s:Body>")
        append("<u:").append(action).append(" xmlns:u=\"").append(escapeXml(serviceType)).append("\">")
        args.forEach { (key, value) ->
            append('<').append(key).append('>')
            append(escapeXml(value))
            append("</").append(key).append('>')
        }
        append("</u:").append(action).append('>')
        append("</s:Body></s:Envelope>")
    }

    fun soapAction(serviceType: String, action: String): String = "\"$serviceType#$action\""

    fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    fun xmlText(xml: String, tag: String): String? {
        val regex = Regex(
            "<(?:[\\w.-]+:)?$tag(?:\\s[^>]*)?>([\\s\\S]*?)</(?:[\\w.-]+:)?$tag>",
            setOf(RegexOption.IGNORE_CASE),
        )
        val raw = regex.find(xml)?.groupValues?.getOrNull(1)?.trim() ?: return null
        return unescapeXml(raw).trim().takeIf { it.isNotEmpty() }
    }

    fun parseSinkMimeTypes(protocolInfoXml: String): List<String> {
        val sink = xmlText(protocolInfoXml, "Sink") ?: return emptyList()
        return sink.split(',')
            .mapNotNull { entry ->
                entry.split(':').getOrNull(2)?.substringBefore(';')?.trim()?.takeIf { it.isNotEmpty() }
            }
            .distinct()
    }

    fun parseTransportState(xml: String): String? = xmlText(xml, "CurrentTransportState")

    fun parseRelTimeMs(xml: String): Long? = parseDlnaTimeMs(xmlText(xml, "RelTime"))

    fun parseDurationMs(xml: String): Long? = parseDlnaTimeMs(xmlText(xml, "TrackDuration"))

    fun formatDlnaTime(positionMs: Long): String {
        val total = (positionMs / 1000L).coerceAtLeast(0L)
        val hours = total / 3600L
        val minutes = (total % 3600L) / 60L
        val seconds = total % 60L
        return "%d:%02d:%02d".format(hours, minutes, seconds)
    }

    fun parseDlnaTimeMs(value: String?): Long? {
        val clock = value?.trim()?.takeIf { it.isNotEmpty() && it != "NOT_IMPLEMENTED" } ?: return null
        val parts = clock.substringBefore('.').split(':')
        if (parts.size != 3) return null
        val hours = parts[0].toLongOrNull() ?: return null
        val minutes = parts[1].toLongOrNull() ?: return null
        val seconds = parts[2].toLongOrNull() ?: return null
        if (hours < 0L || minutes !in 0L..59L || seconds !in 0L..59L) return null
        return ((hours * 3600L) + (minutes * 60L) + seconds) * 1000L
    }

    fun faultMessage(xml: String): String? {
        val description = xmlText(xml, "errorDescription") ?: xmlText(xml, "faultstring")
        val code = xmlText(xml, "errorCode")
        return when {
            description != null && code != null -> "$code $description"
            description != null -> description
            code != null -> code
            else -> null
        }
    }

    private fun unescapeXml(value: String): String = value
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")
}
