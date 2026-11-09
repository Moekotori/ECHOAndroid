package app.echo.android.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import app.echo.android.design.displayMetadataOrUnknown
import app.echo.android.design.formatDuration
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.LibrarySource
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

internal data class TrackInfoField(val label: String, val value: String?, val opensArtist: Boolean = false)

internal data class TrackInfoSection(val title: Int, val fields: List<TrackInfoField>)

/** The sheet and clipboard share the same localized, read-only details. No file or network reads. */
@Composable
internal fun trackInfoSections(track: EchoTrack, audio: TrackInfoAudioFormat?): List<TrackInfoSection> {
    fun text(value: String?) = value?.trim()?.takeIf(String::isNotBlank)
    fun metadata(value: String?) = displayMetadataOrUnknown(value, "").takeIf(String::isNotEmpty)
    val rate = audio?.sampleRateHz?.takeIf { it > 0 } ?: track.sampleRateHz?.takeIf { it > 0 }
    val locale = LocalConfiguration.current.locales[0]
    val modified = remember(track.dateModifiedSeconds, locale) {
        track.dateModifiedSeconds.takeIf { it in 1..(Long.MAX_VALUE / 1000) }?.let {
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, locale).format(Date(it * 1000))
        }
    }
    val tags = listOf(
        TrackInfoField(stringResource(R.string.feature_library_title_af1111), metadata(track.title)),
        TrackInfoField(stringResource(R.string.feature_library_artist_b6e7ad), metadata(track.artist), opensArtist = true),
        TrackInfoField(stringResource(R.string.feature_library_album_eb13be), metadata(track.album)),
        TrackInfoField(stringResource(R.string.feature_library_album_artist_8a1c9e), metadata(track.albumArtist)),
        TrackInfoField(stringResource(R.string.metadata_composer), metadata(track.composer)),
        TrackInfoField(stringResource(R.string.library_filter_genre), metadata(track.genre)),
        TrackInfoField(stringResource(R.string.feature_library_track_000e4d), track.trackNumber?.takeIf { it > 0 }?.toString()),
        TrackInfoField(stringResource(R.string.feature_library_disc_3128c0), track.discNumber?.takeIf { it > 0 }?.toString()),
        TrackInfoField(stringResource(R.string.feature_library_year_fe373f), track.year?.takeIf { it > 0 }?.toString()),
    )
    val audioFields = listOf(
        TrackInfoField(stringResource(R.string.feature_library_format_a7775b), formatTrackMimeType(track.mimeType) ?: text(audio?.codec)),
        TrackInfoField(stringResource(R.string.feature_library_sample_rate_f6382c), rate?.let(::formatTrackSampleRate)),
        TrackInfoField(stringResource(R.string.nav_bit_depth), audio?.bitDepth?.takeIf { it in 1..64 }?.let { "$it bit" }),
        TrackInfoField(stringResource(R.string.nav_channels), audio?.channelCount?.takeIf { it > 0 }?.toString()),
        TrackInfoField(stringResource(R.string.nav_bitrate), audio?.bitrate?.takeIf { it > 0 }?.let { ((it.toLong() + 500) / 1000).toString() }),
        TrackInfoField(stringResource(R.string.feature_library_duration_573e08), track.durationMs.takeIf { it > 0 }?.let(::formatDuration)),
    )
    val fileFields = buildList {
        add(TrackInfoField(stringResource(R.string.track_info_file_name), text(track.fileName)))
        add(TrackInfoField(stringResource(R.string.feature_library_size_f63a75), track.sizeBytes.takeIf { it > 0 }?.let { formatTrackFileSize(it) }))
        add(TrackInfoField(stringResource(R.string.track_info_mime), text(track.mimeType)))
        add(TrackInfoField(stringResource(R.string.track_info_modified), modified))
        add(TrackInfoField(stringResource(R.string.feature_library_source_4aff16), text(track.source.id.takeUnless { track.source == LibrarySource.Unknown })))
        if (track.clipStartMs > 0 || track.clipEndMs > 0) {
            add(TrackInfoField(stringResource(R.string.track_info_clip_start), "${track.clipStartMs.coerceAtLeast(0)} ms"))
            add(TrackInfoField(stringResource(R.string.track_info_clip_end), track.clipEndMs.takeIf { it > 0 }?.let { "$it ms" }))
        }
    }
    return listOf(
        TrackInfoSection(R.string.track_info_section_tags, tags),
        TrackInfoSection(R.string.track_info_section_audio, audioFields),
        TrackInfoSection(R.string.track_info_section_file, fileFields),
    )
}
