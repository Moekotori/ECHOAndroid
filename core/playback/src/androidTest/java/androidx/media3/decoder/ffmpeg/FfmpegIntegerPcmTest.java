package androidx.media3.decoder.ffmpeg;

import static org.junit.Assert.*;
import android.os.SystemClock;
import android.util.Base64;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.decoder.SimpleDecoderOutputBuffer;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import app.echo.android.usbaudio.UsbBitPerfectPacker;
import java.nio.ByteOrder;
import java.util.Collections;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class FfmpegIntegerPcmTest {
  @Test public void losslessFixturesRemainExactThroughDecodeFlushAndUsbPacking() throws Exception {
    byte[] json;
    try (java.io.InputStream input = InstrumentationRegistry.getInstrumentation().getContext()
        .getAssets().open("bitperfect-codecs.json")) { json = input.readAllBytes(); }
    JSONArray fixtures = new JSONArray(new String(json, java.nio.charset.StandardCharsets.UTF_8));
    for (int f = 0; f < fixtures.length(); f++) {
      JSONObject fixture = fixtures.getJSONObject(f);
      int bits = fixture.getInt("bits");
      Format format = new Format.Builder().setSampleMimeType(fixture.getString("codec").equals("flac")
          ? MimeTypes.AUDIO_FLAC : MimeTypes.AUDIO_ALAC).setSampleRate(44100).setChannelCount(1)
          .setInitializationData(Collections.singletonList(Base64.decode(fixture.getString("extra"), Base64.DEFAULT))).build();
      FfmpegAudioDecoder decoder = new FfmpegAudioDecoder(format, 2, 2, 65536, C.ENCODING_PCM_32BIT);
      try {
        for (int pass = 0; pass < 2; pass++) {
          if (pass != 0) decoder.flush();
          long deadline = SystemClock.elapsedRealtime() + 3000;
          DecoderInputBuffer input;
          while ((input = decoder.dequeueInputBuffer()) == null) {
            assertTrue(SystemClock.elapsedRealtime() < deadline); SystemClock.sleep(5);
          }
          byte[] packet = Base64.decode(fixture.getString("packet"), Base64.DEFAULT);
          input.ensureSpaceForWrite(packet.length); input.data.put(packet); input.flip(); decoder.queueInputBuffer(input);
          SimpleDecoderOutputBuffer output;
          while ((output = decoder.dequeueOutputBuffer()) == null) {
            assertTrue(SystemClock.elapsedRealtime() < deadline); SystemClock.sleep(5);
          }
          try {
            assertEquals(bits, decoder.getSourceBitDepth());
            assertEquals(44100, decoder.getSampleRate());
            JSONArray expected = fixture.getJSONArray("samples");
            output.data.order(ByteOrder.LITTLE_ENDIAN);
            assertEquals(expected.length() * 4, output.data.remaining());
            for (int i = 0; i < expected.length(); i++) {
              assertEquals(expected.getInt(i) << (32 - bits), output.data.getInt());
            }
            output.data.rewind();
            byte[] usb = new byte[expected.length() * (bits / 8)];
            UsbBitPerfectPacker.INSTANCE.pack(output.data, 4, bits, false, bits, bits / 8, usb, 1);
            for (int i = 0; i < expected.length(); i++) {
              for (int b = 0; b < bits / 8; b++) {
                assertEquals((byte)(expected.getInt(i) >> (b * 8)), usb[i * (bits / 8) + b]);
              }
            }
          } finally { output.release(); }
        }
      } finally { decoder.release(); }
    }
  }
}
