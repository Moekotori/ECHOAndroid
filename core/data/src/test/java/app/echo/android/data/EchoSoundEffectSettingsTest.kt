package app.echo.android.data

import androidx.datastore.preferences.core.*
import app.echo.android.model.playback.*
import org.junit.Assert.*
import org.junit.Test

class EchoSoundEffectSettingsTest {
    @Test fun allPcPresetsRoundTripWithoutChangingPersonalEqOrOtherDsp() {
        assertEquals(22, EchoSoundEffectPreset.entries.size)
        for (preset in EchoSoundEffectPreset.entries) {
            val settings = EchoDspSettings(hrtfEnabled = true, equalLoudnessEnabled = true,
                soundEffect = EchoSoundEffectSettings(true, preset))
            assertEquals(settings, EchoDspCodec.decode(EchoDspCodec.encode(settings)))
        }
        assertFalse(EchoDspCodec.decode(org.json.JSONObject().put("soundEffectEnabled", true).put("soundEffectPreset", "future")).soundEffect.enabled)
    }

    @Test fun outputProfilesSaveEffectAndRestoreThePreviousSound() {
        val active = booleanPreferencesKey("dsp_sound_effect_enabled")
        val preset = stringPreferencesKey("dsp_sound_effect_preset")
        val eq = stringPreferencesKey("equalizer_preset")
        val prefs = mutablePreferencesOf(active to true, preset to "hall", eq to "personal")
        OutputAudioProfilePreferences.saveInto(prefs, "wired:Headphones")
        prefs[active] = false; prefs[preset] = "tape"
        OutputAudioProfilePreferences.activateIn(prefs, "wired:Headphones")
        assertTrue(prefs[active]!!); assertEquals("hall", prefs[preset]); assertEquals("personal", prefs[eq])
        OutputAudioProfilePreferences.activateIn(prefs, "speaker:Phone")
        assertFalse(prefs[active]!!); assertEquals("tape", prefs[preset])
    }
}
