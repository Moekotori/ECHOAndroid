package dev.t1m3.qplayer.audio.mix;

public final class LevelMatcher {

    static final float POWER_TAU_SECS = 0.4f;

    static final float GAIN_TAU_SECS = 0.25f;

    static final float MAX_GAIN_DB = 6.0f;

    static final float MAX_GAIN = (float) Math.pow(10.0, MAX_GAIN_DB / 20.0);

    static final float MIN_GAIN = 1.0f / MAX_GAIN;

    private static final float SILENT_POWER = 1e-10f;

    private final float powerAlpha;
    private final float gainAlpha;

    private float powerA;
    private float powerB;
    private float gain = 1.0f;
    private float lastGain = 1.0f;

    public LevelMatcher(int rate) {
        this.powerAlpha = alphaFor(POWER_TAU_SECS, rate);
        this.gainAlpha = alphaFor(GAIN_TAU_SECS, rate);
    }

    private static float alphaFor(float tauSecs, int rate) {
        if (rate <= 0) {
            return 1.0f;
        }
        double a = 1.0 - Math.exp(-1.0 / ((double) tauSecs * rate));
        return (float) Math.max(0.0, Math.min(1.0, a));
    }

    public void reset() {
        powerA = 0.0f;
        powerB = 0.0f;
        gain = 1.0f;
        lastGain = 1.0f;
    }

    public float gainForFrame(float framePowerA, float framePowerB, float strength) {
        powerA += (framePowerA - powerA) * powerAlpha;
        powerB += (framePowerB - powerB) * powerAlpha;

        float target = 1.0f;
        if (powerB > SILENT_POWER && powerA > SILENT_POWER) {
            target = (float) Math.sqrt((double) (powerA / powerB));
            if (target > MAX_GAIN) {
                target = MAX_GAIN;
            } else if (target < MIN_GAIN) {
                target = MIN_GAIN;
            }
        }
        gain += (target - gain) * gainAlpha;

        float s = strength;
        if (s <= 0.0f) {
            lastGain = 1.0f;
            return lastGain;
        }
        if (s > 1.0f) {
            s = 1.0f;
        }
        lastGain = 1.0f + (gain - 1.0f) * s;
        return lastGain;
    }

    public float[] buffer(int frames) {
        if (gainBuffer == null || gainBuffer.length < frames) {
            gainBuffer = new float[Math.max(frames, 1024)];
        }
        return gainBuffer;
    }

    private float[] gainBuffer;

    public float lastGain() {
        return lastGain;
    }
}
