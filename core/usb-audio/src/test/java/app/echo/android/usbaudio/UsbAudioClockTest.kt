package app.echo.android.usbaudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class UsbAudioClockTest {
    private val uac2 = UsbAudioStreamingFormat(1, 1, UsbAudioClassVersion.Uac2,
        acInterfaceNumber = 3, clockSourceIds = listOf(0x12), endpointAddress = 1)

    @Test fun uac2ReadsClockEntityOnItsControlInterface() {
        var calls = 0
        val connection = UsbAudioControlTransfer { type, request, value, index, payload, length, _ ->
            calls++
            assertEquals(0xA1, type)
            assertEquals(0x01, request)
            assertEquals(0x0100, value)
            assertEquals(0x1203, index)
            assertEquals(4, length)
            byteArrayOf(0x80.toByte(), 0xBB.toByte(), 0, 0).copyInto(payload)
            4
        }
        assertEquals(48_000, UsbAudioClock.getSampleRate(connection, uac2))
        assertEquals(1, calls)
    }

    @Test fun uac2SetsThenVerifiesRateUsingUac2Requests() {
        val requests = mutableListOf<Pair<Int, Int>>()
        val connection = UsbAudioControlTransfer { type, request, value, index, payload, length, _ ->
            requests += type to request
            assertEquals(0x0100, value)
            assertEquals(0x1203, index)
            if (request == 0x02) {
                // The device doesn't provide ranges; SET/GET CUR still work.
                -1
            } else {
                assertEquals(0x01, request)
                assertEquals(4, length)
                val rate = byteArrayOf(0x80.toByte(), 0xBB.toByte(), 0, 0)
                if (type == 0x21) org.junit.Assert.assertArrayEquals(rate, payload)
                else rate.copyInto(payload)
                4
            }
        }
        assertTrue(UsbAudioClock.setSampleRate(connection, uac2, 48_000))
        assertEquals(listOf(0xA1 to 0x02, 0x21 to 0x01, 0xA1 to 0x01), requests)
    }

    @Test fun uac2RangeUsesEntityIndexAndRangeRequest() {
        val connection = UsbAudioControlTransfer { type, request, value, index, payload, _, _ ->
            assertEquals(0xA1, type)
            assertEquals(0x02, request)
            assertEquals(0x0100, value)
            assertEquals(0x1203, index)
            byteArrayOf(1, 0, 0x80.toByte(), 0xBB.toByte(), 0, 0,
                0x80.toByte(), 0xBB.toByte(), 0, 0, 0, 0, 0, 0).copyInto(payload)
            14
        }
        assertEquals(listOf(UsbAudioClockRange(48_000, 48_000, 0)),
            UsbAudioClock.getSupportedSampleRates(connection, uac2))
    }

    @Test fun uac1EndpointReadKeepsItsOriginalRequestCode() {
        val requests = mutableListOf<Pair<Int, Int>>()
        val connection = UsbAudioControlTransfer { type, request, _, index, payload, _, _ ->
            requests += type to request
            if (type == 0xA2) {
                assertEquals(0x81, request)
                assertEquals(1, index)
                byteArrayOf(0x80.toByte(), 0xBB.toByte(), 0).copyInto(payload)
                3
            } else -1
        }
        assertEquals(48_000, UsbAudioClock.getSampleRate(connection, uac2.copy(audioClassVersion = UsbAudioClassVersion.Uac1)))
        assertEquals(0xA2 to 0x81, requests.last())
    }

    @Test
    fun rejectsReadbackMismatchEvenWhenSetSucceeded() {
        assertTrue(UsbAudioClock.accepted(48_000, setSucceeded = true, readBackHz = 48_000))
        assertTrue(UsbAudioClock.accepted(48_000, setSucceeded = true, readBackHz = null))
        assertFalse(UsbAudioClock.accepted(48_000, setSucceeded = true, readBackHz = 44_100))
        assertFalse(UsbAudioClock.accepted(48_000, setSucceeded = false, readBackHz = null))
    }

    @Test fun selectedClockIsSetAndReadOnlyAfterStreamingInterfaceIsReapplied() {
        val selected = uac2.copy(clockSourceIds = listOf(0x20),
            clockSelectorSources = mapOf(0x20 to listOf(0x11, 0x12)))
        val events = mutableListOf<String>()
        var activated = false
        val connection = UsbAudioControlTransfer { type, request, _, index, payload, length, _ ->
            if (index == 0x2003) {
                assertEquals(0xA1, type)
                assertEquals(1, length)
                events += "selector"
                payload[0] = 2
                1
            } else {
                // Never address inactive source 0x11 or send a frequency to selector 0x20.
                assertEquals(0x1203, index)
                when {
                    request == 2 -> { events += "range"; -1 }
                    type == 0x21 -> { events += "set"; 4 }
                    else -> {
                        events += "read"
                        assertTrue(activated)
                        byteArrayOf(0x44, 0xAC.toByte(), 0, 0).copyInto(payload)
                        4
                    }
                }
            }
        }
        val result = UsbAudioClock.configureSampleRate(connection, selected, 44_100) {
            events += "interface"
            activated = true
        }
        assertTrue(result.verified)
        assertEquals(0x12, result.clockId)
        assertEquals(listOf("selector", "range", "set", "interface", "read"), events)
    }

    @Test fun missingOrMismatchedReadbackCannotVerifyStrictOutput() {
        listOf<Int?>(null, 48_000).forEach { rate ->
            val connection = UsbAudioControlTransfer { type, request, _, _, payload, _, _ ->
                when {
                    request == 2 -> -1
                    type == 0x21 -> 4
                    rate == null -> -1
                    else -> { byteArrayOf(0x80.toByte(), 0xBB.toByte(), 0, 0).copyInto(payload); 4 }
                }
            }
            val result = UsbAudioClock.configureSampleRate(connection, uac2, 44_100)
            assertTrue(result.setSucceeded)
            assertFalse(result.verified)
            assertEquals(rate == null, result.accepted)
        }
    }

    @Test fun unknownAmbiguousOrCyclicClocksAreNeverGuessed() {
        val noTransfers = UsbAudioControlTransfer { _, _, _, _, _, _, _ ->
            throw AssertionError("Must not probe unrelated clocks or endpoints")
        }
        listOf(
            uac2.copy(clockSourceIds = emptyList()),
            uac2.copy(clockSourceIds = listOf(0x11, 0x12)),
            uac2.copy(clockSelectorSources = mapOf(0x12 to listOf(0x12))),
        ).forEach {
            assertNull(UsbAudioClock.getSampleRate(noTransfers, it))
            assertFalse(UsbAudioClock.configureSampleRate(noTransfers, it, 44_100).verified)
        }
    }

    @Test
    fun parsesDiscreteAndContinuousUac2Ranges() {
        val payload = byteArrayOf(
            2, 0,
            0x44, 0xAC.toByte(), 0x00, 0x00,
            0x44, 0xAC.toByte(), 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x77, 0x01, 0x00,
            0x00, 0xEE.toByte(), 0x02, 0x00,
            0x00, 0x77, 0x01, 0x00,
        )
        val ranges = UsbAudioClock.parseRangePayload(payload, payload.size)
        assertEquals(2, ranges.size)
        assertTrue(UsbAudioClock.supportsRate(ranges, 44_100))
        assertFalse(UsbAudioClock.supportsRate(ranges, 48_000))
        assertTrue(UsbAudioClock.supportsRate(ranges, 192_000))
        assertTrue(UsbAudioClock.supportsRate(ranges, 96_000))
        assertFalse(UsbAudioClock.supportsRate(ranges, 88_200))
    }

    @Test
    fun discreteRangeWithZeroResolutionStillMatchesMinAndMax() {
        val range = UsbAudioClockRange(minHz = 48_000, maxHz = 48_000, resolutionHz = 0)
        assertTrue(range.contains(48_000))
        assertFalse(range.contains(96_000))
    }
}
