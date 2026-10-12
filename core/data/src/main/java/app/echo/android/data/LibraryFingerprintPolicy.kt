package app.echo.android.data

object LibraryFingerprintPolicy {
    const val StableRemoteDateModifiedSeconds = 0L

    // wallClockEpochMs is accepted but ignored so re-sync stays byte-stable across device clocks.
    fun remoteDateModifiedSeconds(wallClockEpochMs: Long): Long = StableRemoteDateModifiedSeconds

    fun fingerprint(
        contentUri: String,
        sizeBytes: Long,
        sampleRateHz: Int?,
        dateModifiedSeconds: Long,
        title: String,
        artist: String,
        album: String?,
        albumArtist: String?,
        artworkUri: String?,
        durationMs: Long,
        trackNumber: Int?,
        discNumber: Int?,
        year: Int?,
        mimeType: String?,
        relativePath: String?,
        remote: Boolean,
        clipStartMs: Long = 0L,
        clipEndMs: Long = 0L,
        genre: String? = null,
        composer: String? = null,
        fileName: String? = null,
    ): String {
        val stableDate = if (remote) {
            remoteDateModifiedSeconds(dateModifiedSeconds * 1000L)
        } else {
            dateModifiedSeconds
        }
        return listOf(
            contentUri,
            sizeBytes.toString(),
            sampleRateHz?.toString().orEmpty(),
            stableDate.toString(),
            title,
            artist,
            album.orEmpty(),
            albumArtist.orEmpty(),
            artworkUri.orEmpty(),
            durationMs.toString(),
            trackNumber?.toString().orEmpty(),
            discNumber?.toString().orEmpty(),
            year?.toString().orEmpty(),
            mimeType.orEmpty(),
            relativePath.orEmpty(),
            clipStartMs.toString(),
            clipEndMs.toString(),
            genre.orEmpty(),
            composer.orEmpty(),
            fileName.orEmpty(),
        ).joinToString("|")
    }
}
