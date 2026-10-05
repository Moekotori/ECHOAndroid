package app.echo.android.library

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.widget.Toast
import app.echo.android.MainActivity
import app.echo.android.R
import app.echo.android.playback.EchoPlaybackLibraryIds
import java.security.MessageDigest

data class EchoLibraryShortcutRequest(val mediaId: String, val play: Boolean)

/** Only stable catalog identifiers go into launcher intents, never media URLs or credentials. */
object EchoLibraryShortcuts {
    const val Action = "app.echo.android.action.LIBRARY_SHORTCUT"
    private const val MediaId = "library_media_id"
    private const val Play = "library_play"

    @androidx.media3.common.util.UnstableApi
    suspend fun rememberCollection(context: Context, mediaId: String) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        app.echo.android.playback.EchoPlaybackProcessRuntime.catalog().item(mediaId)?.let {
            rememberPlayed(context, mediaId, it.title)
        }
    }

    fun intent(context: Context, mediaId: String, play: Boolean) =
        Intent(context, MainActivity::class.java).setAction(Action)
            .putExtra(MediaId, mediaId).putExtra(Play, play)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    fun request(intent: Intent): EchoLibraryShortcutRequest? {
        if (intent.action != Action) return null
        val id = intent.getStringExtra(MediaId)?.takeIf { it.length <= 2048 && isCollection(it) } ?: return null
        return EchoLibraryShortcutRequest(id, intent.getBooleanExtra(Play, false))
    }

    fun pin(context: Context, mediaId: String, label: String, play: Boolean) {
        val manager = context.getSystemService(ShortcutManager::class.java)
        val accepted = runCatching {
            manager.enableShortcuts(listOf(shortcutId(mediaId, play)))
            manager.isRequestPinShortcutSupported && manager.requestPinShortcut(info(context, mediaId, label, play), null)
        }.getOrDefault(false)
        if (!accepted) Toast.makeText(context, R.string.library_shortcut_unsupported, Toast.LENGTH_SHORT).show()
    }

    fun rememberPlayed(context: Context, mediaId: String, label: String) {
        if (!isCollection(mediaId)) return
        val manager = context.getSystemService(ShortcutManager::class.java)
        runCatching {
            if (manager.isRateLimitingActive) return
            val next = info(context, mediaId, label, true)
            manager.enableShortcuts(listOf(shortcutId(mediaId, true), shortcutId(mediaId, false)))
            manager.updateShortcuts(listOf(next, info(context, mediaId, label, false)))
            val capacity = (manager.maxShortcutCountPerActivity - manager.manifestShortcuts.size).coerceIn(0, 4)
            manager.dynamicShortcuts = (listOf(next) + manager.dynamicShortcuts.filter { it.id != next.id }).take(capacity)
            manager.reportShortcutUsed(next.id)
        }
    }

    fun unavailable(context: Context, request: EchoLibraryShortcutRequest) {
        runCatching { context.getSystemService(ShortcutManager::class.java)
            .disableShortcuts(listOf(shortcutId(request.mediaId, true), shortcutId(request.mediaId, false)), context.getString(R.string.library_shortcut_missing)) }
    }

    private fun info(context: Context, mediaId: String, label: String, play: Boolean) =
        ShortcutInfo.Builder(context, shortcutId(mediaId, play))
            .setShortLabel(label.trim().ifEmpty { context.getString(R.string.app_name) }.take(40))
            .setLongLabel(context.getString(if (play) R.string.library_shortcut_play_label else R.string.library_shortcut_open_label, label.take(100)))
            .setIcon(Icon.createWithResource(context, if (play) R.drawable.echo_ic_play else R.mipmap.ic_launcher))
            .setIntent(intent(context, mediaId, play)).build()

    private fun shortcutId(id: String, play: Boolean): String = "library_" +
        MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).take(12).joinToString("") { "%02x".format(it) } + "_$play"

    private fun isCollection(id: String) = EchoPlaybackLibraryIds.albumKey(id) != null || EchoPlaybackLibraryIds.playlistId(id) != null
}
