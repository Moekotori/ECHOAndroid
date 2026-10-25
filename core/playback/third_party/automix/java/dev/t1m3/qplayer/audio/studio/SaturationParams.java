package dev.t1m3.qplayer.audio.studio;

public final class SaturationParams {
    public enum Curve {
        TANH("tanh soft saturation"),
        SOFT_CLIP("soft clip"),
        DIODE("piecewise (diode)");

        public final String label;

        Curve(String label) {
            this.label = label;
        }

        public static Curve fromIndex(int index) {
            Curve[] all = values();
            return index >= 0 && index < all.length ? all[index] : TANH;
        }

        public static String[] labels() {
            Curve[] all = values();
            String[] out = new String[all.length];
            for (int i = 0; i < all.length; i++) {
                out[i] = all[i].label;
            }
            return out;
        }
    }

    public final boolean enabled;
    public final Curve curve;
    public final double driveDb;
    public final double makeupDb;
    public final double mix;
    public final int oversample;

    private SaturationParams(
            boolean enabled,
            Curve curve,
            double driveDb,
            double makeupDb,
            double mix,
            int oversample) {
        this.enabled = enabled;
        this.curve = curve == null ? Curve.TANH : curve;
        this.driveDb = clamp(driveDb, 0.0, 24.0);
        this.makeupDb = clamp(makeupDb, -24.0, 0.0);
        this.mix = clamp(mix, 0.0, 1.0);
        this.oversample = oversample >= 4 ? 4 : (oversample >= 2 ? 2 : 1);
    }

    public static SaturationParams off() {
        return new SaturationParams(false, Curve.TANH, 6.0, -6.0, 1.0, 2);
    }

    public static SaturationParams of(
            Curve curve, double driveDb, double makeupDb, double mix, int oversample) {
        return new SaturationParams(true, curve, driveDb, makeupDb, mix, oversample);
    }

    public SaturationParams withEnabled(boolean value) {
        return new SaturationParams(value, curve, driveDb, makeupDb, mix, oversample);
    }

    public SaturationParams withCurve(Curve value) {
        return new SaturationParams(enabled, value, driveDb, makeupDb, mix, oversample);
    }

    public SaturationParams withDriveDb(double value) {
        return new SaturationParams(enabled, curve, value, makeupDb, mix, oversample);
    }

    public SaturationParams withMakeupDb(double value) {
        return new SaturationParams(enabled, curve, driveDb, value, mix, oversample);
    }

    public SaturationParams withMix(double value) {
        return new SaturationParams(enabled, curve, driveDb, makeupDb, value, oversample);
    }

    public SaturationParams withOversample(int value) {
        return new SaturationParams(enabled, curve, driveDb, makeupDb, mix, value);
    }

    public boolean active() {
        return enabled && mix > 1e-9 && driveDb > 1e-9;
    }

    public double driveLinear() {
        return Math.pow(10.0, driveDb / 20.0);
    }

    public double makeupLinear() {
        return Math.pow(10.0, makeupDb / 20.0);
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
                "SaturationParams{%s %s, drive %.1f dB, makeup %.1f dB, dry/wet %.2f, oversample"
                    + " %d×}",
                enabled ? "on" : "off",
                curve.label,
                Double.valueOf(driveDb),
                Double.valueOf(makeupDb),
                Double.valueOf(mix),
                Integer.valueOf(oversample));
    }
}
