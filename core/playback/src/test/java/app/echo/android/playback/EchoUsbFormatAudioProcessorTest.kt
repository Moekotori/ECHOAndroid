package app.echo.android.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import app.echo.android.model.playback.EchoUsbOutputOptions
import app.echo.android.usbaudio.UsbAudioClassVersion
import app.echo.android.usbaudio.UsbAudioDescriptorInfo
import app.echo.android.usbaudio.UsbAudioStreamingFormat
import app.echo.android.usbaudio.UsbEndpointDirection
import app.echo.android.usbaudio.UsbEndpointTransferType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class EchoUsbFormatAudioProcessorTest {
    @Test fun unsignedEightBitPcmWidensBeforeUsbProcessing() {
        val processor = EchoPcm8AudioProcessor()
        val outputFormat = processor.configure(AudioProcessor.AudioFormat(44_100, 1, C.ENCODING_PCM_8BIT))
        assertEquals(C.ENCODING_PCM_16BIT, outputFormat.encoding)
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        processor.queueInput(ByteBuffer.wrap(byteArrayOf(0, 128.toByte(), 255.toByte())))
        val samples = processor.output.order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(listOf(-32768, 0, 32512), List(3) { samples.short.toInt() })
    }

    @Test fun automaticFormatKeepsSupportedPcmAndAdaptsUnsupportedChannelsAndRate() {
        val format = UsbAudioStreamingFormat(1, 1, UsbAudioClassVersion.Uac2,
            formatType = 1, pcmIntegerSupported = true, channelCount = 2, subslotSize = 2, bitResolution = 16,
            endpointDirection = UsbEndpointDirection.Out, endpointTransferType = UsbEndpointTransferType.Isochronous)
        val descriptor = UsbAudioDescriptorInfo(streamingFormats = listOf(format))
        val options = EchoUsbOutputOptions()
        val supported = AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT)
        assertEquals(supported, selectUsbCompatibleFormat(supported, options, descriptor, listOf(48_000)))
        val surround = AudioProcessor.AudioFormat(96_000, 6, C.ENCODING_PCM_24BIT)
        assertEquals(supported, selectUsbCompatibleFormat(surround, options, descriptor, listOf(48_000)))
        val restricted = descriptor.copy(streamingFormats = listOf(format.copy(sampleRates = listOf(48_000))))
        assertEquals(supported, selectUsbCompatibleFormat(surround, options, restricted, emptyList()))
        val manual = options.copy(sampleRateHz = 96_000)
        org.junit.Assert.assertThrows(AudioProcessor.UnhandledAudioFormatException::class.java) {
            selectUsbCompatibleFormat(surround, manual, restricted, emptyList())
        }
    }

    private class Converter(override val available: Boolean = true) : UsbResamplerBackend {
        var closes = 0
        var tailRead = false
        override fun create(inRate: Int, inChannels: Int, inBits: Int, outRate: Int, outChannels: Int, outBits: Int): Long {
            tailRead = false
            return 1L
        }
        override fun convert(handle: Long, input: ByteBuffer?, offset: Int, frames: Int,
            bigEndian: Boolean, output: ByteBuffer, capacity: Int): Int {
            if (input == null && tailRead) return 0
            output.putInt(0, if (input == null) 5678 else 1234)
            if (input == null) tailRead = true
            return 4
        }
        override fun close(handle: Long) { closes++ }
    }

    @Test fun unavailableConverterDoesNotReportAnAcceptedFormat() {
        val events = mutableListOf<app.echo.android.model.playback.EchoUsbConversionDiagnostics?>()
        val processor = EchoUsbFormatAudioProcessor(
            { AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_32BIT) }, Converter(false), events::add)
        try {
            processor.configure(AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_24BIT))
            org.junit.Assert.fail("Unavailable conversion must fail")
        } catch (_: AudioProcessor.UnhandledAudioFormatException) { }
        assertEquals(null, events.last())
    }

    @Test fun diagnosticsTrackNegotiationAndAreClearedAtReset() {
        val events = mutableListOf<app.echo.android.model.playback.EchoUsbConversionDiagnostics?>()
        val processor = EchoUsbFormatAudioProcessor(
            { AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_32BIT) }, Converter(), events::add)
        processor.configure(AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_24BIT))
        val readout = events.last()!!
        assertEquals(44100, readout.input.sampleRateHz)
        assertEquals(48000, readout.output.sampleRateHz)
        assertTrue(readout.resampled)
        assertEquals(app.echo.android.model.playback.EchoPcmPrecisionChange.IntegerWidening, readout.precision)
        processor.reset()
        assertEquals(null, events.last())
    }

    @Test fun eosKeepsReturnedOutputIntactThenDrainsAndFlushReleasesOldContext() {
        val converter = Converter()
        val processor = EchoUsbFormatAudioProcessor({ AudioProcessor.AudioFormat(48000, 1, C.ENCODING_PCM_16BIT) }, converter)
        processor.configure(AudioProcessor.AudioFormat(44100, 1, C.ENCODING_PCM_16BIT))
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        processor.queueInput(ByteBuffer.allocateDirect(4))
        val first = processor.output
        assertEquals(1234, first.getInt(0))
        processor.queueEndOfStream()
        assertEquals(1234, first.getInt(0)) // EOS must not overwrite the buffer still held by the sink.
        assertFalse(processor.isEnded)
        first.position(first.limit())
        val tail = processor.output
        assertEquals(5678, tail.int)
        processor.output
        assertTrue(processor.isEnded)
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        assertEquals(1, converter.closes)
        processor.reset()
        assertEquals(2, converter.closes)
    }

    @Test fun configuringNextTrackDoesNotChangeTheCurrentPcmFrameSize() {
        val target = AudioProcessor.AudioFormat(48_000, 1, C.ENCODING_PCM_16BIT)
        val processor = EchoUsbFormatAudioProcessor({ target }, Converter())
        processor.configure(AudioProcessor.AudioFormat(96_000, 2, C.ENCODING_PCM_24BIT))
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        processor.configure(AudioProcessor.AudioFormat(44_100, 1, C.ENCODING_PCM_16BIT))
        val lastOldFrame = ByteBuffer.allocateDirect(6)
        processor.queueInput(lastOldFrame)
        assertFalse(lastOldFrame.hasRemaining())
        processor.reset()
    }
}
