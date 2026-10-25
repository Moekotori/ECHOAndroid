package dev.t1m3.qplayer.audio.mix;

import dev.t1m3.qplayer.audio.core.AudioBuf;
import dev.t1m3.qplayer.audio.core.ChannelLayout;
import dev.t1m3.qplayer.audio.dsp.Resampler;
import dev.t1m3.qplayer.audio.core.AudioException;
import dev.t1m3.qplayer.audio.decode.Decoder;
import dev.t1m3.qplayer.audio.decode.TrackSpec;
import dev.t1m3.qplayer.audio.dsp.PitchShifter;
import dev.t1m3.qplayer.util.Logger;

import java.nio.FloatBuffer;
import java.util.Arrays;

public final class Automix {

    public static final int ANALYSIS_DECODE_FRAMES = 65_536;

    public static final double ANALYSIS_SECS = 12.0;

    public static final float OUTRO_PITCH_GLIDE_END = 0.25f;

    public static final float INTRO_PITCH_GLIDE_START = 0.70f;

    private static final float FRAC_PI_2 = (float) (Math.PI / 2.0);

    private static final float FRAC_PI_4 = (float) (Math.PI / 4.0);

    private Automix() {
    }

    public static long fallbackWindow(MixStyle style, int rate) {
        return fallbackWindowWith(style, rate, 1.0f);
    }

    public static long fallbackWindowWith(MixStyle style, int rate, float scale) {
        float s = scale > 0.0f ? scale : 1.0f;
        float frames = style.fallbackSecs() * s * (float) Math.max(rate, 1);
        return Math.max((long) Math.round(frames), 1L);
    }

    public static long limitWindow(long idealFrames, double songSecs, int rate) {
        return limitWindowWith(idealFrames, songSecs, rate, MixTuning.MAX_WINDOW_FRACTION);
    }

    public static long limitWindowWith(long idealFrames, double songSecs, int rate, double fraction) {
        long ideal = Math.max(idealFrames, 1L);
        double f = fraction > 0.0 ? fraction : MixTuning.MAX_WINDOW_FRACTION;

        if (!(songSecs > 0.0) || rate == 0) {

            return ideal;
        }

        long cap = Math.round(songSecs * f * (double) rate);
        return Math.min(ideal, Math.max(cap, 1L));
    }

    public static float introPitchGlide(float progress) {
        return 1.0f - smoothstep(INTRO_PITCH_GLIDE_START, 1.0f, progress);
    }

    public static float[][] readStereoWindow(String source, java.util.Map<String, String> headers,
                                             double startSecs, int windowFrames, int outRate) {
        int srcRate;
        boolean mono;
        float[] left;
        float[] right;
        Decoder dec = headers == null || headers.isEmpty()
                ? Decoder.open(source) : Decoder.open(source, headers);
        try {
            TrackSpec spec = dec.spec();
            srcRate = Math.max(spec.sampleRate, 1);
            mono = spec.layout.count() < 2;

            long wantSrc = Math.min(
                    (long) Math.ceil((double) windowFrames * (double) srcRate / (double) Math.max(outRate, 1))
                            + (long) RESAMPLE_MARGIN_FRAMES,
                    (long) Integer.MAX_VALUE - 8);
            if (startSecs > 1e-9) {
                dec.seek(startSecs);
            }

            int cap = Math.min(Automix.ANALYSIS_DECODE_FRAMES,
                    (int) Math.min(wantSrc + 4096L, Integer.MAX_VALUE - 8));
            AudioBuf buf = new AudioBuf(spec.layout, Math.max(cap, 1));
            left = new float[(int) wantSrc];
            right = new float[(int) wantSrc];
            int len = 0;
            while ((long) len < wantSrc) {
                int n = dec.decodeInto(buf);
                if (n == 0) {
                    break;
                }
                if ((long) len + n > left.length) {
                    long grown = Math.min(Math.max((long) left.length * 2L, (long) len + n),
                            (long) Integer.MAX_VALUE - 8);
                    left = Arrays.copyOf(left, (int) grown);
                    right = Arrays.copyOf(right, (int) grown);
                }

                FloatBuffer l = buf.channel(0);
                FloatBuffer r = buf.channel(mono ? 0 : Math.min(1, buf.channels() - 1));
                for (int i = 0; i < n; i++) {
                    left[len + i] = l.get(i);
                    right[len + i] = r.get(i);
                }
                len += n;
            }
            if (len < left.length) {
                left = Arrays.copyOf(left, len);
                right = Arrays.copyOf(right, len);
            }
        } finally {
            dec.close();
        }
        left = resampleMono(left, srcRate, outRate, windowFrames);

        right = mono ? left : resampleMono(right, srcRate, outRate, windowFrames);
        return new float[][]{left, right};
    }

    static float[] resampleMono(float[] src, int srcRate, int outRate, int wantFrames) {

        if (srcRate == outRate) {
            return Arrays.copyOf(src, Math.min(src.length, wantFrames));
        }
        float[] resampled = dev.t1m3.qplayer.audio.model.BeatModels.resampleMono(
                src, srcRate, outRate);

        return Arrays.copyOf(resampled, Math.min(resampled.length, Math.max(wantFrames, 0)));
    }

    private static int copyOut(int produced, float[] dst, int at, int limit, AudioBuf src) {
        int n = Math.min(Math.min(produced, src.frames()), limit - at);
        if (n <= 0) {
            return 0;
        }
        FloatBuffer r = src.channel(0);
        for (int i = 0; i < n; i++) {
            dst[at + i] = r.get(i);
        }
        return n;
    }

    public static PitchShifter newPitchShifter(int channels, int rate, float semitones, int chunk) {
        return new PitchShifter(channels, rate, semitones, chunk);
    }

    static float togetherAngle(float p, float together) {
        float t = clampF32(together, 0.0f, 0.9f);
        float x = clampF32(p, 0.0f, 1.0f);

        float arc = (1.0f - t) * 0.5f;
        if (arc <= 0.0f) {
            return x * FRAC_PI_2;
        }
        if (x < arc) {
            return (x / arc) * FRAC_PI_4;
        }
        if (x < arc + t) {
            return FRAC_PI_4;
        }
        return FRAC_PI_4 + ((x - arc - t) / arc) * FRAC_PI_4;
    }

    static float smoothstep(float edge0, float edge1, float x) {
        if (edge1 <= edge0) {
            return x < edge0 ? 0.0f : 1.0f;
        }
        float t = clampF32((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private static final int RESAMPLE_MARGIN_FRAMES = 128;

    private static final int DECODE_CHUNK_FRAMES = 4096;

    static Mono readMono(String source, boolean tail, double secs) {
        return readMono(source, null, tail, secs);
    }

    static Mono readMono(String source, java.util.Map<String, String> headers, boolean tail, double secs) {
        boolean noHeaders = headers == null || headers.isEmpty();
        Decoder dec = noHeaders
                ? Decoder.open(source)
                : Decoder.open(source, headers);
        try {
            TrackSpec spec = dec.spec();
            int rate = Math.max(spec.sampleRate, 1);
            double wantSecs = secs > 0.0 ? secs : 0.0;

            long want = Math.min((long) ((double) rate * wantSecs), (long) Integer.MAX_VALUE - 8);

            if (tail) {
                Double dur = spec.durationSecs();
                if (dur == null) {
                    throw AudioException.config(
                            "file duration unknown (no length header, or streaming input), cannot locate the tail "
                                    + fileStem(source));
                }
                double start = dur.doubleValue() - secs;
                if (start < 0.0) {
                    start = 0.0;
                }
                if (start > 0.0) {
                    dec.seek(start);
                }
            }

            long hint = Math.min(want, 1L << 22) + 4096L;
            float[] pcm = new float[(int) Math.min(hint, (long) Integer.MAX_VALUE - 8)];
            int len = 0;
            AudioBuf buf = new AudioBuf(spec.layout, ANALYSIS_DECODE_FRAMES);

            while ((long) len < want) {
                int n = dec.decodeInto(buf);
                if (n == 0) {
                    break;
                }
                if ((long) len + n > pcm.length) {
                    long grown = Math.max((long) pcm.length * 2L, (long) len + n);
                    pcm = Arrays.copyOf(pcm, (int) Math.min(grown, (long) Integer.MAX_VALUE - 8));
                }
                int chs = Math.max(buf.channels(), 1);

                float[] backing = buf.backingArray();
                int[] chanBase = new int[chs];
                for (int c = 0; c < chs; c++) {
                    chanBase[c] = buf.channelBase(c);
                }
                for (int i = 0; i < n; i++) {
                    float s = 0.0f;
                    for (int c = 0; c < chs; c++) {
                        s += backing[chanBase[c] + i];
                    }
                    pcm[len + i] = s / (float) chs;
                }
                len += n;
            }

            int kept = (int) Math.min((long) len, want);
            float[] out = kept == pcm.length ? pcm : Arrays.copyOf(pcm, kept);
            return new Mono(out, rate, spec.durationSecs());
        } finally {
            dec.close();
        }
    }

    public static final class Mono {

        public final float[] pcm;

        public final int rate;

        public final Double durationSecs;

        Mono(float[] pcm, int rate, Double durationSecs) {
            this.pcm = pcm;
            this.rate = rate;
            this.durationSecs = durationSecs;
        }

        @Override
        public String toString() {
            return "Mono { pcm: [" + pcm.length + " samples], rate: " + rate
                    + ", duration_secs: " + durationSecs + " }";
        }
    }

    static float clampF32(float v, float lo, float hi) {
        if (v < lo) {
            return lo;
        }
        if (v > hi) {
            return hi;
        }
        return v;
    }

    static float minF32(float a, float b) {
        if (Float.isNaN(a)) {
            return b;
        }
        if (Float.isNaN(b)) {
            return a;
        }
        return Math.min(a, b);
    }

    static float maxF32(float a, float b) {
        if (Float.isNaN(a)) {
            return b;
        }
        if (Float.isNaN(b)) {
            return a;
        }
        return Math.max(a, b);
    }

    private static String fileStem(String source) {
        if (source == null) {
            return "null";
        }
        int slash = Math.max(source.lastIndexOf('/'), source.lastIndexOf('\\'));
        String name = slash >= 0 ? source.substring(slash + 1) : source;
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
