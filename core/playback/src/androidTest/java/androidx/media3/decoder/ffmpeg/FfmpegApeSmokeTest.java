package androidx.media3.decoder.ffmpeg;

import static org.junit.Assert.*;

import android.os.SystemClock;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.decoder.SimpleDecoderOutputBuffer;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tiny synthetic silent APE frame exercises actual JNI setup and multiple PCM blocks. */
@RunWith(AndroidJUnit4.class)
public class FfmpegApeSmokeTest {
  @Test public void apeDecodesAllBlocksAndFlushesAtEachBitDepth() throws Exception {
    assertTrue(FfmpegLibrary.supportsFormat("audio/ape"));
    for (int encoding : new int[] {C.ENCODING_PCM_8BIT, C.ENCODING_PCM_16BIT, C.ENCODING_PCM_24BIT}) {
      Format format = new Format.Builder().setSampleMimeType("audio/ape")
          .setSampleRate(44100).setChannelCount(2).setPcmEncoding(encoding)
          .setInitializationData(Collections.singletonList(new byte[] {(byte) 0x96, 15, (byte) 0xd0, 7, 0, 0}))
          .build();
      FfmpegAudioDecoder decoder = new FfmpegAudioDecoder(format, 2, 2, 64, false);
      try {
        checkSilentFrame(decoder);
        decoder.flush();
        checkSilentFrame(decoder);
      } finally { decoder.release(); }
    }
  }

  private static void checkSilentFrame(FfmpegAudioDecoder decoder) throws Exception {
    long deadline = SystemClock.elapsedRealtime() + 5000;
    DecoderInputBuffer input;
    while ((input = decoder.dequeueInputBuffer()) == null) {
      assertTrue(SystemClock.elapsedRealtime() < deadline);
      SystemClock.sleep(5);
    }
    ByteBuffer packet = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN);
    packet.putInt(32768).putInt(0).putInt(0x80000000).putInt(3).putInt(0);
    input.ensureSpaceForWrite(20);
    input.data.put(packet.array());
    input.flip();
    decoder.queueInputBuffer(input);
    SimpleDecoderOutputBuffer output;
    while ((output = decoder.dequeueOutputBuffer()) == null) {
      assertTrue("Timed out waiting for APE PCM", SystemClock.elapsedRealtime() < deadline);
      SystemClock.sleep(5);
    }
    try {
      assertEquals(44100, decoder.getSampleRate());
      assertEquals(2, decoder.getChannelCount());
      assertEquals(32768 * 2 * 2, output.data.remaining());
      while (output.data.hasRemaining()) assertEquals(0, output.data.get());
    } finally { output.release(); }
  }
}
