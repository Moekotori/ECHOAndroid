package app.echo.android.data

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import app.echo.android.model.settings.EchoLibraryCategory
import app.echo.android.model.settings.EchoLibraryLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryLayoutPreferencesTest {
    @Test fun missingAndUnknownPreferencesKeepCategoriesAccessible() {
        assertEquals(EchoLibraryLayout(), LibraryLayoutPreferences.read(mutablePreferencesOf()))
        val restored = LibraryLayoutPreferences.read(mutablePreferencesOf(
            stringPreferencesKey("library_category_order") to "future,folders,folders,songs",
            stringPreferencesKey("library_hidden_categories") to "future,albums",
        ))
        assertEquals(EchoLibraryCategory.Folders, restored.order.first())
        assertEquals(EchoLibraryCategory.entries.size, restored.order.size)
        assertEquals(setOf(EchoLibraryCategory.Albums), restored.hidden)
    }

    @Test fun onlyFoldersSurvivesReloadAndReset() {
        val preferences = mutablePreferencesOf()
        val layout = EchoLibraryLayout(EchoLibraryCategory.entries.reversed(),
            EchoLibraryCategory.entries.toSet() - EchoLibraryCategory.Folders, showAlbumArtists = true)
        LibraryLayoutPreferences.write(preferences, layout)
        assertEquals(layout, LibraryLayoutPreferences.read(preferences))
        LibraryLayoutPreferences.write(preferences, EchoLibraryLayout())
        assertEquals(EchoLibraryLayout(), LibraryLayoutPreferences.read(preferences))
    }
}
