package dev.t1m3.qplayer.audio.dsp;

public final class BandLevels {

    public static final int BANDS = 3;

    private static final float LOW_CROSSOVER_HZ = 200f;

    private static final float MID_CROSSOVER_HZ = 2_000f;

    private static final float FLOOR_DB = -60f;

    private static final float ATTACK = 0.55f;
    private static final float RELEASE = 0.12f;

    private final OnePole lowPass;
    private final OnePole midPass;
    private final float[] smoothed = new float[BANDS];

    public BandLevels(int rate) {
        this.lowPass = OnePole.lowPass(rate, LOW_CROSSOVER_HZ);
        this.midPass = OnePole.lowPass(rate, MID_CROSSOVER_HZ);
    }

    public void reset() {
        lowPass.reset();
        midPass.reset();
        smoothed[0] = 0f;
        smoothed[1] = 0f;
        smoothed[2] = 0f;
    }

    public void process(float[] samples, int length, int channels, float[] out, int offset) {
        if (channels < 1) {
            throw new IllegalArgumentException("channel count must be >= 1: " + channels);
        }
        double energyLow = 0.0;
        double energyMid = 0.0;
        double energyHigh = 0.0;
        int frames = 0;
        for (int i = 0; i + channels <= length; i += channels) {
            float mono = 0f;
            for (int c = 0; c < channels; c++) {
                mono += samples[i + c];
            }
            mono /= channels;
            float low = lowPass.process(mono);
            float upToMid = midPass.process(mono);
            float mid = upToMid - low;
            float high = mono - upToMid;
            energyLow += (double) low * low;
            energyMid += (double) mid * mid;
            energyHigh += (double) high * high;
            frames++;
        }
        if (frames == 0) {
            return;
        }
        smooth(0, toLevel(energyLow / frames));
        smooth(1, toLevel(energyMid / frames));
        smooth(2, toLevel(energyHigh / frames));
        for (int i = 0; i < BANDS; i++) {
            out[offset + i] = smoothed[i];
        }
    }

    private static float toLevel(double meanSquare) {
        double rms = Math.sqrt(meanSquare);
        if (rms <= 1e-7) {
            return 0f;
        }
        double db = 20.0 * Math.log10(rms);
        double normalized = (db - FLOOR_DB) / -FLOOR_DB;
        if (normalized <= 0.0) {
            return 0f;
        }
        return normalized >= 1.0 ? 1f : (float) normalized;
    }

    private void smooth(int band, float value) {
        float previous = smoothed[band];
        float coefficient = value > previous ? ATTACK : RELEASE;
        smoothed[band] = previous + (value - previous) * coefficient;
    }
}
