package app.echo.android.data

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class EmbeddedTagReadRegressionTest {
    @Test fun lyricsOnlyMp3AndFlacAreNotEmptyTags() {
        val lyrics = "[00:01.00]只有歌词 🎵"
        assertEquals(lyrics, read(id3(frame("USLT", byteArrayOf(3) + "eng\u0000$lyrics".toByteArray())) )?.lyrics)
        assertEquals(lyrics, read(flac(block(0x84, comments("LYRICS=$lyrics"))))?.lyrics)
    }

    @Test fun coverOnlyId3AndFlacAreReadable() {
        val image = byteArrayOf(1, 2, 3, 4)
        assertArrayEquals(image, read(id3(frame("APIC", apic(image))))?.artworkBytes)
        assertArrayEquals(image, read(flac(block(0x86, picture(image))))?.artworkBytes)
    }

    @Test fun ffmpegCustomLyricsAndReplayGainAreReadable() {
        for (name in listOf("lyrics", "USLT")) {
            val body = frame("TXXX", byteArrayOf(3) + "$name\u0000[00:01.00]Line one\nLine two".toByteArray()) +
                frame("TXXX", byteArrayOf(3) + "REPLAYGAIN_TRACK_GAIN\u0000-6.25 dB".toByteArray())
            val tags = read(id3(body))!!
            assertEquals("[00:01.00]Line one\nLine two", tags.lyrics)
            assertEquals(-6.25f, tags.replayGainTrackGainDb!!, 0.001f)
        }
    }

    @Test fun threeMiBCoverDoesNotHideMetadataAndReadsBack() {
        val image = ByteArray(3 * 1024 * 1024) { 7 }
        val tags = read(id3(frame("APIC", apic(image)) + text("TIT2", "Large cover song") + text("TCOM", "Composer")))!!
        assertEquals("Large cover song", tags.title)
        assertEquals("Composer", tags.composer)
        assertArrayEquals(image, tags.artworkBytes)
    }

    @Test fun oversizedCoverIsSkippedButTextAfterItSurvives() {
        val bytes = id3(frame("APIC", apic(ByteArray(MaxEmbeddedArtworkBytes + 8192))) + text("TIT2", "After picture"))
        var readBytes = 0
        val input = object : java.io.ByteArrayInputStream(bytes) {
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int = super.read(bytes, offset, length)
                .also { if (it > 0) readBytes += it }
        }
        assertEquals("After picture", readLocalAudioTags(input)?.title)
        assertTrue("Oversized picture must be skipped", readBytes < 1024)
    }

    @Test fun flacCoverAfterCommentsPrefersFrontAndStopsBeforeAudio() {
        val front = byteArrayOf(4, 5, 6)
        val bytes = flac(block(4, comments("TITLE=Song", "COMPOSER=Writer", "GENRE=Jazz")) +
            block(6, picture(byteArrayOf(9), type = 4)) + block(0x86, picture(front))) + ByteArray(200)
        val input = bytes.inputStream()
        val tags = readLocalAudioTags(input)!!
        assertEquals("Song", tags.title)
        assertEquals("Writer", tags.composer)
        assertEquals("Jazz", tags.genre)
        assertArrayEquals(front, tags.artworkBytes)
        assertEquals(200, input.available())
    }

    @Test fun malformedFlacPictureDoesNotHideText() {
        assertEquals("Still readable", read(flac(block(6, be(Int.MAX_VALUE)) +
            block(0x84, comments("TITLE=Still readable"))))?.title)
    }

    @Test fun id3v22ComposerGenreLyricsAndPictureAreRead() {
        fun oldFrame(id: String, bytes: ByteArray) = id.toByteArray() + byteArrayOf(
            (bytes.size shr 16).toByte(), (bytes.size shr 8).toByte(), bytes.size.toByte()) + bytes
        val body = oldFrame("TCM", byteArrayOf(0) + "Writer".toByteArray()) +
            oldFrame("TCO", byteArrayOf(0) + "Jazz".toByteArray()) +
            oldFrame("ULT", byteArrayOf(3) + "eng\u0000Words".toByteArray()) +
            oldFrame("PIC", byteArrayOf(0) + "PNG".toByteArray() + byteArrayOf(3, 0, 1, 2, 3))
        val tags = read(id3(body, version = 2))!!
        assertEquals("Writer", tags.composer)
        assertEquals("Jazz", tags.genre)
        assertEquals("Words", tags.lyrics)
        assertArrayEquals(byteArrayOf(1, 2, 3), tags.artworkBytes)
    }

    @Test fun largeWavId3ChunkIsReadWithoutLoadingPcm() {
        val image = ByteArray(3 * 1024 * 1024) { 3 }
        val tag = id3(frame("APIC", apic(image)) + text("TIT2", "WAV with large cover"))
        fun chunk(id: String, bytes: ByteArray) = id.toByteArray() + le(bytes.size) + bytes +
            if (bytes.size % 2 == 1) byteArrayOf(0) else ByteArray(0)
        val body = "WAVE".toByteArray() + chunk("data", ByteArray(64)) + chunk("id3 ", tag)
        val tags = read("RIFF".toByteArray() + le(body.size) + body)!!
        assertEquals("WAV with large cover", tags.title)
        assertArrayEquals(image, tags.artworkBytes)
    }

    @Test fun mp4TailMetadataIncludesLyricsArtworkAndNumbersWithoutReadingAudio() {
        val image = byteArrayOf(3, 2, 1)
        val items = item("©nam", "曲名 🎵") + item("©ART", "Artist") + item("aART", "Album artist") +
            item("©wrt", "Writer") + item("©gen", "Jazz") + item("©lyr", "[00:01.00]Words") +
            atom("trkn", atom("data", be(0) + be(0) + byteArrayOf(0, 0, 0, 7, 0, 12, 0, 0))) +
            atom("disk", atom("data", be(0) + be(0) + byteArrayOf(0, 0, 0, 2, 0, 3))) +
            atom("covr", atom("data", be(14) + be(0) + image))
        val bytes = atom("ftyp", "M4A \u0000\u0000\u0000\u0000".toByteArray()) + atom("mdat", ByteArray(3 * 1024 * 1024)) +
            atom("moov", atom("udta", atom("meta", be(0) + atom("ilst", items))))
        var transferred = 0
        val input = RandomAccessTagInput(bytes.size.toLong()) { position, count ->
            bytes.copyOfRange(position.toInt(), position.toInt() + count).also { transferred += it.size }
        }
        val tags = readLocalAudioTags(input)!!
        assertEquals("曲名 🎵", tags.title)
        assertEquals("Album artist", tags.albumArtist)
        assertEquals("Writer", tags.composer)
        assertEquals("Jazz", tags.genre)
        assertEquals("[00:01.00]Words", tags.lyrics)
        assertEquals(7, tags.trackNumber)
        assertEquals(2, tags.discNumber)
        assertArrayEquals(image, tags.artworkBytes)
        assertTrue("mdat is skipped with range reads", transferred < 600_000)
    }

    @Test fun mp4MalformedAtomLengthIsRejectedWithoutAllocation() {
        val bytes = atom("ftyp", ByteArray(8)) + be(Int.MAX_VALUE) + "moov".toByteArray()
        assertNull(read(bytes))
    }

    @Test fun opusCommentsCanContinueAcrossPagesAndKeepAllArtists() {
        val packet = "OpusTags".toByteArray() + comments("TITLE=Song", "ARTIST=A", "ARTIST=B", "LYRICS=" + "词".repeat(120))
        assertTrue(packet.size > 255)
        val bytes = page(2, 0, listOf("OpusHead".toByteArray())) +
            page(0, 1, listOf(packet.copyOfRange(0, 255)), unfinished = true) +
            page(1, 2, listOf(packet.copyOfRange(255, packet.size)))
        val tags = read(bytes)!!
        assertEquals("Song", tags.title)
        assertEquals("A; B", tags.artist)
        assertEquals("词".repeat(120), tags.lyrics)
    }

    @Test fun vorbisLyricsAndBase64PictureAreRead() {
        val image = byteArrayOf(8, 6, 4)
        val packet = byteArrayOf(3) + "vorbis".toByteArray() + comments("LYRICS=Words",
            "METADATA_BLOCK_PICTURE=" + java.util.Base64.getEncoder().encodeToString(picture(image)))
        val tags = read(page(2, 0, listOf(byteArrayOf(1) + "vorbis".toByteArray())) + page(0, 1, listOf(packet)))!!
        assertEquals("Words", tags.lyrics)
        assertArrayEquals(image, tags.artworkBytes)
    }

    @Test fun remoteId3v1UsesOnlyHeaderAndTailRanges() {
        val tail = ByteArray(128).also { "TAG".toByteArray().copyInto(it); "Tail song".toByteArray().copyInto(it, 3) }
        val length = 500_000_000L
        var transferred = 0
        val input = RandomAccessTagInput(length) { position, count ->
            ByteArray(count).also { bytes ->
                transferred += count
                if (position == length - 128) tail.copyInto(bytes)
            }
        }
        assertEquals("Tail song", readLocalAudioTags(input)?.title)
        assertTrue(transferred < 300_000)
    }

    @Test fun changedRemoteFilesInvalidateLyricsCache() {
        val directory = java.nio.file.Files.createTempDirectory("echo-tag-lyrics").toFile()
        try {
            val store = EmbeddedLyricsStore(directory)
            val track = remoteTrack()
            store.store(track, "Words")
            assertEquals("Words", store.read(track))
            assertNull(store.read(track.copy(sizeBytes = 99)))
        } finally { directory.deleteRecursively() }
    }

    @Test fun unchangedRemoteFilesReuseTagsAndFailuresKeepExistingMetadata() {
        val directory = java.nio.file.Files.createTempDirectory("echo-tag-scan").toFile()
        try {
            val incoming = remoteTrack()
            var reads = 0
            val first = RemoteFileTagScan(directory, incoming.source, { reads++; AudioTagFields("Tagged", "Artist", null, null, null, null, null) }, { null })
            val tagged = first.enrich(incoming, null)
            first.complete()
            val next = RemoteFileTagScan(directory, incoming.source, { reads++; error("Unchanged file read") }, { null })
            assertEquals("Tagged", next.enrich(incoming, tagged).title)
            assertEquals(1, reads)
            val failed = RemoteFileTagScan(directory, "another-source", { throw java.io.IOException("Unavailable") }, { null })
            assertEquals("Tagged", failed.enrich(incoming, tagged).title)
            failed.complete()
            assertEquals(1, directory.listFiles()!!.size)
        } finally { directory.deleteRecursively() }
    }

    @Test fun webDavRangesReadTagsAndDoNotSendCredentialsToOtherOrigins() {
        val bytes = id3(text("TIT2", "WebDAV title")) + ByteArray(512)
        val endpoint = WebDavEndpoint("https://music.example/Music", "user", "test-password")
        var requests = 0
        val client = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            requests++
            val request = chain.request()
            assertEquals(webDavBasicAuthorization("user", "test-password"), request.header("Authorization"))
            val range = request.header("Range")!!.removePrefix("bytes=").split('-')
            val start = range[0].toInt()
            val end = minOf(range[1].toInt(), bytes.lastIndex)
            okhttp3.Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_1_1).code(206).message("Partial")
                .header("Content-Range", "bytes $start-$end/${bytes.size}")
                .body(bytes.copyOfRange(start, end + 1).toResponseBody()).build()
        }.build()
        val track = remoteTrack().copy(contentUri = "https://music.example/Music/song.mp3", sizeBytes = bytes.size.toLong())
        val reader = RemoteFileTagReader(endpoint, client)
        assertEquals("WebDAV title", reader.read(track) {}?.title)
        val completedRequests = requests
        try {
            reader.read(track.copy(contentUri = "https://outside.example/song.mp3")) {}
            fail("Cross-origin credential request must be rejected")
        } catch (_: java.io.IOException) { }
        assertEquals(completedRequests, requests)
    }

    @Test fun cancellationDuringRemoteTagReadIsPropagated() {
        val directory = java.nio.file.Files.createTempDirectory("echo-tag-cancel").toFile()
        try {
            val track = remoteTrack()
            val scan = RemoteFileTagScan(directory, track.source, { throw kotlinx.coroutines.CancellationException("Cancelled") }, { null })
            try { scan.enrich(track, null); fail("Cancellation must not become untagged metadata") }
            catch (_: kotlinx.coroutines.CancellationException) { }
            assertTrue(directory.listFiles()!!.isEmpty())
        } finally { directory.deleteRecursively() }
    }

    private fun remoteTrack() = LibraryTrackEntity("smb:x:file:1", "smb://host/share/song.mp3", "Fallback", "SMB", "Folder", "SMB", null,
        0, null, null, null, "audio/mpeg", 1000, dateModifiedSeconds = 10, source = "smb:x")
    private fun read(bytes: ByteArray) = readLocalAudioTags(bytes.inputStream())
    private fun id3(body: ByteArray, version: Int = 4) = byteArrayOf(73, 68, 51, version.toByte(), 0, 0) + sync(body.size) + body
    private fun frame(id: String, payload: ByteArray) = id.toByteArray() + sync(payload.size) + byteArrayOf(0, 0) + payload
    private fun text(id: String, value: String) = frame(id, byteArrayOf(3) + value.toByteArray())
    private fun apic(bytes: ByteArray) = byteArrayOf(0) + "image/png".toByteArray() + byteArrayOf(0, 3, 0) + bytes
    private fun picture(bytes: ByteArray, type: Int = 3) = be(type) + be(9) + "image/png".toByteArray() + be(0) +
        be(1) + be(1) + be(24) + be(0) + be(bytes.size) + bytes
    private fun flac(blocks: ByteArray) = "fLaC".toByteArray() + block(0, ByteArray(34)) + blocks
    private fun block(type: Int, bytes: ByteArray) = byteArrayOf(type.toByte(), (bytes.size shr 16).toByte(), (bytes.size shr 8).toByte(), bytes.size.toByte()) + bytes
    private fun comments(vararg values: String) = ByteArrayOutputStream().apply {
        write(le(0)); write(le(values.size))
        values.forEach { val bytes = it.toByteArray(); write(le(bytes.size)); write(bytes) }
    }.toByteArray()
    private fun atom(id: String, bytes: ByteArray) = be(bytes.size + 8) + id.toByteArray(Charsets.ISO_8859_1) + bytes
    private fun item(key: String, text: String) = atom(key, atom("data", be(1) + be(0) + text.toByteArray()))
    private fun page(flags: Int, sequence: Int, packets: List<ByteArray>, unfinished: Boolean = false): ByteArray {
        val segments = packets.flatMap { packet -> buildList {
            repeat(packet.size / 255) { add(255) }
            if (!unfinished || packet.size % 255 != 0) add(packet.size % 255)
        } }
        return "OggS".toByteArray() + byteArrayOf(0, flags.toByte()) + ByteArray(8) + le(1) + le(sequence) + le(0) +
            byteArrayOf(segments.size.toByte()) + segments.map { it.toByte() }.toByteArray() + packets.fold(ByteArray(0)) { a, b -> a + b }
    }
    private fun sync(size: Int) = byteArrayOf((size shr 21 and 127).toByte(), (size shr 14 and 127).toByte(), (size shr 7 and 127).toByte(), (size and 127).toByte())
    private fun be(value: Int) = ByteBuffer.allocate(4).putInt(value).array()
    private fun le(value: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
}
