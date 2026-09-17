package app.echo.android.data

import app.echo.android.model.backup.EchoBackupDocument
import app.echo.android.model.backup.EchoBackupException
import app.echo.android.model.backup.EchoBackupPlaylist
import app.echo.android.model.backup.EchoBackupSettings
import app.echo.android.model.backup.EchoBackupTrackRef
import app.echo.android.model.playback.EchoChannelBalanceState
import app.echo.android.model.playback.EchoEqualizerPreset
import app.echo.android.model.playback.EchoEqualizerUserPreset
import app.echo.android.model.playback.EchoTrackTransitionOptions
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoBackupCodecTest {
    @Test
    fun roundTripsEqBalanceAndTheme() {
        val original = EchoBackupDocument(
            version = 1,
            exportedAtEpochMs = 1_700_000_000_000L,
            settings = EchoBackupSettings(
                themeMode = "dark",
                colorTheme = "default",
                equalizerEnabled = true,
                equalizerPreset = "custom",
                equalizerBandGains = listOf(1f, 0f, -1f),
                equalizerPreampDb = -3f,
                equalizerUserPresets = listOf(
                    EchoEqualizerUserPreset(
                        id = "g1",
                        name = "Evening",
                        graphicPresetId = EchoEqualizerPreset.Bass,
                        gainsDb = listOf(4f, 2.8f, 0.4f, -0.8f, -1.2f),
                        preampDb = -2.5f,
                    ),
                ),
                equalizerActiveUserPresetId = "g1",
                opraLastQuery = "HD 650",
                channelBalance = EchoChannelBalanceState(enabled = true, balance = -0.25f),
                trackTransitions = EchoTrackTransitionOptions(fadeEnabled = true, fadeDurationMs = 1500),
                replayGainEnabled = true,
                replayGainMode = "album",
                pauseOnAudioDisconnect = false,
                resumeOnAudioReconnect = true,
            ),
            playlists = listOf(
                EchoBackupPlaylist(
                    name = "Late night",
                    tracks = listOf(
                        EchoBackupTrackRef("Song", "Artist", "Music/Album/song.flac", 240_000L),
                    ),
                ),
            ),
            favorites = listOf(EchoBackupTrackRef("Song", "Artist", "Music/Album/song.flac")),
        )
        val decoded = EchoBackupCodec.decode(EchoBackupCodec.encode(original))
        assertEquals("dark", decoded.settings.themeMode)
        assertEquals(listOf(1f, 0f, -1f), decoded.settings.equalizerBandGains)
        assertEquals(-3f, decoded.settings.equalizerPreampDb)
        assertEquals("Evening", decoded.settings.equalizerUserPresets?.single()?.name)
        assertEquals("g1", decoded.settings.equalizerActiveUserPresetId)
        assertEquals("HD 650", decoded.settings.opraLastQuery)
        assertEquals(false, decoded.settings.pauseOnAudioDisconnect)
        assertEquals(true, decoded.settings.resumeOnAudioReconnect)
        assertEquals(-0.25f, decoded.settings.channelBalance?.balance)
        assertEquals("Late night", decoded.playlists.single().name)
        assertEquals("Music/Album/song.flac", decoded.playlists.single().tracks.single().relativePath)
        assertEquals(1, decoded.favorites.size)
    }

    @Test
    fun encodedSettingsDoNotIncludeSecretKeys() {
        val encoded = EchoBackupCodec.encode(
            EchoBackupDocument(
                settings = EchoBackupSettings(themeMode = "light", equalizerEnabled = true),
            ),
        )
        val keys = EchoBackupCodec.collectKeys(JSONObject(encoded))
        assertFalse(keys.any { EchoBackupCodec.isForbiddenKey(it) })
        assertFalse(keys.contains("lastFmApiKey"))
        assertFalse(keys.contains("echoLinkPcToken"))
        assertTrue(keys.contains("themeMode"))
        assertTrue(keys.contains("equalizerEnabled"))
        assertFalse(keys.contains("equalizerUserPresets"))
    }

    @Test
    fun missingUserPresetsStayNull() {
        val decoded = EchoBackupCodec.decode(
            JSONObject().put("version", 1).put("settings", JSONObject().put("themeMode", "dark")).toString(),
        )
        assertEquals(null, decoded.settings.equalizerUserPresets)
        assertEquals(null, decoded.settings.opraLastQuery)
    }

    @Test
    fun newerVersionIsRejected() {
        val json = JSONObject().put("version", EchoBackupDocument.CurrentVersion + 1).toString()
        try {
            EchoBackupCodec.decode(json)
            throw AssertionError("expected EchoBackupException")
        } catch (error: EchoBackupException) {
            assertTrue(error.message.orEmpty().contains("newer"))
        }
    }

    @Test
    fun secretKeyInFileIsRejected() {
        val json = JSONObject()
            .put("version", 1)
            .put("settings", JSONObject().put("listenBrainzToken", "abc"))
            .toString()
        try {
            EchoBackupCodec.decode(json)
            throw AssertionError("expected EchoBackupException")
        } catch (error: EchoBackupException) {
            assertTrue(error.message.orEmpty().contains("secret"))
        }
    }

    @Test
    fun playlistTrackMatchesRelativePath() {
        val entry = M3uEntry(location = "Music/Album/song.flac", title = "Song")
        val rows = listOf(
            M3uMatchRow("t1", "Song", "Artist", "Music/Album/song.flac", "content://1"),
            M3uMatchRow("t2", "Other", "Band", "Music/Album/other.mp3", "content://2"),
        )
        assertEquals("t1", M3uPlaylistCodec.matchTrackId(entry, rows))
    }
}
