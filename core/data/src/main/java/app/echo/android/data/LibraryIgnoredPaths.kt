package app.echo.android.data

import app.echo.android.model.library.LibraryFolderScanRules
import app.echo.android.model.library.LibraryScanOptions
import java.util.Locale

fun LibraryScanOptions.withFolderIgnoredPaths(folders: List<WatchedLibraryTree>): LibraryScanOptions =
    copy(ignoredPathsByFolder = buildMap {
        for (folder in folders) {
            val root = folder.scanRootPath() ?: continue
            val rules = parseFolderIgnoredPaths(folder.rules.ignoredPaths)
            if (rules.isNotEmpty()) put(root, rules)
        }
    })

/** Explicitly managed folders are scanned by their own SAF rules during automatic checks. */
fun LibraryScanOptions.skipManagedFolders(folders: List<WatchedLibraryTree>): LibraryScanOptions =
    copy(excludedRelativePaths = excludedRelativePaths + folders.mapNotNull { it.scanRootPath() })

private fun WatchedLibraryTree.scanRootPath(): String? {
    val (volume, path) = LibraryScanPolicy.splitDocumentTreeId(documentId) ?: return null
    return LibraryScanPolicy.documentTreeRelativePath(volume, path)?.scanPathKey()
}

internal fun parseFolderIgnoredPaths(input: String): Set<String> =
    input.take(LibraryFolderScanRules.MaxIgnoredPathsLength).splitToSequence(';', '\n', '\r')
        .map { it.scanPathKey() }
        .filter { it.isNotEmpty() && it != ".." && (it == "." || it.split('/').none { part -> part == ".." || part == "." }) }
        .toSet()

internal fun String.scanPathKey(): String = trim().replace('\\', '/').trim('/').lowercase(Locale.ROOT)

internal fun LibraryScanOptions.ignoresFolderPath(pathKey: String): Boolean =
    ignoredPathsByFolder.any { (root, rules) ->
        val relative = when {
            root.isEmpty() -> pathKey
            pathKey.startsWith("$root/") -> pathKey.substring(root.length + 1)
            else -> return@any false
        }
        rules.any { rule ->
            when {
                rule == "." -> relative.startsWith('.') || "/." in relative
                '/' in rule -> relative == rule || relative.startsWith("$rule/")
                else -> relative == rule || relative.startsWith("$rule/") ||
                    relative.endsWith("/$rule") || "/$rule/" in relative
            }
        }
    }
