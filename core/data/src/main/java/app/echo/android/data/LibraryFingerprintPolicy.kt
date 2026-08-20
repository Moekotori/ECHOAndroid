package app.echo.android.data

object LibraryFingerprintPolicy {
    const val StableRemoteDateModifiedSeconds = 0L

    fun remoteDateModifiedSeconds(wallClockEpochMs: Long): Long {
        wallClockEpochMs // ignore wall clock so re-sync stays stable
        return StableRemoteDateModifiedSeconds
    }

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
        ).joinToString("|")
    }
}
