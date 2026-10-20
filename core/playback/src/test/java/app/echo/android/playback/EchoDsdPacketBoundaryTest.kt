package app.echo.android.playback

import androidx.media3.common.DataReader
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.TrackOutput
import org.junit.Assert.assertEquals
import org.junit.Test

@UnstableApi
class EchoDsdPacketBoundaryTest {
    @Test
    fun shortNetworkReadsDoNotSplitPlanarChannelsOrDopWords() {
        val cases = listOf(
            Triple(EchoDsfExtractor(), EchoDsdFixtures.dsf(blockSize = 64, realBytesPerChannel = 64), 128),
            Triple(EchoDffExtractor(), EchoDsdFixtures.dff(frames = 65), 130),
        )
        for ((extractor, file, expectedBytes) in cases) {
            var position = 0
            val reader = DataReader { target, offset, length ->
                if (position == file.size) -1 else {
                    val count = minOf(3, length, file.size - position)
                    file.copyInto(target, offset, position, position + count)
                    position += count
                    count
                }
            }
            val captured = CaptureOutput()
            // Match a TrackOutput allowed to consume only one short DataReader read.
            val track = object : TrackOutput by captured.track {
                override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean, sampleDataPart: Int): Int {
                    val bytes = ByteArray(length)
                    val count = input.read(bytes, 0, length)
                    if (count > 0) captured.track.sampleData(ParsableByteArray(bytes), count, sampleDataPart)
                    return count
                }
            }
            extractor.init(object : ExtractorOutput by captured {
                override fun track(id: Int, type: Int): TrackOutput = track
            })
            val input = DefaultExtractorInput(reader, 0, file.size.toLong())
            val seek = PositionHolder()
            while (extractor.read(input, seek) != Extractor.RESULT_END_OF_INPUT) Unit
            assertEquals(listOf(expectedBytes), captured.track.sampleBytes.map { it.size })
            extractor.release()
        }
    }
}
