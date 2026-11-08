package app.echo.android.data

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import androidx.datastore.preferences.core.edit
import app.echo.android.model.settings.EchoAppearancePack
import java.io.File
import java.io.InputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** All asset I/O stays on IO. Applied resources and the previous appearance survive restarts. */
class EchoAppearancePackStore(context: Context) {
    private val context = context.applicationContext
    private val settings = EchoSettingsStore(this.context)
    private val root = File(this.context.filesDir, "appearance-packs")

    suspend fun read(uri: Uri): DecodedAppearancePack = withContext(Dispatchers.IO) {
        val bytes = open(uri).use { it.readLimited(EchoAppearancePackCodec.MaxPackageBytes) }
        EchoAppearancePackCodec.decode(bytes).also(::validateAssets)
    }

    suspend fun export(uri: Uri, name: String) = withContext(Dispatchers.IO) {
        val s = settings.appSettings.first()
        val appearance = EchoAppearancePack(
            name = name.filterNot(Char::isISOControl).trim().take(48).ifBlank { "ECHO" }, playerStyle = s.playerPageStyle,
            textScale = s.playerTextScale, artworkScale = s.playerArtworkScale, lyricsBackdrop = s.lyricsBackdrop,
            fontFamily = s.lyricsFontFamily, fontScale = s.lyricsFontScale, alignment = s.lyricsAlignment,
            lineSpacing = s.lyricsLineSpacing, backgroundDim = s.lyricsBackgroundDim, wordStyle = s.lyricsWordHighlightStyle,
            wordIntensity = s.lyricsWordHighlightIntensity, colorTheme = s.colorTheme, colors = s.customColors,
            backgroundBlur = s.customBackgroundBlur, backgroundBrightness = s.customBackgroundBrightness, backgroundGlass = s.customBackgroundGlass,
            showTranslation = s.lyricsShowTranslation, showRomanization = s.lyricsShowRomanization,
            colorMode = s.lyricsColorMode, wordHighlightEnabled = s.lyricsWordHighlightEnabled, estimatedWordHighlightEnabled = s.lyricsEstimatedWordHighlightEnabled,
            translationScale = s.lyricsOptions.translationScale, romanizationScale = s.lyricsOptions.romanizationScale,
            backgroundScale = s.customBackgroundScale, backgroundPositionX = s.customBackgroundPositionX, backgroundPositionY = s.customBackgroundPositionY,
            dynamicColorEnabled = s.dynamicColorEnabled)
        val background = s.customBackgroundUri?.takeIf { s.customBackgroundMode == "image" }?.let {
            open(Uri.parse(it)).use { input -> input.readLimited(8 * 1024 * 1024) }
        }
        val font = if (appearance.fontFamily == "imported") {
            val fontUri = requireNotNull(s.importedFontUri)
            open(Uri.parse(fontUri)).use { it.readLimited(4 * 1024 * 1024) }
        } else null
        val pack = DecodedAppearancePack(appearance, background, font)
        validateAssets(pack)
        val bytes = EchoAppearancePackCodec.encode(pack)
        // Validate our export against the exact import contract before touching the destination.
        EchoAppearancePackCodec.decode(bytes)
        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { it.write(bytes) }
    }

    suspend fun canUndo(): Boolean = context.echoSettings.data.first()[AppearancePackPreferences.undoKey] != null

    suspend fun apply(pack: DecodedAppearancePack) = withContext(Dispatchers.IO) { lock.withLock {
        // Decode again so all externally supplied presentation parameters are validated.
        val checked = EchoAppearancePackCodec.decode(EchoAppearancePackCodec.encode(pack))
        validateAssets(checked)
        root.mkdirs()
        val dir = File(root, java.util.UUID.randomUUID().toString()).also { check(it.mkdir()) }
        try {
            val background = checked.background?.let { bytes -> File(dir, "background").also { it.writeBytes(bytes) }.toURI().toString() }
            val font = checked.font?.let { bytes -> File(dir, "font").also { it.writeBytes(bytes) }.toURI().toString() }
            context.echoSettings.edit { AppearancePackPreferences.apply(it, checked.appearance, background, font) }
        } catch (error: Exception) {
            // Never remove an asset after a successful durable settings commit.
            withContext(kotlinx.coroutines.NonCancellable) {
                val prefs = context.echoSettings.data.first()
                if (prefs.asMap().values.none { it is String && it.contains(dir.name) }) dir.deleteRecursively()
            }
            throw error
        }
        syncStartupTheme()
        prune()
    } }

    suspend fun undo(): Boolean = withContext(Dispatchers.IO) { lock.withLock {
        var restored = false
        context.echoSettings.edit { restored = AppearancePackPreferences.restore(it) }
        if (restored) { syncStartupTheme(); prune() }
        restored
    } }

    private suspend fun syncStartupTheme() {
        context.writeEchoStartupThemeSnapshot(settings.appSettings.first().toStartupThemeSnapshot(), synchronous = true)
    }

    private suspend fun prune() {
        val prefs = context.echoSettings.data.first()
        val references = prefs.asMap().values.filterIsInstance<String>()
        root.listFiles().orEmpty().filter { it.isDirectory }.forEach { dir ->
            if (references.none { it.contains(dir.name) }) dir.deleteRecursively()
        }
    }

    private fun validateAssets(pack: DecodedAppearancePack) {
        pack.background?.let { bytes ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            require(options.outWidth in 1..8192 && options.outHeight in 1..8192 && options.outWidth.toLong() * options.outHeight <= 16_777_216)
        }
        pack.font?.let { bytes ->
            require(bytes.size in 12..(4 * 1024 * 1024))
            val signature = bytes.take(4).toByteArray()
            require(signature.contentEquals(byteArrayOf(0, 1, 0, 0)) || signature.toString(Charsets.US_ASCII) in listOf("OTTO", "ttcf"))
            val temp = File.createTempFile("echo-font-", ".font", context.cacheDir)
            try { temp.writeBytes(bytes); Typeface.createFromFile(temp) } finally { temp.delete() }
        }
    }

    private fun open(uri: Uri): InputStream {
        require(uri.scheme in listOf("content", "file"))
        return requireNotNull(context.contentResolver.openInputStream(uri))
    }

    private companion object { val lock = Mutex() }
}

private fun InputStream.readLimited(max: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(out.size() + count <= max)
        out.write(buffer, 0, count)
    }
    return out.toByteArray()
}
