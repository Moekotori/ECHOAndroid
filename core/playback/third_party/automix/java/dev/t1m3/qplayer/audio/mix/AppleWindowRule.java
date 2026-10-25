package dev.t1m3.qplayer.audio.mix;

public final class AppleWindowRule {

    public static final double[] ALIAS_FACTORS = {0.5, 1.0, 2.0};

    public static final class Result {
        public final double m;
        public final double scaledIncomingLen;
        public final double outgoingEndRate;
        public final double incomingStartRate;
        public final double virtualRampStart;
        public final double virtualRampEnd;

        Result(double m, double scaledIncomingLen, double outgoingEndRate,
               double incomingStartRate, double virtualRampStart, double virtualRampEnd) {
            this.m = m;
            this.scaledIncomingLen = scaledIncomingLen;
            this.outgoingEndRate = outgoingEndRate;
            this.incomingStartRate = incomingStartRate;
            this.virtualRampStart = virtualRampStart;
            this.virtualRampEnd = virtualRampEnd;
        }
    }

    private AppleWindowRule() {
    }

    public static double barSecs(double bpm) {
        return bpm > 0.0 ? 4.0 * 60.0 / bpm : 0.0;
    }

    public static double barAlignedWindow(double windowSecsNow, double bpmA, double bpmB,
                                          int targetBars) {
        if (targetBars <= 0) {
            return windowSecsNow;
        }
        double barA = barSecs(bpmA);
        double barB = barSecs(bpmB);
        if (!(barA > 0.0) || !(barB > 0.0)) {
            return windowSecsNow;
        }
        double lenA = targetBars * barA;
        double lenB = targetBars * barB;
        double len = Math.min(lenA, lenB);
        return Math.max(barA, Math.round(len / barA) * barA);
    }

    public static int barsFor(double windowSecs, double bpm) {
        double bar = barSecs(bpm);
        if (!(bar > 0.0)) {
            return 0;
        }
        return Math.max(1, (int) Math.round(windowSecs / bar));
    }

    public static Result compute(double outgoingLen, double incomingLen,
                                 double outgoingBars, double incomingBars,
                                 int aliasIndex, double incomingStart, double incomingEnd) {
        if (!(outgoingBars > 0.0) || !(incomingBars > 0.0)
                || aliasIndex < 0 || aliasIndex >= ALIAS_FACTORS.length) {
            throw new IllegalArgumentException("bars must be positive and aliasIndex 0..2");
        }
        double alias = ALIAS_FACTORS[aliasIndex];
        double m = alias * incomingBars / outgoingBars;
        double scaled = incomingLen / m;
        boolean positive = outgoingLen > 0.0 && scaled > 0.0;
        double outEnd = positive ? outgoingLen / scaled : 1.0;
        double provisional = positive ? scaled / outgoingLen : 1.0;
        double virtualStart = incomingEnd - scaled;
        double virtualEnd = incomingEnd;
        double inStart;
        if (incomingStart >= virtualEnd) {
            inStart = 1.0;
        } else if (incomingStart <= virtualStart) {
            inStart = provisional;
        } else {
            double progress = (incomingStart - virtualStart) / (virtualEnd - virtualStart);
            inStart = provisional + progress * (1.0 - provisional);
        }
        return new Result(m, scaled, outEnd, inStart, virtualStart, virtualEnd);
    }
}
