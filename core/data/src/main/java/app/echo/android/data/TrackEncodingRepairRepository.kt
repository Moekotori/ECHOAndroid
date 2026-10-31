package app.echo.android.data

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.AtomicFile
import app.echo.android.model.library.*
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.nio.charset.Charset
import java.security.MessageDigest

/** Keeps a bounded undo snapshot; it never writes source audio or sends data over the network. */
class TrackEncodingRepairRepository(context: Context, private val repository: EchoLibraryRepository) {
    private val context = context.applicationContext
    private val directory = File(context.filesDir, "text-encoding-repairs")
    private val mutex = Mutex()
    private fun file(id: String): File = File(directory,
        MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).joinToString("") { "%02x".format(it) } + ".json")

    private fun revision(row: LibraryTrackEntity) = "${row.contentUri}|${row.sizeBytes}|${row.dateModifiedSeconds}|${row.metadataEditedAtEpochMs}"

    suspend fun previewFolder(id: String, charset: String): List<EchoFolderEncodingItem> = withContext(Dispatchers.IO) {
        require(charset in EchoTextEncodings.names)
        val row = requireNotNull(repository.database.trackDao().getTrackById(id))
        require(row.source in listOf("mediastore", "saf") && !row.relativePath.isNullOrBlank())
        val tracks = repository.database.trackDao().queryTracks(androidx.sqlite.db.SimpleSQLiteQuery(
            "SELECT * FROM library_tracks WHERE source = ? AND relativePath = ? AND clipStartMs = 0 AND clipEndMs = 0 AND NOT EXISTS (SELECT 1 FROM library_hidden_tracks h WHERE h.trackId = library_tracks.id) ORDER BY id LIMIT 51",
            arrayOf(row.source, row.relativePath)))
        require(tracks.size <= 50) { "Choose a folder containing at most 50 tracks" }
        tracks.map { track ->
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val value = try { preview(track.id, charset) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { null }
            EchoFolderEncodingItem(track.id, track.title, value)
        }
    }

    suspend fun applyFolder(items: List<EchoFolderEncodingItem>): EchoFolderEncodingResult {
        require(items.size <= 50)
        var applied = 0
        var failed = 0
        for (item in items) {
            val value = item.preview ?: continue
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            try { apply(item.trackId, value); applied++ }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { failed++ }
        }
        return EchoFolderEncodingResult(applied, failed)
    }

    suspend fun preview(id: String, charset: String?): EchoEncodingPreview = withContext(Dispatchers.IO) {
        val row = requireNotNull(repository.database.trackDao().getTrackById(id))
        require(row.source in listOf("mediastore", "saf") && row.clipStartMs == 0L && row.clipEndMs == 0L)
        val fields = if (charset == null) {
            context.contentResolver.openFileDescriptor(Uri.parse(row.contentUri), "r")?.use { descriptor ->
                ParcelFileDescriptor.AutoCloseInputStream(descriptor).use(::readLocalAudioTags)
            }?.let { mapOf("TIT2" to it.title, "TPE1" to it.artist, "TALB" to it.album.orEmpty(), "TPE2" to it.albumArtist.orEmpty()) }.orEmpty()
        } else {
            require(charset in EchoTextEncodings.names)
            context.contentResolver.openFileDescriptor(Uri.parse(row.contentUri), "r")?.use { descriptor ->
                ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { LegacyMetadataPreviewReader.read(it, Charset.forName(charset)) }
            }.orEmpty().also { require(it.isNotEmpty()) { "No legacy ID3 text available" } }
        }
        EchoEncodingPreview(charset, fields["TIT2"]?.takeIf(String::isNotBlank) ?: row.title,
            fields["TPE1"]?.takeIf(String::isNotBlank) ?: row.artist,
            fields["TALB"]?.takeIf(String::isNotBlank) ?: row.album,
            fields["TPE2"]?.takeIf(String::isNotBlank) ?: row.albumArtist, file(id).isFile, revision(row))
    }

    suspend fun apply(id: String, preview: EchoEncodingPreview) = withContext(Dispatchers.IO) { mutex.withLock {
        require(preview.charset in EchoTextEncodings.names)
        val row = requireNotNull(repository.database.trackDao().getTrackById(id))
        require(row.source in listOf("mediastore", "saf"))
        require(preview.sourceRevision == revision(row)) { "Track changed; preview again" }
        val target = file(id)
        if (!target.exists()) {
            directory.mkdirs()
            require((directory.listFiles()?.size ?: 0) < 512) { "Repair history is full" }
            val original = JSONObject().put("title", row.title).put("artist", row.artist)
                .put("album", row.album ?: JSONObject.NULL).put("albumArtist", row.albumArtist ?: JSONObject.NULL)
                .put("uri", row.contentUri).put("modified", row.dateModifiedSeconds).put("size", row.sizeBytes)
                .put("editedAt", row.metadataEditedAtEpochMs ?: JSONObject.NULL)
            val atomic = AtomicFile(target)
            val bytes = original.toString().toByteArray()
            require(bytes.size <= 16384) { "Undo metadata exceeds the storage limit" }
            val stream = atomic.startWrite()
            try { stream.write(bytes); atomic.finishWrite(stream) }
            catch (error: Throwable) { atomic.failWrite(stream); throw error }
        }
        val updated = update(row, preview)
        val original = JSONObject(AtomicFile(target).openRead().use { it.readBytes().toString(Charsets.UTF_8) })
        original.put("appliedRevision", revision(updated))
        val atomic = AtomicFile(target)
        val bytes = original.toString().toByteArray()
        require(bytes.size <= 16384)
        val stream = atomic.startWrite()
        try { stream.write(bytes); atomic.finishWrite(stream) }
        catch (error: Throwable) { atomic.failWrite(stream); throw error }
    } }

    suspend fun restore(id: String) = withContext(Dispatchers.IO) { mutex.withLock {
        val row = requireNotNull(repository.database.trackDao().getTrackById(id))
        val target = file(id)
        require(target.length() in 1..16384)
        val before = JSONObject(AtomicFile(target).openRead().use { it.readBytes().toString(Charsets.UTF_8) })
        require(row.contentUri == before.getString("uri") && row.sizeBytes == before.getLong("size") &&
            row.dateModifiedSeconds == before.getLong("modified")) { "Source changed; preview automatic decoding again" }
        require(!before.has("appliedRevision") || before.getString("appliedRevision") == revision(row)) { "Track was edited after repair" }
        update(row, EchoEncodingPreview(null, before.getString("title"), before.getString("artist"),
            before.optString("album").takeUnless { before.isNull("album") },
            before.optString("albumArtist").takeUnless { before.isNull("albumArtist") }),
            if (before.isNull("editedAt")) null else before.getLong("editedAt"))
        check(target.delete())
    } }

    private suspend fun update(row: LibraryTrackEntity, value: EchoEncodingPreview, editedAt: Long? = System.currentTimeMillis()): LibraryTrackEntity {
        val updated = row.withUserMetadata(EchoTrackMetadataUpdate(row.id, value.title, value.artist,
            value.album, value.albumArtist, row.trackNumber, row.discNumber, row.year, row.composer, row.artworkUri, genre = row.genre),
            System.currentTimeMillis()).copy(metadataEditedAtEpochMs = editedAt)
        repository.database.withTransaction {
            require(repository.database.trackDao().getTrackById(row.id)?.let(::revision) == revision(row)) { "Track changed; preview again" }
            repository.database.trackDao().upsertBatchWithFts(listOf(updated))
            val keys = row.toSummaryKeySet() + updated.toSummaryKeySet()
            repository.database.trackDao().rebuildLibrarySummariesForKeys(keys.albumKeys, keys.artistKeys, keys.folderKeys, keys.genreKeys)
        }
        return updated
    }
}
