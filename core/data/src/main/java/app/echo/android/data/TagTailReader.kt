package app.echo.android.data

import java.io.FileInputStream
import java.io.InputStream

/** Random access is a capability that decorators must preserve, not a concrete stream type. */
internal interface TagTailReader {
    fun tail(count: Int): ByteArray?
}

internal fun readTagTail(input: InputStream, count: Int): ByteArray? {
    require(count >= 0)
    return when (input) {
        is TagTailReader -> input.tail(count)
        is FileInputStream -> {
            val channel = input.channel
            val previous = channel.position()
            try {
                if (channel.size() < count) null else {
                    channel.position(channel.size() - count)
                    input.tagBytes(count)
                }
            } finally { channel.position(previous) }
        }
        else -> null // Never walk the audio payload to emulate seeking on a pipe.
    }
}
