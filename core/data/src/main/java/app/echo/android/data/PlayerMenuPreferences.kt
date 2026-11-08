package app.echo.android.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import app.echo.android.model.settings.EchoPlayerMenuAction
import app.echo.android.model.settings.EchoPlayerMenuLayout

internal object PlayerMenuPreferences {
    private val Order = stringPreferencesKey("player_menu_order")
    private val Hidden = stringPreferencesKey("player_menu_hidden")

    fun read(preferences: Preferences) = EchoPlayerMenuLayout(
        order = preferences[Order]?.split(',')?.mapNotNull(EchoPlayerMenuAction::fromId).orEmpty(),
        hidden = preferences[Hidden]?.split(',')?.mapNotNull(EchoPlayerMenuAction::fromId)?.toSet().orEmpty(),
    ).normalized()

    fun write(preferences: MutablePreferences, value: EchoPlayerMenuLayout) {
        val layout = value.normalized()
        preferences[Order] = layout.order.joinToString(",") { it.id }
        preferences[Hidden] = layout.order.filter { it in layout.hidden }.joinToString(",") { it.id }
    }
}
