package app.echo.android.model.library

object CueSheetPolicy {
    const val CueSuffix = "#cue:"
    const val CueUriFragment = "echo-cue"
    const val MaxTracks = 4096
    const val MaxCueBytes = 256 * 1024

    fun cueTrackId(baseId: String, number: Int): String = "$baseId$CueSuffix$number"

    fun isCueTrackId(id: String): Boolean = id.contains(CueSuffix)

    fun baseTrackId(id: String): String {
        val index = id.indexOf(CueSuffix)
        return if (index < 0) id else id.substring(0, index)
    }

    fun contentUri(baseUri: String, number: Int): String {
        val trimmed = baseUri.substringBefore('#')
        return "$trimmed#$CueUriFragment=$number"
    }

    fun playbackUri(contentUri: String): String = contentUri.substringBefore('#')

    fun withEndTimes(tracks: List<CueSheetTrack>, fileDurationMs: Long): List<CueSheetTrack> {
        if (tracks.isEmpty()) return emptyList()
        val duration = fileDurationMs.coerceAtLeast(0L)
        val numbers = HashSet<Int>()
        for ((index, track) in tracks.withIndex()) {
            if (!numbers.add(track.number) || track.startMs < 0L ||
                (duration > 0L && track.startMs >= duration) ||
                (index > 0 && track.startMs <= tracks[index - 1].startMs) ||
                (track.endMs != 0L && track.endMs <= track.startMs)) return emptyList()
        }
        return tracks.mapIndexed { index, track ->
            val nextStart = tracks.getOrNull(index + 1)?.startMs
            val end = listOfNotNull(
                nextStart, track.endMs.takeIf { it > 0L }, duration.takeIf { it > 0L },
            ).minOrNull() ?: 0L
            track.copy(endMs = end)
        }
    }

    fun tracksForAudio(sheet: CueSheet, audioName: String?): List<CueSheetTrack> {
        val name = audioName?.substringAfterLast('/')?.trim().orEmpty()
        if (name.isEmpty()) return emptyList()
        if (sheet.tracks.any { !it.fileName.isNullOrBlank() }) {
            return sheet.tracks.filter { matchAudioName(it.fileName, listOf(name)) != null }
        }
        return if (matchAudioName(sheet.fileName, listOf(name)) != null) sheet.tracks else emptyList()
    }

    fun isMatchedToAudio(sheet: CueSheet, cueFileName: String?, audioNames: Collection<String>): Boolean {
        if (audioNames.isEmpty()) return false
        val names = audioNames.map { it.substringAfterLast('/') }
        val wanted = buildList {
            cueFileName?.let(::add)
            sheet.fileName?.let(::add)
            sheet.tracks.forEach { track -> track.fileName?.let(::add) }
        }
        return wanted.any { matchAudioName(it, names) != null }
    }

    fun matchAudioName(fileName: String?, audioNames: Collection<String>): String? {
        val wanted = localAudioName(fileName) ?: return null
        audioNames.firstOrNull { it.equals(wanted, ignoreCase = true) }?.let { return it }
        // Only the known lossless replacement is safe; unrelated files do not disable it.
        if (wanted.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT) !in setOf("wav", "wave", "cue")) return null
        val stem = wanted.substringBeforeLast('.')
        return audioNames.filter {
            it.substringBeforeLast('.').equals(stem, true) &&
                it.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT) in setOf("flac", "ape", "wv")
        }.singleOrNull()
    }

    /** Same-directory references only: never silently relocate absolute or parent paths. */
    fun localAudioName(fileName: String?): String? {
        var name = fileName?.trim()?.replace('\\', '/') ?: return null
        while (name.startsWith("./")) name = name.removePrefix("./")
        return name.takeIf { it.isNotBlank() && '/' !in it && ':' !in it && '\u0000' !in it && it != ".." }
    }

    fun framesToMs(minutes: Int, seconds: Int, frames: Int): Long {
        val totalFrames = ((minutes.toLong() * 60L) + seconds.toLong()) * 75L + frames.toLong()
        return (totalFrames * 1000L) / 75L
    }
}
