package app.echo.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.echo.android.model.library.PlaylistTrackOrder
import app.echo.android.model.library.PlaylistTrackSort
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.playlistOrders by preferencesDataStore(name = "echo-playlist-orders")

internal class PlaylistOrderStore(context: Context) {
    private val store = context.applicationContext.playlistOrders
    private fun key(id: String) = stringPreferencesKey("order:$id")

    fun observe(id: String) = store.data.map { decode(it[key(id)]) }.distinctUntilChanged()

    suspend fun save(id: String, order: PlaylistTrackOrder) {
        store.edit { values ->
            if (order.sort == PlaylistTrackSort.Default) {
                values.remove(key(id))
            } else {
                values[key(id)] = "${order.sort.name}|${order.descending}|${System.currentTimeMillis()}"
            }
            // Saved view choices are bounded independently of the library size.
            values.asMap().entries.filter { it.key.name.startsWith("order:") }
                .sortedByDescending { it.value.toString().substringAfterLast('|').toLongOrNull() ?: 0L }
                .drop(256).forEach { values.remove(stringPreferencesKey(it.key.name)) }
        }
    }

    suspend fun remove(id: String) = store.edit { it.remove(key(id)) }

    private fun decode(value: String?): PlaylistTrackOrder {
        val parts = value?.split('|') ?: return PlaylistTrackOrder()
        val sort = PlaylistTrackSort.entries.firstOrNull { it.name == parts.firstOrNull() }
            ?: return PlaylistTrackOrder()
        return PlaylistTrackOrder(sort, sort != PlaylistTrackSort.Default && parts.getOrNull(1) == "true")
    }
}
