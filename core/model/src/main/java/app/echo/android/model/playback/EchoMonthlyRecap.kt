package app.echo.android.model.playback

data class EchoMonthlyRecap(
    val year: Int,
    val month: Int,
    val partial: Boolean,
    val totalPlays: Int = 0,
    val listenedMs: Long = 0L,
    val activeDays: Int = 0,
    val newArtistCount: Int = 0,
    val newArtists: List<ListeningStatsEntry> = emptyList(),
    val topAlbums: List<ListeningStatsEntry> = emptyList(),
    val playsByHour: List<Int> = List(24) { 0 },
) {
    val isEmpty: Boolean get() = totalPlays == 0
}
