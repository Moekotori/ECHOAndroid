package app.echo.android.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import app.echo.android.model.connect.EchoLinkSharingPort

private val phoneLibraryPortKey = intPreferencesKey("echo_link_phone_library_port")

internal fun readEchoPhoneLibraryPort(preferences: Preferences): Int =
    preferences[phoneLibraryPortKey]?.takeIf(EchoLinkSharingPort::isValid) ?: EchoLinkSharingPort.DefaultPhone

internal fun writeEchoPhoneLibraryPort(preferences: MutablePreferences, port: Int) {
    preferences[phoneLibraryPortKey] = EchoLinkSharingPort.requireValid(port)
}
