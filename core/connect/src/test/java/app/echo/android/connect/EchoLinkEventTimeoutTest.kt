package app.echo.android.connect

import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoLinkEventTimeoutTest {
    @Test
    fun eventTicketsRejectForeignOriginsAndSubscriptionsDoNotFollowRedirects() = runBlocking {
        val requests = LinkedBlockingQueue<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/echo-link/v2/events/ticket") { exchange ->
            val bytes = """{"ticket":"one","eventsUrl":"https://other.example/events?ticket=one"}""".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.createContext("/events") { exchange ->
            exchange.responseHeaders.add("Location", "http://127.0.0.1:${server.address.port}/redirected?ticket=one")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/redirected") { exchange ->
            requests.add(exchange.requestURI.toString())
            exchange.sendResponseHeaders(500, -1)
            exchange.close()
        }
        server.start()
        var subscription: EchoLinkEventSubscription? = null
        try {
            val transport = OkHttpEchoLinkTransport()
            val endpoint = EchoPairingParser.parseManual("127.0.0.1:${server.address.port}", "paired-token")!!
            val rejected = runCatching { transport.createEventTicket(endpoint) }.exceptionOrNull()
            assertTrue(rejected is EchoLinkHttpException)
            assertEquals(400, (rejected as EchoLinkHttpException).statusCode)
            val closed = kotlinx.coroutines.CompletableDeferred<Throwable?>()
            subscription = transport.subscribeEvents(endpoint,
                EchoLinkEventTicket("one", endpoint.resolveEventsUrl("/events?ticket=one")!!),
                onEvent = { fail("Redirect must not produce a snapshot") }, onClosed = { closed.complete(it) })
            val error = kotlinx.coroutines.withTimeout(2_000) { closed.await() }
            assertEquals(302, (error as EchoLinkHttpException).statusCode)
            assertTrue(requests.isEmpty())
        } finally { subscription?.cancel(); server.stop(0) }
    }

    @Test
    fun idleEventStreamOutlivesTheControlReadTimeout() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/events") { exchange ->
            exchange.responseHeaders.add("Content-Type", "text/event-stream")
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use {
                it.write(": ready\n\n".toByteArray())
                it.flush()
                Thread.sleep(150)
                it.write("data: {\"state\":\"paused\",\"positionMs\":42}\n\n".toByteArray())
                it.flush()
            }
            exchange.close()
        }
        server.createContext("/echo-link/v1/status") { exchange ->
            Thread.sleep(150)
            runCatching {
                exchange.sendResponseHeaders(200, 2)
                exchange.responseBody.use { it.write("{}".toByteArray()) }
            }
            exchange.close()
        }
        server.start()
        var subscription: EchoLinkEventSubscription? = null
        try {
            val transport = OkHttpEchoLinkTransport(OkHttpClient.Builder()
                .readTimeout(50, TimeUnit.MILLISECONDS).retryOnConnectionFailure(false).build())
            val endpoint = EchoPairingParser.parseManual("127.0.0.1:${server.address.port}")!!
            val received = LinkedBlockingQueue<Long>()
            val failure = AtomicReference<Throwable?>()
            subscription = transport.subscribeEvents(
                endpoint,
                EchoLinkEventTicket("test", "http://127.0.0.1:${server.address.port}/events".toHttpUrl()),
                onEvent = { received.add((it as app.echo.android.model.connect.EchoRemoteMessage.StatusSnapshot).payload.positionMs) },
                onClosed = { failure.set(it) },
            )
            assertEquals(42L, received.poll(2, TimeUnit.SECONDS))
            assertNull(failure.get())
            try {
                transport.fetchStatus(endpoint)
                fail("Ordinary control requests must retain their short read timeout")
            } catch (_: IOException) {
                // The derived event client must not modify ordinary requests.
            }
        } finally {
            subscription?.cancel()
            server.stop(0)
        }
    }
}
