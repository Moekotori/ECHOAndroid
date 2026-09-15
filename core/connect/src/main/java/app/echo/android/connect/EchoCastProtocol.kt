package app.echo.android.connect

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

internal data class EchoCastMessage(
    val sourceId: String,
    val destinationId: String,
    val namespace: String,
    val payloadUtf8: String,
)

internal object EchoCastProtocol {
    const val ConnectionNamespace = "urn:x-cast:com.google.cast.tp.connection"
    const val HeartbeatNamespace = "urn:x-cast:com.google.cast.tp.heartbeat"
    const val ReceiverNamespace = "urn:x-cast:com.google.cast.receiver"
    const val MediaNamespace = "urn:x-cast:com.google.cast.media"
    const val SenderId = "sender-0"
    const val ReceiverId = "receiver-0"
    const val MaxMessageBytes = 64 * 1024

    fun connectJson(): String = """{"type":"CONNECT"}"""
    fun pingJson(): String = """{"type":"PING"}"""
    fun launchJson(requestId: Int, appId: String): String =
        """{"type":"LAUNCH","appId":${jsonString(appId)},"requestId":$requestId}"""

    fun loadJson(
        requestId: Int,
        contentId: String,
        contentType: String,
        positionMs: Long,
        title: String,
        artist: String,
    ): String {
        val seconds = positionMs.coerceAtLeast(0L) / 1000.0
        return buildString {
            append("""{"type":"LOAD","requestId":$requestId,"autoplay":true,"currentTime":$seconds,""")
            append(""""media":{"contentId":${jsonString(contentId)},"streamType":"BUFFERED","contentType":${jsonString(contentType)},""")
            append(""""metadata":{"metadataType":3,"title":${jsonString(title)},"artist":${jsonString(artist)}}}}""")
        }
    }

    fun encode(message: EchoCastMessage): ByteArray {
        val body = ByteArrayOutputStream()
        writeVarintField(body, 1, 0)
        writeStringField(body, 2, message.sourceId)
        writeStringField(body, 3, message.destinationId)
        writeStringField(body, 4, message.namespace)
        writeVarintField(body, 5, 0)
        writeStringField(body, 6, message.payloadUtf8)
        val payload = body.toByteArray()
        val framed = ByteArray(4 + payload.size)
        ByteBuffer.wrap(framed).order(ByteOrder.BIG_ENDIAN).putInt(payload.size)
        System.arraycopy(payload, 0, framed, 4, payload.size)
        return framed
    }

    fun read(input: InputStream): EchoCastMessage {
        val header = readFully(input, 4)
        val length = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN).int
        if (length <= 0 || length > MaxMessageBytes) throw IllegalArgumentException("Cast frame $length")
        return decode(readFully(input, length))
    }

    fun decode(payload: ByteArray): EchoCastMessage {
        var index = 0
        var source = SenderId
        var destination = ReceiverId
        var namespace = ""
        var utf8 = ""
        while (index < payload.size) {
            val (tag, next) = readVarint(payload, index)
            index = next
            val field = (tag ushr 3).toInt()
            val wire = (tag and 0x7).toInt()
            when {
                wire == 0 -> {
                    val value = readVarint(payload, index)
                    index = value.second
                }
                wire == 2 -> {
                    val len = readVarint(payload, index)
                    index = len.second
                    val size = len.first.toInt()
                    if (size < 0 || index + size > payload.size) throw IllegalArgumentException("Cast field")
                    val text = String(payload, index, size, StandardCharsets.UTF_8)
                    index += size
                    when (field) {
                        2 -> source = text
                        3 -> destination = text
                        4 -> namespace = text
                        6 -> utf8 = text
                    }
                }
                else -> throw IllegalArgumentException("Cast wire $wire")
            }
        }
        return EchoCastMessage(source, destination, namespace, utf8)
    }

    fun jsonString(value: String): String = buildString {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
        append('"')
    }

    fun transportId(payload: String): String? {
        val key = "\"transportId\""
        val start = payload.indexOf(key)
        if (start < 0) return null
        val colon = payload.indexOf(':', start + key.length)
        val quote = payload.indexOf('"', colon + 1)
        val end = payload.indexOf('"', quote + 1)
        if (quote < 0 || end <= quote) return null
        return payload.substring(quote + 1, end).takeIf { it.isNotBlank() }
    }

    private fun writeStringField(output: ByteArrayOutputStream, field: Int, value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        writeVarint(output, (field shl 3) or 2)
        writeVarint(output, bytes.size)
        output.write(bytes)
    }

    private fun writeVarintField(output: ByteArrayOutputStream, field: Int, value: Int) {
        writeVarint(output, field shl 3)
        writeVarint(output, value)
    }

    private fun writeVarint(output: ByteArrayOutputStream, value: Int) {
        var current = value
        while (current and 0x7F.inv() != 0) {
            output.write((current and 0x7F) or 0x80)
            current = current ushr 7
        }
        output.write(current)
    }

    private fun readVarint(bytes: ByteArray, start: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var index = start
        while (index < bytes.size) {
            val next = bytes[index].toInt() and 0xFF
            index += 1
            result = result or ((next and 0x7F).toLong() shl shift)
            if (next and 0x80 == 0) return result to index
            shift += 7
            if (shift > 63) throw IllegalArgumentException("Cast varint")
        }
        throw IllegalArgumentException("Truncated Cast varint")
    }

    private fun readFully(input: InputStream, count: Int): ByteArray {
        val bytes = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = input.read(bytes, offset, count - offset)
            if (read < 0) throw IllegalArgumentException("Cast stream closed")
            offset += read
        }
        return bytes
    }
}
