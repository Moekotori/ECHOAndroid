package app.echo.android

import android.app.Application
import android.os.Build
import app.echo.android.connect.*
import app.echo.android.data.EchoLibraryDatabase
import app.echo.android.data.EchoSharedLocalLibrary
import app.echo.android.model.connect.EchoPhoneLibraryRegistration
import app.echo.android.model.connect.EchoRemoteConnectionState
import app.echo.android.model.connect.EchoRemoteEndpoint
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
        if (job?.isActive == true) return
        val endpoint = client.status.value.endpoint ?: return
        if (client.status.value.connectionState != EchoRemoteConnectionState.Connected) return
        val current = ++generation
        state.value = "starting"
        target = endpoint
        job = scope.launch {
            var ownedServer: EchoLinkCastServer? = null
            try {
                val host = withContext(Dispatchers.IO) { EchoLinkLanAddresses.ipv4(application) }
                    ?: error("no_lan")
                val token = EchoLinkCastPolicy.newToken()
                val catalog = EchoSharedLocalLibrary(EchoLibraryDatabase.create(application))
                val readyRoutes = java.util.concurrent.atomic.AtomicReference<EchoPhoneLibraryRoutes?>(null)
                val created = EchoLinkCastServer(
                    openBody = EchoLinkCastMediaOpener(application.contentResolver),
                    allowedPeerHost = { endpoint.host },
                    bindHost = host,
                    metadata = { path, headers -> readyRoutes.get()?.metadata(path, headers) },
                    resolvePublication = { path -> readyRoutes.get()?.publication(path) },
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
                    ))
                    url
                }
                val published = EchoPhoneLibraryRegistration(EchoLinkCastPolicy.newToken(), baseUrl, token, Build.MODEL.take(80))
                registration = published
                EchoPhoneLibraryService.start(application, published.sessionId)
                while (isActive) {
                    client.registerPhoneLibrary(endpoint, published)
                    state.value = "sharing"
                    delay(60_000)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (current == generation) state.value = if (client.isPhoneLibraryUnsupported(error)) "unsupported" else "error"
            } finally {
                ownedServer?.stop()
                if (current == generation) {
                    server = null
                    EchoPhoneLibraryService.stop(application)
                }
            }
        }
    }

    fun connectionChanged(endpoint: EchoRemoteEndpoint?, connected: Boolean) {
        if (target != null && (!connected || target != endpoint)) stop()
    }

    fun serviceDestroyed(sessionId: String?) {
        if (sessionId != null && sessionId == registration?.sessionId && state.value in listOf("starting", "sharing")) stop()
    }

    fun stop() {
        generation++
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
