package app.echo.android.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.util.AtomicFile
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.echo.android.model.library.EchoArtistArtwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

private val Context.artistArtworkPreferences by preferencesDataStore("echo-artist-artwork")

/** Persistent user-owned pictures and a bounded local lookup cache. Network refreshes cannot write here. */
class ArtistArtworkStore(context: Context, private val database: EchoLibraryDatabase) {
    private val context = context.applicationContext
    private val store = this.context.artistArtworkPreferences
    private val assets = File(this.context.filesDir, "artist-artwork")
    private val writes = Mutex()
    private val key = stringPreferencesKey("profiles")
    val overrides = store.data.map { decode(it[key]) }.catch { error ->
        if (error is IOException || error is org.json.JSONException || error is IllegalArgumentException) emit(emptyMap()) else throw error
    }.flowOn(Dispatchers.IO)

    private suspend fun readProfiles() = decode(store.data.first()[key])

    suspend fun setManual(name: String, uri: Uri) = setImage(name, uri, manual = true)
    suspend fun clearManual(name: String) = withContext(Dispatchers.IO) { writes.withLock {
        store.edit { prefs ->
            val profiles = decode(prefs[key]).toMutableMap(); val id = EchoArtistArtwork.key(name)
            profiles[id]?.let { profiles[id] = it.copy(manualUri = null, checkedAt = 0) }
            prefs[key] = encode(profiles)
        }
        prune(readProfiles())
    } }

    suspend fun discoverLocal(name: String) = withContext(Dispatchers.IO) {
        if (name.isBlank() || name.length > 512) return@withContext
        val id = EchoArtistArtwork.key(name)
        val current = readProfiles()[id]
        if (current?.manualUri != null || (current != null && System.currentTimeMillis() - current.checkedAt in 0..299_999)) return@withContext
        val tracks = database.trackDao().artistArtworkTracks(libraryArtistKey(name.normalizedForSearch()), name.normalizedForSearch())
        val folders = hashSetOf<String>()
        for (track in tracks) {
            coroutineContext.ensureActive()
            val group = track.relativePath ?: track.contentUri.substringBeforeLast('/')
            if (!folders.add(group)) continue
            if (folders.size > 3) break
            val siblings = try { DocumentSiblingFiles.read(context.contentResolver, Uri.parse(track.contentUri), ImageExtensions) }
                catch (_: IOException) { null } catch (_: SecurityException) { null }
            val image = siblings?.files?.firstOrNull { matches(it.name, name) }
            if (image != null) { setImage(name, image.uri, manual = false); return@withContext }
        }
        writes.withLock { store.edit { prefs ->
            val profiles = decode(prefs[key]).toMutableMap()
            profiles[id] = (profiles[id] ?: EchoArtistArtwork()).copy(checkedAt = System.currentTimeMillis())
            trim(profiles); prefs[key] = encode(profiles)
        } }
    }

    suspend fun matchFolder(name: String, tree: Uri) = withContext(Dispatchers.IO) {
        val parent = DocumentsContract.getTreeDocumentId(tree)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
        val candidates = ArrayList<Pair<String, Uri>>()
        context.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            var visited = 0
            while (cursor.moveToNext()) {
                coroutineContext.ensureActive(); require(++visited <= 4_096)
                val fileName = cursor.getString(1).orEmpty()
                if (matches(fileName, name)) candidates += fileName to DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
            }
        } ?: error("Cannot read picture folder")
        val candidate = candidates.minByOrNull { it.first.lowercase(java.util.Locale.ROOT) } ?: error("No matching artist picture")
        setImage(name, candidate.second, manual = false)
    }

    private suspend fun setImage(name: String, uri: Uri, manual: Boolean) = withContext(Dispatchers.IO) { writes.withLock {
        require(name.isNotBlank() && name.length <= 512)
        require(uri.scheme in setOf("content", "file"))
        val current = readProfiles()
        prune(current)
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream(); val buffer = ByteArray(32 * 1024); var total = 0
            while (true) {
                coroutineContext.ensureActive()
                val count = input.read(buffer); if (count < 0) break
                total += count; require(total <= 8 * 1024 * 1024); out.write(buffer, 0, count)
            }
            out.toByteArray()
        } ?: error("Cannot open picture")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth in 1..20_000 && bounds.outHeight in 1..20_000 && bounds.outWidth.toLong() * bounds.outHeight <= 80_000_000)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1_024) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: error("Invalid picture")
        val encoded = try { ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.WEBP, 90, output)); output.toByteArray()
        } } finally { bitmap.recycle() }
        require(encoded.size <= 2 * 1024 * 1024)
        val hash = MessageDigest.getInstance("SHA-256").digest(encoded).joinToString("") { "%02x".format(it) }
        assets.mkdirs(); val file = File(assets, "$hash.webp")
        if (!file.exists() || file.length() != encoded.size.toLong()) {
            require(assets.listFiles().orEmpty().sumOf { it.length() } - file.length() + encoded.size <= 512L * 1024 * 1024)
            val atomic = AtomicFile(file)
            val output = atomic.startWrite()
            try { output.write(encoded); atomic.finishWrite(output) }
            catch (failure: Throwable) { atomic.failWrite(output); throw failure }
        }
        coroutineContext.ensureActive()
        store.edit { prefs ->
            val profiles = decode(prefs[key]).toMutableMap(); val id = EchoArtistArtwork.key(name)
            val old = profiles[id] ?: EchoArtistArtwork()
            profiles[id] = if (manual) old.copy(manualUri = file.toURI().toString())
                else old.copy(localUri = file.toURI().toString(), checkedAt = System.currentTimeMillis())
            trim(profiles); prefs[key] = encode(profiles)
        }
        prune(readProfiles())
    } }

    private fun prune(profiles: Map<String, EchoArtistArtwork>) {
        val live = profiles.values.flatMap { listOfNotNull(it.manualUri, it.localUri) }.toHashSet()
        assets.listFiles().orEmpty().filter { it.name.matches(Regex("[0-9a-f]{64}\\.webp")) }.forEach { file ->
            if (file.toURI().toString() !in live && file.canonicalFile.parentFile == assets.canonicalFile) file.delete()
        }
    }
    private fun trim(profiles: MutableMap<String, EchoArtistArtwork>) {
        require(profiles.values.count { it.manualUri != null } <= 5_000)
        profiles.entries.filter { it.value.manualUri == null }.sortedBy { it.value.checkedAt }
            .take((profiles.size - 5_000).coerceAtLeast(0)).forEach { profiles.remove(it.key) }
    }
    private fun decode(raw: String?): Map<String, EchoArtistArtwork> {
        if (raw.isNullOrBlank()) return emptyMap()
        require(raw.length <= 4 * 1024 * 1024)
        val json = JSONObject(raw)
        return buildMap { json.keys().asSequence().take(5_000).forEach { id ->
            val value = json.optJSONObject(id) ?: return@forEach
            fun uri(key: String) = value.optString(key).takeIf { it.startsWith(assets.toURI().toString()) && it.endsWith(".webp") }
            put(id, EchoArtistArtwork(uri("manual"), uri("local"), value.optLong("checked")))
        } }
    }
    private fun encode(profiles: Map<String, EchoArtistArtwork>) = JSONObject().apply {
        profiles.forEach { (id, value) -> put(id, JSONObject().put("manual", value.manualUri).put("local", value.localUri).put("checked", value.checkedAt)) }
    }.toString().also { require(it.length <= 4 * 1024 * 1024) }
    companion object {
        private val ImageExtensions = listOf(".jpg", ".jpeg", ".png", ".webp")
        internal fun matches(fileName: String, artist: String): Boolean {
            if (ImageExtensions.none { fileName.endsWith(it, ignoreCase = true) } || !fileName.startsWith("artist_", ignoreCase = true)) return false
            return EchoArtistArtwork.key(fileName.substringBeforeLast('.').substring(7)) == EchoArtistArtwork.key(artist)
        }
    }
}
