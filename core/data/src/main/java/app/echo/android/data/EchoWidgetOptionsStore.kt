package app.echo.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.echo.android.model.settings.EchoWidgetOptions
import kotlinx.coroutines.flow.first
import org.json.JSONObject

private val Context.echoWidgetOptions by preferencesDataStore(name = "echo-widget-options")

class EchoWidgetOptionsStore(context: Context) {
    private val context = context.applicationContext
    private val key = stringPreferencesKey("instances_v1")

    suspend fun read(widgetId: Int): EchoWidgetOptions {
        val json = decode(context.echoWidgetOptions.data.first()[key]).optJSONObject(widgetId.toString()) ?: return EchoWidgetOptions()
        return EchoWidgetOptions(json.optBoolean("artwork", true), json.optBoolean("lyrics", true),
            json.optBoolean("controls", true), json.optDouble("opacity", 1.0).toFloat(),
            json.optString("playlist").takeIf { it.isNotBlank() }, json.optString("name").takeIf { it.isNotBlank() }).normalized()
    }

    suspend fun save(widgetId: Int, options: EchoWidgetOptions, activeIds: Set<Int>) {
        require(widgetId > 0)
        context.echoWidgetOptions.edit { prefs ->
            val json = decode(prefs[key])
            json.keys().asSequence().toList().filter { it.toIntOrNull() !in activeIds }.forEach(json::remove)
            check(json.has(widgetId.toString()) || json.length() < 64)
            val safe = options.normalized()
            json.put(widgetId.toString(), JSONObject().apply {
                put("artwork", safe.showArtwork); put("lyrics", safe.showLyrics); put("controls", safe.fullControls)
                put("opacity", safe.backgroundOpacity.toDouble())
                safe.playlistMediaId?.let { put("playlist", it) }; safe.playlistName?.let { put("name", it) }
            })
            prefs[key] = json.toString()
        }
    }

    suspend fun remove(widgetId: Int) { context.echoWidgetOptions.edit { prefs ->
        val json = decode(prefs[key]); json.remove(widgetId.toString()); prefs[key] = json.toString()
    } }

    private fun decode(raw: String?) = if (raw == null || raw.length > 262144) JSONObject()
        else runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
}
