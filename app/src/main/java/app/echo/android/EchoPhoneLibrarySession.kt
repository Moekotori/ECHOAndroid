package app.echo.android

import android.app.Application
import android.os.Build
import app.echo.android.connect.*
import app.echo.android.data.EchoLibraryDatabase
import app.echo.android.data.EchoSharedLocalLibrary
import app.echo.android.data.EchoSharedLocalCatalog
import app.echo.android.lyrics.SharedPhoneLyricsResolver
import app.echo.android.model.connect.EchoPhoneLibraryRegistration
import app.echo.android.model.connect.EchoRemoteConnectionState
import app.echo.android.model.connect.EchoRemoteEndpoint
import app.echo.android.model.error.EchoErrorLog
import app.echo.android.model.error.EchoErrorSource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Explicit, process-owned sharing session. Closing it revokes all media URLs. */
class EchoPhoneLibrarySession(
    private val application: Application,
    private val client: EchoRemoteClient,
    private val scope: CoroutineScope,
) {
    private val state = MutableStateFlow("off")
    val status = state.asStateFlow()
    private var job: Job? = null
    private var server: EchoLinkCastServer? = null
    private var target: EchoRemoteEndpoint? = null
    private var registration: EchoPhoneLibraryRegistration? = null
    private var generation = 0

    fun start() {
        val connection = client.status.value
        connectionChanged(connection.endpoint, connection.connectionState)
        if (job?.isActive == true) return
        val endpoint = connection.endpoint
        if (endpoint == null || connection.connectionState != EchoRemoteConnectionState.Connected) {
            state.value = "connection_required"
            return
        }
        val current = ++generation
        state.value = "starting"
        target = endpoint
        job = scope.launch {
            var ownedServer: EchoLinkCastServer? = null
            var stage = "route"
            try {
                val host = withContext(Dispatchers.IO) { EchoLinkLanAddresses.ipv4ForPeer(endpoint.host, endpoint.port) }
                    ?: error("no_lan")
                stage = "server"
                val token = EchoLinkCastPolicy.newToken()
                val catalog = EchoSharedLocalLibrary(EchoLibraryDatabase.create(application))
                val categories = EchoSharedLocalCatalog(EchoLibraryDatabase.create(application))
                val lyrics = SharedPhoneLyricsResolver(application, catalog)
                val readyRoutes = java.util.concurrent.atomic.AtomicReference<EchoPhoneLibraryRoutes?>(null)
                val artwork = EchoPhoneArtworkOpener(application)
                val created = EchoLinkCastServer(
                    openBody = EchoLinkCastMediaOpener(application.contentResolver),
                    allowedPeerHost = { endpoint.host },
                    bindHost = host,
                    metadata = { path, headers -> readyRoutes.get()?.metadata(path, headers) },
                    resolvePublication = { path -> readyRoutes.get()?.publication(path) },
                    openArtwork = artwork::open,
                )
                ownedServer = created
                server = created
                val baseUrl = withContext(Dispatchers.IO) {
                    val port = created.start()
                    val url = EchoLinkCastPolicy.advertisedBaseUrl(host, port)
                    readyRoutes.set(EchoPhoneLibraryRoutes(token, url,
                        page = { query, offset, size -> runBlocking {
                            val (rows, total) = catalog.page(query, offset, size)
                            EchoPhoneLibraryPage(rows.map(::publication), total)
                        } },
                        track = { id -> runBlocking { catalog.track(id)?.let(::publication) } },
                        catalog = { query -> runBlocking { categories.page(query) } },
                        lyrics = { id -> runBlocking { lyrics.read(id) } },
                    ))
                    url
                }
                val published = EchoPhoneLibraryRegistration(EchoLinkCastPolicy.newToken(), baseUrl, token, Build.MODEL.take(80))
                registration = published
                stage = "service"
                EchoPhoneLibraryService.start(application, published.sessionId)
                client.setBackgroundConnectionRequired(true)
                stage = "registration"
                maintainEchoPhoneLibraryLease(register = { client.registerPhoneLibrary(endpoint, published) }, onRenewed = {
                    ensureActive()
                    if (current != generation) throw CancellationException("Sharing session replaced")
                    state.value = "sharing"
                })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (current == generation) {
                    val failure = when {
                        stage == "route" && error.message == "no_lan" -> "no_lan"
                        stage == "service" -> "service_error"
                        else -> client.phoneLibraryFailureStatus(error)
                    }
                    state.value = failure
                    // Do not persist exception messages/URLs, which may include session credentials.
                    EchoErrorLog.record(EchoErrorSource.Connect, "Phone library sharing failed",
                        detail = "stage=$stage; reason=$failure; type=${error.javaClass.simpleName}")
                }
            } finally {
                ownedServer?.stop()
                if (current == generation) {
                    client.setBackgroundConnectionRequired(false)
                    server = null
                    EchoPhoneLibraryService.stop(application)
                }
            }
        }
    }

    fun connectionChanged(endpoint: EchoRemoteEndpoint?, connectionState: EchoRemoteConnectionState) {
        val currentTarget = target ?: return
        if (!shouldKeepEchoPhoneLibrarySharing(endpoint, currentTarget, connectionState)) {
            val wasActive = state.value == "starting" || state.value == "sharing"
            val previousStatus = state.value
            stop()
            state.value = if (wasActive) "connection_lost" else previousStatus
        }
    }

    fun serviceDestroyed(sessionId: String?) {
        if (sessionId != null && sessionId == registration?.sessionId && state.value in listOf("starting", "sharing")) stop()
    }

    fun stop() {
        generation++
        client.setBackgroundConnectionRequired(false)
        job?.cancel()
        job = null
        server?.stop()
        server = null
        EchoPhoneLibraryService.stop(application)
        val oldTarget = target
        val oldRegistration = registration
        target = null
        registration = null
        state.value = "off"
        if (oldTarget != null && oldRegistration != null) scope.launch {
            try { client.unregisterPhoneLibrary(oldTarget, oldRegistration.sessionId) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* PC expires unreachable sessions after three minutes. */ }
        }
    }

    private fun publication(row: app.echo.android.data.LibraryTrackEntity) = EchoLinkCastPublication(
        token = "unused-local-publication", trackId = row.id, uri = row.contentUri,
        title = row.title, artist = row.artist, album = row.album, durationMs = row.durationMs,
        mimeType = row.mimeType,
        artworkUrl = row.artworkUri?.takeIf(EchoLinkCastPolicy::isLocalFileUri),
        format = EchoLinkCastFormat.fromTrack(row.contentUri, row.mimeType, row.sampleRateHz, null, null, null),
    )
}
