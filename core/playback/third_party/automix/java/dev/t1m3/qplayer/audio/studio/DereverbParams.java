package dev.t1m3.qplayer.audio.studio;

public final class DereverbParams {
    public static final double MAX_AMOUNT = 1.0;

    public static final double MIN_FLOOR_DB = -30.0;

    public static final double MAX_FLOOR_DB = -6.0;

    public final boolean enabled;
    public final double amount;
    public final double floorDb;
    public final double mix;

    private DereverbParams(boolean enabled, double amount, double floorDb, double mix) {
        this.enabled = enabled;
        double a = Double.isNaN(amount) ? 0.6 : amount;
        this.amount = Math.max(0.0, Math.min(MAX_AMOUNT, a));
        double f = Double.isNaN(floorDb) ? -18.0 : floorDb;
        this.floorDb = Math.max(MIN_FLOOR_DB, Math.min(MAX_FLOOR_DB, f));
        double m = Double.isNaN(mix) ? 1.0 : mix;
        this.mix = Math.max(0.0, Math.min(1.0, m));
    }

    public static DereverbParams off() {
        return new DereverbParams(false, 0.6, -18.0, 1.0);
    }

    public static DereverbParams of(double amount, double floorDb, double mix) {
        return new DereverbParams(true, amount, floorDb, mix);
    }

    public DereverbParams withEnabled(boolean value) {
        return new DereverbParams(value, amount, floorDb, mix);
    }

    public DereverbParams withAmount(double value) {
        return new DereverbParams(enabled, value, floorDb, mix);
    }

    public DereverbParams withFloorDb(double value) {
        return new DereverbParams(enabled, amount, value, mix);
    }

    public DereverbParams withMix(double value) {
        return new DereverbParams(enabled, amount, floorDb, value);
    }

    public boolean active() {
        return enabled && mix > 1e-9 && amount > 1e-9;
    }

    public double overSubtraction() {
        return 1.0 + 2.0 * amount;
    }

    public double floorLinear() {
        return Math.pow(10.0, floorDb / 20.0);
    }

    @Override
    public String toString() {
        return String.format(
                java.util.Locale.ROOT,
                "DereverbParams{%s amount %.0f%%, noise floor %.0f dB, mix %.2f}",
                enabled ? "on" : "off",
                Double.valueOf(amount * 100),
                Double.valueOf(floorDb),
                Double.valueOf(mix));
    }
}
