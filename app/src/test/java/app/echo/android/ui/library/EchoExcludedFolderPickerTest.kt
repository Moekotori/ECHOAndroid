package app.echo.android.ui.library

import org.junit.Assert.*
import org.junit.Test

class EchoExcludedFolderPickerTest {
    private val storageProvider = "com.android.externalstorage.documents"

    @Test fun selectedFoldersUseTheScannerPathRepresentation() {
        assertEquals("Recordings/Calls", excludedFolderPath(storageProvider, "primary:Recordings/Calls"))
        assertEquals("Music/日本語", excludedFolderPath(storageProvider, "primary:Music/日本語/"))
        assertEquals(
            "Removable/abcd-1234/Recordings/Calls",
            excludedFolderPath(storageProvider, "ABCD-1234:Recordings/Calls"),
        )
    }

    @Test fun rootsAndUnmappableProvidersDoNotCreateRules() {
        assertNull(excludedFolderPath(storageProvider, "primary:"))
        assertNull(excludedFolderPath(storageProvider, "ABCD-1234:"))
        assertNull(excludedFolderPath(storageProvider, "primary:Music/../Recordings"))
        assertNull(excludedFolderPath(storageProvider, "primary"))
        assertNull(excludedFolderPath("cloud.documents", "primary:Music"))
        assertNull(excludedFolderPath(storageProvider, null))
    }
}
