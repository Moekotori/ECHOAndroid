package app.echo.android.data

import android.content.ContentResolver
import android.os.Build
import android.os.Environment
import android.net.Uri
import android.provider.MediaStore
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import app.echo.android.model.library.CueSheet
import app.echo.android.model.library.CueSheetPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** External CUE discovery belongs to scanning, never playback. */
internal object MediaStoreCueCatalog {
    internal data class IndexedCueFile(
        val folder: String,
        val fileName: String,
        val sheet: CueSheet,
    )

    internal data class CueSheetCatalog(
        val byKey: Map<String, CueSheet>,
        val files: List<IndexedCueFile>,
        val uncertainFolders: Set<String> = emptySet(),
        val discoveryFailed: Boolean = false,
    ) {
        private val uncertain = ScanDirectoryIndex(uncertainFolders, ignoreCase = true)
        fun isUncertain(folder: String?): Boolean {
            return discoveryFailed || uncertain.contains(folder)
        }
    }

    suspend fun load(contentResolver: ContentResolver, relativePathPrefix: String? = null): CueSheetCatalog {
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
        ) + if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                MediaStore.Files.FileColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.VOLUME_NAME,
            )
        } else {
            @Suppress("DEPRECATION")
            arrayOf(MediaStore.Files.FileColumns.DATA)
        }
        @Suppress("DEPRECATION")
        val storageRoot = Environment.getExternalStorageDirectory()
            .absolutePath
            .replace('\\', '/')
            .trimEnd('/')
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }
        val scope = CueCatalogScope(relativePathPrefix, Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q, storageRoot)
        val cueScope = scope.cueSelection()
        val cursor = try {
            contentResolver.query(
                uri,
                projection,
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?" + (cueScope?.let { " AND ${it.sql}" } ?: ""),
                arrayOf("%.cue") + (cueScope?.args ?: emptyArray()),
                null,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) { null } ?: return CueSheetCatalog(emptyMap(), emptyList(), discoveryFailed = true)
        val sheets = LinkedHashMap<String, CueSheet>()
        val files = ArrayList<IndexedCueFile>()
        data class PendingCue(val uri: Uri, val folder: String, val name: String, val legacyFolder: String?)
        val pending = ArrayList<PendingCue>()
        var trackCount = 0
        val uncertainFolders = HashSet<String>()

        cursor.use { listing ->
            val idIndex = listing.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameIndex = listing.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeIndex = listing.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
            val pathIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                listing.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)
            } else {
                -1
            }
            val volumeIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                listing.getColumnIndex(MediaStore.MediaColumns.VOLUME_NAME)
            } else {
                -1
            }
            @Suppress("DEPRECATION")
            val dataIndex = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                listing.getColumnIndex(MediaStore.Files.FileColumns.DATA)
            } else {
                -1
            }
            while (listing.moveToNext()) {
                coroutineContext.ensureActive()
                val name = listing.getStringOrNull(nameIndex) ?: continue
                val size = if (sizeIndex >= 0) listing.getLongOrNull(sizeIndex) ?: 0L else 0L
                val id = listing.getLong(idIndex)
                val folder = LibraryScanPolicy.cueSheetFolder(
                    volumeName = if (volumeIndex >= 0) listing.getStringOrNull(volumeIndex) else null,
                    mediaStoreRelativePath = if (pathIndex >= 0) listing.getStringOrNull(pathIndex) else null,
                    legacyDataPath = if (dataIndex >= 0) listing.getStringOrNull(dataIndex) else null,
                    primaryStorageRoot = storageRoot,
                )
                if (!LocalAudioFileTypes.isCueSheet(name, null)) continue
                if (pending.size >= 4096)
                    return CueSheetCatalog(emptyMap(), files, discoveryFailed = true)
                if (size > CueSheetPolicy.MaxCueBytes) { uncertainFolders += folder.lowercase(java.util.Locale.ROOT); continue }
                pending += PendingCue(Uri.withAppendedPath(uri, id.toString()), folder.trim('/'), name,
                    if (dataIndex >= 0) listing.getStringOrNull(dataIndex)?.substringBeforeLast('/') else null)
            }
        }
        if (pending.isEmpty()) return CueSheetCatalog(emptyMap(), emptyList(), uncertainFolders)
        // Read names before decoding: exact non-ASCII FILE references disambiguate legacy charsets.
        val wantedFolders = pending.mapTo(HashSet()) { it.folder }
        fun wanted(folder: String): Boolean {
            var current = folder
            while (true) {
                if (current in wantedFolders) return true
                if (current.isEmpty()) return false
                current = current.substringBeforeLast('/', "")
            }
        }
        val audioNames = HashMap<String, MutableList<String>>()
        var nameCount = 0
        val (fallbackClause, fallbackArgs) = LocalAudioFileTypes.mediaStoreFallbackNameClause(MediaStore.Files.FileColumns.DISPLAY_NAME)
        for (nameScope in scope.audioSelections(wantedFolders, pending.mapNotNull { it.legacyFolder })) {
        val audioCursor = try { contentResolver.query(
            uri, projection,
            "(${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO} OR $fallbackClause)" +
                (nameScope?.let { " AND ${it.sql}" } ?: ""),
            fallbackArgs + (nameScope?.args ?: emptyArray()), null,
        ) } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) { null }
        if (audioCursor == null) return CueSheetCatalog(emptyMap(), files, uncertainFolders, discoveryFailed = true)
        audioCursor.use { listing ->
            val nameIndex = listing.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val pathIndex = listing.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)
            val volumeIndex = listing.getColumnIndex(MediaStore.MediaColumns.VOLUME_NAME)
            @Suppress("DEPRECATION")
            val dataIndex = listing.getColumnIndex(MediaStore.Files.FileColumns.DATA)
            while (listing.moveToNext()) {
                coroutineContext.ensureActive()
                val name = listing.getStringOrNull(nameIndex) ?: continue
                val folder = LibraryScanPolicy.cueSheetFolder(
                    volumeName = if (volumeIndex >= 0) listing.getStringOrNull(volumeIndex) else null,
                    mediaStoreRelativePath = if (pathIndex >= 0) listing.getStringOrNull(pathIndex) else null,
                    legacyDataPath = if (dataIndex >= 0) listing.getStringOrNull(dataIndex) else null,
                    primaryStorageRoot = storageRoot,
                )
                if (wanted(folder)) {
                    if (++nameCount > 65536) return CueSheetCatalog(emptyMap(), emptyList(), discoveryFailed = true)
                    audioNames.getOrPut(folder) { ArrayList() }.add(name)
                }
            }
        }
        }
        val indices = audioNames.mapValues { CueAudioNameIndex(it.value) }
        for (cue in pending) {
            coroutineContext.ensureActive()
            val sheet = try {
                contentResolver.openInputStream(cue.uri)?.use { input ->
                    readCueSheet(input) { reference ->
                        val (descendant, name) = cueReference(reference) ?: return@readCueSheet false
                        val folder = listOf(cue.folder, descendant)
                            .filter(String::isNotEmpty).joinToString("/")
                        indices[folder]?.match(name) != null
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }
            if (sheet == null) { uncertainFolders += cue.folder; continue }
            val directories = sheet.forLocalDirectories()
            trackCount += directories.filterKeys { descendant ->
                scope.includes(listOf(cue.folder, descendant).filter(String::isNotEmpty).joinToString("/"))
            }.values.sumOf { it.tracks.size }
            if (trackCount > 32768) return CueSheetCatalog(emptyMap(), files, discoveryFailed = true)
            if (directories.values.sumOf { it.tracks.size } != sheet.tracks.size) uncertainFolders += cue.folder
            directories.forEach { (descendant, localized) ->
                val target = listOf(cue.folder, descendant).filter(String::isNotEmpty).joinToString("/")
                if (scope.includes(target)) files += IndexedCueFile(target, cue.name, localized)
            }
        }
        val catalogs = HashMap<String, CueDirectoryCatalog>()
        files.forEach { cue ->
            coroutineContext.ensureActive()
            catalogs.getOrPut(cue.folder) { CueDirectoryCatalog(audioNames[cue.folder].orEmpty()) }
                .add(cue.sheet, cue.fileName)
        }
        catalogs.forEach { (folder, catalog) ->
            if (catalog.uncertain) uncertainFolders += folder
            catalog.sheets().forEach { (audio, sheet) -> sheets[cueFolderKey(folder, audio)] = sheet }
        }
        return CueSheetCatalog(sheets, files, uncertainFolders)
    }

}

internal fun cueFolderKey(folder: String, name: String): String =
    "${folder.replace('\\', '/').trim('/')}\u0000$name"
