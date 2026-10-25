package dev.t1m3.qplayer.audio.studio;

public final class PitchParams {
    public static final int MAX_SEMITONE = 12;

    public static final double MAX_CENTS = 100.0;

    public final boolean enabled;
    public final int semitone;
    public final double cents;
    public final double mix;

    private PitchParams(boolean enabled, int semitone, double cents, double mix) {
        this.enabled = enabled;
        this.semitone =
                (int)
                        Math.max(
                                -MAX_SEMITONE,
                                Math.min(MAX_SEMITONE, Double.isNaN(semitone) ? 0 : semitone));
        double c = Double.isNaN(cents) ? 0.0 : cents;
        this.cents = Math.max(-MAX_CENTS, Math.min(MAX_CENTS, c));
        double m = Double.isNaN(mix) ? 1.0 : mix;
        this.mix = Math.max(0.0, Math.min(1.0, m));
    }

    public static PitchParams off() {
        return new PitchParams(false, 0, 0.0, 1.0);
    }

    public static PitchParams of(int semitone, double cents, double mix) {
        return new PitchParams(true, semitone, cents, mix);
    }

    public static PitchParams semitones(int semitone) {
        return new PitchParams(true, semitone, 0.0, 1.0);
    }

    public PitchParams withEnabled(boolean value) {
        return new PitchParams(value, semitone, cents, mix);
    }

    public PitchParams withSemitone(int value) {
        return new PitchParams(enabled, value, cents, mix);
    }

    public PitchParams withCents(double value) {
        return new PitchParams(enabled, semitone, value, mix);
    }

    public PitchParams withMix(double value) {
        return new PitchParams(enabled, semitone, cents, value);
    }

    public double totalSemitones() {
        return semitone + cents / 100.0;
    }

    public boolean active() {
        if (!enabled || mix <= 1e-9) {
            return false;
        }
        return Math.abs(totalSemitones()) > 1e-9;
    }

    @Override
    public String toString() {
        return String.format(
                java.util.Locale.ROOT,
                "PitchParams{%s %+d semitones %+.1f cents, mix %.2f}",
                enabled ? "on" : "off",
                Integer.valueOf(semitone),
                Double.valueOf(cents),
                Double.valueOf(mix));
    }
}
