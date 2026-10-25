package dev.t1m3.qplayer.audio.studio;

public enum BandType {
    PEAK("peak"),
    LOW_SHELF("low_shelf"),
    HIGH_SHELF("high_shelf"),
    HIGH_PASS("high_pass"),
    LOW_PASS("low_pass"),
    BAND_PASS("band_pass"),
    NOTCH("notch"),
    ALL_PASS("all_pass");

    private final String json;

    BandType(String json) {
        this.json = json;
    }

    public String json() {
        return json;
    }

    public String label() {
        switch (this) {
            case PEAK:
                return "Peak";
            case LOW_SHELF:
                return "Low shelf";
            case HIGH_SHELF:
                return "High shelf";
            case HIGH_PASS:
                return "High pass";
            case LOW_PASS:
                return "Low pass";
            case BAND_PASS:
                return "Band pass";
            case NOTCH:
                return "Notch";
            default:
                return "All pass";
        }
    }

    public static String[] labels() {
        BandType[] all = values();
        String[] out = new String[all.length];
        for (int i = 0; i < all.length; i++) {
            out[i] = all[i].label();
        }
        return out;
    }

    public static BandType fromJson(String value) {
        if (value != null) {
            for (BandType t : values()) {
                if (t.json.equalsIgnoreCase(value) || t.name().equalsIgnoreCase(value)) {
                    return t;
                }
            }
        }
        return PEAK;
    }

    public boolean usesGain() {
        return this == PEAK || this == LOW_SHELF || this == HIGH_SHELF;
    }

    public boolean usesSlope() {
        return this == HIGH_PASS || this == LOW_PASS;
    }
}
