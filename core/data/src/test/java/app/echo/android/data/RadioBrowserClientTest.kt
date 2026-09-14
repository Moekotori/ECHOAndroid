package app.echo.android.data

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioBrowserClientTest {
    @Test
    fun searchHitsTheDirectoryPathAndParsesStations() = runBlocking {
        val requests = LinkedBlockingQueue<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/json/stations/search") { exchange ->
            requests.add(exchange.requestURI.query)
            val body = """[{"stationuuid":"u1","name":"Test FM","url":"https://stream.example/live"}]""".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
            exchange.close()
        }
        server.start()
        try {
            val origin = "http://127.0.0.1:${server.address.port}"
            val client = RadioBrowserClient("test", servers = listOf(origin))
            val stations = client.search("bbc")
            assertEquals("u1", stations.single().id)
            assertEquals("Test FM", stations.single().name)
            val query = requests.poll(2, TimeUnit.SECONDS)!!
            assertTrue(query.contains("name=bbc"))
            assertTrue(query.contains("hidebroken=true"))
            assertTrue(query.contains("limit=${RadioBrowserPolicy.MaxResults}"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun failedPrimaryServerFallsOverOnce() = runBlocking {
        val primary = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        primary.createContext("/json/stations/search") { exchange ->
            exchange.sendResponseHeaders(503, -1)
            exchange.close()
        }
        val backup = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        backup.createContext("/json/stations/search") { exchange ->
            val body = """[{"stationuuid":"u2","name":"Backup","url":"https://stream.example/live"}]""".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
            exchange.close()
        }
        primary.start()
        backup.start()
        try {
            val client = RadioBrowserClient(
                "test",
                servers = listOf(
                    "http://127.0.0.1:${primary.address.port}",
                    "http://127.0.0.1:${backup.address.port}",
                ),
            )
            assertEquals("Backup", client.search("jazz").single().name)
        } finally {
            primary.stop(0)
            backup.stop(0)
        }
    }
}
