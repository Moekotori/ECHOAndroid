package app.echo.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Device-local catalog references; deliberately excluded from portable settings backups. */
data class NfcMusicBinding(val mediaId: String, val label: String)

class NfcMusicBindingStore(context: Context) {
    private val context = context.applicationContext
    private val key = stringPreferencesKey("nfc_music_bindings_v1")

    suspend fun find(token: String): NfcMusicBinding? = withContext(Dispatchers.IO) {
        NfcMusicBindingCodec.decode(context.echoSettings.data.first()[key])[token]
    }

    suspend fun save(token: String, binding: NfcMusicBinding) = withContext(Dispatchers.IO) {
        context.echoSettings.edit { prefs ->
            val bindings = NfcMusicBindingCodec.decode(prefs[key]).toMutableMap()
            require(NfcMusicBindingCodec.validToken(token))
            // Never evict another physical card silently.
            check(token in bindings || bindings.size < NfcMusicBindingCodec.MaxBindings)
            bindings[token] = binding
            prefs[key] = NfcMusicBindingCodec.encode(bindings)
        }
    }
}

internal object NfcMusicBindingCodec {
    const val MaxBindings = 256
    private const val MaxBytes = 768 * 1024
    private val tokenPattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
    fun validToken(token: String) = tokenPattern.matches(token)

    fun encode(bindings: Map<String, NfcMusicBinding>): String {
        require(bindings.size <= MaxBindings)
        return JSONObject().apply {
            bindings.forEach { (token, binding) ->
                require(validToken(token) && valid(binding))
                put(token, JSONObject().put("id", binding.mediaId).put("label", binding.label))
            }
        }.toString().also { require(it.length <= MaxBytes) }
    }

    fun decode(raw: String?): Map<String, NfcMusicBinding> {
        if (raw == null || raw.length > MaxBytes) return emptyMap()
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        if (json.length() > MaxBindings) return emptyMap()
        return buildMap {
            json.keys().forEach { token ->
                val entry = json.optJSONObject(token) ?: return@forEach
                val binding = NfcMusicBinding(entry.optString("id"), entry.optString("label"))
                if (validToken(token) && valid(binding)) put(token, binding)
            }
        }
    }

    private fun valid(binding: NfcMusicBinding) =
        binding.mediaId.length in 1..2048 && binding.label.length in 1..200
}
