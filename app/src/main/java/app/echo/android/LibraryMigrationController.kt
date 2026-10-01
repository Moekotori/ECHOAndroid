package app.echo.android

import android.content.Context
import android.net.Uri
import app.echo.android.data.*
import app.echo.android.model.backup.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID
import java.util.zip.*

internal class LibraryMigrationController(private val context: Context, private val repository: EchoLibraryRepository,
    private val settings: EchoSettingsStore, private val lyrics: LyricsController) {
    private val lock = Mutex()
    private var pending: Pair<Uri,EchoBackupDocument>? = null
    private val _preview = MutableStateFlow<EchoBackupPreview?>(null)
    val preview = _preview.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    suspend fun export(uri: Uri) = lock.withLock {
        _busy.value = true
        try { withContext(Dispatchers.IO) {
            val preferences = settings.appSettings.first()
            val selected = lyrics.backupSelections(repository::matchLegacyLyricHashes)
            val references = repository.backupTrackReferences(selected.keys.toList())
            val lyricRows = selected.map { (id,value) ->
                val metadata = value.first?.let { org.json.JSONObject(it).optJSONObject("metadata") }
                val reference = references[id] ?: EchoBackupTrackRef(metadata?.optString("echo_title").orEmpty(),metadata?.optString("echo_artist").orEmpty(),
                    durationMs = metadata?.optLong("echo_duration_ms") ?: 0,album = metadata?.optString("echo_album")?.takeIf(String::isNotBlank))
                EchoBackupLyrics(reference,value.first,value.second)
            }
            val sources = linkedMapOf<String,Uri>()
            preferences.customBackgroundUri?.let { sources["background"] = Uri.parse(it) }
            preferences.startupBackgroundUri?.let { sources["startup"] = Uri.parse(it) }
            preferences.importedFontUri?.let { sources["font"] = Uri.parse(it) }
            val assets = sources.map { (role,source) ->
                val extension = if (role == "font") "ttf" else android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(context.contentResolver.getType(source))
                    ?: source.lastPathSegment?.substringAfterLast('.', "bin")?.takeIf { it.matches(Regex("[a-zA-Z0-9]{1,8}")) } ?: "bin"
                EchoBackupAsset(role,"assets/$role.$extension")
            }
            val document = EchoBackupDocument(exportedAtEpochMs = System.currentTimeMillis(), settings = preferences.toBackupSettings(),
                playlists = repository.exportBackupPlaylists(),favorites = repository.exportBackupFavorites(),bookmarks = repository.exportBackupBookmarks(),
                history = repository.exportListeningHistory(),lyrics = lyricRows,assets = assets)
            val bytes = EchoBackupCodec.encode(document).toByteArray(Charsets.UTF_8)
            require(bytes.size <= 32 * 1024 * 1024)
            context.contentResolver.openOutputStream(uri)?.use { output -> ZipOutputStream(output).use { zip ->
                zip.setLevel(Deflater.NO_COMPRESSION)
                zip.putNextEntry(ZipEntry("metadata.json")); zip.write(bytes); zip.closeEntry()
                assets.forEach { asset ->
                    zip.putNextEntry(ZipEntry(asset.entry))
                    val source = checkNotNull(sources[asset.role])
                    openSource(source).use { input -> copyBounded(input,zip,512L * 1024 * 1024) }
                    zip.closeEntry()
                }
            } } ?: error("Could not write backup")
        } } finally { _busy.value = false }
    }

    suspend fun prepare(uri: Uri) = lock.withLock {
        _busy.value = true
        try {
            val document = withContext(Dispatchers.IO) { readDocument(uri) }
            val preview = repository.backupPreview(document,settings.appSettings.first().toBackupSettings())
            pending = uri to document; _preview.value = preview
        } finally { _busy.value = false }
    }
    fun dismiss() { if (!_busy.value) { pending = null; _preview.value = null } }

    suspend fun apply(): EchoBackupRestoreResult = lock.withLock {
        val (uri,document) = checkNotNull(pending)
        _busy.value = true
        try { withContext(Dispatchers.IO) {
            val restoredAssets = extractAssets(uri,document)
            val result = repository.restoreBackupCatalog(document.playlists,document.favorites)
            val momentsMissing = repository.restoreBackupBookmarks(document.bookmarks)
            repository.restoreListeningHistory(document.history)
            var lyricMissing = 0
            document.lyrics.forEach { selection ->
                currentCoroutineContext().ensureActive()
                val id = repository.resolveBackupTrack(selection.track)
                if (id == null) lyricMissing++ else lyrics.restoreSelection(id,selection.documentJson,selection.userOffsetMs)
            }
            settings.applyBackupSettings(document.settings)
            restoredAssets["background"]?.let { settings.setCustomBackground(document.settings.backgroundMode ?: "image", Uri.fromFile(it).toString()) }
            restoredAssets["startup"]?.let { settings.setStartupBackgroundUri(Uri.fromFile(it).toString()) }
            restoredAssets["font"]?.let { settings.setImportedFontUri(Uri.fromFile(it).toString()) }
            pending = null; _preview.value = null
            result.copy(tracksMissing = result.tracksMissing + momentsMissing + lyricMissing)
        } } finally { _busy.value = false }
    }

    private fun openSource(uri: Uri) = if (uri.scheme == "file") File(checkNotNull(uri.path)).inputStream()
        else context.contentResolver.openInputStream(uri) ?: error("Could not read asset")
    private suspend fun readDocument(uri: Uri): EchoBackupDocument {
        openSource(uri).buffered().use { input ->
            input.mark(4); val first = input.read(); val second = input.read(); input.reset()
            if (first != 80 || second != 75) return EchoBackupCodec.decode(readBounded(input))
            ZipInputStream(input).use { zip ->
                val entry = zip.nextEntry ?: error("Backup metadata is missing")
                require(entry.name == "metadata.json")
                return EchoBackupCodec.decode(readBounded(zip))
            }
        }
    }
    private suspend fun extractAssets(uri: Uri, document: EchoBackupDocument): Map<String,File> {
        if (document.assets.isEmpty()) return emptyMap()
        val expected = document.assets.associateBy(EchoBackupAsset::entry)
        val directory = File(context.filesDir,"migration-assets").apply { mkdirs() }
        val result = mutableMapOf<String,File>()
        try {
            openSource(uri).use { input -> ZipInputStream(input).use { zip -> while (true) {
                val entry = zip.nextEntry ?: break
                val asset = expected[entry.name] ?: continue
                require(asset.role !in result)
                val target = File(directory,"${UUID.randomUUID()}.${entry.name.substringAfterLast('.')}")
                result[asset.role] = target
                target.outputStream().use { output -> copyBounded(zip,output,512L * 1024 * 1024) }
            } } }
            require(result.size == expected.size)
            return result
        } catch (error: Exception) { result.values.forEach { it.delete() }; throw error }
    }
    private suspend fun readBounded(input: java.io.InputStream): String {
        val output = java.io.ByteArrayOutputStream()
        copyBounded(input,output,32L * 1024 * 1024)
        return output.toString("UTF-8")
    }
    private suspend fun copyBounded(input: java.io.InputStream, output: java.io.OutputStream, maximum: Long) {
        val buffer = ByteArray(65536); var total = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer); if (count < 0) return
            total += count; require(total <= maximum); output.write(buffer,0,count)
        }
    }
}
