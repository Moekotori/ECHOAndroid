package app.echo.android.data

import org.json.JSONObject

data class LastFmPendingAuth(val apiKey: String, val sharedSecret: String, val token: String, val createdAt: Long)

class LastFmPendingAuthStore(private val secrets: EchoSecretStore) {
    fun read(): LastFmPendingAuth? {
        val value = secrets.get(KEY) ?: return null
        val root = JSONObject(value)
        val createdAt = root.getLong("createdAt")
        if (System.currentTimeMillis() - createdAt !in 0..3_600_000L) { clear(); return null }
        return LastFmPendingAuth(root.getString("apiKey"), root.getString("sharedSecret"), root.getString("token"), createdAt)
    }

    fun write(auth: LastFmPendingAuth) {
        secrets.set(KEY, JSONObject().put("apiKey", auth.apiKey).put("sharedSecret", auth.sharedSecret)
            .put("token", auth.token).put("createdAt", auth.createdAt).toString())
        check(read() == auth) { "Unable to save Last.fm authorization securely" }
    }

    fun clear() { secrets.set(KEY, null) }
    private companion object { const val KEY = "lastfm_pending_authorization" }
}
