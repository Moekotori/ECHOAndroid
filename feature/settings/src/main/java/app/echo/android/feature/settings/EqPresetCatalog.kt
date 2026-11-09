package app.echo.android.feature.settings

import app.echo.android.model.playback.EchoEqualizerPreset
import app.echo.android.model.playback.EchoEqualizerPresets

internal enum class EqPresetGroup(val labelRes: Int) {
    Featured(R.string.eq_catalog_featured),
    Harman(R.string.eq_catalog_harman),
    Tone(R.string.eq_catalog_tone),
    Genre(R.string.eq_catalog_genre),
    Saved(R.string.eq_catalog_mine),
}

private const val HarmanOverEarBass = "harman-oe-2018-bass"
private const val HarmanInEarBass = "harman-ie-2019-bass"

private val FeaturedIds = listOf(
    EchoEqualizerPreset.Flat, HarmanOverEarBass, HarmanInEarBass, EchoEqualizerPreset.Vocal,
    EchoEqualizerPreset.Bass, "soft-sibilance", "dialogue-focus", EchoEqualizerPreset.Night,
)
private val GenreIds = setOf(
    "anime-jpop", "rock", "classical", "classic-smiley", "city-pop", "acoustic-silk",
    "piano-room", "lofi-dusk", "cinema-orchestra", "live-house", "jazz-club",
    "hip-hop-sub", "metal-edge", "dance-pulse", "folk-strings", "ambient-space",
)

private val FactoryById = EchoEqualizerPresets.presets.associateBy { it.id }
internal val FeaturedEqPresets = FeaturedIds.map(FactoryById::getValue)
internal val HarmanEqPresets =
    listOf(HarmanOverEarBass, HarmanInEarBass).map(FactoryById::getValue) +
        EchoEqualizerPresets.presets.filter {
            it.id.startsWith("harman-") && it.id != HarmanOverEarBass && it.id != HarmanInEarBass
        }
internal val GenreEqPresets = EchoEqualizerPresets.presets.filter { it.id in GenreIds }
internal val ToneEqPresets = EchoEqualizerPresets.presets.filterNot { it.id in GenreIds || it.id.startsWith("harman-") }

internal fun initialEqPresetGroup(activeUserId: String?, selectedId: String): EqPresetGroup = when {
    activeUserId != null -> EqPresetGroup.Saved
    selectedId in FeaturedIds || selectedId == EchoEqualizerPreset.Custom -> EqPresetGroup.Featured
    selectedId.startsWith("harman-") -> EqPresetGroup.Harman
    selectedId in GenreIds -> EqPresetGroup.Genre
    else -> EqPresetGroup.Tone
}

internal val EqPresetTitleRes = mapOf(
    EchoEqualizerPreset.Custom to R.string.eq_preset_custom,
    EchoEqualizerPreset.Flat to R.string.eq_preset_flat,
    EchoEqualizerPreset.Bass to R.string.eq_preset_bass,
    EchoEqualizerPreset.Vocal to R.string.eq_preset_vocal,
    EchoEqualizerPreset.Bright to R.string.eq_preset_bright,
    EchoEqualizerPreset.Warm to R.string.eq_preset_warm,
    EchoEqualizerPreset.Night to R.string.eq_preset_night,
    EchoEqualizerPreset.Acoustic to R.string.eq_preset_acoustic,
    EchoEqualizerPreset.Electronic to R.string.eq_preset_electronic,
    "harman-target" to R.string.eq_catalog_name_harman_target,
    "harman-in-ear" to R.string.eq_catalog_name_harman_in_ear,
    "harman-over-ear-2013" to R.string.eq_catalog_name_harman_over_ear_2013,
    "harman-over-ear-2015" to R.string.eq_catalog_name_harman_over_ear_2015,
    "harman-over-ear-2018-no-bass" to R.string.eq_catalog_name_harman_over_ear_2018_no_bass,
    "harman-in-ear-2016" to R.string.eq_catalog_name_harman_in_ear_2016,
    "harman-in-ear-2017" to R.string.eq_catalog_name_harman_in_ear_2017,
    "harman-in-ear-2019-no-bass" to R.string.eq_catalog_name_harman_in_ear_2019_no_bass,
    "harman-speaker-room-2013" to R.string.eq_catalog_name_harman_speaker_room_2013,
    "harman-oe-2018-bass" to R.string.eq_catalog_name_harman_oe_2018_bass,
    "harman-ie-2019-bass" to R.string.eq_catalog_name_harman_ie_2019_bass,
    "diffuse-field" to R.string.eq_catalog_name_diffuse_field,
    "diffuse-field-iso-11904-1" to R.string.eq_catalog_name_diffuse_field_iso_11904_1,
    "diffuse-field-gras-kemar" to R.string.eq_catalog_name_diffuse_field_gras_kemar,
    "diffuse-field-5128" to R.string.eq_catalog_name_diffuse_field_5128,
    "bk-room-curve" to R.string.eq_catalog_name_bk_room_curve,
    "studio-neutral" to R.string.eq_catalog_name_studio_neutral,
    "loudness" to R.string.eq_catalog_name_loudness,
    "anime-jpop" to R.string.eq_catalog_name_anime_jpop,
    "rock" to R.string.eq_catalog_name_rock,
    "classical" to R.string.eq_catalog_name_classical,
    "vinyl-warmth" to R.string.eq_catalog_name_vinyl_warmth,
    "broadcast-voice" to R.string.eq_catalog_name_broadcast_voice,
    "city-pop" to R.string.eq_catalog_name_city_pop,
    "piano-room" to R.string.eq_catalog_name_piano_room,
    "lofi-dusk" to R.string.eq_catalog_name_lofi_dusk,
    "cinema-orchestra" to R.string.eq_catalog_name_cinema_orchestra,
    "live-house" to R.string.eq_catalog_name_live_house,
    "female-vocal-air" to R.string.eq_catalog_name_female_vocal_air,
    "deep-sub" to R.string.eq_catalog_name_deep_sub,
    "tight-bass" to R.string.eq_catalog_name_tight_bass,
    "clear-mids" to R.string.eq_catalog_name_clear_mids,
    "soft-sibilance" to R.string.eq_catalog_name_soft_sibilance,
    "full-voice" to R.string.eq_catalog_name_full_voice,
    "small-speaker-clear" to R.string.eq_catalog_name_small_speaker_clear,
    "dialogue-focus" to R.string.eq_catalog_name_dialogue_focus,
    "jazz-club" to R.string.eq_catalog_name_jazz_club,
    "hip-hop-sub" to R.string.eq_catalog_name_hip_hop_sub,
    "metal-edge" to R.string.eq_catalog_name_metal_edge,
    "dance-pulse" to R.string.eq_catalog_name_dance_pulse,
    "folk-strings" to R.string.eq_catalog_name_folk_strings,
    "ambient-space" to R.string.eq_catalog_name_ambient_space,
)

internal val EqPresetDetailRes = mapOf(
    "flat" to R.string.eq_catalog_detail_flat,
    "bass-boost" to R.string.eq_catalog_detail_bass_boost,
    "vocal-clear" to R.string.eq_catalog_detail_vocal_clear,
    "night" to R.string.eq_catalog_detail_night,
    "harman-oe-2018-bass" to R.string.eq_catalog_detail_harman_oe_2018_bass,
    "harman-ie-2019-bass" to R.string.eq_catalog_detail_harman_ie_2019_bass,
    "deep-sub" to R.string.eq_catalog_detail_deep_sub,
    "tight-bass" to R.string.eq_catalog_detail_tight_bass,
    "clear-mids" to R.string.eq_catalog_detail_clear_mids,
    "soft-sibilance" to R.string.eq_catalog_detail_soft_sibilance,
    "full-voice" to R.string.eq_catalog_detail_full_voice,
    "small-speaker-clear" to R.string.eq_catalog_detail_small_speaker_clear,
    "dialogue-focus" to R.string.eq_catalog_detail_dialogue_focus,
    "jazz-club" to R.string.eq_catalog_detail_jazz_club,
    "hip-hop-sub" to R.string.eq_catalog_detail_hip_hop_sub,
    "metal-edge" to R.string.eq_catalog_detail_metal_edge,
    "dance-pulse" to R.string.eq_catalog_detail_dance_pulse,
    "folk-strings" to R.string.eq_catalog_detail_folk_strings,
    "ambient-space" to R.string.eq_catalog_detail_ambient_space,
)
