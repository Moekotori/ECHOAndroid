package app.echo.android.data

/** Only explicit tag value separators are lists; punctuation in band names stays intact. */
internal object LibraryArtistPolicy {
    fun names(value: String?): List<String> = value.orEmpty()
        .split(';', '；', '\u0000')
        .map(String::trim)
        .filter { it.isNotBlank() }
        .distinctBy { it.normalizedForSearch() }

    fun display(values: Iterable<String>): String? = values.flatMap(::names)
        .distinctBy { it.normalizedForSearch() }
        .takeIf { it.isNotEmpty() }
        ?.joinToString("; ")

    fun keys(value: String): Set<String> = names(value)
        .mapTo(linkedSetOf()) { libraryArtistKey(it.normalizedForSearch()) }

    fun memberships(track: LibraryTrackEntity): List<LibraryTrackArtistEntity> =
        if (!LibraryScanPolicy.isLocalLibrarySource(track.source)) emptyList()
        else names(track.artist).ifEmpty { listOf(canonicalUnknownArtist()) }.map { name ->
            LibraryTrackArtistEntity(track.id, libraryArtistKey(name.normalizedForSearch()), name, ChinesePinyin.toPinyin(name))
        }
}
