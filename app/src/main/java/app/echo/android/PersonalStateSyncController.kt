package app.echo.android

import android.content.Context
import app.echo.android.connect.EchoLibrarySyncSession
import app.echo.android.connect.EchoPersonalStateCodec
import app.echo.android.data.*
import app.echo.android.lyrics.ImportedLyricsStore
import app.echo.android.model.connect.*
import app.echo.android.model.library.EchoTrack
import app.echo.android.playback.EchoPlaybackProcessRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Only explicit user actions exchange personal data or replace a playback queue. */
@androidx.media3.common.util.UnstableApi
internal class PersonalStateSyncController(context: Context, private val repository: EchoLibraryRepository) {
    private val lyrics = ImportedLyricsStore(context.applicationContext)
    private val operation = Mutex()

    private suspend fun snapshot(): EchoPersonalState = withContext(Dispatchers.IO) {
        val records = repository.personalStateRecords()
        val offsets = lyrics.offsetsForSync()
        val tracks = repository.tracksByIds(offsets.keys.toList()).associateBy { it.id }
        val surface = EchoPlaybackProcessRuntime.surface.value
        val resume = surface.mediaId?.takeIf { surface.hasTrack }?.let { id ->
            repository.trackById(id)?.toEchoTrack()?.let { EchoPersonalResume(it.ref(), surface.positionMs.coerceIn(0, EchoPersonalStateLimits.MaxDurationMs)) }
        }
        var state = EchoPersonalStatePolicy.portable(records.copy(
            lyricsOffsets = offsets.mapNotNull { (id, value) -> tracks[id]?.let { EchoPersonalLyricsOffset(it.ref(), value) } }, resume = resume))
        // Leave room for the POST envelope too; the preview shows exactly this bounded payload.
        while (EchoPersonalStateCodec.encode(state).toString().toByteArray(Charsets.UTF_8).size > EchoPersonalStateLimits.MaxBytes - 1024) {
            state = when {
                state.history.isNotEmpty() -> state.copy(history = state.history.take(state.history.size * 3 / 4))
                state.bookmarks.isNotEmpty() -> state.copy(bookmarks = state.bookmarks.take(state.bookmarks.size * 3 / 4))
                else -> state.copy(lyricsOffsets = state.lyricsOffsets.take(state.lyricsOffsets.size * 3 / 4))
            }
        }
        state.copy(revision = EchoPersonalStateCodec.revision(state))
    }

    suspend fun preview(session: EchoLibrarySyncSession): EchoPersonalStatePreview = operation.withLock {
        val pc = session.personalState() // Authenticate/capability check before reading phone data.
        EchoPersonalStatePreview(snapshot(), pc)
    }

    suspend fun apply(session: EchoLibrarySyncSession, preview: EchoPersonalStatePreview, fromPc: Boolean): EchoPersonalStateResult = operation.withLock {
        // Revalidate the paired connection and both snapshots before applying reviewed writes.
        check(session.personalState().revision == preview.pc.revision) { "Personal state changed after preview" }
        check(snapshot().revision == preview.phone.revision) { "Personal state changed after preview" }
        if (!fromPc) return@withLock session.importPersonalState(preview.pc.revision, preview.phone.copy(resume = null))
        val state = preview.pc
        val result = repository.applyPersonalStateRecords(state)
        var applied = 0; var missing = result.missing
        for (offset in state.lyricsOffsets) {
            coroutineContext.ensureActive()
            val track = repository.matchPersonalStateTrack(offset.track)
            if (track == null) missing++ else {
                val expected = preview.phone.lyricsOffsets.firstOrNull { it.track == track.ref() }?.offsetMs ?: 0L
                lyrics.setLyricsOffsetIfUnchanged(track.id, expected, offset.offsetMs)
                applied++
            }
        }
        result.copy(offsetsApplied = applied, missing = missing)
    }

    suspend fun resumeTrack(session: EchoLibrarySyncSession, reviewed: EchoPersonalResume): Pair<EchoTrack, Long> = operation.withLock {
        val actual = session.personalState().resume ?: error("PC has no resumable track")
        check(actual.track == reviewed.track) { "PC track changed after preview" }
        val track = repository.matchPersonalStateTrack(actual.track) ?: error("Track is not available on this phone")
        track to actual.positionMs.coerceIn(0, track.durationMs.takeIf { it > 0 } ?: EchoPersonalStateLimits.MaxDurationMs)
    }

    private fun EchoTrack.ref() = EchoSyncTrackRef(title, artist, album, durationMs)
}
