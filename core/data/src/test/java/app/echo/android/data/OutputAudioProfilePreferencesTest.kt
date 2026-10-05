package app.echo.android.data

import androidx.datastore.preferences.core.*
import org.junit.Assert.*
import org.junit.Test

class OutputAudioProfilePreferencesTest {
    private val gain = floatPreferencesKey("replay_gain_preamp_db")
    private val balance = floatPreferencesKey("channel_balance")
    private val eq = booleanPreferencesKey("equalizer_enabled")

    @Test fun switchingDevicesAndLeavingProfileRestoresGeneralSettingsAtomically() {
        val prefs = mutablePreferencesOf(gain to -4f, balance to .3f, eq to true)
        OutputAudioProfilePreferences.saveInto(prefs, "bluetooth:Headphones")
        prefs[gain] = -8f; prefs[balance] = -.2f
        OutputAudioProfilePreferences.saveInto(prefs, "usb:DAC")
        prefs[gain] = 0f; prefs.remove(balance); prefs[eq] = false
        OutputAudioProfilePreferences.activateIn(prefs, "bluetooth:Headphones")
        assertEquals(-4f, prefs[gain]!!, 0f)
        OutputAudioProfilePreferences.activateIn(prefs, "usb:DAC")
        assertEquals(-8f, prefs[gain]!!, 0f)
        OutputAudioProfilePreferences.activateIn(prefs, "speaker:Phone")
        assertEquals(0f, prefs[gain]!!, 0f)
        assertNull(prefs[balance]); assertFalse(prefs[eq]!!)
    }

    @Test fun repeatedRouteDoesNotOverwriteLiveEditsAndRemovalRestoresBaseline() {
        val prefs = mutablePreferencesOf(gain to -4f)
        OutputAudioProfilePreferences.saveInto(prefs, "wired:Headphones")
        prefs[gain] = 0f
        OutputAudioProfilePreferences.activateIn(prefs, "wired:Headphones")
        prefs[gain] = -6f
        OutputAudioProfilePreferences.activateIn(prefs, "wired:Headphones")
        assertEquals(-6f, prefs[gain]!!, 0f)
        OutputAudioProfilePreferences.saveInto(prefs, "wired:Headphones")
        OutputAudioProfilePreferences.removeFrom(prefs, "wired:Headphones")
        assertEquals(0f, prefs[gain]!!, 0f)
        assertTrue(OutputAudioProfilePreferences.keys(prefs).isEmpty())
    }

    @Test fun profilesAreBoundedAndNeverRestoreCredentialsOrUsbMode() {
        val token = stringPreferencesKey("remote_token")
        val usb = booleanPreferencesKey("usb_exclusive_enabled")
        val prefs = mutablePreferencesOf(token to "before", usb to false)
        repeat(20) { OutputAudioProfilePreferences.saveInto(prefs, "bluetooth:$it") }
        assertEquals(16, OutputAudioProfilePreferences.keys(prefs).size)
        prefs[token] = "after"; prefs[usb] = true
        OutputAudioProfilePreferences.activateIn(prefs, "bluetooth:19")
        assertEquals("after", prefs[token]); assertTrue(prefs[usb]!!)
    }
}
