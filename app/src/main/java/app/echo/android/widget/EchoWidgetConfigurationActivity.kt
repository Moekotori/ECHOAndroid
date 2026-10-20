@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package app.echo.android.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.glance.appwidget.GlanceAppWidgetManager
import app.echo.android.data.EchoWidgetOptionsStore
import app.echo.android.data.readEchoStartupThemeSnapshot
import app.echo.android.design.EchoMobileTheme
import app.echo.android.feature.settings.WidgetConfigurationScreen
import app.echo.android.i18n.wrapEchoAppLocaleToMatchApplication
import app.echo.android.model.settings.EchoWidgetOptions
import app.echo.android.model.settings.EchoWidgetPlaylistOption
import app.echo.android.playback.EchoPlaybackLibraryIds
import app.echo.android.playback.EchoPlaybackProcessRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EchoWidgetConfigurationActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(newBase.wrapEchoAppLocaleToMatchApplication()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)
        if (widgetId <= 0 || info?.provider?.packageName != packageName) { finish(); return }
        val store = EchoWidgetOptionsStore(this)
        val theme = applicationContext.readEchoStartupThemeSnapshot()
        setContent {
            EchoMobileTheme(darkTheme = app.echo.android.resolveEchoDarkTheme(isSystemInDarkTheme(), theme.themeMode,
                theme.scheduledDarkModeEnabled, theme.scheduledDarkStartMinute, theme.scheduledDarkEndMinute,
                app.echo.android.currentMinuteOfDayNow()),
                colorTheme = app.echo.android.model.settings.EchoColorTheme.fromId(theme.colorTheme), customColors = theme.customColors) {
                var failed by remember { mutableStateOf(false) }
                val initial by produceState<EchoWidgetOptions?>(null, widgetId) {
                    try { value = withContext(Dispatchers.IO) { store.read(widgetId) } }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { failed = true; value = EchoWidgetOptions() }
                }
                var query by rememberSaveable { mutableStateOf("") }
                var playlists by remember { mutableStateOf<List<EchoWidgetPlaylistOption>>(emptyList()) }
                var loading by remember { mutableStateOf(true) }
                var busy by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()
                LaunchedEffect(query) {
                    loading = true
                    failed = false
                    try {
                        delay(200)
                        val catalog = EchoPlaybackProcessRuntime.catalog()
                        playlists = withContext(Dispatchers.IO) {
                            val items = catalog.widgetPlaylists(query.take(256))
                            items.filter { EchoPlaybackLibraryIds.playlistId(it.mediaId) != null }
                                .map { EchoWidgetPlaylistOption(it.mediaId, it.title) }
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { failed = true }
                    finally { loading = false }
                }
                val options = initial
                if (options == null) CircularProgressIndicator()
                else {
                    val snapshot = EchoPlaybackProcessRuntime.surfaceSnapshot
                    WidgetConfigurationScreen(options, playlists, loading, busy, failed, query, { query = it },
                        onSave = { next -> scope.launch {
                            busy = true; failed = false
                            try {
                                val manager = AppWidgetManager.getInstance(this@EchoWidgetConfigurationActivity)
                                val activeIds = listOf(EchoPlaybackWidgetReceiver::class.java, EchoMiniPlaybackWidgetReceiver::class.java,
                                    EchoCoverPlaybackWidgetReceiver::class.java, EchoLyricsPlaybackWidgetReceiver::class.java)
                                    .flatMap { manager.getAppWidgetIds(ComponentName(this@EchoWidgetConfigurationActivity, it)).toList() }.toSet() + widgetId
                                withContext(Dispatchers.IO) { store.save(widgetId, next, activeIds) }
                                EchoPlaybackWidgets.updateInstance(this@EchoWidgetConfigurationActivity,
                                    GlanceAppWidgetManager(this@EchoWidgetConfigurationActivity).getGlanceIdBy(widgetId))
                                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                                finish()
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { failed = true }
                            finally { busy = false }
                        } }, onCancel = { finish() }, title = snapshot.title.ifBlank { getString(app.echo.android.R.string.app_name) },
                        artist = snapshot.artist,
                        lyric = EchoPlaybackProcessRuntime.notificationLyricLine.collectAsState().value)
                }
            }
        }
    }
}
