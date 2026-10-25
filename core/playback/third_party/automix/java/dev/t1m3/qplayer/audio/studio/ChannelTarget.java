package dev.t1m3.qplayer.audio.studio;

public enum ChannelTarget {
    STEREO("stereo"),
    LEFT("left"),
    RIGHT("right"),
    MID("mid"),
    SIDE("side");

    private final String json;

    ChannelTarget(String json) {
        this.json = json;
    }

    public String json() {
        return json;
    }

    public String label() {
        switch (this) {
            case LEFT:
                return "Left";
            case RIGHT:
                return "Right";
            case MID:
                return "Mid";
            case SIDE:
                return "Side";
            default:
                return "Stereo";
        }
    }

    public static String[] labels() {
        ChannelTarget[] all = values();
        String[] out = new String[all.length];
        for (int i = 0; i < all.length; i++) {
            out[i] = all[i].label();
        }
        return out;
    }

    public static ChannelTarget fromJson(String value) {
        if (value != null) {
            for (ChannelTarget t : values()) {
                if (t.json.equalsIgnoreCase(value) || t.name().equalsIgnoreCase(value)) {
                    return t;
                }
            }
        }
        return STEREO;
    }
}
