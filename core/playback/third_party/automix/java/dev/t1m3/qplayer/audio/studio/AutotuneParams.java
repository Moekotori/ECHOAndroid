package dev.t1m3.qplayer.audio.studio;

public final class AutotuneParams {
    public static final double MAX_STRENGTH = 1.0;

    public static final double MAX_RETUNE_MS = 200.0;

    public final boolean enabled;
    public final ScaleQuantizer.Scale scale;
    public final int tonic;
    public final double a4;
    public final double strength;
    public final double retuneMs;
    public final boolean preserveVibrato;
    public final double mix;

    private AutotuneParams(
            boolean enabled,
            ScaleQuantizer.Scale scale,
            int tonic,
            double a4,
            double strength,
            double retuneMs,
            boolean preserveVibrato,
            double mix) {
        this.enabled = enabled;
        this.scale = scale == null ? ScaleQuantizer.Scale.CHROMATIC : scale;
        this.tonic = ((tonic % 12) + 12) % 12;
        double ref = Double.isNaN(a4) ? ScaleQuantizer.DEFAULT_A4 : a4;
        this.a4 = Math.max(400.0, Math.min(480.0, ref));
        double st = Double.isNaN(strength) ? 1.0 : strength;
        this.strength = Math.max(0.0, Math.min(MAX_STRENGTH, st));
        double rt = Double.isNaN(retuneMs) ? 40.0 : retuneMs;
        this.retuneMs = Math.max(0.0, Math.min(MAX_RETUNE_MS, rt));
        this.preserveVibrato = preserveVibrato;
        double m = Double.isNaN(mix) ? 1.0 : mix;
        this.mix = Math.max(0.0, Math.min(1.0, m));
    }

    public static AutotuneParams off() {
        return new AutotuneParams(
                false, ScaleQuantizer.Scale.CHROMATIC, 0, 440.0, 1.0, 40.0, true, 1.0);
    }

    public static AutotuneParams of(
            ScaleQuantizer.Scale scale,
            int tonic,
            double a4,
            double strength,
            double retuneMs,
            boolean preserveVibrato,
            double mix) {
        return new AutotuneParams(true, scale, tonic, a4, strength, retuneMs, preserveVibrato, mix);
    }

    public AutotuneParams withEnabled(boolean value) {
        return new AutotuneParams(
                value, scale, tonic, a4, strength, retuneMs, preserveVibrato, mix);
    }

    public AutotuneParams withScale(ScaleQuantizer.Scale value) {
        return new AutotuneParams(
                enabled, value, tonic, a4, strength, retuneMs, preserveVibrato, mix);
    }

    public AutotuneParams withTonic(int value) {
        return new AutotuneParams(
                enabled, scale, value, a4, strength, retuneMs, preserveVibrato, mix);
    }

    public AutotuneParams withA4(double value) {
        return new AutotuneParams(
                enabled, scale, tonic, value, strength, retuneMs, preserveVibrato, mix);
    }

    public AutotuneParams withStrength(double value) {
        return new AutotuneParams(enabled, scale, tonic, a4, value, retuneMs, preserveVibrato, mix);
    }

    public AutotuneParams withRetuneMs(double value) {
        return new AutotuneParams(enabled, scale, tonic, a4, strength, value, preserveVibrato, mix);
    }

    public AutotuneParams withPreserveVibrato(boolean value) {
        return new AutotuneParams(enabled, scale, tonic, a4, strength, retuneMs, value, mix);
    }

    public AutotuneParams withMix(double value) {
        return new AutotuneParams(
                enabled, scale, tonic, a4, strength, retuneMs, preserveVibrato, value);
    }

    public boolean active() {
        return enabled && mix > 1e-9 && strength > 1e-9;
    }

    @Override
    public String toString() {
        return String.format(
                java.util.Locale.ROOT,
                "AutotuneParams{%s %s%s, A4=%.1f, strength %.0f%%, retune %.0f ms, vibrato %s, mix"
                    + " %.2f}",
                enabled ? "on" : "off",
                scale.label,
                scale == ScaleQuantizer.Scale.CHROMATIC ? "" : ("/" + tonic),
                Double.valueOf(a4),
                Double.valueOf(strength * 100),
                Double.valueOf(retuneMs),
                preserveVibrato ? "kept" : "flattened",
                Double.valueOf(mix));
    }
}
