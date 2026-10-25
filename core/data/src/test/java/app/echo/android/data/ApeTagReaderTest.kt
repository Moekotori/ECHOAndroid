package app.echo.android.data

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class ApeTagReaderTest {
    @Test fun scanDecoratorPreservesApeTagsAndEmbeddedCue() {
        val cue = "FILE \"old.wav\" WAVE\nTRACK 01 AUDIO\nTITLE \"First\"\nINDEX 01 00:00:00\nTRACK 02 AUDIO\nTITLE \"Second\"\nINDEX 01 01:00:00"
        val items = item("Title", "Album image".toByteArray()) + item("CUESHEET", cue.toByteArray())
        val bytes = "wvpk".toByteArray() + ByteArray(72) + items + footer(items.size + 32, 2)
        assertEquals("Album image", readScanMetadata(randomInput(bytes), ::readLocalAudioTags)?.title)
        val sheet = readScanMetadata(randomInput(bytes), ApeTagReader::readCueChecked)!!
        assertEquals(listOf("First", "Second"), sheet.tracks.map { it.title })
        assertTrue(sheet.tracks.all { it.fileName == null })
        val source = app.echo.android.model.library.EchoTrack("saf:wv", "content://file/wv", "Image", "Artist", durationMs = 120_000).toLibraryTrackEntity()
        val expansion = source.resolveLocalCue(null, "album.wv") { sheet } as CueExpansion.Success
        assertEquals(listOf(60_000L, 60_000L), expansion.tracks.map { it.durationMs })
        assertNull(ApeTagReader.readCueChecked(randomInput("MAC ".toByteArray() + ByteArray(200))))
        val broken = items + footer(Int.MAX_VALUE, 2)
        assertThrows(java.io.IOException::class.java) { ApeTagReader.readCueChecked(randomInput(broken)) }
    }

    @Test fun apeTagsSurviveAnId3v1TrailerAndIncludeArtwork() {
        val image = byteArrayOf(1, 2, 3, 4)
        val items = item("Title", "歌曲".toByteArray()) + item("Artist", "歌手".toByteArray()) +
            item("Album Artist", "合辑歌手".toByteArray()) + item("Track", "2/12".toByteArray()) +
            item("Lyrics", "[00:01.00]歌词".toByteArray()) +
            item("Cover Art (Front)", "cover.png\u0000".toByteArray() + image, 2)
        val bytes = "MAC ".toByteArray() + ByteArray(72) + items + footer(items.size + 32, 6) +
            "TAG".toByteArray() + ByteArray(125)
        val input = randomInput(bytes)
        val tags = readLocalAudioTags(input)!!
        assertEquals("歌曲", tags.title)
        assertEquals("歌手", tags.artist)
        assertEquals("合辑歌手", tags.albumArtist)
        assertEquals(2, tags.trackNumber)
        assertEquals("[00:01.00]歌词", tags.lyrics)
        assertArrayEquals(image, tags.artworkBytes)
        assertEquals("image/png", tags.artworkMime)
    }

    @Test fun malformedTailAndUnseekableStreamsDoNotWalkTheAudio() {
        val bytes = "MAC ".toByteArray() + ByteArray(72) + footer(Int.MAX_VALUE, 1)
        assertNull(readLocalAudioTags(randomInput(bytes)))
        val input = bytes.inputStream()
        assertNull(ApeTagReader.read(input))
        assertEquals(bytes.size, input.available())
        val badItem = item("Title", "x".toByteArray()).also {
            ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(Int.MAX_VALUE)
        }
        assertNull(ApeTagReader.read(randomInput(badItem + footer(badItem.size + 32, 1))))
    }

    @Test fun fileStreamTailLookupRestoresTheCallerPosition() {
        val item = item("Title", "Disk title".toByteArray())
        val file = java.io.File.createTempFile("echo-ape-tags-", ".ape")
        try {
            file.writeBytes("MAC ".toByteArray() + ByteArray(72) + item + footer(item.size + 32, 1))
            file.inputStream().use { input ->
                input.skip(10)
                assertEquals("Disk title", ApeTagReader.read(input)?.title)
                assertEquals(10L, input.channel.position())
            }
        } finally { file.delete() }
    }

    @Test fun apeWithoutApeV2KeepsLegacyId3v1Metadata() {
        val trailer = ByteArray(128)
        "TAG".toByteArray().copyInto(trailer)
        "Legacy song".toByteArray().copyInto(trailer, 3)
        "Legacy artist".toByteArray().copyInto(trailer, 33)
        val tags = readLocalAudioTags(randomInput("MAC ".toByteArray() + ByteArray(72) + trailer))!!
        assertEquals("Legacy song", tags.title)
        assertEquals("Legacy artist", tags.artist)
    }

    private fun randomInput(bytes: ByteArray) = RandomAccessTagInput(bytes.size.toLong()) { at, count ->
        bytes.copyOfRange(at.toInt(), at.toInt() + count)
    }
    private fun item(key: String, data: ByteArray, flags: Int = 0): ByteArray =
        ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(data.size).putInt(flags).array() +
            key.toByteArray() + 0 + data
    private fun footer(size: Int, count: Int): ByteArray = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
        .put("APETAGEX".toByteArray()).putInt(2000).putInt(size).putInt(count).putInt(0).putLong(0).array()
}
