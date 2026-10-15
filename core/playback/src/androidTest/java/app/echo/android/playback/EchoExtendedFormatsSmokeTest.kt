package app.echo.android.playback

import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.decoder.ffmpeg.FfmpegAudioDecoder
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** One short synthesized tone per container; actual demux, JNI decode, drain and seek. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class EchoExtendedFormatsSmokeTest {
    @Test fun newContainersDecodeAndSeekThroughTheExistingBackend() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val expected = JSONObject(assets.open("format-smoke/expected.json").bufferedReader().use { it.readText() })
        for (name in listOf("tone.aiff", "tone.aifc", "tone.dts", "tone.wv", "tone.wma")) {
            val data = assets.open("format-smoke/$name").use { it.readBytes() }
            val uri = Uri.parse("memory:///$name")
            val extractor = EchoFfmpegExtractor(uri, DataSource.Factory { ByteArrayDataSource(data) })
            val capture = Packets()
            extractor.init(capture)
            try {
                extract(extractor, data, uri, sniff = true)
                val pcm = decode(capture.format!!, capture.values, 0)
                assertEquals(name, expected.getJSONObject(name).getInt("pcmBytes"), pcm.size)
                assertTrue("Silent output: $name", pcm.any { it != 0.toByte() })
                if (name.endsWith(".aiff") || name.endsWith(".wv")) {
                    val hash = MessageDigest.getInstance("SHA-256").digest(pcm).joinToString("") { "%02x".format(it) }
                    assertEquals(name, expected.getJSONObject(name).getString("sha256"), hash)
                }
                assertTrue("No duration: $name", capture.seekMap!!.durationUs > 0)
                assertTrue("No seeking: $name", capture.seekMap!!.isSeekable)
                capture.values.clear()
                extractor.seek(0, 100_000)
                extract(extractor, data, uri, sniff = false)
                val afterSeek = decode(capture.format!!, capture.values, 100_000)
                assertTrue("Empty seek output: $name", afterSeek.isNotEmpty())
                assertTrue("Seek did not shorten output: $name", afterSeek.size < pcm.size)
            } finally { extractor.release() }
        }
    }

    private fun extract(extractor: Extractor, data: ByteArray, uri: Uri, sniff: Boolean) {
        var source = ByteArrayDataSource(data)
        source.open(DataSpec.Builder().setUri(uri).build())
        var input: ExtractorInput = DefaultExtractorInput(source, 0, data.size.toLong())
        if (sniff) {
            assertTrue(extractor.sniff(input))
            input.resetPeekPosition()
        }
        val seek = PositionHolder()
        try {
            repeat(512) {
                when (extractor.read(input, seek)) {
                    Extractor.RESULT_END_OF_INPUT -> return
                    Extractor.RESULT_SEEK -> {
                        source.close()
                        source = ByteArrayDataSource(data)
                        source.open(DataSpec.Builder().setUri(uri).setPosition(seek.position).build())
                        input = DefaultExtractorInput(source, seek.position, data.size.toLong())
                    }
                }
            }
            fail("Extractor did not finish within the packet budget")
        } finally { source.close() }
    }

    private fun decode(format: Format, packets: List<Pair<Long, ByteArray>>, startUs: Long): ByteArray {
        val decoder = FfmpegAudioDecoder(format, 2, 2, 64 * 1024, false)
        decoder.setOutputStartTimeUs(startUs)
        val pcm = ByteArrayOutputStream()
        var queued = 0
        var eosQueued = false
        val deadline = SystemClock.elapsedRealtime() + 5000
        try {
            while (SystemClock.elapsedRealtime() < deadline) {
                if (!eosQueued) {
                    decoder.dequeueInputBuffer()?.let { input ->
                        if (queued < packets.size) {
                            val (time, bytes) = packets[queued++]
                            input.timeUs = time
                            input.ensureSpaceForWrite(bytes.size)
                            input.data!!.put(bytes)
                            input.flip()
                        } else {
                            input.addFlag(C.BUFFER_FLAG_END_OF_STREAM)
                            eosQueued = true
                        }
                        decoder.queueInputBuffer(input)
                    }
                }
                val output = decoder.dequeueOutputBuffer()
                if (output != null) {
                    try {
                        if (output.isEndOfStream) return pcm.toByteArray()
                        assertTrue("Output precedes seek target", output.timeUs >= startUs)
                        val bytes = ByteArray(output.data!!.remaining())
                        output.data!!.get(bytes)
                        pcm.write(bytes)
                    } finally { output.release() }
                } else SystemClock.sleep(1)
            }
            throw AssertionError("Timed out decoding ${format.sampleMimeType}")
        } finally { decoder.release() }
    }

    private class Packets : ExtractorOutput, TrackOutput {
        var format: Format? = null
        var seekMap: SeekMap? = null
        val values = mutableListOf<Pair<Long, ByteArray>>()
        private var bytes = ByteArray(0)
        override fun track(id: Int, type: Int): TrackOutput = this
        override fun endTracks() = Unit
        override fun seekMap(seekMap: SeekMap) { this.seekMap = seekMap }
        override fun format(format: Format) { this.format = format }
        override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean, sampleDataPart: Int): Int =
            throw UnsupportedOperationException()
        override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
            bytes = ByteArray(length)
            data.readBytes(bytes, 0, length)
        }
        override fun sampleMetadata(timeUs: Long, flags: Int, size: Int, offset: Int, cryptoData: TrackOutput.CryptoData?) {
            values += timeUs to if (size == 0) ByteArray(0) else bytes
        }
    }
}
