package app.echo.android.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.setValue
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.EchoTrackMetadataUpdate
import kotlin.coroutines.cancellation.CancellationException

internal class TrackMetadataEditorState(
    private val draftArtwork: MutableState<String?> = mutableStateOf(null),
) {
    val artworkUri: String? get() = draftArtwork.value

    fun editArtwork(uri: String) {
        draftArtwork.value = uri
    }

    var title by mutableStateOf("")
    var artist by mutableStateOf("")
    var album by mutableStateOf("")
    var albumArtist by mutableStateOf("")
    var trackNumber by mutableStateOf("")
    var discNumber by mutableStateOf("")
    var year by mutableStateOf("")
    var composer by mutableStateOf("")
    var lyrics by mutableStateOf("")
        private set
    var lyricsLoading by mutableStateOf(true)
    private var lyricsEdited = false

    fun editLyrics(value: String) {
        lyrics = value
        lyricsEdited = true
    }

    fun loadLyrics(value: String) {
        if (!lyricsEdited) lyrics = value
    }

    val canSave: Boolean
        get() = title.isNotBlank() && artist.isNotBlank() &&
            trackNumberValid && discNumberValid && yearValid

    val trackNumberValid: Boolean get() = trackNumber.isValidMetadataNumber()
    val discNumberValid: Boolean get() = discNumber.isValidMetadataNumber()
    val yearValid: Boolean get() = year.isValidMetadataNumber(max = 9999)

    fun toUpdate(track: EchoTrack): EchoTrackMetadataUpdate =
        EchoTrackMetadataUpdate(
            trackId = track.id,
            title = title.trim(),
            artist = artist.trim(),
            album = album.trim().takeIf { it.isNotBlank() },
            albumArtist = albumArtist.trim().takeIf { it.isNotBlank() },
            trackNumber = trackNumber.toPositiveIntOrNull(),
            discNumber = discNumber.toPositiveIntOrNull(),
            year = year.toPositiveIntOrNull(),
            composer = composer.trim(),
            artworkUri = artworkUri,
            lyrics = lyrics.takeIf { lyricsEdited },
        )
}

@Composable
internal fun rememberTrackMetadataEditorState(track: EchoTrack): TrackMetadataEditorState {
    // Retain the whole draft across configuration changes without serializing large lyrics.
    // Leaving the editor permanently retires the retained draft.
    val state = retain(track.id) {
        TrackMetadataEditorState().apply {
            title = track.title
            artist = track.artist
            album = track.album.orEmpty()
            albumArtist = track.albumArtist.orEmpty()
            trackNumber = track.trackNumber?.toString().orEmpty()
            discNumber = track.discNumber?.toString().orEmpty()
            year = track.year?.toString().orEmpty()
            composer = track.composer.orEmpty()
        }
    }
    val loader = LocalEmbeddedLyricsLoader.current
    LaunchedEffect(track.id, loader) {
        state.lyricsLoading = true
        try {
            state.loadLyrics(loader?.load(track.id).orEmpty())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A failed read must not turn an unrelated tag edit into a lyrics deletion.
        } finally {
            state.lyricsLoading = false
        }
    }
    return state
}

private fun String.toPositiveIntOrNull(): Int? =
    trim().toIntOrNull()?.takeIf { it > 0 }

private fun String.isValidMetadataNumber(max: Int = Int.MAX_VALUE): Boolean =
    isBlank() || trim().let { text ->
        text.all { it in '0'..'9' } && text.toIntOrNull()?.let { it in 1..max } == true
    }
