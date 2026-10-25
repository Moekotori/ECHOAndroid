package dev.t1m3.qplayer.audio.mix;

import java.util.Locale;

public final class FlowFeatures {

    public final float tempo;

    public final float headTempo;

    public final float tailTempo;

    public final float level;

    public final float headLevel;

    public final float tailLevel;

    public final float tone;

    public final double seconds;

    public FlowFeatures(float tempo, float headTempo, float tailTempo,
                        float level, float headLevel, float tailLevel,
                        float tone, double seconds) {
        this.tempo = tempo;
        this.headTempo = headTempo;
        this.tailTempo = tailTempo;
        this.level = level;
        this.headLevel = headLevel;
        this.tailLevel = tailLevel;
        this.tone = tone;
        this.seconds = seconds;
    }

    public boolean hasTempo() {
        return tempo > 0.0f;
    }

    public float outgoingTempo() {
        return tailTempo > 0.0f ? tailTempo : 0.0f;
    }

    public float incomingTempo() {
        return headTempo > 0.0f ? headTempo : 0.0f;
    }

    public float outgoingLevel() {
        return tailLevel;
    }

    public float incomingLevel() {
        return headLevel;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT,
                "FlowFeatures { tempo %.1f (head %.1f / tail %.1f), loudness %.4f (head %.4f / tail %s), "
                        + "centroid %.0f Hz, duration %.1fs }",
                tempo, headTempo, tailTempo, level, headLevel,
                Float.isNaN(tailLevel) ? "none" : String.format(Locale.ROOT, "%.4f", tailLevel),
                tone, seconds);
    }
}
