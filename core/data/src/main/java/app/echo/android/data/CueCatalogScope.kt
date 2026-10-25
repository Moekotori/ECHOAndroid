package app.echo.android.data

internal data class CueCatalogSelection(val sql: String, val args: Array<String>)

/** Directory discovery includes ancestor sidecars, whose FILE entries may point into the selection. */
internal class CueCatalogScope(private val selected: String?, private val modern: Boolean, private val storageRoot: String) {
    private val selectedIndex = selected?.takeIf(String::isNotBlank)?.let { ScanDirectoryIndex(listOf(it)) }
    fun includes(folder: String): Boolean = selectedIndex?.contains(folder) ?: true

    fun cueSelection(): CueCatalogSelection? {
        val selected = selected?.trim('/')?.takeIf(String::isNotEmpty) ?: return null
        val clauses = ArrayList<CueCatalogSelection>()
        clauses += subtree(selected)
        var parent = selected.substringBeforeLast('/', "")
        while (true) {
            // Do not climb out of a removable volume into the logical Removable directory.
            if (parent == "Removable") break
            clauses += directory(parent)
            if (parent.isEmpty() || (parent.startsWith("Removable/") && parent.count { it == '/' } == 1)) break
            parent = parent.substringBeforeLast('/', "")
        }
        return combine(clauses)
    }

    fun audioSelections(folders: Collection<String>, legacyFolders: Collection<String> = emptyList()): List<CueCatalogSelection?> {
        selected?.takeIf(String::isNotBlank)?.let { return listOf(subtree(it.trim('/'))) }
        if (!modern) {
            if (legacyFolders.isEmpty()) return listOf(null)
            val roots = legacyFolders.mapTo(HashSet()) { it.trimEnd('/') }
            val minimal = minimalRoots(roots)
            return minimal.chunked(64).map { chunk -> combine(chunk.map { path ->
                CueCatalogSelection("_data LIKE ? ESCAPE '\\'", arrayOf(escapeSqlLikeArgument("$path/") + "%"))
            }) }
        }
        val all = folders.mapTo(HashSet()) { it.trim('/') }
        return minimalRoots(all).chunked(64).map { combine(it.map(::subtree)) }
    }

    private fun minimalRoots(all: Set<String>): List<String> = all.filter { folder ->
            var parent = folder
            var covered = false
            val volumeRoot = if (modern && folder.startsWith("Removable/")) "Removable/" + folder.removePrefix("Removable/").substringBefore('/') else ""
            while (parent != volumeRoot && parent.isNotEmpty()) {
                parent = parent.substringBeforeLast('/', "")
                if (parent in all) { covered = true; break }
            }
            !covered
        }

    private fun subtree(folder: String): CueCatalogSelection = path(folder, true)
    private fun directory(folder: String): CueCatalogSelection = path(folder, false)
    private fun path(folder: String, recursive: Boolean): CueCatalogSelection {
        if (!modern) {
            val absolute = listOf(storageRoot.trimEnd('/'), folder).filter(String::isNotEmpty).joinToString("/") + "/"
            return if (recursive) CueCatalogSelection("_data LIKE ? ESCAPE '\\'", arrayOf(escapeSqlLikeArgument(absolute) + "%"))
            else CueCatalogSelection("(_data LIKE ? ESCAPE '\\' AND _data NOT LIKE ? ESCAPE '\\')",
                arrayOf(escapeSqlLikeArgument(absolute) + "%", escapeSqlLikeArgument(absolute) + "%/%"))
        }
        val removable = folder.startsWith("Removable/")
        val volume = if (removable) folder.removePrefix("Removable/").substringBefore('/') else null
        val relative = if (removable) folder.removePrefix("Removable/$volume").trim('/') else folder
        val value = if (relative.isEmpty()) "" else "$relative/"
        val directory = when {
            recursive && relative.isEmpty() -> "(relative_path LIKE ? ESCAPE '\\' OR relative_path IS NULL)"
            recursive -> "relative_path LIKE ? ESCAPE '\\'"
            else -> "(relative_path = ? OR (relative_path IS NULL AND ? = ''))"
        }
        val volumeSql = if (removable) "volume_name = ?" else "(volume_name IN ('external_primary','external') OR volume_name IS NULL)"
        val args = buildList {
            if (volume != null) add(volume)
            if (recursive) add(escapeSqlLikeArgument(value) + "%") else { add(value); add(value) }
        }
        return CueCatalogSelection("($volumeSql AND $directory)", args.toTypedArray())
    }

    private fun combine(parts: List<CueCatalogSelection>): CueCatalogSelection =
        CueCatalogSelection(parts.joinToString(" OR ", "(", ")") { it.sql }, parts.flatMap { it.args.toList() }.toTypedArray())
}
