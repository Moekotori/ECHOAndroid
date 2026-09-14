package app.echo.android.data

import app.echo.android.model.radio.EchoRadioStation
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class EchoRadioDefaultsTest {
    @Test fun newAndExistingLibrariesReceiveDefault() {
        val existing = EchoRadioStation("custom", "Custom", "https://example.com/live")
        val fresh = EchoRadioDefaults.initialize(emptyList())
        assertEquals("https://echonext.moe/radio/stream", fresh.single().url)
        assertEquals(fresh + existing, EchoRadioDefaults.initialize(listOf(existing)))
    }

    @Test fun existingOfficialUrlIsPreservedWithoutDuplicate() {
        val custom = EchoRadioStation("custom", "My ECHO", "https://echonext.moe/radio/stream/")
        assertEquals(listOf(custom), EchoRadioDefaults.initialize(listOf(custom)))
    }

    @Test fun fullCollectionIsPreserved() {
        val stations = List(EchoRadioStation.MaxStations) {
            EchoRadioStation("$it", "$it", "https://example.com/$it")
        }
        assertEquals(stations, EchoRadioDefaults.initialize(stations))
    }

    @Test fun deletedDefaultRemainsAnInitializedEmptyCollection() {
        val saved = JSONObject(EchoRadioDefaults.encode(emptyList()))
        assertEquals(0, saved.getJSONArray("stations").length())
    }
}
