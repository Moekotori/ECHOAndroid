package dev.t1m3.qplayer.audio.mix;

public final class AppleRateMap {

    private AppleRateMap() {
    }

    public static final class Endpoints {
        public final float outStart;
        public final float outEnd;
        public final float inStart;
        public final float inEnd;
        public final float ratio;

        Endpoints(float outStart, float outEnd, float inStart, float inEnd, float ratio) {
            this.outStart = outStart;
            this.outEnd = outEnd;
            this.inStart = inStart;
            this.inEnd = inEnd;
            this.ratio = ratio;
        }
    }

    public static Endpoints endpoints(double outgoingLen, double incomingLen) {
        double ratio = (outgoingLen > 0.0 && incomingLen > 0.0)
                ? outgoingLen / incomingLen : 1.0;
        return new Endpoints(1f, (float) ratio, (float) (1.0 / ratio), 1f, (float) ratio);
    }

    public static double elapsed(double t, double r0, double r1, double length) {
        if (length <= 0.0) {
            return t * ((r0 + r1) * 0.5);
        }
        double slope = (r1 - r0) / length;
        if (Math.abs(slope) < NEAR_CONSTANT_RATE_SLOPE) {
            return t * ((r0 + r1) * 0.5);
        }
        return (Math.exp(Math.log(r0) + slope * t) - r0) / slope;
    }

    static final double NEAR_CONSTANT_RATE_SLOPE = 1e-6;
}
