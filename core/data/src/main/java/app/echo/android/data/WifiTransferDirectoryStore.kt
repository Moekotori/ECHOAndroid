package app.echo.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/** Local directory grant is device-specific and is deliberately excluded from portable backups. */
class WifiTransferDirectoryStore(context: Context) {
    private val context = context.applicationContext
    private val key = stringPreferencesKey("wifi_transfer_directory")
    suspend fun load(): String? = context.echoSettings.data.first()[key]
    suspend fun save(uri: String) { context.echoSettings.edit { it[key] = uri } }
}
