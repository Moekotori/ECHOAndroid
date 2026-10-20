package app.echo.android.data

import app.echo.android.model.library.CueSheet
import app.echo.android.model.library.CueSheetTrack

/** FLAC sample offsets, including the mandatory lead-out and non-audio boundaries. */
internal object FlacBinaryCueReader {
    fun parse(bytes: ByteArray, rate: Int, totalSamples: Long): CueSheet? {
        if (bytes.size < 396 || rate <= 0) return null
        val cd = bytes[136].toInt() and 128 != 0
        val count = bytes[395].toInt() and 255
        if (count < 2 || cd && (rate != 44100 || count > 100)) return null
        fun sample(position: Int): Long? {
            if (position > bytes.size - 8 || bytes[position] < 0) return null
            val value = (0..7).fold(0L) { value, i -> (value shl 8) or (bytes[position + i].toLong() and 255) }
            return value.takeIf { it <= Long.MAX_VALUE / 1000 && (totalSamples <= 0 || it <= totalSamples) }
        }
        val tracks = ArrayList<CueSheetTrack>()
        val numbers = HashSet<Int>()
        var offset = 396
        var lastOffset = 0L
        fun bound(samples: Long): Boolean {
            val previous = tracks.lastOrNull() ?: return true
            val time = samples * 1000 / rate
            if (time <= previous.startMs) return false
            if (previous.endMs == 0L) tracks[tracks.lastIndex] = previous.copy(endMs = time)
            return true
        }
        repeat(count) { trackIndex ->
            if (offset > bytes.size - 36) return null
            val trackOffset = sample(offset) ?: return null
            val number = bytes[offset + 8].toInt() and 255
            val indices = bytes[offset + 35].toInt() and 255
            val data = bytes[offset + 21].toInt() and 128 != 0
            if (number == 0 || !numbers.add(number) || trackOffset < lastOffset || cd && trackOffset % 588 != 0L) return null
            lastOffset = trackOffset
            offset += 36
            if (trackIndex == count - 1) {
                if (number != (if (cd) 170 else 255) || indices != 0 || !bound(trackOffset)) return null
            } else {
                if (number == (if (cd) 170 else 255) || indices == 0 || cd && (number > 99 || indices > 100)) return null
                var start: Long? = null
                var lastIndex = -1
                var lastRelative = 0L
                repeat(indices) { index ->
                    if (offset > bytes.size - 12) return null
                    val relative = sample(offset) ?: return null
                    val indexNumber = bytes[offset + 8].toInt() and 255
                    if ((index == 0 && indexNumber > 1) || (index > 0 && indexNumber != lastIndex + 1) ||
                        relative < lastRelative || (cd && relative % 588 != 0L) ||
                        relative > Long.MAX_VALUE / 1000 - trackOffset) return null
                    val absolute = trackOffset + relative
                    if (totalSamples > 0 && absolute > totalSamples) return null
                    if (indexNumber == 1) start = absolute
                    lastIndex = indexNumber; lastRelative = relative
                    offset += 12
                }
                if (data) { if (!bound(trackOffset)) return null }
                else {
                    val samples = start ?: return null
                    if (!bound(samples)) return null
                    tracks += CueSheetTrack(number, "Track $number", startMs = samples * 1000 / rate)
                }
            }
        }
        return if (offset == bytes.size && tracks.isNotEmpty()) CueSheet(tracks = tracks) else null
    }
}
