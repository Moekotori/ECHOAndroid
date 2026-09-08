package androidx.media3.decoder.ffmpeg;

import static org.junit.Assert.*;

import android.os.SystemClock;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.decoder.SimpleDecoderOutputBuffer;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.nio.ByteOrder;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Optional device smoke test: real JNI loading and known PCM samples, no audio device required. */
@RunWith(AndroidJUnit4.class)
public class FfmpegBackendSmokeTest {
  @Test
  public void bundledDecoderProducesKnownSamplesAndCanFlush() throws Exception {
    assertTrue("Packaged FFmpeg JNI must load", FfmpegLibrary.isAvailable());
    for (String mime : new String[] {MimeTypes.AUDIO_FLAC, MimeTypes.AUDIO_ALAC,
        MimeTypes.AUDIO_AAC, MimeTypes.AUDIO_MPEG, MimeTypes.AUDIO_OPUS}) {
      assertTrue("Missing decoder: " + mime, FfmpegLibrary.supportsFormat(mime));
    }
    Format format = new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_MLAW)
        .setSampleRate(8000).setChannelCount(1).build();
    for (boolean useFloat : new boolean[] {false, true}) {
      FfmpegAudioDecoder decoder = new FfmpegAudioDecoder(format, 2, 2, 64, useFloat);
      try {
        checkPacket(decoder, useFloat);
        decoder.flush();
        checkPacket(decoder, useFloat);
      } finally {
        decoder.release();
      }
    }
  }

  private static void checkPacket(FfmpegAudioDecoder decoder, boolean useFloat) throws Exception {
    long deadline = SystemClock.elapsedRealtime() + 3000;
    DecoderInputBuffer input;
    while ((input = decoder.dequeueInputBuffer()) == null) {
      assertTrue("Timed out waiting for input", SystemClock.elapsedRealtime() < deadline);
      SystemClock.sleep(5);
    }
    input.ensureSpaceForWrite(4);
    input.data.put(new byte[] {(byte) 0xff, 0x00, (byte) 0x80, 0x7f});
    input.flip();
    decoder.queueInputBuffer(input);
    SimpleDecoderOutputBuffer output;
    while ((output = decoder.dequeueOutputBuffer()) == null) {
      assertTrue("Timed out waiting for PCM", SystemClock.elapsedRealtime() < deadline);
      SystemClock.sleep(5);
    }
    try {
      assertEquals(8000, decoder.getSampleRate());
      assertEquals(1, decoder.getChannelCount());
      output.data.order(ByteOrder.LITTLE_ENDIAN);
      assertEquals(useFloat ? 16 : 8, output.data.remaining());
      for (short sample : new short[] {0, -32124, 32124, 0}) {
        if (useFloat) {
          assertEquals(sample / 32768f, output.data.getFloat(), 0.000001f);
        } else {
          assertEquals(sample, output.data.getShort());
        }
      }
    } finally {
      output.release();
    }
  }
}
