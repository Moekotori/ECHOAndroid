package app.echo.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.echoOnboarding by preferencesDataStore(name = "echo-onboarding")
private val CompletedKey = booleanPreferencesKey("completed")

/** Installation-local state, independent of imported library/settings backups. */
class EchoOnboardingStore internal constructor(private val preferences: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.echoOnboarding)

    val completed: Flow<Boolean> = preferences.data
        .map { it[CompletedKey] ?: false }
        .distinctUntilChanged()

    suspend fun complete() {
        preferences.edit { it[CompletedKey] = true }
    }
}
