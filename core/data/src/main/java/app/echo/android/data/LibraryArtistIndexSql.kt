package app.echo.android.data

internal object LibraryArtistIndexSql {
    const val Rebuild = """
        INSERT INTO library_artist_summaries
            (artistKey, name, artworkUri, albumCount, trackCount, durationMs, pinyinName)
        SELECT a.artistKey, MIN(a.name), MAX(t.artworkUri), COUNT(DISTINCT t.albumKey),
               COUNT(*), COALESCE(SUM(t.durationMs), 0), MAX(a.pinyinName)
        FROM library_track_artists a JOIN library_tracks t ON t.id = a.trackId
        WHERE t.source IN ('mediastore', 'saf')
        GROUP BY a.artistKey
    """

    const val RebuildForKeys = """
        INSERT INTO library_artist_summaries
            (artistKey, name, artworkUri, albumCount, trackCount, durationMs, pinyinName)
        SELECT a.artistKey, MIN(a.name), MAX(t.artworkUri), COUNT(DISTINCT t.albumKey),
               COUNT(*), COALESCE(SUM(t.durationMs), 0), MAX(a.pinyinName)
        FROM library_track_artists a JOIN library_tracks t ON t.id = a.trackId
        WHERE t.source IN ('mediastore', 'saf') AND a.artistKey IN (:keys)
        GROUP BY a.artistKey
    """
}
