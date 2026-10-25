package app.echo.android.data

import app.echo.android.model.library.CueSheetPolicy
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class FlacEmbeddedCueReaderTest {
    @Test
    fun largeUnrelatedVorbisValuesDoNotHideCueOrForceWholeBlockAllocation() {
        val artwork = "METADATA_BLOCK_PICTURE=".toByteArray() + ByteArray(2 * 1024 * 1024 + 64) { 65 }
        val cue = "CUESHEET=TRACK 01 AUDIO\nTITLE \"One\"\nINDEX 01 00:00:00".toByteArray()
        val audio = "PCM must stay unread".toByteArray()
        for (withCue in listOf(false, true)) {
            val values = if (withCue) listOf(artwork, cue) else listOf(artwork)
            val comments = ByteArrayOutputStream().apply {
                fun number(value: Int) { write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()) }
                number(0); number(values.size)
                values.forEach { number(it.size); write(it) }
            }.toByteArray()
            var readBytes = 0
            val input = object : java.io.FilterInputStream((flac(4 to comments) + audio).inputStream()) {
                override fun read(bytes: ByteArray, offset: Int, length: Int): Int = `in`.read(bytes, offset, length).also {
                    if (it > 0) readBytes += it
                }
            }
            val sheet = FlacEmbeddedCueReader.readChecked(input)
            if (withCue) assertEquals("One", sheet?.tracks?.single()?.title) else assertNull(sheet)
            assertTrue(readBytes < 8192)
            assertEquals(audio.size, input.available())
        }
    }

    private fun info(): ByteArray = ByteArray(34).also {
        ByteBuffer.wrap(it).putLong(10, (44100L shl 44) or (120L * 44100))
    }
    private fun flac(vararg blocks: Pair<Int, ByteArray>): ByteArray = ByteArrayOutputStream().apply {
        write("fLaC".toByteArray())
        val all = listOf(0 to info()) + blocks
        all.forEachIndexed { index, (type, bytes) ->
            write(type or if (index == all.lastIndex) 128 else 0)
            write(bytes.size ushr 16); write(bytes.size ushr 8); write(bytes.size)
            write(bytes)
        }
    }.toByteArray()
    private fun comments(text: String): ByteArray {
        val value = "CUESHEET=$text".toByteArray()
        return ByteBuffer.allocate(12 + value.size).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0).putInt(1).putInt(value.size).put(value).array()
    }
    private fun binary(): ByteArray = ByteArrayOutputStream().apply {
        write(ByteArray(396).also { it[136] = 128.toByte(); it[395] = 3 })
        listOf(1 to 0L, 2 to 60L * 44100, 170 to 120L * 44100).forEach { (number, samples) ->
            write(ByteArray(36).also {
                ByteBuffer.wrap(it).putLong(samples)
                it[8] = number.toByte(); it[35] = if (number == 170) 0 else 1
            })
            if (number != 170) write(ByteArray(12).also { it[8] = 1 })
        }
    }.toByteArray()

    @Test
    fun readsEmbeddedTextAndUsesTheActualFlacInsteadOfFileLabels() {
        val text = "TITLE \"Album\"\nFILE \"old.wav\" WAVE\nTRACK 01 AUDIO\nTITLE \"One\"\nINDEX 01 00:00:00\nTRACK 02 AUDIO\nTITLE \"Two\"\nINDEX 01 01:00:00"
        val sheet = FlacEmbeddedCueReader.read(flac(4 to comments(text)).inputStream())!!
        assertEquals("Album", sheet.album)
        assertNull(sheet.fileName)
        assertTrue(sheet.tracks.all { it.fileName == null })
        assertEquals(listOf(60_000L, 120_000L), sheet.tracks.map { it.endMs })
        assertNull(FlacEmbeddedCueReader.readChecked(flac(4 to comments("")).inputStream()))
    }

    @Test
    fun rejectsDamagedCommentTailAndConflictingEmbeddedSheets() {
        val text = "TRACK 01 AUDIO\nTITLE \"One\"\nINDEX 01 00:00:00"
        val broken = comments(text).also {
            // Declared two comments, but the second one was not completely written.
            ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(4, 2)
        }
        assertThrows(java.io.IOException::class.java) {
            FlacEmbeddedCueReader.readChecked(flac(4 to broken).inputStream())
        }
        assertThrows(java.io.IOException::class.java) {
            FlacEmbeddedCueReader.readChecked(flac(4 to comments(text), 4 to comments(text.replace("One", "Other"))).inputStream())
        }
        val malformedUtf8 = comments(text).also { it[it.lastIndex] = 0xff.toByte() }
        assertThrows(java.io.IOException::class.java) {
            FlacEmbeddedCueReader.readChecked(flac(4 to malformedUtf8).inputStream())
        }
        assertEquals("One", FlacEmbeddedCueReader.readChecked(flac(4 to comments(text), 4 to comments(text)).inputStream())?.tracks?.single()?.title)
    }

    @Test
    fun readsBinaryOffsetsAndLeadOutAndRejectsBrokenLeadOut() {
        val binary = binary()
        val sheet = FlacEmbeddedCueReader.read(flac(5 to binary).inputStream())!!
        assertEquals(listOf(0L, 60_000L), sheet.tracks.map { it.startMs })
        assertEquals(listOf(60_000L, 120_000L), sheet.tracks.map { it.endMs })
        binary[396 + 48 + 48 + 8] = 3
        assertNull(FlacEmbeddedCueReader.read(flac(5 to binary).inputStream()))
    }

    @Test
    fun rejectsTruncatedAndOversizedMetadataWithoutReadingAudio() {
        assertNull(FlacEmbeddedCueReader.read(flac(5 to binary()).dropLast(1).toByteArray().inputStream()))
        val bytes = flac(5 to ByteArray(CueSheetPolicy.MaxCueBytes + 1))
        val input = bytes.inputStream()
        assertNull(FlacEmbeddedCueReader.read(input))
        assertTrue(input.available() > CueSheetPolicy.MaxCueBytes)
        val external = ByteArray(CueSheetPolicy.MaxCueBytes * 2) { 32 }.inputStream()
        assertNull(readCueSheet(external))
        assertEquals(CueSheetPolicy.MaxCueBytes - 1, external.available())
    }

    @Test
    fun exactDirectorySourceWinsAndAmbiguousReplacementDoesNotSplit() {
        val sheet = CueSheetParser.parseText("FILE \"a.wav\" WAVE\nTRACK 01 AUDIO\nINDEX 01 00:00:00\nFILE \"a.flac\" WAVE\nTRACK 02 AUDIO\nINDEX 01 00:00:00")!!
        val resolved = sheet.forDirectoryAudio(listOf("a.wav", "a.flac", "bonus.mp3"), "album.cue")
        assertEquals(listOf(1), resolved.getValue("a.wav").tracks.map { it.number })
        assertEquals(listOf(2), resolved.getValue("a.flac").tracks.map { it.number })
        assertTrue(sheet.copy(tracks = sheet.tracks.take(1)).forDirectoryAudio(listOf("a.flac", "a.ape"), "album.cue").isEmpty())
    }
    @Test
    fun descendantReferencesKeepTheirDirectoryAndNeverRelocateParentOrAbsolutePaths() {
        val sheet = CueSheetParser.parseText("FILE \"disc\\album.flac\" WAVE\nTRACK 01 AUDIO\nINDEX 01 00:00:00")!!
        val localized = sheet.forLocalDirectories().getValue("disc")
        assertEquals("album.flac", localized.tracks.single().fileName)
        assertEquals(setOf("album.flac"), localized.forDirectoryAudio(listOf("album.flac"), "album.cue").keys)
        assertTrue(sheet.copy(tracks = sheet.tracks.map { it.copy(fileName = "../album.flac") }).forLocalDirectories().isEmpty())
        assertTrue(sheet.copy(tracks = sheet.tracks.map { it.copy(fileName = "C:\\album.flac") }).forLocalDirectories().isEmpty())
    }

    @Test
    fun checkedReadsPreserveExistingMovementsOnTruncatedOrMalformedCue() {
        val source = app.echo.android.model.library.EchoTrack("saf:file", "content://audio/file",
            "Original", "Artist", durationMs = 120_000L).toLibraryTrackEntity()
        for (bytes in listOf(flac(5 to binary()).dropLast(1).toByteArray(), flac(4 to comments("invalid cue")))) {
            assertSame(CueExpansion.ReadFailed, source.resolveLocalCue(null, "file.flac") {
                FlacEmbeddedCueReader.readChecked(bytes.inputStream())
            })
        }
        val absent = source.resolveLocalCue(null, "file.flac") { FlacEmbeddedCueReader.readChecked(flac().inputStream()) }
        assertTrue(absent is CueExpansion.Success)
    }

}
