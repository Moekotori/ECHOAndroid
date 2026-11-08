package app.echo.android.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import app.echo.android.design.displayArtist
import app.echo.android.design.displayTrack
import app.echo.android.design.formatDuration
import app.echo.android.model.library.EchoTrack
import java.text.DateFormat
import java.util.Date

/** Optional facts for this song; output-device settings must never be supplied here. */
data class TrackInfoAudioFormat(
    val codec: String? = null,
    val sampleRateHz: Int? = null,
    val bitDepth: Int? = null,
    val channelCount: Int? = null,
    val bitrate: Int? = null,
)

internal data class TrackInfoField(val label: String, val value: String, val opensArtist: Boolean = false)

/** The dialog and clipboard share the same localized, read-only details. No file or network reads. */
@Composable
internal fun trackInfoFields(track: EchoTrack, audio: TrackInfoAudioFormat?): List<TrackInfoField> {
    val missing = stringResource(R.string.feature_library_not_available_7d3220)
    fun text(value: String?) = value?.trim()?.takeIf(String::isNotBlank) ?: missing
    val rate = audio?.sampleRateHz?.takeIf { it > 0 } ?: track.sampleRateHz?.takeIf { it > 0 }
    val locale = LocalConfiguration.current.locales[0]
    val modified = remember(track.dateModifiedSeconds, locale) {
        track.dateModifiedSeconds.takeIf { it in 1..(Long.MAX_VALUE / 1000) }?.let {
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, locale).format(Date(it * 1000))
        }
    }
    return listOf(
        TrackInfoField(stringResource(R.string.feature_library_title_af1111), displayTrack(track.title)),
        TrackInfoField(stringResource(R.string.feature_library_artist_b6e7ad), displayArtist(track.artist), opensArtist = true),
        TrackInfoField(stringResource(R.string.feature_library_album_eb13be), text(track.album)),
        TrackInfoField(stringResource(R.string.feature_library_album_artist_8a1c9e), text(track.albumArtist)),
        TrackInfoField(stringResource(R.string.metadata_composer), text(track.composer)),
        TrackInfoField(stringResource(R.string.library_filter_genre), text(track.genre)),
        TrackInfoField(stringResource(R.string.feature_library_track_000e4d), track.trackNumber?.takeIf { it > 0 }?.toString() ?: missing),
        TrackInfoField(stringResource(R.string.feature_library_disc_3128c0), track.discNumber?.takeIf { it > 0 }?.toString() ?: missing),
        TrackInfoField(stringResource(R.string.feature_library_year_fe373f), track.year?.takeIf { it > 0 }?.toString() ?: missing),
        TrackInfoField(stringResource(R.string.feature_library_format_a7775b),
            formatTrackMimeType(track.mimeType) ?: text(audio?.codec)),
        TrackInfoField(stringResource(R.string.feature_library_sample_rate_f6382c), rate?.let(::formatTrackSampleRate) ?: missing),
        TrackInfoField(stringResource(R.string.nav_bit_depth), audio?.bitDepth?.takeIf { it in 1..64 }?.let { "$it bit" } ?: missing),
        TrackInfoField(stringResource(R.string.nav_channels), audio?.channelCount?.takeIf { it > 0 }?.toString() ?: missing),
        TrackInfoField(stringResource(R.string.nav_bitrate), audio?.bitrate?.takeIf { it > 0 }?.let { ((it.toLong() + 500) / 1000).toString() } ?: missing),
        TrackInfoField(stringResource(R.string.feature_library_duration_573e08), track.durationMs.takeIf { it > 0 }?.let(::formatDuration) ?: missing),
        TrackInfoField(stringResource(R.string.feature_library_size_f63a75), formatTrackFileSize(track.sizeBytes)),
        TrackInfoField(stringResource(R.string.track_info_file_name), text(track.fileName)),
        TrackInfoField(stringResource(R.string.track_info_mime), text(track.mimeType)),
        TrackInfoField(stringResource(R.string.track_info_modified), modified ?: missing),
        TrackInfoField(stringResource(R.string.feature_library_source_4aff16), text(track.source.id)),
    ) + if (track.clipStartMs > 0 || track.clipEndMs > 0) listOf(
        TrackInfoField(stringResource(R.string.track_info_clip_start), "${track.clipStartMs.coerceAtLeast(0)} ms"),
        TrackInfoField(stringResource(R.string.track_info_clip_end), track.clipEndMs.takeIf { it > 0 }?.let { "$it ms" } ?: missing),
    ) else emptyList()
}
