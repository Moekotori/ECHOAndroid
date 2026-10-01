package app.echo.android.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import app.echo.android.model.settings.EchoHomeLayout
import app.echo.android.model.settings.EchoHomeSection

/** Home presentation settings stay separate from library contents and Room migrations. */
internal object HomeLayoutPreferences {
    private val Order = stringPreferencesKey("home_section_order")
    private val Hidden = stringPreferencesKey("home_hidden_sections")

    fun read(preferences: Preferences): EchoHomeLayout = EchoHomeLayout(
        order = preferences[Order]?.split(',')?.mapNotNull(EchoHomeSection::fromId).orEmpty(),
        hidden = preferences[Hidden]?.split(',')?.mapNotNull(EchoHomeSection::fromId)?.toSet().orEmpty(),
    ).normalized()

    fun write(preferences: MutablePreferences, value: EchoHomeLayout) {
        val layout = value.normalized()
        preferences[Order] = layout.order.joinToString(",") { it.id }
        preferences[Hidden] = layout.order.filter { it in layout.hidden }.joinToString(",") { it.id }
    }
}
