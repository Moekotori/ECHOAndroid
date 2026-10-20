package app.echo.android.data

/** Null optional edit requests preserve existing tags; explicit clears must read back empty. */
internal fun AudioTagFields?.matchesWrittenTags(expected: AudioTagFields): Boolean =
    this != null && title == expected.title &&
        normalizedNames(artist) == normalizedNames(expected.artist) &&
        normalizedText(album) == normalizedText(expected.album) &&
        normalizedNames(albumArtist) == normalizedNames(expected.albumArtist) &&
        trackNumber == expected.trackNumber && discNumber == expected.discNumber && year == expected.year &&
        (!(expected.composerModified || expected.composer != null) || normalizedText(composer) == normalizedText(expected.composer)) &&
        (!(expected.genreModified || expected.genre != null) || normalizedText(genre) == normalizedText(expected.genre)) &&
        (expected.lyrics == null || normalizedLyrics(lyrics) == normalizedLyrics(expected.lyrics)) &&
        (expected.artworkBytes == null || artworkBytes?.contentEquals(expected.artworkBytes) == true) &&
        (expected.replayGainTrackGainDb == null || replayGainTrackGainDb?.let {
            kotlin.math.abs(it - expected.replayGainTrackGainDb) <= 0.011f
        } == true)

private fun normalizedNames(value: String?): String? = LibraryArtistPolicy.display(listOf(value.orEmpty()))
private fun normalizedText(value: String?): String = value.orEmpty().trim('\u0000', ' ')
private fun normalizedLyrics(value: String?): String =
    value.orEmpty().replace("\r\n", "\n").trimEnd('\u0000').trim()
