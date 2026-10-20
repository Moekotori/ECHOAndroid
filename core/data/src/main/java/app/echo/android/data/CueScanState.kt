package app.echo.android.data

/** Internal fingerprint provenance; no Room schema or playback identity changes. */
internal object CueScanState {
    private const val WholeFilePrefix = "cue-whole-v1:"

    fun isWholeFileCue(fingerprint: String?): Boolean = fingerprint?.startsWith(WholeFilePrefix) == true
    fun markWholeFile(fingerprint: String): String = WholeFilePrefix + fingerprint.removePrefix(WholeFilePrefix)
    fun clearWholeFile(fingerprint: String?): String? = fingerprint?.removePrefix(WholeFilePrefix)
    fun preserveProvenance(previous: String?, computed: String): String =
        if (isWholeFileCue(previous)) markWholeFile(computed) else computed

    fun preservedIds(baseId: String, baseExists: Boolean, children: Collection<String>): List<String> =
        buildList { if (baseExists) add(baseId); addAll(children) }
}
