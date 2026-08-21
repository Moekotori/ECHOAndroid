package app.echo.android.data

import app.echo.android.model.i18n.echoText
import app.echo.android.model.settings.EchoAppLanguage
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EchoAppLanguageTest {
    @Test
    fun fromIdKeepsSupportedValues() {
        assertEquals(EchoAppLanguage.System, EchoAppLanguage.fromId("system"))
        assertEquals(EchoAppLanguage.Chinese, EchoAppLanguage.fromId("zh"))
        assertEquals(EchoAppLanguage.English, EchoAppLanguage.fromId("en"))
        assertEquals(EchoAppLanguage.Japanese, EchoAppLanguage.fromId("ja"))
    }

    @Test
    fun fromIdFallsBackToSystem() {
        assertEquals(EchoAppLanguage.System, EchoAppLanguage.fromId(null))
        assertEquals(EchoAppLanguage.System, EchoAppLanguage.fromId(""))
        assertEquals(EchoAppLanguage.System, EchoAppLanguage.fromId("fr"))
    }

    @Test
    fun localeOrNullMapsExplicitLanguages() {
        assertEquals(Locale.SIMPLIFIED_CHINESE, EchoAppLanguage.localeOrNull(EchoAppLanguage.Chinese))
        assertEquals(Locale.ENGLISH, EchoAppLanguage.localeOrNull(EchoAppLanguage.English))
        assertEquals(Locale.JAPANESE, EchoAppLanguage.localeOrNull(EchoAppLanguage.Japanese))
        assertNull(EchoAppLanguage.localeOrNull(EchoAppLanguage.System))
    }

    @Test
    fun echoTextFollowsDefaultLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ENGLISH)
            assertEquals("Settings", echoText(en = "Settings", zh = "设置", ja = "設定"))
            Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
            assertEquals("设置", echoText(en = "Settings", zh = "设置", ja = "設定"))
            Locale.setDefault(Locale.JAPANESE)
            assertEquals("設定", echoText(en = "Settings", zh = "设置", ja = "設定"))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun snapshotCarriesAppLanguage() {
        val settings = EchoAppSettings(appLanguage = EchoAppLanguage.Japanese)
        val snapshot = settings.toStartupThemeSnapshot()
        assertEquals(EchoAppLanguage.Japanese, snapshot.appLanguage)
        assertEquals(EchoAppLanguage.Japanese, snapshot.toAppSettings().appLanguage)
    }
}
