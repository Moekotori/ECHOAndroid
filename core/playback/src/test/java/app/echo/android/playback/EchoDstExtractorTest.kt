package app.echo.android.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.PositionHolder
import org.junit.Assert.*
import org.junit.Test

@UnstableApi
class EchoDstExtractorTest {
    @Test fun dstPacketsKeepFrameTimesAndSeekBeforePlaybackHasBuiltAnIndex() {
        val file = EchoDsdFixtures.dstDff()
        val extractor = EchoDffExtractor()
        val output = CaptureOutput()
        var input = BufferExtractorInput(file)
        extractor.init(output)
        val holder = PositionHolder()
        assertEquals(Extractor.RESULT_CONTINUE, extractor.read(input, holder))
        assertEquals(EchoDstPackets.Mime, output.track.format!!.sampleMimeType)
        assertFalse(EchoDsdMime.isDecoderMime(output.track.format!!.sampleMimeType))
        assertEquals(40_000L, output.seekMap!!.durationUs)
        val point = output.seekMap!!.getSeekPoints(27_000).first
        extractor.seek(point.position, 27_000)
        input = BufferExtractorInput(file, point.position.toInt())
        assertEquals(Extractor.RESULT_CONTINUE, extractor.read(input, holder))
        assertEquals(26_666L, output.track.sampleTimes.single())
        assertEquals(9409, output.track.sampleBytes.single().size)
        assertEquals(0x6b.toByte(), output.track.sampleBytes.single()[1])
        assertEquals(Extractor.RESULT_END_OF_INPUT, extractor.read(input, holder))
    }
}
