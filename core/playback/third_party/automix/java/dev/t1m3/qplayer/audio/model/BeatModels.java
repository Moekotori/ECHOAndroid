package dev.t1m3.qplayer.audio.model;

import dev.t1m3.qplayer.audio.core.AudioBuf;
import dev.t1m3.qplayer.audio.core.ChannelLayout;
import dev.t1m3.qplayer.audio.dsp.Resampler;

public final class BeatModels {
    public static final String PREFERRED_ID = "beat_this";

    public static final double BUDGET_SECS = 10.0;

    public interface Opener {
        BeatDetector open(String label, java.io.File path, MelParams params, PeakParams peaks);
    }

    private static volatile Opener opener;

    private BeatModels() {}

    public static void install(Opener o) {
        opener = o;
    }

    public static Opener opener() {
        return opener;
    }

    public static boolean isInstalled() {
        return opener != null;
    }

    public static void uninstall() {
        opener = null;
    }

    public static BeatDetector open(String id) {
        Opener o = opener;
        if (o == null) {
            throw ModelException.notAvailable(
                    "no inference backend installed: the host must call BeatModels.install(...) at"
                        + " startup");
        }
        throw ModelException.notAvailable("this build ships no model store");
    }

    public static boolean isAvailable(String id) {
        return false;
    }

    public static BeatDetector openPreferred() {
        return open(PREFERRED_ID);
    }

    public static float[] resampleMono(float[] pcm, int inRate, int outRate) {
        if (inRate == outRate || pcm.length == 0) {
            return pcm;
        }
        if (inRate <= 0 || outRate <= 0) {
            throw new IllegalArgumentException(
                    "sample rate must be greater than 0: " + inRate + " → " + outRate);
        }

        final int chunk = 4096;

        Resampler rs = new Resampler(inRate, outRate, 1);
        AudioBuf in = new AudioBuf(ChannelLayout.MONO, chunk);
        AudioBuf out = new AudioBuf(ChannelLayout.MONO, chunk);

        long expected = (long) ((double) pcm.length * (double) outRate / (double) inRate) + 64L;
        float[] result = new float[(int) Math.min(expected, (long) Integer.MAX_VALUE - 8)];
        int len = 0;

        int offset = 0;
        while (offset < pcm.length) {
            int n = Math.min(chunk, pcm.length - offset);
            in.setFrames(n);
            for (int i = 0; i < n; i++) {
                in.channelWriter(0).put(i, pcm[offset + i]);
            }
            offset += n;

            int fed = 0;
            while (fed < n) {
                int got = rs.pushFrom(in, fed);
                if (got == 0) {
                    int produced = rs.pull(out);
                    if (produced == 0) {
                        throw new IllegalStateException(
                                "resampler is stuck: cannot feed or pull (already "
                                        + len
                                        + " frames out)");
                    }
                    result = growInto(result, len, out, produced);
                    len += produced;
                    continue;
                }
                fed += got;
                int produced = rs.pull(out);
                if (produced > 0) {
                    result = growInto(result, len, out, produced);
                    len += produced;
                }
            }
        }

        while (true) {
            int produced = rs.drain(out);
            if (produced == 0) {
                break;
            }
            result = growInto(result, len, out, produced);
            len += produced;
        }

        return java.util.Arrays.copyOf(result, len);
    }

    static float[] growInto(float[] dst, int len, AudioBuf out, int n) {
        if (len + n > dst.length) {
            long grown = Math.max((long) dst.length * 2L, (long) len + n);
            dst = java.util.Arrays.copyOf(dst, (int) Math.min(grown, (long) Integer.MAX_VALUE - 8));
        }
        java.nio.FloatBuffer src = out.channel(0);
        for (int i = 0; i < n; i++) {
            dst[len + i] = src.get(i);
        }
        return dst;
    }
}
