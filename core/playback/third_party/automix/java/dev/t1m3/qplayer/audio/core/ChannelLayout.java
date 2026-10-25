package dev.t1m3.qplayer.audio.core;

public final class ChannelLayout {

    public static final ChannelLayout MONO = new ChannelLayout(1, "mono");

    public static final ChannelLayout STEREO = new ChannelLayout(2, "stereo");

    public static final ChannelLayout SURROUND_5_1 = new ChannelLayout(6, "5.1");

    public static final ChannelLayout SURROUND_7_1 = new ChannelLayout(8, "7.1");

    private static final int MAX_CHANNELS = 65_535;

    private final int count;
    private final String label;

    private ChannelLayout(int count, String label) {
        this.count = count;
        this.label = label;
    }

    public static ChannelLayout of(int channels) {
        switch (channels) {
            case 1: return MONO;
            case 2: return STEREO;
            case 6: return SURROUND_5_1;
            case 8: return SURROUND_7_1;
            default:
                return new ChannelLayout(channels > MAX_CHANNELS ? MAX_CHANNELS : channels,
                        (channels > MAX_CHANNELS ? MAX_CHANNELS : channels) + "ch");
        }
    }

    public int count() {
        return count;
    }

    public boolean isSurround() {
        return count > 2;
    }

    public boolean sameGeometry(ChannelLayout other) {
        return other != null && count == other.count;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChannelLayout)) return false;
        return count == ((ChannelLayout) o).count;
    }

    @Override
    public int hashCode() {
        return count;
    }

    @Override
    public String toString() {
        return label;
    }
}
