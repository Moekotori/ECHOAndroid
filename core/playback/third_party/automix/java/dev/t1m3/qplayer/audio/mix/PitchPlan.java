package dev.t1m3.qplayer.audio.mix;

public final class PitchPlan {

    public static final PitchPlan ZERO = new PitchPlan(0f, 0f);

    public final float outro;

    public final float intro;

    public PitchPlan(float outro, float intro) {
        this.outro = outro;
        this.intro = intro;
    }

    public boolean isZero() {
        return Math.abs(outro) < 1e-6f && Math.abs(intro) < 1e-6f;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PitchPlan)) return false;
        PitchPlan p = (PitchPlan) o;
        return Float.compare(p.outro, outro) == 0 && Float.compare(p.intro, intro) == 0;
    }

    @Override
    public int hashCode() {
        return 31 * Float.floatToIntBits(outro) + Float.floatToIntBits(intro);
    }

    @Override
    public String toString() {
        return "PitchPlan{outro=" + outro + ", intro=" + intro + '}';
    }
}
