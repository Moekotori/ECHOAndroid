package app.echo.android.lyrics

import app.echo.android.data.LibraryScanPolicy

/** Translate the library's volume-qualified folder into the matching MediaStore location. */
internal class LyricsSidecarLocation private constructor(
    private val libraryFolder: String,
    val volumeName: String,
    val relativePath: String,
) {
    fun containsLegacyFile(dataPath: String?, primaryStorageRoot: String): Boolean =
        LibraryScanPolicy.legacyDataRelativePath(dataPath, primaryStorageRoot)?.trim('/') == libraryFolder

    companion object {
        fun fromLibraryFolder(folder: String?): LyricsSidecarLocation? {
            val clean = folder?.trim('/')?.takeIf { it.isNotBlank() } ?: return null
            if (clean.startsWith(LibraryScanPolicy.RemovableRootPrefix, ignoreCase = true)) {
                val volumeAndPath = clean.substringAfter('/').split('/', limit = 2)
                val volume = volumeAndPath[0].takeIf { it.isNotBlank() } ?: return null
                val path = volumeAndPath.getOrElse(1) { "" }
                return LyricsSidecarLocation(clean, volume, if (path.isEmpty()) "/" else "$path/")
            }
            return LyricsSidecarLocation(clean, LibraryScanPolicy.MediaStorePrimaryVolume, "$clean/")
        }
    }
}
