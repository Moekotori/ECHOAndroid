package app.echo.android.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFileTagRewriterTest {
    @Test
    fun unrelatedEditsPreserveSeparateArtistValues() {
        val mp3 = id3File(4, listOf(textFrameV24("TPE1", "One\u0000Two"),
            textFrameV24("TPE1", "Three"), textFrameV24("TPE2", "Owner One\u0000Owner Two")), FAKE_MP3)
        val flac = flacFile(listOf("ARTIST=One", "ARTIST=Two", "ARTIST=Three",
            "ALBUM ARTIST=Owner One", "ALBUM ARTIST=Owner Two"), byteArrayOf(1, 2))
        val fields = SAMPLE_FIELDS.copy(artist = "One; Two; Three", albumArtist = "Owner One; Owner Two")
        val rewrittenMp3 = rewrite(mp3, fields, "audio/mpeg").asString()
        assertTrue(rewrittenMp3.contains("One\u0000Two"))
        assertEquals(2, Regex("TPE1").findAll(rewrittenMp3).count())
        val rewrittenFlac = rewrite(flac, fields, "audio/flac").asString()
        assertTrue(rewrittenFlac.contains("ARTIST=Two"))
        assertTrue(rewrittenFlac.contains("ALBUM ARTIST=Owner Two"))
        val payloadSize = ((flac[43].toInt() and 255) shl 16) or
            ((flac[44].toInt() and 255) shl 8) or (flac[45].toInt() and 255)
        val comments = VorbisTagReader.comments(VorbisCommentRewriter.rewrite(flac.copyOfRange(46, 46 + payloadSize), fields)!!)
        assertEquals(listOf("One", "Two", "Three"), comments.filter { it.first == "ARTIST" }.map { it.second })
        val changed = rewrite(mp3, fields.copy(artist = "New", albumArtist = null), "audio/mpeg")
        assertEquals("New", readLocalAudioTags(changed.inputStream())?.artist)
        assertNull(readLocalAudioTags(changed.inputStream())?.albumArtist)
    }

    @Test
    fun wavTrackAliasCanBeClearedAndRepeatedUnknownInfoIsPreserved() {
        val base = silentWav()
        val source = base + wavChunk("LIST", "INFO".toByteArray() + wavChunk("TRCK", "3/12\u0000".toByteArray()) +
            wavChunk("ICMT", "first note\u0000".toByteArray()) + wavChunk("ICMT", "second note\u0000".toByteArray()))
        leInt(source.size - 8).copyInto(source, 4)
        val fields = SAMPLE_FIELDS.copy(trackNumber = null)
        val rewritten = rewrite(source, fields, "audio/wav")
        assertNull(readLocalAudioTags(rewritten.inputStream())?.trackNumber)
        assertTrue(rewritten.asString().contains("first note"))
        assertTrue(rewritten.asString().contains("second note"))
        val numbered = rewrite(source, fields.copy(trackNumber = 4), "audio/wav")
        assertTrue(numbered.asString().contains("4/12"))
    }

    @Test
    fun wavRejectsPartialInfoBeforeChangingDestination() {
        val base = silentWav()
        for (tail in listOf("BROKEN".toByteArray(), "ICMT".toByteArray() + leInt(100) + byteArrayOf(1))) {
            val bytes = base + wavChunk("LIST", "INFO".toByteArray() + wavChunk("INAM", "Old\u0000".toByteArray()) + tail)
            leInt(bytes.size - 8).copyInto(bytes, 4)
            val source = java.io.File.createTempFile("echo-info-source", ".wav")
            val output = java.io.File.createTempFile("echo-info-output", ".wav")
            try {
                source.writeBytes(bytes)
                output.writeText("untouched")
                assertEquals(AudioTagRewriteStatus.InvalidSource,
                    AudioFileTagRewriter.rewriteFile(source, output, SAMPLE_FIELDS, "audio/wav"))
                assertEquals("untouched", output.readText())
                assertTrue(bytes.contentEquals(source.readBytes()))
            } finally { source.delete(); output.delete() }
        }
    }
    @Test
    fun id3PrefersFrontCoverOverEarlierBackCover() {
        fun picture(type: Int, image: ByteArray) = Id3FrameBytes("APIC",
            byteArrayOf(3) + "image/png".toByteArray() + byteArrayOf(0, type.toByte(), 0) + image)
        val source = id3File(4, listOf(picture(4, byteArrayOf(9)), picture(3, TINY_PNG)), FAKE_MP3)
        assertTrue(TINY_PNG.contentEquals(readLocalAudioTags(source.inputStream())?.artworkBytes))
    }

    @Test
    fun vorbisCoverReplacementKeepsBackCoverAndReadsNewFrontCover() {
        fun picture(type: Int, image: ByteArray): String {
            val bytes = ByteArrayOutputStream()
            java.io.DataOutputStream(bytes).apply {
                writeInt(type)
                writeInt(9); write("image/png".toByteArray()); writeInt(0)
                repeat(4) { writeInt(0) }
                writeInt(image.size); write(image)
            }
            return java.util.Base64.getEncoder().encodeToString(bytes.toByteArray())
        }
        val source = flacFile(listOf("TITLE=Old", "ARTIST=Artist",
            "METADATA_BLOCK_PICTURE=${picture(4, byteArrayOf(9))}",
            "METADATA_BLOCK_PICTURE=${picture(3, byteArrayOf(8))}"), byteArrayOf(1))
        val payloadSize = ((source[43].toInt() and 255) shl 16) or
            ((source[44].toInt() and 255) shl 8) or (source[45].toInt() and 255)
        val payload = source.copyOfRange(46, 46 + payloadSize)
        val rewritten = VorbisCommentRewriter.rewrite(payload,
            SAMPLE_FIELDS.copy(artworkBytes = TINY_PNG, artworkMime = "image/png"))!!
        assertEquals(2, VorbisTagReader.comments(rewritten).count { it.first == "METADATA_BLOCK_PICTURE" })
        assertTrue(TINY_PNG.contentEquals(VorbisTagReader.fields(rewritten).artworkBytes))
    }
    @Test fun batchGenreAndComposerSurviveFileRewrite() {
        val fields = SAMPLE_FIELDS.copy(genre = "Ambient",composer = "Composer")
        val rewritten = rewrite(FAKE_MP3,fields,"audio/mpeg")
        val read = readLocalAudioTags(rewritten.inputStream())!!
        assertEquals("Ambient",read.genre)
        assertEquals("Composer",read.composer)
        assertEquals(fields.title,read.title)
        assertEquals(fields.artist,read.artist)
    }
    @Test fun unselectedGenreIsKeptAndExplicitClearRemovesIt() {
        val source = id3File(4,listOf(textFrameV24("TCON","Ambient")),FAKE_MP3)
        assertEquals("Ambient",readLocalAudioTags(rewrite(source,SAMPLE_FIELDS,"audio/mpeg").inputStream())?.genre)
        assertNull(readLocalAudioTags(rewrite(source,SAMPLE_FIELDS.copy(genreModified = true),"audio/mpeg").inputStream())?.genre)
    }
    @Test
    fun id3RepeatedFramesAndNullSeparatedValuesKeepAllArtists() {
        for ((encoding, charset) in listOf(1 to Charsets.UTF_16, 2 to Charsets.UTF_16BE, 3 to Charsets.UTF_8)) {
            val frames = listOf(
                Id3FrameBytes("TPE1", byteArrayOf(encoding.toByte()) + "A\u0000B".toByteArray(charset)),
                Id3FrameBytes("TPE1", byteArrayOf(encoding.toByte()) + "C".toByteArray(charset)),
                Id3FrameBytes("TPE2", byteArrayOf(encoding.toByte()) + "Owner A\u0000Owner B".toByteArray(charset)),
            )
            val tags = readLocalAudioTags(id3File(4, frames, FAKE_MP3).inputStream())!!
            assertEquals("A; B; C", tags.artist)
            assertEquals("Owner A; Owner B", tags.albumArtist)
        }
    }

    @Test
    fun readsId3TitleVersionsAndDeclaredUnicodeEncodings() {
        for (version in listOf(2, 3, 4)) {
            for ((encoding, charset) in listOf(1 to Charsets.UTF_16, 2 to Charsets.UTF_16BE, 3 to Charsets.UTF_8)) {
                val title = "六兆年と一夜物語 · Hélène · 봄날 · 𠮷 🎵"
                val payload = byteArrayOf(encoding.toByte()) + title.toByteArray(charset)
                val frame = if (version == 2) {
                    "TT2".toByteArray() + byteArrayOf(0, 0, payload.size.toByte()) + payload
                } else {
                    "TIT2".toByteArray() + (if (version == 4) synchsafe(payload.size) else byteArrayOf(0, 0, 0, payload.size.toByte())) + byteArrayOf(0, 0) + payload
                }
                val file = id3File(version, listOf(frame), FAKE_MP3)
                assertEquals("version=$version encoding=$encoding", title, readLocalAudioTags(file.inputStream())?.title)
            }
        }
    }

    @Test
    fun id3LegacyJapaneseAndChineseTagsDecode() {
        for ((title, encoding) in listOf(
            "夜に駆ける" to "Shift_JIS",
            "青花瓷" to "GBK",
            "起風了" to "GBK",
            "周杰倫" to "Big5",
            "봄날" to "EUC-KR",
            "Hélène" to "ISO-8859-1",
        )) {
            val payload = byteArrayOf(0) + title.toByteArray(java.nio.charset.Charset.forName(encoding))
            val frame = Id3FrameBytes("TIT2", payload)
            assertEquals(title, readLocalAudioTags(id3File(4, listOf(frame), FAKE_MP3).inputStream())?.title)
        }
    }

    @Test
    fun id3v1WithoutV2UsesLegacyCharsets() {
        val gbk = java.nio.charset.Charset.forName("GBK")
        val tags = readLocalAudioTags(ByteArrayInputStream(FAKE_MP3 + id3v1(
            title = "青花瓷",
            artist = "周杰伦",
            album = "我很忙",
            charset = gbk,
            year = "2007",
            track = 3,
        )))!!
        assertEquals("青花瓷", tags.title)
        assertEquals("周杰伦", tags.artist)
        assertEquals("我很忙", tags.album)
        assertEquals(2007, tags.year)
        assertEquals(3, tags.trackNumber)

        val big5 = readLocalAudioTags(ByteArrayInputStream(FAKE_MP3 + id3v1(
            title = "周杰倫",
            artist = "周杰倫",
            album = "七里香",
            charset = java.nio.charset.Charset.forName("Big5"),
        )))!!
        assertEquals("周杰倫", big5.title)
        assertEquals("七里香", big5.album)
    }

    @Test
    fun id3v2FieldsWinAndBlankFieldsFallBackToId3v1() {
        val audio = FAKE_MP3 + id3v1(
            title = "旧题",
            artist = "周杰伦",
            album = "我很忙",
            charset = java.nio.charset.Charset.forName("GBK"),
            track = 4,
        )
        val file = id3File(4, listOf(textFrameV24("TIT2", "新题")), audio)
        val tags = readLocalAudioTags(ByteArrayInputStream(file))!!
        assertEquals("新题", tags.title)
        assertEquals("周杰伦", tags.artist)
        assertEquals("我很忙", tags.album)
        assertEquals(4, tags.trackNumber)
    }

    @Test
    fun id3BlankOrMalformedDuplicateDoesNotHideValidTitle() {
        val frames = listOf(textFrameV24("TIT2", ""), Id3FrameBytes("TIT2", byteArrayOf(3, -23)), textFrameV24("TIT2", "Valid 🎵"))
        assertEquals("Valid 🎵", readLocalAudioTags(id3File(4, frames, FAKE_MP3).inputStream())?.title)
    }

    @Test
    fun id3v24ReadsUnsynchronisedGroupedFrameWithLengthIndicator() {
        val title = "夜に駆ける"
        val text = byteArrayOf(1) + title.toByteArray(Charsets.UTF_16)
        val payload = byteArrayOf(7) + synchsafe(text.size) + text
        val encoded = ByteArrayOutputStream().apply {
            payload.forEach { byte -> write(byte.toInt()); if (byte == (-1).toByte()) write(0) }
        }.toByteArray()
        val frame = "TIT2".toByteArray() + synchsafe(encoded.size) + byteArrayOf(0, 0x43) + encoded
        val file = id3File(4, listOf(frame, textFrameV24("TPE1", "Artist")), FAKE_MP3)
        assertEquals(title, readLocalAudioTags(file.inputStream())?.title)
        // A tag-level flag also applies per frame in v2.4, without shifting frame boundaries.
        file[5] = 0x80.toByte()
        assertEquals("Artist", readLocalAudioTags(file.inputStream())?.artist)
        assertEquals(title, readLocalAudioTags(file.inputStream())?.title)
    }

    @Test
    fun flacBlankDuplicateDoesNotOverwriteUnicodeTitle() {
        val file = flacFile(listOf("TITLE=봄날 · 𠮷 🎵", "TITLE="), byteArrayOf())
        assertEquals("봄날 · 𠮷 🎵", readLocalAudioTags(file.inputStream())?.title)
    }

    @Test
    fun mp3WithoutId3PrependsUtf8Tags() {
        val source = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00, 1, 2, 3, 4)
        val rewritten = rewrite(source, SAMPLE_FIELDS, "audio/mpeg")
        val fields = AudioFileTagRewriter.readFields(ByteArrayInputStream(rewritten))
        assertEquals("夜曲", fields?.title)
        assertEquals("周杰伦", fields?.artist)
        assertEquals("十一月的萧邦", fields?.album)
        assertEquals("Jay Chou", fields?.albumArtist)
        assertEquals(7, fields?.trackNumber)
        assertEquals(1, fields?.discNumber)
        assertEquals(2005, fields?.year)
        assertTrue(rewritten.copyOfRange(rewritten.size - source.size, rewritten.size).contentEquals(source))
    }

    @Test
    fun id3v24RoundTripReplacesTitleAndKeepsArtworkFrame() {
        val artwork = Id3FrameBytes("APIC", byteArrayOf(0, 105, 109, 97, 103, 101, 47, 106, 112, 101, 103, 0, 3, 0))
        val original = id3File(
            version = 4,
            frames = listOf(
                textFrameV24("TIT2", "Old Title"),
                textFrameV24("TPE1", "Old Artist"),
                artwork,
            ),
            audio = FAKE_MP3,
        )
        val rewritten = rewrite(original, SAMPLE_FIELDS, "audio/mpeg")
        val fields = AudioFileTagRewriter.readFields(ByteArrayInputStream(rewritten))
        assertEquals("夜曲", fields?.title)
        assertEquals("周杰伦", fields?.artist)
        assertTrue("APIC frame should be preserved", rewritten.asString().contains("APIC"))
        assertTrue(rewritten.copyOfRange(rewritten.size - FAKE_MP3.size, rewritten.size).contentEquals(FAKE_MP3))
    }

    @Test
    fun clearingAlbumRemovesTalb() {
        val original = id3File(
            version = 4,
            frames = listOf(
                textFrameV24("TIT2", "Keep"),
                textFrameV24("TPE1", "Band"),
                textFrameV24("TALB", "Should Go"),
            ),
            audio = FAKE_MP3,
        )
        val rewritten = rewrite(
            original,
            SAMPLE_FIELDS.copy(album = null),
            "audio/mpeg",
        )
        val fields = AudioFileTagRewriter.readFields(ByteArrayInputStream(rewritten))
        assertNull(fields?.album)
        assertTrue(!rewritten.asString().contains("Should Go"))
    }

    @Test
    fun flacReplacesTitleAndKeepsReplayGain() {
        val original = flacFile(
            comments = listOf(
                "TITLE=Old",
                "ARTIST=Who",
                "REPLAYGAIN_TRACK_GAIN=-6.00 dB",
            ),
            audio = byteArrayOf(9, 8, 7, 6),
        )
        val rewritten = rewrite(original, SAMPLE_FIELDS, "audio/flac")
        val fields = AudioFileTagRewriter.readFields(ByteArrayInputStream(rewritten))
        assertEquals("夜曲", fields?.title)
        assertEquals("周杰伦", fields?.artist)
        assertEquals("十一月的萧邦", fields?.album)
        assertTrue(rewritten.asString().contains("REPLAYGAIN_TRACK_GAIN=-6.00 dB"))
        assertTrue(rewritten.copyOfRange(rewritten.size - 4, rewritten.size).contentEquals(byteArrayOf(9, 8, 7, 6)))
    }

    @Test
    fun flacWriteReplacesReplayGainTrackGain() {
        val original = flacFile(
            comments = listOf(
                "TITLE=Old",
                "ARTIST=Who",
                "REPLAYGAIN_TRACK_GAIN=-6.00 dB",
            ),
            audio = byteArrayOf(9, 8, 7, 6),
        )
        val rewritten = rewrite(
            original,
            SAMPLE_FIELDS.copy(replayGainTrackGainDb = -3.5f),
            "audio/flac",
        )
        val text = rewritten.asString()
        assertTrue(text.contains("REPLAYGAIN_TRACK_GAIN=-3.50 dB"))
        assertTrue(!text.contains("-6.00 dB"))
    }

    @Test
    fun writesLyricsIntoId3AndKeepsArtwork() {
        val artwork = Id3FrameBytes("APIC", byteArrayOf(0, 105, 109, 97, 103, 101, 47, 106, 112, 101, 103, 0, 3, 0, 1, 2, 3))
        val original = id3File(
            version = 4,
            frames = listOf(
                textFrameV24("TIT2", "Old"),
                textFrameV24("TPE1", "Band"),
                artwork,
            ),
            audio = FAKE_MP3,
        )
        val rewritten = rewrite(
            original,
            SAMPLE_FIELDS.copy(lyrics = "[00:01.00]夜曲"),
            "audio/mpeg",
        )
        val fields = AudioFileTagRewriter.readFields(ByteArrayInputStream(rewritten))
        assertEquals("[00:01.00]夜曲", fields?.lyrics)
        assertTrue(rewritten.asString().contains("APIC"))
    }

    @Test
    fun explicitLyricsEditRemovesStaleSyncedLyricsButUnrelatedEditKeepsThem() {
        val synced = Id3FrameBytes("SYLT", byteArrayOf(3) + "eng".toByteArray() +
            byteArrayOf(2, 1, 0) + "old lyrics".toByteArray() + byteArrayOf(0, 0, 0, 3, -24))
        val original = id3File(4, listOf(synced), FAKE_MP3)
        assertTrue(rewrite(original, SAMPLE_FIELDS, "audio/mpeg").asString().contains("SYLT"))
        for (lyrics in listOf("new lyrics", "")) {
            val rewritten = rewrite(original, SAMPLE_FIELDS.copy(lyrics = lyrics), "audio/mpeg")
            assertTrue(!rewritten.asString().contains("SYLT"))
            assertEquals(lyrics.takeIf { it.isNotEmpty() }, readLocalAudioTags(rewritten.inputStream())?.lyrics)
        }
    }

    @Test
    fun unrelatedTagEditPreservesLegacyId3v22Lyrics() {
        val payload = byteArrayOf(3) + "eng".toByteArray() + byteArrayOf(0) + "Old lyrics".toByteArray()
        val frame = "ULT".toByteArray() + byteArrayOf(0, 0, payload.size.toByte()) + payload
        val rewritten = rewrite(id3File(2, listOf(frame), FAKE_MP3), SAMPLE_FIELDS, "audio/mpeg")
        assertEquals("Old lyrics", readLocalAudioTags(rewritten.inputStream())?.lyrics)
    }

    @Test
    fun flacWritesLyricsAndArtwork() {
        val original = flacFile(
            comments = listOf("TITLE=Old", "ARTIST=Who", "REPLAYGAIN_TRACK_GAIN=-6.00 dB"),
            audio = byteArrayOf(9, 8, 7, 6),
        )
        val rewritten = rewrite(
            original,
            SAMPLE_FIELDS.copy(lyrics = "歌词", artworkBytes = TINY_PNG, artworkMime = "image/png"),
            "audio/flac",
        )
        val fields = AudioFileTagRewriter.readFields(ByteArrayInputStream(rewritten))
        assertEquals("歌词", fields?.lyrics)
        assertTrue(rewritten.asString().contains("REPLAYGAIN_TRACK_GAIN=-6.00 dB"))
        assertTrue(rewritten.toList().windowed(TINY_PNG.size).any { it == TINY_PNG.toList() })
    }

    @Test
    fun wavWritesInfoAndLyrics() {
        val original = silentWav()
        val rewritten = rewrite(
            original,
            SAMPLE_FIELDS.copy(lyrics = "WAV lyrics"),
            "audio/wav",
        )
        val fields = AudioFileTagRewriter.readFields(ByteArrayInputStream(rewritten))
        assertEquals("夜曲", fields?.title)
        assertEquals("周杰伦", fields?.artist)
        assertEquals("十一月的萧邦", fields?.album)
        assertEquals(7, fields?.trackNumber)
        assertEquals(2005, fields?.year)
        assertEquals("WAV lyrics", fields?.lyrics)
        assertTrue(rewritten.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF")
    }

    @Test
    fun truncatedMp4IsRejected() {
        val ftyp = byteArrayOf(0, 0, 0, 24, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(), 1, 2, 3, 4)
        val status = AudioFileTagRewriter.rewrite(
            ByteArrayInputStream(ftyp),
            ByteArrayOutputStream(),
            SAMPLE_FIELDS,
            "audio/mp4",
        )
        assertEquals(AudioTagRewriteStatus.InvalidSource, status)
    }

    @Test
    fun clearingFieldsUpdatesLegacyFooterWithoutChangingAudioOrComment() {
        val audio = FAKE_MP3 + ByteArray(70_000) { (it % 251).toByte() }
        val footer = id3v1("Title", "Artist", "Old album", Charsets.ISO_8859_1, "2007", 3)
        "Comment".toByteArray().copyInto(footer, 97)
        val output = rewrite(audio + footer, SAMPLE_FIELDS.copy(album = null, year = null, trackNumber = null), "audio/mpeg")
        val read = readLocalAudioTags(output.inputStream())!!
        assertNull(read.album)
        assertNull(read.year)
        assertNull(read.trackNumber)
        val audioStart = output.size - audio.size - 128
        assertTrue(output.copyOfRange(audioStart, audioStart + audio.size).contentEquals(audio))
        assertTrue(output.copyOfRange(output.size - 31, output.size - 24).contentEquals("Comment".toByteArray()))
    }

    @Test
    fun id3v22EditKeepsPictureCommentAndComposer() {
        fun frame(id: String, payload: ByteArray) = id.toByteArray() +
            byteArrayOf(0, (payload.size shr 8).toByte(), payload.size.toByte()) + payload
        val picture = byteArrayOf(0) + "PNG".toByteArray() + byteArrayOf(3, 0, 1, 2, 3)
        val comment = byteArrayOf(0) + "eng".toByteArray() + byteArrayOf(0) + "Keep comment".toByteArray()
        val original = id3File(2, listOf(frame("PIC", picture), frame("COM", comment),
            frame("TCM", byteArrayOf(0) + "Composer".toByteArray())), FAKE_MP3)
        val output = rewrite(original, SAMPLE_FIELDS, "audio/mpeg")
        val read = readLocalAudioTags(output.inputStream())!!
        assertTrue(read.artworkBytes!!.contentEquals(byteArrayOf(1, 2, 3)))
        assertEquals("image/png", read.artworkMime)
        assertEquals("Composer", read.composer)
        assertTrue(output.toString(Charsets.ISO_8859_1).contains("Keep comment"))
    }

    @Test
    fun id3v22UnknownFrameRefusesRewriteInsteadOfDroppingData() {
        val source = id3File(2, listOf("XYZ".toByteArray() + byteArrayOf(0, 0, 3, 1, 2, 3)), FAKE_MP3)
        val out = ByteArrayOutputStream()
        assertEquals(AudioTagRewriteStatus.InvalidSource,
            AudioFileTagRewriter.rewrite(source.inputStream(), out, SAMPLE_FIELDS, "audio/mpeg"))
        assertEquals(0, out.size())
    }

    @Test
    fun unrelatedEditKeepsFullDatesTotalsAndRepeatedComments() {
        val fields = SAMPLE_FIELDS.copy(year = 2020, trackNumber = 3, discNumber = 1)
        val flac = rewrite(flacFile(listOf("DATE=2020-06-07", "TRACKNUMBER=3/12", "DISCNUMBER=1/2",
            "COMMENT=first note", "COMMENT=second note"), byteArrayOf(1, 2)), fields, "audio/flac").asString()
        listOf("DATE=2020-06-07", "TRACKNUMBER=3/12", "DISCNUMBER=1/2", "COMMENT=first note", "COMMENT=second note")
            .forEach { assertTrue("missing $it", flac.contains(it)) }
        val mp3 = id3File(4, listOf(textFrameV24("TDRC", "2020-06-07"),
            textFrameV24("TRCK", "3/12"), textFrameV24("TPOS", "1/2")), FAKE_MP3)
        val changed = rewrite(mp3, fields.copy(trackNumber = 4), "audio/mpeg").asString()
        assertTrue(changed.contains("2020-06-07"))
        assertTrue(changed.contains("4/12"))
        assertTrue(changed.contains("1/2"))
    }

    @Test
    fun clearingAndReplacingVorbisAliasesLeavesNoOldValues() {
        val source = flacFile(listOf("ALBUM_ARTIST=Old owner", "TRACK=3/12", "DISC=1/2", "DATE=2020-06-07"), byteArrayOf(1))
        val cleared = SAMPLE_FIELDS.copy(albumArtist = null, trackNumber = null, discNumber = null, year = null)
        val output = rewrite(source, cleared, "audio/flac")
        val read = readLocalAudioTags(output.inputStream())!!
        assertNull(read.albumArtist)
        assertNull(read.trackNumber)
        assertNull(read.discNumber)
        assertNull(read.year)
        assertTrue(read.matchesWrittenTags(cleared))
        val replaced = readLocalAudioTags(rewrite(source, cleared.copy(albumArtist = "New owner", trackNumber = 4), "audio/flac").inputStream())!!
        assertEquals("New owner", replaced.albumArtist)
        assertEquals(4, replaced.trackNumber)
    }

    @Test
    fun wavInfoDateAndTrackTotalSurviveTitleEdit() {
        val base = silentWav()
        val info = wavChunk("LIST", "INFO".toByteArray() + wavChunk("ICRD", "2020-06-07\u0000".toByteArray()) +
            wavChunk("ITRK", "3/12\u0000".toByteArray()))
        val source = base + info
        leInt(source.size - 8).copyInto(source, 4)
        val output = rewrite(source, SAMPLE_FIELDS.copy(year = 2020, trackNumber = 3), "audio/wav").asString()
        assertTrue(output.contains("2020-06-07"))
        assertTrue(output.contains("3/12"))
    }

    @Test
    fun wavRejectsUnparsedChunksAndOversizedTagsBeforeWriting() {
        val base = silentWav()
        val manyChunks = base.copyOfRange(0, 36) +
            List(256) { wavChunk("JUNK", byteArrayOf()) }.fold(byteArrayOf()) { all, chunk -> all + chunk } +
            base.copyOfRange(36, base.size)
        leInt(manyChunks.size - 8).copyInto(manyChunks, 4)
        val bigTag = id3File(4, listOf(textFrameV24("TIT2", "Original"), ByteArray(2 * 1024 * 1024)), byteArrayOf())
        val oversized = base + wavChunk("id3 ", bigTag)
        leInt(oversized.size - 8).copyInto(oversized, 4)
        for (bytes in listOf(manyChunks, oversized)) {
            val source = java.io.File.createTempFile("echo-wav-source", ".wav")
            val output = java.io.File.createTempFile("echo-wav-output", ".wav")
            try {
                source.writeBytes(bytes)
                output.writeText("untouched")
                assertEquals(AudioTagRewriteStatus.InvalidSource,
                    AudioFileTagRewriter.rewriteFile(source, output, SAMPLE_FIELDS, "audio/wav"))
                assertEquals("untouched", output.readText())
                assertTrue(bytes.contentEquals(source.readBytes()))
            } finally { source.delete(); output.delete() }
        }
    }

    @Test
    fun wavLeadingId3LyricsAndArtworkSurviveTitleEdit() {
        val lyrics = "Keep these lyrics"
        val leading = id3File(4, listOf(
            Id3FrameBytes("USLT", byteArrayOf(3) + "eng".toByteArray() + byteArrayOf(0) + lyrics.toByteArray()),
            Id3FrameBytes("APIC", byteArrayOf(0) + "image/png".toByteArray() + byteArrayOf(0, 3, 0) + TINY_PNG),
        ), silentWav())
        val source = java.io.File.createTempFile("echo-prefix", ".wav")
        val output = java.io.File.createTempFile("echo-prefix-output", ".wav")
        try {
            source.writeBytes(leading)
            assertEquals(AudioTagRewriteStatus.Written,
                AudioFileTagRewriter.rewriteFile(source, output, SAMPLE_FIELDS, "audio/wav"))
            val tags = readLocalAudioTags(output.inputStream())!!
            assertEquals(SAMPLE_FIELDS.title, tags.title)
            assertEquals(lyrics, tags.lyrics)
            assertTrue(TINY_PNG.contentEquals(tags.artworkBytes))
            assertTrue(output.readBytes().takeLast(16).toByteArray().contentEquals(ByteArray(16)))
        } finally { source.delete(); output.delete() }
    }

    @Test
    fun unchangedGenreAndComposerKeepAllValuesWhileEditsAndClearsReplaceThem() {
        val original = flacFile(listOf("GENRE=Rock", "GENRE=Jazz", "COMPOSER=One", "COMPOSER=Two"), byteArrayOf(1))
        val unchanged = SAMPLE_FIELDS.copy(genre = "Rock", composer = "One", genreModified = true, composerModified = true)
        val kept = rewrite(original, unchanged, "audio/flac").asString()
        assertTrue(kept.contains("GENRE=Jazz"))
        assertTrue(kept.contains("COMPOSER=Two"))
        val edited = rewrite(original, unchanged.copy(genre = "Ambient", composer = "New"), "audio/flac").asString()
        assertTrue(edited.contains("GENRE=Ambient"))
        assertTrue(!edited.contains("GENRE=Jazz") && !edited.contains("COMPOSER=Two"))
        val cleared = readLocalAudioTags(rewrite(original,
            unchanged.copy(genre = null, composer = null), "audio/flac").inputStream())!!
        assertNull(cleared.genre)
        assertNull(cleared.composer)
        val mp3 = id3File(4, listOf(textFrameV24("TCON", "Rock"), textFrameV24("TCON", "Jazz"),
            textFrameV24("TCOM", "One"), textFrameV24("TCOM", "Two")), FAKE_MP3)
        val mp3Kept = rewrite(mp3, unchanged, "audio/mpeg").asString()
        assertTrue(mp3Kept.contains("Jazz") && mp3Kept.contains("Two"))
    }

    @Test
    fun wavPreservesV23GroupedArtworkWhenEditingText() {
        val payload = byteArrayOf(9, 0) + "image/png".toByteArray() + byteArrayOf(0, 3, 0) + TINY_PNG
        val frame = "APIC".toByteArray() + java.nio.ByteBuffer.allocate(4).putInt(payload.size).array() +
            byteArrayOf(0, 0x20) + payload
        val base = silentWav()
        val wav = base + wavChunk("id3 ", id3File(3, listOf(frame), byteArrayOf()))
        leInt(wav.size - 8).copyInto(wav, 4)
        assertTrue(TINY_PNG.contentEquals(readLocalAudioTags(wav.inputStream())!!.artworkBytes))

        val rewritten = rewrite(wav, SAMPLE_FIELDS, "audio/wav")
        val after = readLocalAudioTags(rewritten.inputStream())!!
        assertTrue(TINY_PNG.contentEquals(after.artworkBytes))
        assertTrue(after.matchesWrittenTags(SAMPLE_FIELDS))
    }

    @Test
    fun flacRejectsCommentsThatCannotBeCompletelyPreserved() {
        val comments = listOf("TITLE=old", "ARTIST=old", "ALBUM=old", "ALBUMARTIST=old",
            "TRACKNUMBER=1", "DISCNUMBER=1", "DATE=2000") +
            List(4089) { "X_$it=keep" } + "COMMENT=must survive"
        val source = flacFile(comments, byteArrayOf(1))
        val output = ByteArrayOutputStream()
        assertEquals(AudioTagRewriteStatus.InvalidSource,
            AudioFileTagRewriter.rewrite(source.inputStream(), output, SAMPLE_FIELDS, "audio/flac"))
        assertEquals(0, output.size())
        assertTrue(source.asString().contains("COMMENT=must survive"))
    }

    @Test
    fun changingFrontCoverPreservesOtherId3AndFlacPictures() {
        val back = "back-image-must-survive".toByteArray()
        fun id3Picture(type: Byte, image: ByteArray) = Id3FrameBytes("APIC",
            byteArrayOf(0) + "image/png".toByteArray() + byteArrayOf(0, type, 0) + image)
        val mp3 = id3File(4, listOf(id3Picture(3, "old-front".toByteArray()), id3Picture(4, back)), FAKE_MP3)
        fun be(value: Int) = java.nio.ByteBuffer.allocate(4).putInt(value).array()
        fun flacPicture(type: Int, image: ByteArray): ByteArray =
            be(type) + be(9) + "image/png".toByteArray() + be(0) + be(1) + be(1) + be(24) + be(0) + be(image.size) + image
        val base = flacFile(emptyList(), byteArrayOf(1))
        val metadata = base.copyOf(base.size - 1).apply { this[42] = 4 }
        val flac = metadata + flacBlock(6, false, flacPicture(3, "old-front".toByteArray())) +
            flacBlock(6, true, flacPicture(4, back)) + byteArrayOf(1)
        val fields = SAMPLE_FIELDS.copy(artworkBytes = TINY_PNG, artworkMime = "image/png")
        for ((source, mime) in listOf(mp3 to "audio/mpeg", flac to "audio/flac")) {
            val rewritten = rewrite(source, fields, mime)
            assertTrue(rewritten.asString().contains("back-image-must-survive"))
            assertTrue(!rewritten.asString().contains("old-front"))
            assertTrue(readLocalAudioTags(rewritten.inputStream()).matchesWrittenTags(fields))
        }
    }

    @Test
    fun repeatedV23ReplayGainEditsReplaceTheExistingUtf16Frame() {
        val payload = byteArrayOf(1, 0xff.toByte(), 0xfe.toByte()) +
            "REPLAYGAIN_TRACK_GAIN".toByteArray(Charsets.UTF_16LE) +
            byteArrayOf(0, 0, 0xff.toByte(), 0xfe.toByte()) +
            "+1.00 dB".toByteArray(Charsets.UTF_16LE) + byteArrayOf(0, 0)
        val frame = "TXXX".toByteArray() + java.nio.ByteBuffer.allocate(4).putInt(payload.size).array() +
            byteArrayOf(0, 0) + payload
        var source = id3File(3, listOf(frame), FAKE_MP3)
        for (gain in listOf(-5f, -2f)) {
            val fields = SAMPLE_FIELDS.copy(replayGainTrackGainDb = gain)
            source = rewrite(source, fields, "audio/mpeg")
            assertEquals(1, Regex("TXXX").findAll(source.asString()).count())
            assertTrue(readLocalAudioTags(source.inputStream()).matchesWrittenTags(fields))
        }
    }

    private fun rewrite(source: ByteArray, fields: AudioTagFields, mimeType: String): ByteArray {
        val output = ByteArrayOutputStream()
        val status = AudioFileTagRewriter.rewrite(ByteArrayInputStream(source), output, fields, mimeType)
        assertEquals(AudioTagRewriteStatus.Written, status)
        val bytes = output.toByteArray()
        assertNotNull(AudioFileTagRewriter.readFields(ByteArrayInputStream(bytes)))
        return bytes
    }

    private fun id3File(version: Int, frames: List<ByteArray>, audio: ByteArray): ByteArray {
        val body = ByteArrayOutputStream()
        frames.forEach(body::write)
        val bodyBytes = body.toByteArray()
        val header = byteArrayOf(
            'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(),
            version.toByte(), 0, 0,
        ) + synchsafe(bodyBytes.size)
        return header + bodyBytes + audio
    }

    private fun textFrameV24(id: String, value: String): ByteArray {
        val payload = byteArrayOf(3) + value.toByteArray(Charsets.UTF_8)
        return id.toByteArray(Charsets.ISO_8859_1) + synchsafe(payload.size) + byteArrayOf(0, 0) + payload
    }

    private fun Id3FrameBytes(id: String, payload: ByteArray): ByteArray =
        id.toByteArray(Charsets.ISO_8859_1) + synchsafe(payload.size) + byteArrayOf(0, 0) + payload

    private fun id3v1(
        title: String,
        artist: String,
        album: String,
        charset: java.nio.charset.Charset,
        year: String = "2007",
        track: Int = 1,
    ): ByteArray {
        val tag = ByteArray(128)
        tag[0] = 'T'.code.toByte()
        tag[1] = 'A'.code.toByte()
        tag[2] = 'G'.code.toByte()
        writeId3v1Field(tag, 3, title, charset)
        writeId3v1Field(tag, 33, artist, charset)
        writeId3v1Field(tag, 63, album, charset)
        year.toByteArray(Charsets.US_ASCII).copyInto(tag, destinationOffset = 93)
        if (track > 0) {
            tag[125] = 0
            tag[126] = track.toByte()
        }
        return tag
    }

    private fun writeId3v1Field(tag: ByteArray, offset: Int, value: String, charset: java.nio.charset.Charset) {
        val encoded = value.toByteArray(charset)
        encoded.copyInto(tag, destinationOffset = offset, endIndex = minOf(encoded.size, 30))
    }

    private fun flacFile(comments: List<String>, audio: ByteArray): ByteArray {
        val vendor = "test".toByteArray(Charsets.UTF_8)
        val commentBytes = comments.map { it.toByteArray(Charsets.UTF_8) }
        val vorbis = ByteArrayOutputStream()
        vorbis.write(leInt(vendor.size))
        vorbis.write(vendor)
        vorbis.write(leInt(commentBytes.size))
        commentBytes.forEach { bytes ->
            vorbis.write(leInt(bytes.size))
            vorbis.write(bytes)
        }
        val vorbisPayload = vorbis.toByteArray()
        val streamInfo = ByteArray(34)
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf('f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte()))
        out.write(flacBlock(type = 0, last = false, payload = streamInfo))
        out.write(flacBlock(type = 4, last = true, payload = vorbisPayload))
        out.write(audio)
        return out.toByteArray()
    }

    private fun flacBlock(type: Int, last: Boolean, payload: ByteArray): ByteArray {
        val header0 = type or if (last) 0x80 else 0
        val length = payload.size
        return byteArrayOf(
            header0.toByte(),
            ((length shr 16) and 0xFF).toByte(),
            ((length shr 8) and 0xFF).toByte(),
            (length and 0xFF).toByte(),
        ) + payload
    }

    private fun synchsafe(value: Int): ByteArray =
        byteArrayOf(
            ((value shr 21) and 0x7F).toByte(),
            ((value shr 14) and 0x7F).toByte(),
            ((value shr 7) and 0x7F).toByte(),
            (value and 0x7F).toByte(),
        )

    private fun leInt(value: Int): ByteArray =
        byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte(),
        )

    private fun ByteArray.asString(): String = toString(Charsets.ISO_8859_1)

    private companion object {
        val SAMPLE_FIELDS = AudioTagFields(
            title = "夜曲",
            artist = "周杰伦",
            album = "十一月的萧邦",
            albumArtist = "Jay Chou",
            trackNumber = 7,
            discNumber = 1,
            year = 2005,
        )
        val FAKE_MP3 = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00, 9, 8, 7, 6)
        val TINY_PNG = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x02, 0x00, 0x00, 0x00, 0x90.toByte(), 0x77, 0x53, 0xDE.toByte(),
        )
    }

    private fun silentWav(): ByteArray {
        val fmt = byteArrayOf(
            1, 0, 1, 0, 0x40, 0x1F, 0, 0, 0x80.toByte(), 0x3E, 0, 0, 2, 0, 16, 0,
        )
        val fmtChunk = wavChunk("fmt ", fmt)
        val dataChunk = wavChunk("data", ByteArray(16))
        val body = fmtChunk + dataChunk
        val header = ByteArray(12)
        "RIFF".toByteArray(Charsets.US_ASCII).copyInto(header)
        val size = 4 + body.size
        header[4] = (size and 0xFF).toByte()
        header[5] = ((size shr 8) and 0xFF).toByte()
        header[6] = ((size shr 16) and 0xFF).toByte()
        header[7] = ((size shr 24) and 0xFF).toByte()
        "WAVE".toByteArray(Charsets.US_ASCII).copyInto(header, 8)
        return header + body
    }

    private fun wavChunk(id: String, payload: ByteArray): ByteArray {
        val padded = payload.size + (payload.size and 1)
        val out = ByteArray(8 + padded)
        id.padEnd(4, ' ').take(4).toByteArray(Charsets.US_ASCII).copyInto(out)
        out[4] = (payload.size and 0xFF).toByte()
        out[5] = ((payload.size shr 8) and 0xFF).toByte()
        out[6] = ((payload.size shr 16) and 0xFF).toByte()
        out[7] = ((payload.size shr 24) and 0xFF).toByte()
        payload.copyInto(out, 8)
        return out
    }
}
