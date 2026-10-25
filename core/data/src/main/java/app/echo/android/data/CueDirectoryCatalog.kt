package app.echo.android.data

import app.echo.android.model.library.CueSheet

/** Merge sidecars independent of provider order. Conflicts never choose an arbitrary sheet. */
internal class CueDirectoryCatalog(audioNames: Collection<String>) {
    private val index = CueAudioNameIndex(audioNames)
    private val matched = LinkedHashMap<String, CueSheet>()
    private val conflicts = HashSet<String>()
    var uncertain: Boolean = false
        private set

    fun add(sheet: CueSheet, cueName: String): Boolean {
        val hits = sheet.forDirectoryAudio(index, cueName)
        if (hits.values.sumOf { it.tracks.size } != sheet.tracks.size) uncertain = true
        for ((name, resolved) in hits) {
            if (name in conflicts) continue
            val previous = matched[name]
            if (previous != null && previous != resolved) {
                matched.remove(name)
                conflicts += name
                uncertain = true
            } else matched[name] = resolved
        }
        return hits.isNotEmpty()
    }

    fun sheets(): Map<String, CueSheet> = matched.toMap()
}
