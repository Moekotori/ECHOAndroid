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
        fun isUncertain(folder: String?): Boolean {
            val normalized = folder.orEmpty().replace('\\', '/').trim('/').lowercase(java.util.Locale.ROOT)
            return discoveryFailed || uncertainFolders.any { it.isEmpty() || normalized == it || normalized.startsWith("$it/") }
        }
    }

    suspend fun load(contentResolver: ContentResolver): CueSheetCatalog {
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
        val cursor = try {
            contentResolver.query(
                uri,
                projection,
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?",
                arrayOf("%.cue"),
                null,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) { null } ?: return CueSheetCatalog(emptyMap(), emptyList(), discoveryFailed = true)
        val sheets = LinkedHashMap<String, CueSheet>()
        val files = ArrayList<IndexedCueFile>()
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
                if (files.size >= 4096 || trackCount >= 32768)
                    return CueSheetCatalog(emptyMap(), files, discoveryFailed = true)
                if (size > CueSheetPolicy.MaxCueBytes) { uncertainFolders += folder.lowercase(java.util.Locale.ROOT); continue }
                val document = Uri.withAppendedPath(uri, id.toString())
                val sheet = try {
                    contentResolver.openInputStream(document)?.use(::readCueSheet)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) { null }
                if (sheet == null) { uncertainFolders += folder.lowercase(java.util.Locale.ROOT); continue }
                trackCount += sheet.tracks.size
                sheet.forLocalDirectories().forEach { (descendant, localized) ->
                    val target = listOf(folder.trim('/'), descendant).filter { it.isNotEmpty() }.joinToString("/")
                    files += IndexedCueFile(target.lowercase(java.util.Locale.ROOT), name, localized)
                }
            }
        }
        if (files.isEmpty()) return CueSheetCatalog(emptyMap(), emptyList(), uncertainFolders)
        // One lightweight name query only when CUEs exist. Keep candidates in CUE folders only.
        val wantedStems = files.groupBy { it.folder }.mapValues { (_, cues) ->
            cues.flatMap { cue ->
                (listOf(cue.fileName) + cue.sheet.tracks.mapNotNull { it.fileName } + listOfNotNull(cue.sheet.fileName))
                    .mapNotNull(CueSheetPolicy::localAudioName).map { it.substringBeforeLast('.').lowercase(java.util.Locale.ROOT) }
            }.toHashSet()
        }
        val audioNames = HashMap<String, MutableList<String>>()
        val audioCursor = try { contentResolver.query(
            uri, projection,
            "(${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO} OR " +
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? OR " +
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?)",
            arrayOf("%.flac", "%.ape", "%.wv"), null,
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
                ).lowercase(java.util.Locale.ROOT)
                if (name.substringBeforeLast('.').lowercase(java.util.Locale.ROOT) in wantedStems[folder].orEmpty())
                    audioNames.getOrPut(folder) { ArrayList() }.add(name)
            }
        }
        val indices = audioNames.mapValues { CueAudioNameIndex(it.value) }
        files.forEach { cue ->
            coroutineContext.ensureActive()
            cue.sheet.forDirectoryAudio(indices[cue.folder] ?: CueAudioNameIndex(emptyList()), cue.fileName).forEach { (audio, sheet) ->
                sheets.putIfAbsent(cueFolderKey(cue.folder, audio), sheet)
            }
        }
        return CueSheetCatalog(sheets, files, uncertainFolders)
    }

}

internal fun cueFolderKey(folder: String, name: String): String =
    "${folder.lowercase(java.util.Locale.ROOT)}\u0000${name.lowercase(java.util.Locale.ROOT)}"
