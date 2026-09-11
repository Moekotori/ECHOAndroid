package app.echo.android.data

import app.echo.android.model.backup.EchoBackupDocument
import app.echo.android.model.backup.EchoBackupException
import app.echo.android.model.backup.EchoBackupPlaylist
import app.echo.android.model.backup.EchoBackupSettings
import app.echo.android.model.backup.EchoBackupTrackRef
import app.echo.android.model.playback.EchoChannelBalanceMonoMode
import app.echo.android.model.playback.EchoChannelBalanceState
import app.echo.android.model.playback.EchoTrackTransitionOptions
import org.json.JSONArray
import org.json.JSONObject

object EchoBackupCodec {
    private val ForbiddenKeyFragments = listOf(
        "token",
        "password",
        "secret",
        "session",
        "apikey",
        "api_key",
    )

    fun encode(document: EchoBackupDocument): String {
        val json = JSONObject()
            .put("version", document.version)
            .put("exportedAtEpochMs", document.exportedAtEpochMs)
            .put("settings", encodeSettings(document.settings))
            .put("playlists", JSONArray().also { array ->
                document.playlists.forEach { playlist -> array.put(encodePlaylist(playlist)) }
            })
            .put("favorites", JSONArray().also { array ->
                document.favorites.forEach { track -> array.put(encodeTrack(track)) }
            })
        return json.toString()
    }

    fun decode(text: String): EchoBackupDocument {
        val json = runCatching { JSONObject(text) }.getOrElse {
            throw EchoBackupException("Backup file is not valid JSON")
        }
        val version = json.optInt("version", 0)
        if (version <= 0) throw EchoBackupException("Backup file is missing a version")
        if (version > EchoBackupDocument.CurrentVersion) {
            throw EchoBackupException("Backup version $version is newer than this app")
        }
        if (collectKeys(json).any(::isForbiddenKey)) {
            throw EchoBackupException("Backup file contains secret fields")
        }
        return EchoBackupDocument(
            version = version,
            exportedAtEpochMs = json.optLong("exportedAtEpochMs", 0L),
            settings = decodeSettings(json.optJSONObject("settings")),
            playlists = json.optJSONArray("playlists").objects().mapNotNull(::decodePlaylist),
            favorites = json.optJSONArray("favorites").objects().mapNotNull(::decodeTrack),
        )
    }

    fun matchTrackId(track: EchoBackupTrackRef, rows: List<M3uMatchRow>): String? {
        val location = track.relativePath?.trim().orEmpty()
        if (location.isNotEmpty()) {
            val titled = listOf(track.artist, track.title).filter { it.isNotBlank() }.joinToString(" - ")
            M3uPlaylistCodec.matchTrackId(M3uEntry(location = location, title = titled.ifBlank { null }), rows)
                ?.let { return it }
        }
        if (track.title.isBlank()) return null
        return rows.firstOrNull { row ->
            row.title.equals(track.title, ignoreCase = true) &&
                (track.artist.isBlank() || row.artist.equals(track.artist, ignoreCase = true))
        }?.id
    }

    fun collectKeys(value: Any?): Set<String> {
        val keys = linkedSetOf<String>()
        collectKeys(value, keys)
        return keys
    }

    fun isForbiddenKey(key: String): Boolean {
        val lower = key.lowercase().replace("-", "").replace("_", "")
        return ForbiddenKeyFragments.any { fragment -> lower.contains(fragment.replace("_", "")) }
    }

    private fun collectKeys(value: Any?, into: MutableSet<String>) {
        when (value) {
            is JSONObject -> {
                val names = value.keys()
                while (names.hasNext()) {
                    val key = names.next()
                    into += key
                    collectKeys(value.opt(key), into)
                }
            }
            is JSONArray -> {
                for (index in 0 until value.length()) collectKeys(value.opt(index), into)
            }
        }
    }

    private fun encodeSettings(settings: EchoBackupSettings): JSONObject = JSONObject().apply {
        putOpt("themeMode", settings.themeMode)
        putOpt("colorTheme", settings.colorTheme)
        putOpt("appLanguage", settings.appLanguage)
        putOpt("performanceMode", settings.performanceMode)
        putOptNullableBoolean("dynamicColorEnabled", settings.dynamicColorEnabled)
        putOptNullableBoolean("dynamicArtworkEnabled", settings.dynamicArtworkEnabled)
        putOptNullableBoolean("compactModeEnabled", settings.compactModeEnabled)
        putOptNullableBoolean("scheduledDarkModeEnabled", settings.scheduledDarkModeEnabled)
        settings.scheduledDarkStartMinute?.let { put("scheduledDarkStartMinute", it) }
        settings.scheduledDarkEndMinute?.let { put("scheduledDarkEndMinute", it) }
        putOptNullableBoolean("playbackHapticsEnabled", settings.playbackHapticsEnabled)
        putOptNullableBoolean("pcHandoffEnabled", settings.pcHandoffEnabled)
        putOptNullableBoolean("showLyricsControlDeck", settings.showLyricsControlDeck)
        putOptNullableBoolean("onlineLyricsEnabled", settings.onlineLyricsEnabled)
        putOptNullableBoolean("usbExclusiveEnabled", settings.usbExclusiveEnabled)
        putOptNullableBoolean("usbBitPerfectEnabled", settings.usbBitPerfectEnabled)
        putOptNullableBoolean("usbExclusiveAutoRequestOnStartup", settings.usbExclusiveAutoRequestOnStartup)
        putOptNullableBoolean("trackAudioInfoTagsVisible", settings.trackAudioInfoTagsVisible)
        putOptNullableBoolean("replayGainEnabled", settings.replayGainEnabled)
        putOpt("replayGainMode", settings.replayGainMode)
        settings.replayGainPreampDb?.let { put("replayGainPreampDb", it.toDouble()) }
        settings.trackTransitions?.let { transitions ->
            put(
                "trackTransitions",
                JSONObject()
                    .put("fadeEnabled", transitions.fadeEnabled)
                    .put("fadeDurationMs", transitions.fadeDurationMs)
                    .put("smartEnabled", transitions.smartEnabled),
            )
        }
        putOptNullableBoolean("equalizerEnabled", settings.equalizerEnabled)
        putOpt("equalizerPreset", settings.equalizerPreset)
        settings.equalizerBandGains?.let { put("equalizerBandGains", JSONArray(it)) }
        settings.equalizerPreampDb?.let { put("equalizerPreampDb", it.toDouble()) }
        putOptNullableBoolean("equalizerParametric", settings.equalizerParametric)
        putOpt("equalizerSourceLabel", settings.equalizerSourceLabel)
        settings.equalizerFilters?.takeIf { it.isNotEmpty() }?.let {
            put("equalizerFilters", JSONArray(formatEqualizerFilters(it)))
        }
        settings.channelBalance?.let { put("channelBalance", encodeBalance(it)) }
        putOpt("lyricsFontFamily", settings.lyricsFontFamily)
        settings.lyricsFontScale?.let { put("lyricsFontScale", it.toDouble()) }
        putOpt("lyricsColorMode", settings.lyricsColorMode)
        putOpt("lyricsAlignment", settings.lyricsAlignment)
        settings.lyricsLineSpacing?.let { put("lyricsLineSpacing", it.toDouble()) }
        settings.lyricsBackgroundDim?.let { put("lyricsBackgroundDim", it.toDouble()) }
        putOptNullableBoolean("lyricsWordHighlightEnabled", settings.lyricsWordHighlightEnabled)
        settings.lyricsWordHighlightIntensity?.let { put("lyricsWordHighlightIntensity", it.toDouble()) }
        putOptNullableBoolean("lyricsImmersiveModeEnabled", settings.lyricsImmersiveModeEnabled)
        putOpt("lyricsMotionMode", settings.lyricsMotionMode)
        putOptNullableBoolean("lyricsShowTranslation", settings.lyricsShowTranslation)
        putOptNullableBoolean("lyricsShowRomanization", settings.lyricsShowRomanization)
        putOptNullableBoolean("lyricsFocusGlowEnabled", settings.lyricsFocusGlowEnabled)
        putOpt("uiFontFamily", settings.uiFontFamily)
        settings.uiFontScale?.let { put("uiFontScale", it.toDouble()) }
        settings.uiDensityScale?.let { put("uiDensityScale", it.toDouble()) }
    }

    private fun decodeSettings(json: JSONObject?): EchoBackupSettings {
        if (json == null) return EchoBackupSettings()
        val transitions = json.optJSONObject("trackTransitions")?.let { item ->
            EchoTrackTransitionOptions(
                fadeEnabled = item.optBoolean("fadeEnabled", false),
                fadeDurationMs = item.optInt("fadeDurationMs", 1500),
                smartEnabled = item.optBoolean("smartEnabled", false),
            ).normalized()
        }
        val filtersRaw = json.opt("equalizerFilters")
        val filters = when (filtersRaw) {
            is JSONArray -> parseEqualizerFilters(filtersRaw.toString())
            is String -> parseEqualizerFilters(filtersRaw)
            else -> null
        }
        return EchoBackupSettings(
            themeMode = json.optionalString("themeMode"),
            colorTheme = json.optionalString("colorTheme"),
            appLanguage = json.optionalString("appLanguage"),
            performanceMode = json.optionalString("performanceMode"),
            dynamicColorEnabled = json.optionalBoolean("dynamicColorEnabled"),
            dynamicArtworkEnabled = json.optionalBoolean("dynamicArtworkEnabled"),
            compactModeEnabled = json.optionalBoolean("compactModeEnabled"),
            scheduledDarkModeEnabled = json.optionalBoolean("scheduledDarkModeEnabled"),
            scheduledDarkStartMinute = json.optionalInt("scheduledDarkStartMinute"),
            scheduledDarkEndMinute = json.optionalInt("scheduledDarkEndMinute"),
            playbackHapticsEnabled = json.optionalBoolean("playbackHapticsEnabled"),
            pcHandoffEnabled = json.optionalBoolean("pcHandoffEnabled"),
            showLyricsControlDeck = json.optionalBoolean("showLyricsControlDeck"),
            onlineLyricsEnabled = json.optionalBoolean("onlineLyricsEnabled"),
            usbExclusiveEnabled = json.optionalBoolean("usbExclusiveEnabled"),
            usbBitPerfectEnabled = json.optionalBoolean("usbBitPerfectEnabled"),
            usbExclusiveAutoRequestOnStartup = json.optionalBoolean("usbExclusiveAutoRequestOnStartup"),
            trackAudioInfoTagsVisible = json.optionalBoolean("trackAudioInfoTagsVisible"),
            replayGainEnabled = json.optionalBoolean("replayGainEnabled"),
            replayGainMode = json.optionalString("replayGainMode"),
            replayGainPreampDb = json.optionalFloat("replayGainPreampDb"),
            trackTransitions = transitions,
            equalizerEnabled = json.optionalBoolean("equalizerEnabled"),
            equalizerPreset = json.optionalString("equalizerPreset"),
            equalizerBandGains = json.optJSONArray("equalizerBandGains")?.floats(),
            equalizerPreampDb = json.optionalFloat("equalizerPreampDb"),
            equalizerParametric = json.optionalBoolean("equalizerParametric"),
            equalizerSourceLabel = json.optionalString("equalizerSourceLabel"),
            equalizerFilters = filters,
            channelBalance = json.optJSONObject("channelBalance")?.let(::decodeBalance),
            lyricsFontFamily = json.optionalString("lyricsFontFamily"),
            lyricsFontScale = json.optionalFloat("lyricsFontScale"),
            lyricsColorMode = json.optionalString("lyricsColorMode"),
            lyricsAlignment = json.optionalString("lyricsAlignment"),
            lyricsLineSpacing = json.optionalFloat("lyricsLineSpacing"),
            lyricsBackgroundDim = json.optionalFloat("lyricsBackgroundDim"),
            lyricsWordHighlightEnabled = json.optionalBoolean("lyricsWordHighlightEnabled"),
            lyricsWordHighlightIntensity = json.optionalFloat("lyricsWordHighlightIntensity"),
            lyricsImmersiveModeEnabled = json.optionalBoolean("lyricsImmersiveModeEnabled"),
            lyricsMotionMode = json.optionalString("lyricsMotionMode"),
            lyricsShowTranslation = json.optionalBoolean("lyricsShowTranslation"),
            lyricsShowRomanization = json.optionalBoolean("lyricsShowRomanization"),
            lyricsFocusGlowEnabled = json.optionalBoolean("lyricsFocusGlowEnabled"),
            uiFontFamily = json.optionalString("uiFontFamily"),
            uiFontScale = json.optionalFloat("uiFontScale"),
            uiDensityScale = json.optionalFloat("uiDensityScale"),
        )
    }

    private fun encodeBalance(state: EchoChannelBalanceState): JSONObject = JSONObject().apply {
        put("enabled", state.enabled)
        put("balance", state.balance.toDouble())
        put("leftGainDb", state.leftGainDb.toDouble())
        put("rightGainDb", state.rightGainDb.toDouble())
        put("swapLeftRight", state.swapLeftRight)
        put("monoMode", state.monoMode.id)
        put("constantPower", state.constantPower)
        put("invertLeft", state.invertLeft)
        put("invertRight", state.invertRight)
        put("leftBandGainsDb", JSONArray(state.leftBandGainsDb))
        put("rightBandGainsDb", JSONArray(state.rightBandGainsDb))
        put("leftDelayMs", state.leftDelayMs.toDouble())
        put("rightDelayMs", state.rightDelayMs.toDouble())
    }

    private fun decodeBalance(json: JSONObject): EchoChannelBalanceState =
        EchoChannelBalanceState(
            enabled = json.optBoolean("enabled", false),
            balance = json.optDouble("balance", 0.0).toFloat(),
            leftGainDb = json.optDouble("leftGainDb", 0.0).toFloat(),
            rightGainDb = json.optDouble("rightGainDb", 0.0).toFloat(),
            swapLeftRight = json.optBoolean("swapLeftRight", false),
            monoMode = EchoChannelBalanceMonoMode.fromId(json.optString("monoMode")),
            constantPower = json.optBoolean("constantPower", true),
            invertLeft = json.optBoolean("invertLeft", false),
            invertRight = json.optBoolean("invertRight", false),
            leftBandGainsDb = json.optJSONArray("leftBandGainsDb")?.floats().orEmpty(),
            rightBandGainsDb = json.optJSONArray("rightBandGainsDb")?.floats().orEmpty(),
            leftDelayMs = json.optDouble("leftDelayMs", 0.0).toFloat(),
            rightDelayMs = json.optDouble("rightDelayMs", 0.0).toFloat(),
        ).normalized

    private fun encodePlaylist(playlist: EchoBackupPlaylist): JSONObject =
        JSONObject()
            .put("name", playlist.name)
            .put("tracks", JSONArray().also { array ->
                playlist.tracks.forEach { array.put(encodeTrack(it)) }
            })

    private fun decodePlaylist(json: JSONObject): EchoBackupPlaylist? {
        val name = json.optString("name").trim().takeIf { it.isNotEmpty() } ?: return null
        return EchoBackupPlaylist(
            name = name,
            tracks = json.optJSONArray("tracks").objects().mapNotNull(::decodeTrack),
        )
    }

    private fun encodeTrack(track: EchoBackupTrackRef): JSONObject = JSONObject().apply {
        put("title", track.title)
        put("artist", track.artist)
        putOpt("relativePath", track.relativePath)
        if (track.durationMs > 0L) put("durationMs", track.durationMs)
    }

    private fun decodeTrack(json: JSONObject): EchoBackupTrackRef? {
        val title = json.optString("title").trim()
        val artist = json.optString("artist").trim()
        val relativePath = json.optionalString("relativePath")
        if (title.isEmpty() && artist.isEmpty() && relativePath.isNullOrBlank()) return null
        return EchoBackupTrackRef(
            title = title,
            artist = artist,
            relativePath = relativePath,
            durationMs = json.optLong("durationMs", 0L),
        )
    }

    private fun JSONObject.putOptNullableBoolean(key: String, value: Boolean?) {
        if (value != null) put(key, value)
    }

    private fun JSONObject.optionalString(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).trim().takeIf { it.isNotEmpty() } else null

    private fun JSONObject.optionalBoolean(key: String): Boolean? =
        if (has(key) && !isNull(key)) optBoolean(key) else null

    private fun JSONObject.optionalInt(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null

    private fun JSONObject.optionalFloat(key: String): Float? =
        if (has(key) && !isNull(key)) optDouble(key).toFloat().takeIf { it.isFinite() } else null

    private fun JSONArray?.objects(): List<JSONObject> {
        if (this == null) return emptyList()
        return List(length()) { index -> optJSONObject(index) }.filterNotNull()
    }

    private fun JSONArray.floats(): List<Float> =
        List(length()) { index -> optDouble(index, 0.0).toFloat() }
}
