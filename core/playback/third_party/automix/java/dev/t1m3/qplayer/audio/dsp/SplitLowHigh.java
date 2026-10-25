package dev.t1m3.qplayer.audio.dsp;

public final class SplitLowHigh {

    private final Biquad lp;

    public SplitLowHigh(int rate, float crossoverHz) {
        this.lp = Biquad.lowPass(rate, crossoverHz, 0.5f);
    }

    public void reset() {
        lp.reset();
    }

    public SplitLowHigh copy() {

        return new SplitLowHigh(this);
    }

    private SplitLowHigh(SplitLowHigh other) {
        this.lp = other.lp.copy();
    }

    public Split process(float x) {
        float low = lp.process(x);
        return new Split(low, x - low);
    }

    public float[] processInto(float x, float[] out) {
        if (out == null || out.length < 2) {
            throw new IllegalArgumentException("split needs an array of length >= 2, got "
                    + (out == null ? "null" : String.valueOf(out.length)));
        }
        float low = lp.process(x);
        out[0] = low;
        out[1] = x - low;
        return out;
    }

    public static final class Split {

        public final float low;

        public final float high;

        private Split(float low, float high) {
            this.low = low;
            this.high = high;
        }

        @Override
        public String toString() {
            return "(" + low + ", " + high + ")";
        }
    }

    @Override
    public String toString() {
        return "SplitLowHigh{lp=" + lp + '}';
    }
}
