package app.echo.android.model.library

object CueSheetPolicy {
    const val CueSuffix = "#cue:"
    const val CueUriFragment = "echo-cue"
    const val MaxTracks = 99
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
        return tracks.mapIndexed { index, track ->
            val nextStart = tracks.getOrNull(index + 1)?.startMs
            val end = when {
                nextStart != null && nextStart > track.startMs -> nextStart
                duration > track.startMs -> duration
                else -> 0L
            }
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
        val wanted = fileName?.substringAfterLast('/')?.trim().orEmpty()
        if (wanted.isNotEmpty()) {
            audioNames.firstOrNull { it.equals(wanted, ignoreCase = true) }?.let { return it }
        }
        if (audioNames.size != 1) return null
        val only = audioNames.first()
        val cueBase = wanted.substringBeforeLast('.', wanted)
        val audioBase = only.substringBeforeLast('.', only)
        return only.takeIf { cueBase.isNotEmpty() && cueBase.equals(audioBase, ignoreCase = true) }
    }

    fun framesToMs(minutes: Int, seconds: Int, frames: Int): Long {
        val totalFrames = ((minutes.toLong() * 60L) + seconds.toLong()) * 75L + frames.toLong()
        return (totalFrames * 1000L) / 75L
    }
}
