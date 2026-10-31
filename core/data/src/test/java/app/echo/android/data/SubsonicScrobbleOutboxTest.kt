package app.echo.android.data

import app.echo.android.model.library.*
import java.io.IOException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SubsonicScrobbleOutboxTest {
    @get:Rule val folder = TemporaryFolder()
    private val endpoint = SubsonicEndpoint("https://music.example", "listener", "password-not-stored")

    @Test fun uncertainDeliverySurvivesRestartAndNeverAutomaticallyReplays() {
        var sent = 0
        val directory = folder.newFolder()
        SubsonicScrobbleOutbox(directory) { _, _, _ -> sent++; throw IOException("response lost") }.submit(endpoint, "song", 1234)
        val restarted = SubsonicScrobbleOutbox(directory) { _, _, _ -> sent++ }
        restarted.submit(endpoint, "song", 1234)
        restarted.flushPending(endpoint)
        assertEquals(1, sent)
        val receipt = restarted.receipts(endpoint.sourceId).single()
        assertEquals(NavidromeReceiptState.Unknown, receipt.state)
        assertFalse(directory.listFiles()!!.any { it.readText().contains(endpoint.password) })
        restarted.resolve(endpoint, receipt.key, NavidromeReceiptResolution.Retry)
        assertEquals(2, sent)
        assertTrue(restarted.receipts(endpoint.sourceId).isEmpty())
        restarted.submit(endpoint, "song", 1234)
        assertEquals(2, sent)
    }

    @Test fun receiptsAndManualResolutionStayScopedToTheirAccount() {
        val directory = folder.newFolder()
        val outbox = SubsonicScrobbleOutbox(directory) { _, _, _ -> throw SubsonicApiException(50, "denied") }
        outbox.submit(endpoint, "song", 1234)
        val receipt = outbox.receipts(endpoint.sourceId).single()
        val other = SubsonicEndpoint(endpoint.baseUrl, "other", "other-password")
        assertTrue(outbox.receipts(other.sourceId).isEmpty())
        outbox.resolve(other, receipt.key, NavidromeReceiptResolution.Ignore)
        assertEquals(NavidromeReceiptState.Rejected, outbox.receipts(endpoint.sourceId).single().state)
        outbox.resolve(endpoint, receipt.key, NavidromeReceiptResolution.Confirmed)
        assertTrue(outbox.receipts(endpoint.sourceId).isEmpty())
    }
}
