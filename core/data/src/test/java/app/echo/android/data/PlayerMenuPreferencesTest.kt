package app.echo.android.data

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import app.echo.android.model.settings.EchoPlayerMenuAction
import app.echo.android.model.settings.EchoPlayerMenuLayout
import org.junit.Assert.*
import org.junit.Test

class PlayerMenuPreferencesTest {
    @Test fun roundTripsOrderAndHiddenActions() {
        val preferences = mutablePreferencesOf()
        val layout = EchoPlayerMenuLayout(hidden = EchoPlayerMenuAction.entries.toSet()).moved(EchoPlayerMenuAction.Info, -100)
        PlayerMenuPreferences.write(preferences, layout)
        assertEquals(layout, PlayerMenuPreferences.read(preferences))
    }

    @Test fun ignoresUnknownIdsAndRestoresMissingActions() {
        val preferences = mutablePreferencesOf(
            stringPreferencesKey("player_menu_order") to "info,retired,info",
            stringPreferencesKey("player_menu_hidden") to "delete,retired",
        )
        val layout = PlayerMenuPreferences.read(preferences)
        assertEquals(EchoPlayerMenuAction.Info, layout.order.first())
        assertEquals(EchoPlayerMenuAction.entries.size, layout.order.size)
        assertEquals(setOf(EchoPlayerMenuAction.Delete), layout.hidden)
    }
}
