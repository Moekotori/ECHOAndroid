package dev.t1m3.qplayer.audio.mix;

import dev.t1m3.qplayer.audio.core.AudioException;
import dev.t1m3.qplayer.audio.dsp.Fft;

import java.util.Arrays;
import java.util.Map;

public final class FlowAnalyzer {

    public static final double WHOLE_SECS = 30.0;

    public static final double EDGE_SECS = 15.0;

    static final int TONE_FRAME = 2048;

    static final int TONE_HOP = TONE_FRAME * 2;

    public interface Reader {

        Automix.Mono read(String source, Map<String, String> headers, boolean tail, double secs);
    }

    public static final Reader DECODER = new Reader() {
        @Override
        public Automix.Mono read(String source, Map<String, String> headers, boolean tail, double secs) {
            return Automix.readMono(source, headers, tail, secs);
        }
    };

    private final Reader reader;

    public FlowAnalyzer() {
        this(DECODER);
    }

    public FlowAnalyzer(Reader reader) {
        if (reader == null) {
            throw new IllegalArgumentException("reader must not be null");
        }
        this.reader = reader;
    }

    public FlowFeatures analyze(String source, Map<String, String> headers) {
        Automix.Mono main = reader.read(source, headers, false, WHOLE_SECS);
        int rate = Math.max(main.rate, 1);
        if (main.pcm.length < Analysis.FRAME * 2) {

            throw AudioException.unsupportedFormat(
                    "material too short to analyse (" + main.pcm.length + " samples)");
        }

        int headSamples = (int) Math.min((long) main.pcm.length, (long) ((double) rate * EDGE_SECS));
        float[] head = Arrays.copyOfRange(main.pcm, 0, headSamples);

        Analysis.Tempo wholeTempo = Analysis.analyze(main.pcm, rate);
        Analysis.Tempo headTempo = Analysis.analyze(head, rate);

        float tailTempoBpm = 0.0f;
        float tailLevel = Float.NaN;
        try {
            Automix.Mono tail = reader.read(source, headers, true, EDGE_SECS);
            if (tail.pcm.length >= Analysis.FRAME * 2) {
                Analysis.Tempo t = Analysis.analyze(tail.pcm, Math.max(tail.rate, 1));
                tailTempoBpm = t == null ? 0.0f : t.bpm;
                tailLevel = rms(tail.pcm);
            }
        } catch (AudioException ignored) {

        }

        double duration = main.durationSecs == null
                ? (double) main.pcm.length / (double) rate
                : main.durationSecs.doubleValue();

        return new FlowFeatures(
                wholeTempo == null ? 0.0f : wholeTempo.bpm,
                headTempo == null ? 0.0f : headTempo.bpm,
                tailTempoBpm,
                rms(main.pcm),
                rms(head),
                tailLevel,
                toneOf(main.pcm, rate),
                duration);
    }

    static float rms(float[] pcm) {
        if (pcm == null || pcm.length == 0) {
            return 0.0f;
        }
        double sum = 0.0;
        for (int i = 0; i < pcm.length; i++) {
            double x = (double) pcm[i];
            sum += x * x;
        }
        return (float) Math.sqrt(sum / (double) pcm.length);
    }

    static float toneOf(float[] pcm, int rate) {
        if (pcm == null || pcm.length < TONE_FRAME || rate <= 0) {
            return 0.0f;
        }
        Fft fft = new Fft(TONE_FRAME);
        int bins = TONE_FRAME / 2 + 1;
        float[] window = new float[TONE_FRAME];
        Fft.hann(window);
        float[] re = new float[TONE_FRAME];
        float[] im = new float[TONE_FRAME];
        float[] mag = new float[bins];

        double weighted = 0.0;
        double total = 0.0;
        int frames = 0;
        for (int start = 0; start + TONE_FRAME <= pcm.length; start += TONE_HOP) {
            for (int i = 0; i < TONE_FRAME; i++) {
                re[i] = pcm[start + i] * window[i];
                im[i] = 0.0f;
            }
            fft.forward(re, im);
            fft.magnitude(re, im, mag);
            double frameWeighted = 0.0;
            double frameTotal = 0.0;
            for (int k = 0; k < bins; k++) {
                double m = (double) mag[k];
                frameWeighted += ((double) k * (double) rate / (double) TONE_FRAME) * m;
                frameTotal += m;
            }

            if (frameTotal > 0.0) {
                weighted += frameWeighted;
                total += frameTotal;
                frames++;
            }
        }
        if (frames == 0 || total <= 0.0) {
            return 0.0f;
        }
        return (float) (weighted / total);
    }
}
