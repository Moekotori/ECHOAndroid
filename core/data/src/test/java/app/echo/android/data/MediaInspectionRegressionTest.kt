package app.echo.android.data

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.charset.Charset

class MediaInspectionRegressionTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun frame(id: String, bytes: ByteArray): ByteArray = id.toByteArray() +
        byteArrayOf(0, 0, (bytes.size shr 8).toByte(), bytes.size.toByte(), 0, 0) + bytes
    private fun id3(frames: ByteArray): ByteArray = "ID3".toByteArray() + byteArrayOf(3, 0, 0,
        ((frames.size shr 21) and 127).toByte(), ((frames.size shr 14) and 127).toByte(),
        ((frames.size shr 7) and 127).toByte(), (frames.size and 127).toByte()) + frames

    @Test fun explicitLegacyDecodePreservesDeclaredUtfTextAndNeverWritesSource() {
        val bytes = id3(frame("TIT2", byteArrayOf(0) + "海邊".toByteArray(Charset.forName("Big5"))) +
            frame("TPE1", byteArrayOf(3) + "歌手".toByteArray(Charsets.UTF_8)))
        val file = temporary.newFile().apply { writeBytes(bytes) }
        val decoded = file.inputStream().use { LegacyMetadataPreviewReader.read(it, Charset.forName("Big5")) }
        assertEquals("海邊", decoded["TIT2"])
        assertEquals("歌手", decoded["TPE1"])
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test fun galleryReadsMultiplePicturesWithoutChangingNormalCoverSelection() {
        fun picture(kind: Int) = byteArrayOf(0) + "image/png".toByteArray() + byteArrayOf(0, kind.toByte(), 0, 1, 2, 3)
        val bytes = id3(frame("APIC", picture(3)) + frame("APIC", picture(4)))
        fun read(limit: Int) = bytes.inputStream().use { input ->
            Id3TagReader.read(input.tagBytes(10)!!, input, limit)!!.frames.filter { it.id == "APIC" }
        }
        assertEquals(1, read(1).size)
        assertEquals(listOf(3, 4), read(8).map { EmbeddedTagArtwork.id3PictureType(it.payload) })
    }

    @Test fun waveInfoUsesTheChosenLegacyEncodingWithoutReadingAudioPayload() {
        fun le(size: Int) = java.nio.ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(size).array()
        val text = "海边".toByteArray(Charset.forName("GB18030")) + byteArrayOf(0)
        val info = "INFOINAM".toByteArray() + le(text.size) + text + if (text.size % 2 == 1) byteArrayOf(0) else byteArrayOf()
        val body = "WAVELIST".toByteArray() + le(info.size) + info
        val source = "RIFF".toByteArray() + le(body.size) + body
        val file = temporary.newFile().apply { writeBytes(source) }
        val decoded = file.inputStream().use { LegacyMetadataPreviewReader.read(it, Charset.forName("GB18030")) }
        assertEquals("海边", decoded["TIT2"])
        assertArrayEquals(source, file.readBytes())
    }

    @Test fun scannedRelativePathsDisambiguateSameFilenameAndKeepOrder() {
        val location = resolveScannedPlaylistLocation("../Disc 2/song.flac", "Music/Album/Disc 1")
        assertEquals("Music/Album/Disc 2/song.flac", location)
        val matcher = PlaylistImportMatcher(listOf(M3uEntry(location), M3uEntry("missing.flac"), M3uEntry(location)))
        matcher.add(listOf(
            M3uMatchRow("right", "Song", "Artist", "Music/Album/Disc 2/", "content://right", "song.flac"),
            M3uMatchRow("wrong", "Song", "Artist", "Music/Other/", "content://wrong", "song.flac")))
        val preview = matcher.preview()
        assertEquals(listOf("right"), preview.trackIds)
        assertEquals(1, preview.unmatchedEntries)
        assertEquals(1, preview.duplicateEntries)
        assertEquals(0, preview.ambiguousEntries)
    }
}
