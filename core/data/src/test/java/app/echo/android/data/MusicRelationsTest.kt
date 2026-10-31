package app.echo.android.data

import app.echo.android.model.library.*
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class MusicRelationsTest {
    @Test fun repeatedVorbisCreditsAndClassicalTagsSurviveReadAndOrdinaryTagEditing() {
        val comments = listOf("TITLE=Opening", "ARTIST=Ensemble", "COMPOSER=Writer A", "COMPOSER=Writer B",
            "WORK=Concerto", "MOVEMENTNAME=I. Allegro", "CONDUCTOR=Conductor", "PERFORMER=Soloist A",
            "PERFORMER=AC/DC", "VERSION=Studio take", "MUSICBRAINZ_WORKID=work-id")
        val payload = ByteArrayOutputStream().apply {
            write(le(0)); write(le(comments.size))
            comments.forEach { val bytes = it.toByteArray(); write(le(bytes.size)); write(bytes) }
        }.toByteArray()
        val streamInfo = ByteArray(34)
        val source = "fLaC".toByteArray() + block(0, streamInfo) + block(0x84, payload) + byteArrayOf(1, 2)
        val tags = readLocalAudioTags(source.inputStream())!!
        assertEquals("Writer A; Writer B", tags.composer)
        assertEquals("Soloist A; AC/DC", tags.musicRelations.performer)
        assertEquals("I. Allegro", tags.musicRelations.movement)
        assertEquals("work-id", tags.musicRelations.workId)
        val output = ByteArrayOutputStream()
        assertEquals(AudioTagRewriteStatus.Written, AudioFileTagRewriter.rewrite(source.inputStream(), output, tags.copy(title = "Changed")))
        assertEquals(tags.musicRelations, readLocalAudioTags(output.toByteArray().inputStream())!!.musicRelations)
    }

    @Test fun id3ConductorMovementAndCustomWorkAreRead() {
        fun frame(id: String, value: String): ByteArray {
            val payload = byteArrayOf(3) + value.toByteArray()
            return id.toByteArray() + sync(payload.size) + byteArrayOf(0, 0) + payload
        }
        val frames = frame("TIT2", "Opening") + frame("TPE3", "Conductor") + frame("MVNM", "Allegro") +
            frame("TXXX", "WORK\u0000Concerto") + frame("TXXX", "PERFORMER\u0000Soloist") +
            frame("TIPL", "producer\u0000Producer") + frame("TMCL", "violin\u0000Violinist") + frame("TPUB", "Label")
        val bytes = "ID3".toByteArray() + byteArrayOf(4, 0, 0) + sync(frames.size) + frames
        val relations = readLocalAudioTags(bytes.inputStream())!!.musicRelations
        assertEquals("Conductor", relations.conductor)
        assertEquals("Concerto", relations.work)
        assertEquals("Allegro", relations.movement)
        assertEquals("Soloist", relations.performer)
        assertEquals("Label", relations.label)
        assertTrue(relations.credits.any { it.name == "Producer" && it.role == "producer" })
        assertTrue(relations.credits.any { it.name == "Violinist" && it.role == "performer:violin" })
    }

    @Test fun workAndMovementIdentitiesRespectContextAndExplicitIds() {
        fun work(track: LibraryTrackEntity) = musicRelationMemberships(track).single { it.kind == "Work" }.entityKey
        val first = track("Writer A", EchoMusicRelationMetadata(work = "Sonata", movement = "Allegro"))
        val second = first.copy(composer = "Writer B")
        assertNotEquals(work(first), work(second))
        val explicit = first.copy(musicRelationsJson = EchoMusicRelationMetadata(work = "Sonata", workId = "known-id").encoded())
        assertEquals(work(explicit), work(explicit.copy(composer = "Writer alias")))
        val other = first.copy(musicRelationsJson = EchoMusicRelationMetadata(work = "Concerto", movement = "Allegro").encoded())
        fun movement(row: LibraryTrackEntity) = musicRelationMemberships(row).single { it.kind == "Movement" }.entityKey
        assertNotEquals(movement(first), movement(other))
        assertFalse(musicRelationMemberships(first.copy(clipStartMs = 1000)).any { it.kind == "Movement" || it.kind == "Recording" })
    }

    @Test fun namesAreExplicitBoundedAndRemoteTracksAreExcluded() {
        val row = track("Writer", EchoMusicRelationMetadata(conductor = "A; B", performer = "AC/DC"))
        val memberships = musicRelationMemberships(row)
        assertEquals(2, memberships.count { it.kind == "Conductor" })
        assertEquals("AC/DC", memberships.single { it.kind == "Performer" }.name)
        assertFalse(memberships.any { it.kind in listOf("Work", "Movement", "Recording") })
        assertTrue(musicRelationMemberships(row.copy(source = "webdav")).isEmpty())
    }

    @Test fun personIdsConnectAliasesAndSeparateSameNamesWithoutGuessingIdAssignments() {
        val id = "12345678-1234-1234-1234-123456789012"
        fun composerKey(name: String, personId: String) = musicRelationMemberships(track(name,
            EchoMusicRelationMetadata(credits = listOf(EchoMusicCredit(name, "composer", personId)))))
            .single { it.kind == "Composer" }.entityKey
        assertEquals(composerKey("Writer", id), composerKey("作曲家", "https://musicbrainz.org/artist/$id/"))
        assertNotEquals(composerKey("Writer", id), composerKey("Writer", "22345678-1234-1234-1234-123456789012"))
        val mismatched = musicRelationTags(listOf("ARTIST" to "A; B", "MUSICBRAINZ_ARTISTID" to id))
        assertTrue(mismatched.credits.all { it.personId == null })
        val matching = musicRelationTags(listOf("ARTIST" to "A", "MUSICBRAINZ_ARTISTID" to id))
        assertEquals(id, matching.credits.single().personId)
        val homonyms = musicRelationTags(listOf("COMPOSER" to "Same; Same", "MUSICBRAINZ_COMPOSERID" to
            "$id; 22345678-1234-1234-1234-123456789012"))
        assertEquals(2, musicRelationMemberships(track("Same", homonyms)).count { it.kind == "Composer" })
        assertNull(musicPersonId("https://example.com/artist/$id"))
    }

    @Test fun performanceGroupingUsesReleaseIdentityAndCreditsAcrossMovements() {
        val firstTags = EchoMusicRelationMetadata(work = "Concerto", movement = "Allegro", conductor = "A", performer = "Soloist")
        val first = track("Writer", firstTags)
        fun recordingKey(row: LibraryTrackEntity) = musicRelationMemberships(row).single { it.kind == "Recording" }.entityKey
        val second = first.copy(id = "movement-2", trackNumber = 2,
            musicRelationsJson = firstTags.copy(movement = "Adagio", recordingId = "second-movement-recording").encoded())
        assertEquals(recordingKey(first), recordingKey(second))
        assertNotEquals(recordingKey(first), recordingKey(first.copy(albumKey = "another-release-with-same-title")))
        assertNotEquals(recordingKey(first), recordingKey(first.copy(musicRelationsJson = firstTags.copy(conductor = "B").encoded())))
        assertNotEquals(recordingKey(first), recordingKey(first.copy(musicRelationsJson = firstTags.copy(performer = "Another soloist").encoded())))
    }

    @Test fun rolesLabelsAndEditedComposerRetainTheirCorrectMemberships() {
        val metadata = musicRelationTags(listOf("PERFORMER:violin" to "Soloist", "PRODUCER" to "Producer", "LABEL" to "Label",
            "COMPOSER" to "Old writer"))
        val rows = musicRelationMemberships(track("New writer", metadata))
        assertEquals("New writer", rows.single { it.kind == "Composer" }.name)
        assertTrue(rows.any { it.kind == "Performer" && it.name == "Soloist" })
        assertTrue(rows.any { it.kind == "CreditRole" && it.name == "violin" })
        assertTrue(rows.any { it.kind == "Label" && it.name == "Label" })
        assertTrue(rows.any { it.kind == "Contributor" && it.name == "Producer" })
        assertEquals(metadata, decodeMusicRelationTags(metadata.encoded()))
    }

    @Test fun mp4NamesAndPersonIdsAreJoinedAfterReadingAllAtoms() {
        val id = "12345678-1234-1234-1234-123456789012"
        fun atom(name: String, data: ByteArray) = ByteBuffer.allocate(4).putInt(data.size + 8).array() + name.toByteArray(Charsets.ISO_8859_1) + data
        fun data(value: String) = atom("data", byteArrayOf(0, 0, 0, 1, 0, 0, 0, 0) + value.toByteArray())
        fun custom(name: String, value: String) = atom("----", atom("mean", ByteArray(4) + "com.apple.iTunes".toByteArray()) +
            atom("name", ByteArray(4) + name.toByteArray()) + data(value))
        val items = custom("MUSICBRAINZ_ARTISTID", id) + atom("©ART", data("Artist")) +
            atom("©wrk", data("Concerto")) + custom("LABEL", "Label")
        val bytes = atom("ftyp", "M4A ".toByteArray() + ByteArray(4)) +
            atom("moov", atom("udta", atom("meta", ByteArray(4) + atom("ilst", items))))
        val tags = readLocalAudioTags(bytes.inputStream())!!
        assertEquals(id, tags.musicRelations.credits.single().personId)
        assertEquals("Concerto", tags.musicRelations.work)
        assertEquals("Label", tags.musicRelations.label)
    }

    private fun track(composer: String, tags: EchoMusicRelationMetadata) = LibraryTrackEntity(
        id = "test", contentUri = "content://test", title = "I. Allegro", artist = "Artist", album = "Release",
        albumArtist = "Artist", artworkUri = null, durationMs = 1000, trackNumber = 1, discNumber = 1,
        year = 2000, mimeType = "audio/flac", sizeBytes = 100, dateModifiedSeconds = 1,
        composer = composer, albumKey = "album", musicRelationsJson = tags.encoded(),
    )
    private fun le(n: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(n).array()
    private fun sync(n: Int) = byteArrayOf(((n shr 21) and 127).toByte(), ((n shr 14) and 127).toByte(), ((n shr 7) and 127).toByte(), (n and 127).toByte())
    private fun block(type: Int, bytes: ByteArray) = byteArrayOf(type.toByte(), (bytes.size shr 16).toByte(), (bytes.size shr 8).toByte(), bytes.size.toByte()) + bytes
}
