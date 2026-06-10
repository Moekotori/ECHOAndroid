package app.echo.android

import app.echo.android.data.EchoAppSettings
import app.echo.android.data.EchoLibraryRepository
import app.echo.android.data.EchoSettingsStore
import app.echo.android.data.NeteaseSession
import app.echo.android.model.library.NeteaseAccountState
import app.echo.android.model.library.NeteaseImportState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

internal class NeteaseController(
    private val repository: EchoLibraryRepository,
    private val settingsStore: EchoSettingsStore,
    private val scope: CoroutineScope,
) {
    private val _accountState = MutableStateFlow(NeteaseAccountState())
    val accountState: StateFlow<NeteaseAccountState> = _accountState.asStateFlow()

    private val _importState = MutableStateFlow(NeteaseImportState())
    val importState: StateFlow<NeteaseImportState> = _importState.asStateFlow()

    private var session: NeteaseSession? = null
    private var sessionKey: String? = null
    private var playlistRefreshJob: Job? = null
    private var importJob: Job? = null

    fun restore(settings: EchoAppSettings) {
        val userId = settings.neteaseUserId
        val cookie = settings.neteaseCookie
        if (userId == null || cookie.isNullOrBlank()) {
            if (session != null) {
                session = null
                sessionKey = null
                _accountState.value = NeteaseAccountState()
            }
            return
        }
        val key = "$userId|$cookie"
        if (key == sessionKey) return
        sessionKey = key
        session = NeteaseSession(
            userId = userId,
            nickname = settings.neteaseNickname.orEmpty().ifBlank { "NetEase User" },
            cookie = cookie,
        )
        refreshRemotePlaylists()
    }

    fun loginByPhone(phone: String, password: String) {
        if (phone.isBlank() || password.isBlank()) {
            _accountState.update { it.copy(error = "请输入手机号和密码") }
            return
        }
        login {
            repository.loginNeteaseByPhone(phone, password)
        }
    }

    fun loginWithCookie(cookie: String) {
        if (cookie.isBlank()) {
            _accountState.update { it.copy(error = "请输入 MUSIC_U 或完整 Cookie") }
            return
        }
        login {
            repository.loginNeteaseWithCookie(cookie)
        }
    }

    fun refreshRemotePlaylists() {
        val currentSession = session ?: return
        playlistRefreshJob?.cancel()
        _accountState.value = _accountState.value.copy(
            loggedIn = true,
            userId = currentSession.userId,
            nickname = currentSession.nickname,
            loading = true,
            error = null,
            message = "正在读取网易云歌单",
        )
        playlistRefreshJob = scope.launch {
            try {
                val playlists = withContext(Dispatchers.IO) {
                    repository.fetchNeteaseUserPlaylists(currentSession)
                }
                _accountState.value = NeteaseAccountState(
                    loggedIn = true,
                    userId = currentSession.userId,
                    nickname = currentSession.nickname,
                    playlists = playlists,
                    message = "已读取 ${playlists.size} 个网易云歌单",
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _accountState.value = _accountState.value.copy(
                    loading = false,
                    error = error.message ?: "网易云歌单读取失败",
                )
            }
        }
    }

    fun importPlaylist(playlistId: Long) {
        val currentSession = session ?: run {
            _importState.value = NeteaseImportState(error = "请先登录网易云")
            return
        }
        if (importJob?.isActive == true) {
            _importState.update { it.copy(error = "已有网易云歌单正在导入") }
            return
        }
        val remotePlaylist = _accountState.value.playlists.firstOrNull { it.id == playlistId }
        _importState.value = NeteaseImportState(
            importing = true,
            playlistName = remotePlaylist?.name,
            scannedCount = remotePlaylist?.trackCount ?: 0,
            message = "正在导入 ${remotePlaylist?.name ?: playlistId}",
        )
        importJob = scope.launch {
            try {
                val imported = withContext(Dispatchers.IO) {
                    repository.importNeteasePlaylist(currentSession, playlistId)
                }
                _importState.value = NeteaseImportState(
                    playlistName = imported.playlist.name,
                    scannedCount = imported.tracks.size,
                    importedCount = imported.tracks.size,
                    message = "已导入 ${imported.playlist.name} · ${imported.tracks.size} 首",
                )
            } catch (error: CancellationException) {
                _importState.value = NeteaseImportState(error = "网易云导入已取消")
                throw error
            } catch (error: Throwable) {
                _importState.value = NeteaseImportState(
                    playlistName = remotePlaylist?.name,
                    error = error.message ?: "网易云歌单导入失败",
                )
            }
        }
    }

    fun logout() {
        playlistRefreshJob?.cancel()
        importJob?.cancel()
        session = null
        sessionKey = null
        _accountState.value = NeteaseAccountState()
        _importState.value = NeteaseImportState()
        scope.launch(Dispatchers.IO) {
            settingsStore.clearNeteaseSession()
        }
    }

    fun currentCookie(): String? = session?.cookie

    fun clear() {
        playlistRefreshJob?.cancel()
        importJob?.cancel()
    }

    private fun login(block: suspend () -> NeteaseSession) {
        _accountState.value = _accountState.value.copy(
            loading = true,
            error = null,
            message = "正在登录网易云",
        )
        scope.launch {
            try {
                val nextSession = withContext(Dispatchers.IO) { block() }
                withContext(Dispatchers.IO) {
                    settingsStore.setNeteaseSession(
                        userId = nextSession.userId,
                        nickname = nextSession.nickname,
                        cookie = nextSession.cookie,
                    )
                }
                session = nextSession
                sessionKey = "${nextSession.userId}|${nextSession.cookie}"
                refreshRemotePlaylists()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _accountState.value = _accountState.value.copy(
                    loading = false,
                    error = error.message ?: "网易云登录失败",
                )
            }
        }
    }
}
