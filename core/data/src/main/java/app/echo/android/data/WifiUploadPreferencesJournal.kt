package app.echo.android.data

import android.content.Context
import java.io.IOException
import org.json.JSONObject

/** Bounded durable recovery records, separate from portable music/settings backups. */
internal class WifiUploadPreferencesJournal(context: Context) : WifiUploadJournal {
    private val preferences = context.applicationContext.getSharedPreferences("wifi-upload-pending-v1", Context.MODE_PRIVATE)

    override fun entries(): List<PendingWifiUpload> = preferences.all.entries.take(MaxPending).mapNotNull { (id, raw) ->
        runCatching {
            require(id.matches(Regex("[a-f0-9-]{36}")))
            val text = raw as? String ?: return@runCatching null
            require(text.length <= 8192)
            val json = JSONObject(text)
            val name = json.getString("temporaryName")
            require(name == ".echo-upload-$id.part")
            PendingWifiUpload(id, json.getString("tree"), json.getString("parent"), name,
                json.getString("finalName"), json.getString("mime"), json.optString("document").takeIf { it.isNotBlank() })
        }.getOrNull()
    }

    override fun put(upload: PendingWifiUpload) {
        check(preferences.contains(upload.id) || preferences.all.size < MaxPending) { "cleanup_pending" }
        val json = JSONObject().put("tree", upload.tree).put("parent", upload.parent)
            .put("temporaryName", upload.temporaryName).put("finalName", upload.finalName)
            .put("mime", upload.mime).putOpt("document", upload.document)
        val encoded = json.toString()
        require(encoded.length <= 8192) { "invalid_path" }
        if (!preferences.edit().putString(upload.id, encoded).commit()) throw IOException("cleanup_pending")
    }

    override fun remove(id: String) {
        if (!preferences.edit().remove(id).commit()) throw IOException("cleanup_pending")
    }

    private companion object { const val MaxPending = 32 }
}
