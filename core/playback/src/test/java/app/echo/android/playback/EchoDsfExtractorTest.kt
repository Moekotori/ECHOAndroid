package app.echo.android.playback

import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.ParserException
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import java.io.ByteArrayOutputStream
import java.io.EOFException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@UnstableApi
class EchoDsfExtractorTest {
    @Test
    fun dsfExtractorEmitsPlanarPacketsAndDuration() {
        val file = EchoDsdFixtures.dsf(blockSize = 64, realBytesPerChannel = 64)
        val output = CaptureOutput()
        runExtractor(EchoDsfExtractor(), file, output)
        val format = output.track.format!!
        assertEquals(EchoDsdMime.MsbfPlanar, format.sampleMimeType)
        assertEquals(2, format.channelCount)
        assertEquals(352_800, format.sampleRate)
        assertEquals(128, output.track.sampleBytes.single().size)
        assertEquals(0L, output.track.sampleTimes.single())
        assertEquals(64L * 8 * 1_000_000L / 2_822_400L, output.seekMap!!.durationUs)
        assertTrue(output.seekMap!!.isSeekable)
    }

    @Test
    fun dsfExtractorTrimsPaddedLastBlock() {
        val file = EchoDsdFixtures.dsf(blockSize = 64, realBytesPerChannel = 40)
        val output = CaptureOutput()
        runExtractor(EchoDsfExtractor(), file, output)
        assertEquals(80, output.track.sampleBytes.single().size)
        assertEquals(40, output.track.sampleBytes.single().take(40).count { it == 0x69.toByte() })
    }

    @Test
    fun dffExtractorEmitsInterleavedMsbf() {
        val file = EchoDsdFixtures.dff(frames = 64)
        val output = CaptureOutput()
        runExtractor(EchoDffExtractor(), file, output)
        val format = output.track.format!!
        assertEquals(EchoDsdMime.Msbf, format.sampleMimeType)
        assertEquals(2, format.channelCount)
        assertEquals(352_800, format.sampleRate)
        assertEquals(128, output.track.sampleBytes.single().size)
    }

    @Test
    fun dstCompressedDffIsRejected() {
        val file = EchoDsdFixtures.dff(compressed = true)
        try {
            runExtractor(EchoDffExtractor(), file, CaptureOutput())
            throw AssertionError("expected ParserException")
        } catch (error: ParserException) {
            assertTrue(error.message.orEmpty().contains("DST"))
        }
    }

    private fun runExtractor(extractor: Extractor, file: ByteArray, output: CaptureOutput) {
        val input = BufferExtractorInput(file)
        assertTrue(extractor.sniff(input))
        input.resetPeekPosition()
        extractor.init(output)
        val seek = PositionHolder()
        while (true) {
            val result = extractor.read(input, seek)
            if (result == Extractor.RESULT_END_OF_INPUT) break
            assertEquals(Extractor.RESULT_CONTINUE, result)
        }
        extractor.release()
    }
}

@UnstableApi
private class BufferExtractorInput(private val data: ByteArray) : ExtractorInput {
    private var pos = 0
    private var peek = 0

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (pos >= data.size) return C.RESULT_END_OF_INPUT
        val n = minOf(length, data.size - pos)
        System.arraycopy(data, pos, buffer, offset, n)
        pos += n
        peek = pos
        return n
    }

    override fun readFully(target: ByteArray, offset: Int, length: Int, allowEndOfInput: Boolean): Boolean {
        if (pos + length > data.size) {
            if (allowEndOfInput && pos == data.size) return false
            if (allowEndOfInput) {
                val n = data.size - pos
                if (n > 0) System.arraycopy(data, pos, target, offset, n)
                pos = data.size
                peek = pos
                return false
            }
            throw EOFException()
        }
        System.arraycopy(data, pos, target, offset, length)
        pos += length
        peek = pos
        return true
    }

    override fun readFully(target: ByteArray, offset: Int, length: Int) {
        readFully(target, offset, length, false)
    }

    override fun skip(length: Int): Int {
        if (pos >= data.size) return C.RESULT_END_OF_INPUT
        val n = minOf(length, data.size - pos)
        pos += n
        peek = pos
        return n
    }

    override fun skipFully(length: Int, allowEndOfInput: Boolean): Boolean {
        if (pos + length > data.size) {
            if (allowEndOfInput && pos == data.size) return false
            throw EOFException()
        }
        pos += length
        peek = pos
        return true
    }

    override fun skipFully(length: Int) {
        skipFully(length, false)
    }

    override fun peek(target: ByteArray, offset: Int, length: Int): Int {
        if (peek >= data.size) return C.RESULT_END_OF_INPUT
        val n = minOf(length, data.size - peek)
        System.arraycopy(data, peek, target, offset, n)
        peek += n
        return n
    }

    override fun peekFully(target: ByteArray, offset: Int, length: Int, allowEndOfInput: Boolean): Boolean {
        if (peek + length > data.size) {
            if (allowEndOfInput && peek == data.size) return false
            throw EOFException()
        }
        System.arraycopy(data, peek, target, offset, length)
        peek += length
        return true
    }

    override fun peekFully(target: ByteArray, offset: Int, length: Int) {
        peekFully(target, offset, length, false)
    }

    override fun advancePeekPosition(length: Int, allowEndOfInput: Boolean): Boolean {
        if (peek + length > data.size) {
            if (allowEndOfInput && peek == data.size) return false
            throw EOFException()
        }
        peek += length
        return true
    }

    override fun advancePeekPosition(length: Int) {
        advancePeekPosition(length, false)
    }

    override fun resetPeekPosition() {
        peek = pos
    }

    override fun getPeekPosition(): Long = peek.toLong()
    override fun getPosition(): Long = pos.toLong()
    override fun getLength(): Long = data.size.toLong()
    override fun <E : Throwable> setRetryPosition(position: Long, e: E): Unit = throw e
}

@UnstableApi
private class CaptureOutput : ExtractorOutput {
    val track = CaptureTrack()
    var seekMap: SeekMap? = null

    override fun track(id: Int, type: Int): TrackOutput = track
    override fun endTracks() = Unit
    override fun seekMap(seekMap: SeekMap) {
        this.seekMap = seekMap
    }
}

@UnstableApi
private class CaptureTrack : TrackOutput {
    var format: Format? = null
    val sampleTimes = mutableListOf<Long>()
    val sampleBytes = mutableListOf<ByteArray>()
    private val pending = ByteArrayOutputStream()

    override fun format(format: Format) {
        this.format = format
    }

    override fun sampleData(
        input: DataReader,
        length: Int,
        allowEndOfInput: Boolean,
        sampleDataPart: Int,
    ): Int {
        val buffer = ByteArray(length)
        var done = 0
        while (done < length) {
            val read = input.read(buffer, done, length - done)
            if (read == C.RESULT_END_OF_INPUT) {
                if (allowEndOfInput && done == 0) return C.RESULT_END_OF_INPUT
                break
            }
            done += read
        }
        pending.write(buffer, 0, done)
        return done
    }

    override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
        val buffer = ByteArray(length)
        data.readBytes(buffer, 0, length)
        pending.write(buffer)
    }

    override fun sampleMetadata(
        timeUs: Long,
        flags: Int,
        size: Int,
        offset: Int,
        cryptoData: TrackOutput.CryptoData?,
    ) {
        val all = pending.toByteArray()
        val start = all.size - size - offset
        sampleBytes += all.copyOfRange(start, start + size)
        sampleTimes += timeUs
    }
}
