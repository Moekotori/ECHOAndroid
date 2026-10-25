package dev.t1m3.qplayer.audio.studio;

public final class DynamicsParams {
    public static final double MAX_RATIO = 20.0;

    public static final double MIN_TIME_MS = 0.1;
    public static final double MAX_TIME_MS = 2000.0;

    public final boolean gateOn;
    public final double gateThresholdDb;
    public final double gateRangeDb;
    public final double gateHoldMs;

    public final boolean compOn;
    public final double compThresholdDb;
    public final double compRatio;
    public final double compKneeDb;
    public final double compAttackMs;
    public final double compReleaseMs;
    public final double compMakeupDb;
    public final boolean compRms;
    public final double compRmsWindowMs;

    public final boolean limiterOn;
    public final double limiterCeilingDb;
    public final double limiterLookaheadMs;
    public final double limiterReleaseMs;

    private DynamicsParams(
            boolean gateOn,
            double gateThresholdDb,
            double gateRangeDb,
            double gateHoldMs,
            boolean compOn,
            double compThresholdDb,
            double compRatio,
            double compKneeDb,
            double compAttackMs,
            double compReleaseMs,
            double compMakeupDb,
            boolean compRms,
            double compRmsWindowMs,
            boolean limiterOn,
            double limiterCeilingDb,
            double limiterLookaheadMs,
            double limiterReleaseMs) {
        this.gateOn = gateOn;
        this.gateThresholdDb = clamp(gateThresholdDb, -80.0, 0.0);
        this.gateRangeDb = clamp(gateRangeDb, -80.0, 0.0);
        this.gateHoldMs = clamp(gateHoldMs, 0.0, MAX_TIME_MS);
        this.compOn = compOn;
        this.compThresholdDb = clamp(compThresholdDb, -60.0, 0.0);
        this.compRatio = clamp(compRatio, 1.0, MAX_RATIO);
        this.compKneeDb = clamp(compKneeDb, 0.0, 24.0);
        this.compAttackMs = clamp(compAttackMs, MIN_TIME_MS, MAX_TIME_MS);
        this.compReleaseMs = clamp(compReleaseMs, MIN_TIME_MS, MAX_TIME_MS);
        this.compMakeupDb = clamp(compMakeupDb, 0.0, 24.0);
        this.compRms = compRms;
        this.compRmsWindowMs = clamp(compRmsWindowMs, 1.0, 200.0);
        this.limiterOn = limiterOn;
        this.limiterCeilingDb = clamp(limiterCeilingDb, -12.0, 0.0);
        this.limiterLookaheadMs = clamp(limiterLookaheadMs, 0.0, 5.0);
        this.limiterReleaseMs = clamp(limiterReleaseMs, MIN_TIME_MS, MAX_TIME_MS);
    }

    public static DynamicsParams off() {
        return new DynamicsParams(
                false, -40.0, -40.0, 50.0, false, -18.0, 3.0, 6.0, 10.0, 120.0, 0.0, false, 20.0,
                false, -1.0, 2.0, 80.0);
    }

    public static Builder builder() {
        DynamicsParams base = off();
        return new Builder(base);
    }

    public static final class Builder {
        private boolean gateOn;
        private double gateThresholdDb;
        private double gateRangeDb;
        private double gateHoldMs;
        private boolean compOn;
        private double compThresholdDb;
        private double compRatio;
        private double compKneeDb;
        private double compAttackMs;
        private double compReleaseMs;
        private double compMakeupDb;
        private boolean compRms;
        private double compRmsWindowMs;
        private boolean limiterOn;
        private double limiterCeilingDb;
        private double limiterLookaheadMs;
        private double limiterReleaseMs;

        private Builder(DynamicsParams base) {
            gateOn = base.gateOn;
            gateThresholdDb = base.gateThresholdDb;
            gateRangeDb = base.gateRangeDb;
            gateHoldMs = base.gateHoldMs;
            compOn = base.compOn;
            compThresholdDb = base.compThresholdDb;
            compRatio = base.compRatio;
            compKneeDb = base.compKneeDb;
            compAttackMs = base.compAttackMs;
            compReleaseMs = base.compReleaseMs;
            compMakeupDb = base.compMakeupDb;
            compRms = base.compRms;
            compRmsWindowMs = base.compRmsWindowMs;
            limiterOn = base.limiterOn;
            limiterCeilingDb = base.limiterCeilingDb;
            limiterLookaheadMs = base.limiterLookaheadMs;
            limiterReleaseMs = base.limiterReleaseMs;
        }

        public Builder gate(boolean on, double thresholdDb, double rangeDb, double holdMs) {
            this.gateOn = on;
            this.gateThresholdDb = thresholdDb;
            this.gateRangeDb = rangeDb;
            this.gateHoldMs = holdMs;
            return this;
        }

        public Builder compressor(
                boolean on,
                double thresholdDb,
                double ratio,
                double kneeDb,
                double attackMs,
                double releaseMs,
                double makeupDb) {
            this.compOn = on;
            this.compThresholdDb = thresholdDb;
            this.compRatio = ratio;
            this.compKneeDb = kneeDb;
            this.compAttackMs = attackMs;
            this.compReleaseMs = releaseMs;
            this.compMakeupDb = makeupDb;
            return this;
        }

        public Builder rmsDetector(boolean on, double windowMs) {
            this.compRms = on;
            this.compRmsWindowMs = windowMs;
            return this;
        }

        public Builder limiter(boolean on, double ceilingDb, double lookaheadMs, double releaseMs) {
            this.limiterOn = on;
            this.limiterCeilingDb = ceilingDb;
            this.limiterLookaheadMs = lookaheadMs;
            this.limiterReleaseMs = releaseMs;
            return this;
        }

        public DynamicsParams build() {
            return new DynamicsParams(
                    gateOn,
                    gateThresholdDb,
                    gateRangeDb,
                    gateHoldMs,
                    compOn,
                    compThresholdDb,
                    compRatio,
                    compKneeDb,
                    compAttackMs,
                    compReleaseMs,
                    compMakeupDb,
                    compRms,
                    compRmsWindowMs,
                    limiterOn,
                    limiterCeilingDb,
                    limiterLookaheadMs,
                    limiterReleaseMs);
        }
    }

    public boolean active() {
        return gateOn && gateRangeDb < -1e-6 || compOn || limiterOn;
    }

    public int lookaheadFrames(int rate) {
        return limiterOn ? (int) Math.round(limiterLookaheadMs / 1000.0 * Math.max(1, rate)) : 0;
    }

    private static double clamp(double v, double min, double max) {
        if (Double.isNaN(v)) {
            return min;
        }
        return Math.max(min, Math.min(max, v));
    }

    @Override
    public String toString() {
        return String.format(
                java.util.Locale.ROOT,
                "DynamicsParams{gate %s %.0f dB/%.0f dB; comp %s %.1f dB %.1f:1 knee %.1f dB "
                        + "%.0f/%.0f ms makeup %.1f dB %s; limit %s %.1f dB lookahead %.1f ms}",
                gateOn ? "on" : "off",
                Double.valueOf(gateThresholdDb),
                Double.valueOf(gateRangeDb),
                compOn ? "on" : "off",
                Double.valueOf(compThresholdDb),
                Double.valueOf(compRatio),
                Double.valueOf(compKneeDb),
                Double.valueOf(compAttackMs),
                Double.valueOf(compReleaseMs),
                Double.valueOf(compMakeupDb),
                compRms ? "RMS" : "peak",
                limiterOn ? "on" : "off",
                Double.valueOf(limiterCeilingDb),
                Double.valueOf(limiterLookaheadMs));
    }
}
