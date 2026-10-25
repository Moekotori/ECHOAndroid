package dev.t1m3.qplayer.audio.studio;

public final class EqBand {
    public static final double MIN_FREQ = 20.0;
    public static final double MAX_FREQ = 20000.0;

    public static final double MIN_GAIN = -24.0;
    public static final double MAX_GAIN = 24.0;

    public static final double MIN_Q = 0.1;
    public static final double MAX_Q = 18.0;

    public static final int[] SLOPES = {12, 24, 48};

    public final BandType type;
    public final double freqHz;
    public final double gainDb;
    public final double q;
    public final int slopeDbPerOct;
    public final ChannelTarget channel;
    public final boolean enabled;

    private EqBand(
            BandType type,
            double freqHz,
            double gainDb,
            double q,
            int slopeDbPerOct,
            ChannelTarget channel,
            boolean enabled) {
        this.type = type == null ? BandType.PEAK : type;
        this.freqHz = settle(freqHz, MIN_FREQ, MAX_FREQ, 1000.0);
        this.gainDb = settle(gainDb, MIN_GAIN, MAX_GAIN, 0.0);
        this.q = settle(q, MIN_Q, MAX_Q, 0.7071);
        this.slopeDbPerOct = settleSlope(slopeDbPerOct);
        this.channel = channel == null ? ChannelTarget.STEREO : channel;
        this.enabled = enabled;
    }

    public static EqBand peak(double freqHz, double gainDb, double q) {
        return new EqBand(BandType.PEAK, freqHz, gainDb, q, 12, ChannelTarget.STEREO, true);
    }

    public static EqBand of(BandType type, double freqHz, double gainDb, double q) {
        return new EqBand(type, freqHz, gainDb, q, 12, ChannelTarget.STEREO, true);
    }

    public static EqBand of(
            BandType type,
            double freqHz,
            double gainDb,
            double q,
            int slopeDbPerOct,
            ChannelTarget channel,
            boolean enabled) {
        return new EqBand(type, freqHz, gainDb, q, slopeDbPerOct, channel, enabled);
    }

    public EqBand withEnabled(boolean value) {
        return new EqBand(type, freqHz, gainDb, q, slopeDbPerOct, channel, value);
    }

    public EqBand withGain(double value) {
        return new EqBand(type, freqHz, value, q, slopeDbPerOct, channel, enabled);
    }

    public EqBand withFreq(double value) {
        return new EqBand(type, value, gainDb, q, slopeDbPerOct, channel, enabled);
    }

    public EqBand withQ(double value) {
        return new EqBand(type, freqHz, gainDb, value, slopeDbPerOct, channel, enabled);
    }

    public EqBand withType(BandType value) {
        return new EqBand(value, freqHz, gainDb, q, slopeDbPerOct, channel, enabled);
    }

    public EqBand withChannel(ChannelTarget value) {
        return new EqBand(type, freqHz, gainDb, q, slopeDbPerOct, value, enabled);
    }

    public EqBand withSlope(int value) {
        return new EqBand(type, freqHz, gainDb, q, value, channel, enabled);
    }

    public boolean isTransparent() {
        if (!enabled) {
            return true;
        }
        if (type.usesGain() && Math.abs(gainDb) < 1e-6) {
            return true;
        }
        return false;
    }

    public int stages() {
        if (!type.usesSlope()) {
            return 1;
        }
        return Math.max(1, slopeDbPerOct / 12);
    }

    private static double settle(double value, double min, double max, double fallback) {
        if (Double.isNaN(value)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static int settleSlope(int value) {
        for (int s : SLOPES) {
            if (s == value) {
                return s;
            }
        }
        return 12;
    }

    @Override
    public String toString() {
        return type.json()
                + " "
                + round(freqHz)
                + "Hz "
                + round(gainDb)
                + "dB Q"
                + round(q)
                + " "
                + channel.json()
                + (enabled ? "" : " (off)");
    }

    private static String round(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", Double.valueOf(v));
    }
}
