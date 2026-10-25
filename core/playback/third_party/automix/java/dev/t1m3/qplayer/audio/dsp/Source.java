package dev.t1m3.qplayer.audio.dsp;

import java.util.Locale;

public final class Source {

    private static final float HEAD_RADIUS_M = 0.0875f;

    private static final float SPEED_OF_SOUND = 343.0f;

    private static final float MAX_SHADOW_DB = 6.0f;

    private static final float SHADOW_CUTOFF_CLEAR = 20_000.0f;

    private static final float SHADOW_CUTOFF_BLOCKED = 3_000.0f;

    private static final float PI = (float) Math.PI;

    private static final float RADS_PER_DEG = PI / 180.0f;

    public final float azimuth;

    public final boolean nonDirectional;

    private Source(float azimuth, boolean nonDirectional) {
        this.azimuth = azimuth;
        this.nonDirectional = nonDirectional;
    }

    public static Source at(float azimuth) {
        return new Source(azimuth, false);
    }

    public static Source lfe() {
        return new Source(0.0f, true);
    }

    public float itdSecs() {
        if (nonDirectional) {
            return 0.0f;
        }
        float t = Math.abs(azimuth) % 360.0f;

        t = t > 180.0f ? 360.0f - t : t;
        float deg = t > 90.0f ? 180.0f - t : t;
        float theta = deg * RADS_PER_DEG;
        return (HEAD_RADIUS_M / SPEED_OF_SOUND) * (theta + (float) Math.sin(theta));
    }

    private boolean earIsNear(boolean earIsRight) {
        if (nonDirectional) {
            return true;
        }
        return (azimuth >= 0.0f) == earIsRight;
    }

    public float delaySecs(boolean earIsRight) {
        if (earIsNear(earIsRight)) {
            return 0.0f;
        }
        return itdSecs();
    }

    public float shadow(boolean earIsRight) {
        if (nonDirectional) {
            return 0.0f;
        }
        float theta = azimuth * RADS_PER_DEG;
        float s = (float) Math.sin(theta);
        float p = earIsRight ? s : -s;
        float q = (1.0f - p) / 2.0f;

        if (q < 0.0f) {
            q = 0.0f;
        } else if (q > 1.0f) {
            q = 1.0f;
        }

        return q * q;
    }

    public float gain(boolean earIsRight) {
        return Gain.dbToLinear(-MAX_SHADOW_DB * shadow(earIsRight));
    }

    public float cutoffHz(boolean earIsRight) {
        float s = shadow(earIsRight);
        float clear = (float) Math.pow(SHADOW_CUTOFF_CLEAR, (double) (1.0f - s));
        float blocked = (float) Math.pow(SHADOW_CUTOFF_BLOCKED, (double) s);
        return clear * blocked;
    }

    public String describe() {
        if (nonDirectional) {
            return "LFE (no localisation)";
        }
        return String.format(Locale.ROOT, "%.0f°", Float.valueOf(azimuth));
    }

    @Override
    public String toString() {
        return "Source{azimuth=" + azimuth + ", nonDirectional=" + nonDirectional + '}';
    }
}
