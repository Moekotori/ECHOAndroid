package dev.t1m3.qplayer.audio.model;

public final class PeakParams {
    public float thresholdRatio;

    public int minGapFrames;

    public int preMax;

    public int postMax;

    public float offset;

    public PeakParams(
            float thresholdRatio, int minGapFrames, int preMax, int postMax, float offset) {
        this.thresholdRatio = thresholdRatio;
        this.minGapFrames = minGapFrames;
        this.preMax = preMax;
        this.postMax = postMax;
        this.offset = offset;
    }

    public PeakParams(PeakParams other) {
        this(other.thresholdRatio, other.minGapFrames, other.preMax, other.postMax, other.offset);
    }

    public static PeakParams defaults() {
        return new PeakParams(0.5f, 3, 3, 3, 0.0f);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PeakParams)) {
            return false;
        }
        PeakParams p = (PeakParams) o;
        return Float.compare(thresholdRatio, p.thresholdRatio) == 0
                && minGapFrames == p.minGapFrames
                && preMax == p.preMax
                && postMax == p.postMax
                && Float.compare(offset, p.offset) == 0;
    }

    @Override
    public int hashCode() {
        int h = Float.floatToIntBits(thresholdRatio);
        h = 31 * h + minGapFrames;
        h = 31 * h + preMax;
        h = 31 * h + postMax;
        h = 31 * h + Float.floatToIntBits(offset);
        return h;
    }

    @Override
    public String toString() {
        return "PeakParams { threshold_ratio: "
                + thresholdRatio
                + ", min_gap_frames: "
                + minGapFrames
                + ", pre_max: "
                + preMax
                + ", post_max: "
                + postMax
                + ", offset: "
                + offset
                + " }";
    }
}
