package app.echo.android.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import app.echo.android.model.settings.EchoLibraryCategory
import app.echo.android.model.settings.EchoLibraryLayout

internal object LibraryLayoutPreferences {
    private val TrackActions = stringPreferencesKey("library_hidden_track_actions")
    private val FileNames = androidx.datastore.preferences.core.booleanPreferencesKey("library_show_file_names")
    private val Order = stringPreferencesKey("library_category_order")
    private val Hidden = stringPreferencesKey("library_hidden_categories")

    fun read(preferences: Preferences): EchoLibraryLayout = EchoLibraryLayout(
        showFileNames = preferences[FileNames] ?: false,
        hiddenTrackActions = preferences[TrackActions]?.split(',')?.mapNotNull(app.echo.android.model.settings.EchoTrackMenuAction::fromId)?.toSet().orEmpty(),
        order = preferences[Order]?.split(',')?.mapNotNull(EchoLibraryCategory::fromId).orEmpty(),
        hidden = preferences[Hidden]?.split(',')?.mapNotNull(EchoLibraryCategory::fromId)?.toSet().orEmpty(),
    ).normalized()

    fun write(preferences: MutablePreferences, value: EchoLibraryLayout) {
        val layout = value.normalized()
        preferences[FileNames] = layout.showFileNames
        preferences[TrackActions] = layout.hiddenTrackActions.joinToString(",") { it.id }
        preferences[Order] = layout.order.joinToString(",") { it.id }
        preferences[Hidden] = layout.order.filter { it in layout.hidden }.joinToString(",") { it.id }
    }
}
