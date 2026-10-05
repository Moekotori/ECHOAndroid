package app.echo.android.data

import android.content.Context
import androidx.datastore.preferences.core.*
import app.echo.android.model.playback.EchoOutputDspPolicy
import org.json.JSONObject

/** Atomic snapshots of an explicit audio-only allowlist. No accounts, URLs, USB mode or volume. */
internal object OutputAudioProfilePreferences {
    private val profilesKey = stringPreferencesKey("output_audio_profiles_v1")
    private val baselineKey = stringPreferencesKey("output_audio_profile_baseline_v1")
    private val activeKey = stringPreferencesKey("output_audio_profile_active_v1")
    private const val MaxBytes = 524288
    private val booleans = listOf("equalizer_enabled", "equalizer_parametric", "replay_gain_enabled",
        "channel_balance_enabled", "channel_balance_swap", "channel_balance_constant_power",
        "channel_balance_invert_left", "channel_balance_invert_right", "track_fade_enabled", "track_smart_transition")
    private val floats = listOf("equalizer_preamp_db", "replay_gain_preamp_db", "channel_balance",
        "channel_balance_left_gain_db", "channel_balance_right_gain_db", "channel_balance_left_delay_ms", "channel_balance_right_delay_ms")
    private val strings = listOf("equalizer_preset", "equalizer_band_gains", "equalizer_source_label", "equalizer_filters",
        "equalizer_active_user_preset_id", "mseb", "replay_gain_mode", "channel_balance_mono", "channel_balance_left_bands", "channel_balance_right_bands")
    private val names = booleans + floats + strings + "track_fade_duration_ms"

    fun keys(preferences: Preferences): Set<String> = decode(preferences[profilesKey]).keys().asSequence().take(EchoOutputDspPolicy.MaxBindings).toSet()

    suspend fun save(context: Context, key: String) { context.echoSettings.edit { saveInto(it, key) } }
    suspend fun remove(context: Context, key: String) { context.echoSettings.edit { removeFrom(it, key) } }
    suspend fun activate(context: Context, key: String) { context.echoSettings.edit { activateIn(it, key) } }

    fun saveInto(prefs: MutablePreferences, key: String) {
        val safe = key.trim().take(120).takeIf { it.isNotEmpty() } ?: return
        val profiles = decode(prefs[profilesKey])
        val snapshot = capture(prefs)
        require(snapshot.toString().length <= MaxBytes / EchoOutputDspPolicy.MaxBindings - 256)
        val order = profiles.keys().asSequence().map { profiles.optJSONObject(it)?.optLong("_order", 0L) ?: 0L }.maxOrNull() ?: 0L
        snapshot.put("_order", order + 1)
        profiles.remove(safe)
        profiles.put(safe, snapshot)
        while (profiles.length() > EchoOutputDspPolicy.MaxBindings) {
            val oldest = profiles.keys().asSequence().filter { it != safe }
                .minByOrNull { profiles.optJSONObject(it)?.optLong("_order", 0L) ?: 0L } ?: break
            profiles.remove(oldest)
        }
        prefs[profilesKey] = profiles.toString()
    }

    fun removeFrom(prefs: MutablePreferences, key: String) {
        val profiles = decode(prefs[profilesKey])
        profiles.remove(key)
        prefs[profilesKey] = profiles.toString()
        if (prefs[activeKey] == key) restoreBaseline(prefs)
    }

    fun activateIn(prefs: MutablePreferences, key: String) {
        val profiles = decode(prefs[profilesKey])
        val matched = when {
            profiles.has(key) -> key
            profiles.has(key.substringBefore(':')) -> key.substringBefore(':')
            else -> null
        }
        if (matched == prefs[activeKey]) return
        restoreBaseline(prefs)
        val snapshot = matched?.let(profiles::optJSONObject) ?: return
        if (!valid(snapshot)) return
        prefs[baselineKey] = capture(prefs).toString()
        apply(prefs, snapshot)
        prefs[activeKey] = matched
    }

    private fun restoreBaseline(prefs: MutablePreferences) {
        val baseline = decode(prefs[baselineKey])
        if (valid(baseline)) apply(prefs, baseline)
        prefs.remove(baselineKey)
        prefs.remove(activeKey)
    }

    private fun capture(prefs: Preferences): JSONObject {
        val values = prefs.asMap().entries.associate { it.key.name to it.value }
        return JSONObject().apply { names.forEach { put(it, values[it] ?: JSONObject.NULL) } }
    }

    private fun valid(json: JSONObject) = names.all(json::has) &&
        booleans.all { json.isNull(it) || json.opt(it) is Boolean } &&
        floats.all { json.isNull(it) || (json.opt(it) is Number && json.optDouble(it).isFinite()) } &&
        strings.all { json.isNull(it) || json.opt(it) is String } &&
        (json.isNull("track_fade_duration_ms") || json.opt("track_fade_duration_ms") is Number)

    private fun apply(prefs: MutablePreferences, json: JSONObject) {
        booleans.forEach { name -> val key = booleanPreferencesKey(name)
            if (json.isNull(name)) prefs.remove(key) else prefs[key] = json.getBoolean(name) }
        floats.forEach { name -> val key = floatPreferencesKey(name)
            if (json.isNull(name)) prefs.remove(key) else prefs[key] = json.getDouble(name).toFloat() }
        strings.forEach { name -> val key = stringPreferencesKey(name)
            if (json.isNull(name)) prefs.remove(key) else prefs[key] = json.getString(name) }
        val fadeKey = intPreferencesKey("track_fade_duration_ms")
        if (json.isNull(fadeKey.name)) prefs.remove(fadeKey) else prefs[fadeKey] = json.getInt(fadeKey.name)
    }

    private fun decode(raw: String?): JSONObject = if (raw == null || raw.length > MaxBytes) JSONObject()
        else runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
}
