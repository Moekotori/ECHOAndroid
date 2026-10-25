package dev.t1m3.qplayer.audio.mix;

public final class AppleTransitionSpec {

    private AppleTransitionSpec() {
    }

    public static final class Seg {
        public final float t0;
        public final float t1;
        public final float v0;
        public final float v1;

        public final float absT0;

        public final float absT1;
        public final int easeKind;
        public final float easeN;

        public final String symbol;

        Seg(float t0, float t1, float v0, float v1, float absT0, float absT1,
                int easeKind, float easeN, String symbol) {
            this.t0 = t0; this.t1 = t1; this.v0 = v0; this.v1 = v1;
            this.absT0 = absT0; this.absT1 = absT1;
            this.easeKind = easeKind; this.easeN = easeN; this.symbol = symbol;
        }
    }

    public static Auto merged(String key, Seg[] segs) {
        return new Auto(key, segs);
    }

    public static final class Auto {
        public final String key;
        public final Seg[] segs;
        Auto(String key, Seg[] segs) { this.key = key; this.segs = segs; }
    }

    public static final class Style {
        public final int id;
        public final String name;
        public final float durationSecs;
        public final String[] outgoingOps;
        public final String[] incomingOps;
        public final Auto[] outgoing;
        public final Auto[] incoming;
        Style(int id, String name, float durationSecs, String[] outgoingOps,
                String[] incomingOps, Auto[] outgoing, Auto[] incoming) {
            this.id = id; this.name = name; this.durationSecs = durationSecs;
            this.outgoingOps = outgoingOps; this.incomingOps = incomingOps;
            this.outgoing = outgoing; this.incoming = incoming;
        }
    }

    private static volatile double instantFadeSecs = 0.25;

    public static void setInstantFadeSecs(double secs) {
        instantFadeSecs = Math.max(0.0, secs);
    }

    public static double instantFadeSecs() {
        return instantFadeSecs;
    }

    public static float valueAt(Auto auto, float p) {
        return valueAt(auto, p, 0f);
    }

    public static float valueAt(Auto auto, float p, float windowSecs) {
        if (auto == null || auto.segs.length == 0) {
            return 0f;
        }
        float x = p < 0f ? 0f : (p > 1f ? 1f : p);

        Seg best = null;
        for (Seg s : auto.segs) {
            float a0 = s.t0 + (windowSecs > 0f ? s.absT0 / windowSecs : 0f);
            float a1 = s.t1 + (windowSecs > 0f ? s.absT1 / windowSecs : 0f);
            if (windowSecs <= 0f && (s.absT0 != 0f || s.absT1 != 0f)) {

                a0 = s.t0;
                a1 = s.t1;
            }
            if (x >= a0) {
                if (best == null || a0 >= bestStart(best, windowSecs)) {
                    best = s;
                }
            }
        }
        if (best == null) {
            Seg first = auto.segs[0];
            return first.symbol == null ? first.v0 : first.v0;
        }
        float b0 = best.t0 + (windowSecs > 0f ? best.absT0 / windowSecs : 0f);
        float b1 = best.t1 + (windowSecs > 0f ? best.absT1 / windowSecs : 0f);
        if (windowSecs <= 0f && (best.absT0 != 0f || best.absT1 != 0f)) {
            b0 = best.t0;
            b1 = best.t1;
        }
        if (b1 <= b0) {

            float span = windowSecs > 0f
                    ? (float) (instantFadeSecs / windowSecs) : 0f;
            boolean gainLane = auto.key != null && auto.key.endsWith("_gain");
            if (gainLane && span > 1e-6f && Math.abs(best.v1 - best.v0) > 1e-6f) {
                float k = (x - b0) / span;
                if (k <= 0f) {
                    return best.v0;
                }
                if (k >= 1f) {
                    return best.v1;
                }
                return best.v0 + k * (best.v1 - best.v0);
            }
            return x >= b1 ? best.v1 : best.v0;
        }
        if (x >= b1) {
            return best.v1;
        }
        float k = (x - b0) / (b1 - b0);
        if (best.easeKind == 1) {
            k = (float) (1.0 - Math.pow(1.0 - k, best.easeN));
        } else if (best.easeKind == 2) {
            k = (float) Math.pow(k, best.easeN);
        }
        return best.v0 + k * (best.v1 - best.v0);
    }

    private static float bestStart(Seg s, float windowSecs) {
        return s.t0 + (windowSecs > 0f ? s.absT0 / windowSecs : 0f);
    }

    public static final Style[] STYLES = {
        new Style(0, "Gapless", 0.0f,
                new String[] {"RECEIVE_MIXER"}, new String[] {"RECEIVE_MIXER"},
                new Auto[] {new Auto("out_gain", new Seg[] {new Seg(0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 2, 0.5f, null)})},
                new Auto[] {new Auto("out_gain", new Seg[] {new Seg(0.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1, 0.5f, null)})}),
        new Style(1, "constant-power cross-fade", 0.0f,
                new String[] {"RECEIVE_MIXER"}, new String[] {"RECEIVE_MIXER"},
                new Auto[] {new Auto("out_gain", new Seg[] {new Seg(0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 2, 0.5f, null)})},
                new Auto[] {new Auto("out_gain", new Seg[] {new Seg(0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1, 0.5f, null)})}),
        new Style(2, "constant-power long fade-out short fade-in", 0.0f,
                new String[] {"RECEIVE_MIXER"}, new String[] {"RECEIVE_MIXER"},
                new Auto[] {new Auto("out_gain", new Seg[] {new Seg(0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 2, 0.5f, null)})},
                new Auto[] {new Auto("out_gain", new Seg[] {new Seg(0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1, 0.5f, null)})}),
        new Style(22, "splice", 0.0f,
                new String[] {}, new String[] {},
                new Auto[] {},
                new Auto[] {}),
        new Style(3, "overlap", 0.0f,
                new String[] {}, new String[] {},
                new Auto[] {},
                new Auto[] {}),
        new Style(33, "overlap + Reverb", 0.0f,
                new String[] {"AUX_REVERB", "SEND_MIXER", "RECEIVE_MIXER"}, new String[] {},
                new Auto[] {new Auto("fx_reverb_high_frequency_decay_time", new Seg[] {new Seg(0.0f, 0.0f, 0.001f, 6.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("fx_reverb_low_frequency_decay_time", new Seg[] {new Seg(0.0f, 0.0f, 0.001f, 5.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("fx_reverb_min_delay_time", new Seg[] {new Seg(0.0f, 0.0f, 0.008f, 0.9f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("fx_reverb_dry_wet", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 100.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("send_mixer_gain", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 0.75f, 0.0f, 2.0f, 0, 1.0f, null)}), new Auto("fx_mixer_wet", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0, 1.0f, null)})},
                new Auto[] {}),
        new Style(4, "Ease-in Ring-out", 0.0f,
                new String[] {}, new String[] {"RECEIVE_MIXER"},
                new Auto[] {},
                new Auto[] {new Auto("out_gain", new Seg[] {new Seg(0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1, 0.5f, null)})}),
        new Style(44, "Ease-in Ring-out + Reverb", 0.0f,
                new String[] {"AUX_REVERB", "SEND_MIXER"}, new String[] {"RECEIVE_MIXER"},
                new Auto[] {new Auto("fx_reverb_high_frequency_decay_time", new Seg[] {new Seg(0.0f, 0.0f, 0.001f, 6.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("fx_reverb_low_frequency_decay_time", new Seg[] {new Seg(0.0f, 0.0f, 0.001f, 5.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("fx_reverb_min_delay_time", new Seg[] {new Seg(0.0f, 0.0f, 0.008f, 0.9f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("fx_reverb_dry_wet", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 100.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("send_mixer_gain", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 0.75f, 0.0f, 2.0f, 0, 1.0f, null)}), new Auto("fx_mixer_wet", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0, 1.0f, null)})},
                new Auto[] {new Auto("out_gain", new Seg[] {new Seg(0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1, 0.5f, null)})}),
        new Style(5, "BM - Short filter high to low", 2.0f,
                new String[] {"TIME_STRETCHING", "LOW_PASS", "RECEIVE_MIXER"}, new String[] {"TIME_STRETCHING", "HI_PASS", "LOW_PASS", "RECEIVE_MIXER"},
                new Auto[] {new Auto("ts_rate", new Seg[] {new Seg(0.0f, 1.0f, 1.0f, 2.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("lp_cutoff_freq", new Seg[] {new Seg(0.0f, 1.0f, 20000.0f, 200.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("out_gain", new Seg[] {new Seg(0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0, 1.0f, null)})},
                new Auto[] {new Auto("ts_rate", new Seg[] {new Seg(0.0f, 1.0f, 2.0f, 1.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("hp_cutoff_freq", new Seg[] {new Seg(0.0f, 1.0f, 20000.0f, 10.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("lp_cutoff_freq", new Seg[] {new Seg(0.0f, 1.0f, 1200.0f, 20000.0f, 0.0f, 0.0f, 2, 4.0f, null)}), new Auto("out_gain", new Seg[] {new Seg(0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0, 1.0f, null)})}),
        new Style(6, "Beat-matched long fade-out short fade-in - Same Bar num", 0.0f,
                new String[] {"TIME_STRETCHING", "RECEIVE_MIXER"}, new String[] {"TIME_STRETCHING", "RECEIVE_MIXER"},
                new Auto[] {new Auto("ts_rate", new Seg[] {new Seg(0.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("out_gain", new Seg[] {new Seg(0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 2, 0.5f, null)})},
                new Auto[] {new Auto("ts_rate", new Seg[] {new Seg(0.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("out_gain", new Seg[] {new Seg(0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1, 0.5f, null)})}),
        new Style(7, "Beat-matched long fade-out dynamic fade-in", 0.0f,
                new String[] {"TIME_STRETCHING", "RECEIVE_MIXER"}, new String[] {"TIME_STRETCHING", "RECEIVE_MIXER"},
                new Auto[] {new Auto("ts_rate", new Seg[] {new Seg(0.0f, 1.0f, 1.0f, 2.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("out_gain", new Seg[] {new Seg(0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 2, 0.5f, null)})},
                new Auto[] {new Auto("ts_rate", new Seg[] {new Seg(0.0f, 1.0f, 2.0f, 1.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("out_gain", new Seg[] {new Seg(0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1, 0.5f, null)})}),
        new Style(8, "BM - Filter high to low", 8.0f,
                new String[] {"TIME_STRETCHING", "LOW_PASS", "RECEIVE_MIXER"}, new String[] {"TIME_STRETCHING", "HI_PASS", "LOW_PASS", "RECEIVE_MIXER"},
                new Auto[] {new Auto("ts_rate", new Seg[] {new Seg(0.0f, 1.0f, 1.0f, 2.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("lp_cutoff_freq", new Seg[] {new Seg(0.0f, 1.0f, 20000.0f, 200.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("out_gain", new Seg[] {new Seg(0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0, 1.0f, null)})},
                new Auto[] {new Auto("ts_rate", new Seg[] {new Seg(0.0f, 1.0f, 2.0f, 1.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("hp_cutoff_freq", new Seg[] {new Seg(0.0f, 1.0f, 20000.0f, 10.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("lp_cutoff_freq", new Seg[] {new Seg(0.0f, 1.0f, 1200.0f, 20000.0f, 0.0f, 0.0f, 2, 4.0f, null)}), new Auto("out_gain", new Seg[] {new Seg(0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0, 1.0f, null)})}),
        new Style(9, "BM - Filter expansion", 8.0f,
                new String[] {"TIME_STRETCHING", "LOW_PASS", "HI_PASS", "RECEIVE_MIXER"}, new String[] {"TIME_STRETCHING", "LOW_PASS", "HI_PASS", "RECEIVE_MIXER"},
                new Auto[] {new Auto("ts_rate", new Seg[] {new Seg(0.0f, 1.0f, 1.0f, 2.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("lp_cutoff_freq", new Seg[] {new Seg(0.5f, 1.0f, 20000.0f, 20.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("hp_cutoff_freq", new Seg[] {new Seg(0.5f, 0.75f, 20.0f, 200.0f, 0.0f, 0.0f, 1, 0.5f, null)}), new Auto("out_gain", new Seg[] {new Seg(0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0, 1.0f, null)})},
                new Auto[] {new Auto("ts_rate", new Seg[] {new Seg(0.0f, 1.0f, 2.0f, 1.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("lp_cutoff_freq", new Seg[] {new Seg(0.0f, 0.75f, 1000.0f, 20000.0f, 0.0f, 0.0f, 1, 0.5f, null)}), new Auto("hp_cutoff_freq", new Seg[] {new Seg(0.5f, 0.75f, 1000.0f, 20.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("out_gain", new Seg[] {new Seg(0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0, 1.0f, null)})}),
        new Style(10, "BM - Filter long out short in + delay", 0.0f,
                new String[] {"TIME_STRETCHING", "LOW_PASS", "AUX_DELAY", "SEND_MIXER", "RECEIVE_MIXER", "RECEIVE_MIXER"}, new String[] {"TIME_STRETCHING", "HI_PASS", "RECEIVE_MIXER"},
                new Auto[] {new Auto("ts_rate", new Seg[] {new Seg(0.0f, 1.0f, 1.0f, 2.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("lp_cutoff_freq", new Seg[] {new Seg(0.0f, 1.0f, 20000.0f, 200.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("fx_delay_dry_wet", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 100.0f, 0.0f, 1.0f, 1, 2.0f, null)}), new Auto("fx_delay_delay_time", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1, 2.0f, "beat_length")}), new Auto("send_mixer_gain", new Seg[] {new Seg(0.0f, 1.0f, 0.5f, 0.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("fx_mixer_wet", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("out_gain", new Seg[] {new Seg(0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0, 1.0f, null)})},
                new Auto[] {new Auto("ts_rate", new Seg[] {new Seg(0.0f, 1.0f, 2.0f, 1.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("hp_cutoff_freq", new Seg[] {new Seg(0.0f, 1.0f, 20000.0f, 200.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("out_gain", new Seg[] {new Seg(0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0, 1.0f, null)})}),
        new Style(11, "BM - Filter long out short in + Reverb", 0.0f,
                new String[] {"TIME_STRETCHING", "LOW_PASS", "HI_PASS", "AUX_REVERB", "SEND_MIXER"}, new String[] {"TIME_STRETCHING", "LOW_PASS", "HI_PASS", "RECEIVE_MIXER"},
                new Auto[] {new Auto("ts_rate", new Seg[] {new Seg(0.0f, 1.0f, 1.0f, 2.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("lp_cutoff_freq", new Seg[] {new Seg(0.0f, 1.0f, 20000.0f, 80.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("hp_cutoff_freq", new Seg[] {new Seg(0.0f, 1.0f, 10.0f, 200.0f, 0.0f, 0.0f, 2, 4.0f, null)}), new Auto("fx_reverb_high_frequency_decay_time", new Seg[] {new Seg(0.0f, 0.0f, 0.001f, 4.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("fx_reverb_low_frequency_decay_time", new Seg[] {new Seg(0.0f, 0.0f, 0.001f, 4.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("fx_reverb_min_delay_time", new Seg[] {new Seg(0.0f, 0.0f, 0.008f, 0.052f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("fx_reverb_max_delay_time", new Seg[] {new Seg(0.0f, 0.0f, 0.106f, 0.106f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("fx_reverb_randomize_reflections", new Seg[] {new Seg(0.0f, 0.0f, 333.0f, 333.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("fx_reverb_dry_wet", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 100.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("send_mixer_gain", new Seg[] {new Seg(0.0f, 0.25f, 0.0f, 1.0f, 0.0f, 0.0f, 0, 1.0f, null)})},
                new Auto[] {new Auto("ts_rate", new Seg[] {new Seg(0.0f, 1.0f, 2.0f, 1.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("lp_cutoff_freq", new Seg[] {new Seg(0.0f, 1.0f, 60.0f, 21000.0f, 0.0f, 0.0f, 2, 4.0f, null)}), new Auto("hp_cutoff_freq", new Seg[] {new Seg(0.0f, 1.0f, 3000.0f, 10.0f, 0.0f, 0.0f, 1, 4.0f, null)}), new Auto("out_gain", new Seg[] {new Seg(0.5f, 0.5f, 0.0f, 1.0f, 0.0f, 0.0f, 0, 1.0f, null)})}),
        new Style(12, "BM - Long filter high to low", 16.0f,
                new String[] {"TIME_STRETCHING", "LOW_PASS", "RECEIVE_MIXER"}, new String[] {"TIME_STRETCHING", "HI_PASS", "LOW_PASS", "RECEIVE_MIXER"},
                new Auto[] {new Auto("ts_rate", new Seg[] {new Seg(0.0f, 1.0f, 1.0f, 2.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("lp_cutoff_freq", new Seg[] {new Seg(0.0f, 1.0f, 20000.0f, 200.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("out_gain", new Seg[] {new Seg(0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0, 1.0f, null)})},
                new Auto[] {new Auto("ts_rate", new Seg[] {new Seg(0.0f, 1.0f, 2.0f, 1.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("hp_cutoff_freq", new Seg[] {new Seg(0.0f, 1.0f, 20000.0f, 10.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("lp_cutoff_freq", new Seg[] {new Seg(0.0f, 1.0f, 1200.0f, 20000.0f, 0.0f, 0.0f, 2, 4.0f, null)}), new Auto("out_gain", new Seg[] {new Seg(0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0, 1.0f, null)})}),
        new Style(13, "BM - Filter soft + Reverb", 10.0f,
                new String[] {"TIME_STRETCHING", "LOW_PASS", "HI_PASS", "RECEIVE_MIXER", "AUX_REVERB", "SEND_MIXER", "RECEIVE_MIXER", "INPUT_GAIN"}, new String[] {"TIME_STRETCHING", "LOW_PASS", "HI_PASS", "RECEIVE_MIXER"},
                new Auto[] {new Auto("ts_rate", new Seg[] {new Seg(0.0f, 1.0f, 1.0f, 2.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("lp_cutoff_freq", new Seg[] {new Seg(0.4f, 0.8f, 20000.0f, 760.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("hp_cutoff_freq", new Seg[] {new Seg(0.0f, 0.8f, 10.0f, 760.0f, 0.0f, 0.0f, 1, 0.5f, null)}), new Auto("out_gain", new Seg[] {new Seg(0.4f, 0.8f, 1.0f, 0.75f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("fx_reverb_high_frequency_decay_time", new Seg[] {new Seg(0.0f, 0.0f, 0.001f, 4.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("fx_reverb_low_frequency_decay_time", new Seg[] {new Seg(0.0f, 0.0f, 0.001f, 4.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("fx_reverb_min_delay_time", new Seg[] {new Seg(0.0f, 0.0f, 0.008f, 0.052f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("fx_reverb_max_delay_time", new Seg[] {new Seg(0.0f, 0.0f, 0.106f, 0.106f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("fx_reverb_randomize_reflections", new Seg[] {new Seg(0.0f, 0.0f, 333.0f, 333.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("fx_reverb_dry_wet", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 100.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("send_mixer_gain", new Seg[] {new Seg(0.0f, 0.8f, 0.0f, 1.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("fx_mixer_wet", new Seg[] {new Seg(0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 2, 2.0f, null)}), new Auto("fx_mixer_dry", new Seg[] {new Seg(0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 2, 2.0f, null)}), new Auto("player_gain", new Seg[] {new Seg(0.8f, 0.8f, 1.0f, 0.0f, -0.5f, 0.0f, 0, 1.0f, null)})},
                new Auto[] {new Auto("ts_rate", new Seg[] {new Seg(0.0f, 1.0f, 2.0f, 1.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("lp_cutoff_freq", new Seg[] {new Seg(0.0f, 0.0f, 250.0f, 250.0f, 0.0f, 0.0f, 1, 0.5f, null)}), new Auto("lp_cutoff_freq", new Seg[] {new Seg(0.6f, 0.8f, 500.0f, 20000.0f, 0.0f, 0.0f, 2, 2.0f, null)}), new Auto("lp_reso", new Seg[] {new Seg(0.0f, 0.0f, -5.0f, -5.0f, 0.0f, 0.0f, 2, 2.0f, null)}), new Auto("hp_cutoff_freq", new Seg[] {new Seg(0.0f, 0.0f, 150.0f, 150.0f, 0.0f, 0.0f, 2, 2.0f, null)}), new Auto("hp_cutoff_freq", new Seg[] {new Seg(0.7f, 0.8f, 150.0f, 20.0f, 0.0f, 0.0f, 2, 2.0f, null)}), new Auto("out_gain", new Seg[] {new Seg(0.0f, 0.1f, 0.0f, 0.75f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("out_gain", new Seg[] {new Seg(0.65f, 0.7f, 0.75f, 1.0f, 0.0f, 0.0f, 0, 1.0f, null)})}),
        new Style(14, "No vocal clash one", 10.0f,
                new String[] {"TIME_STRETCHING", "LOW_PASS", "HI_PASS", "AUX_REVERB", "SEND_MIXER", "RECEIVE_MIXER", "INPUT_GAIN"}, new String[] {"TIME_STRETCHING", "LOW_PASS", "HI_PASS", "RECEIVE_MIXER"},
                new Auto[] {new Auto("ts_rate", new Seg[] {new Seg(0.0f, 1.0f, 1.0f, 2.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("lp_cutoff_freq", new Seg[] {new Seg(0.0f, 1.0f, 20000.0f, 20.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("hp_cutoff_freq", new Seg[] {new Seg(0.0f, 0.8f, 10.0f, 300.0f, 0.0f, 0.0f, 1, 0.5f, null)}), new Auto("fx_reverb_high_frequency_decay_time", new Seg[] {new Seg(0.0f, 0.0f, 0.001f, 0.5f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("fx_reverb_low_frequency_decay_time", new Seg[] {new Seg(0.0f, 0.0f, 0.001f, 1.5f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("fx_reverb_min_delay_time", new Seg[] {new Seg(0.0f, 0.0f, 0.008f, 0.01f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("fx_reverb_max_delay_time", new Seg[] {new Seg(0.0f, 0.0f, 0.106f, 0.03f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("fx_reverb_dry_wet", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 100.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("send_mixer_gain", new Seg[] {new Seg(0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("fx_mixer_wet", new Seg[] {new Seg(0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 2, 2.0f, null)}), new Auto("fx_mixer_dry", new Seg[] {new Seg(0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 2, 2.0f, null)}), new Auto("player_gain", new Seg[] {new Seg(0.8f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0, 1.0f, null)})},
                new Auto[] {new Auto("ts_rate", new Seg[] {new Seg(0.0f, 1.0f, 2.0f, 1.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("lp_cutoff_freq", new Seg[] {new Seg(0.0f, 0.0f, 250.0f, 250.0f, 0.0f, 0.0f, 1, 0.5f, null)}), new Auto("lp_cutoff_freq", new Seg[] {new Seg(0.6f, 0.8f, 500.0f, 20000.0f, 0.0f, 0.0f, 2, 2.0f, null)}), new Auto("lp_reso", new Seg[] {new Seg(0.0f, 0.0f, -5.0f, -5.0f, 0.0f, 0.0f, 2, 2.0f, null)}), new Auto("hp_cutoff_freq", new Seg[] {new Seg(0.0f, 0.0f, 150.0f, 150.0f, 0.0f, 0.0f, 2, 2.0f, null)}), new Auto("hp_cutoff_freq", new Seg[] {new Seg(0.7f, 0.8f, 150.0f, 20.0f, 0.0f, 0.0f, 2, 2.0f, null)}), new Auto("out_gain", new Seg[] {new Seg(0.0f, 0.1f, 0.0f, 0.75f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("out_gain", new Seg[] {new Seg(0.65f, 0.7f, 0.75f, 1.0f, 0.0f, 0.0f, 0, 1.0f, null)})}),
        new Style(15, "Hiphop - Delay out", 4.0f,
                new String[] {"TIME_STRETCHING", "AUX_DELAY", "SEND_MIXER", "HI_PASS", "RECEIVE_MIXER", "RECEIVE_MIXER"}, new String[] {"TIME_STRETCHING", "LOW_PASS", "HI_PASS", "RECEIVE_MIXER"},
                new Auto[] {new Auto("ts_rate", new Seg[] {new Seg(0.0f, 0.75f, 1.0f, 2.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("fx_delay_dry_wet", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 100.0f, 0.0f, 1.0f, 1, 2.0f, null)}), new Auto("fx_delay_delay_time", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1, 2.0f, "dynamic_beat_length")}), new Auto("fx_delay_lp_cutoff_frequency", new Seg[] {new Seg(0.0f, 0.0f, 3000.0f, 3000.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("fx_delay_feedback", new Seg[] {new Seg(0.0f, 0.0f, 35.0f, 35.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("send_mixer_gain", new Seg[] {new Seg(0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.1f, 0, 1.0f, null)}), new Auto("hp_cutoff_freq", new Seg[] {new Seg(0.25f, 0.5f, 20.0f, 200.0f, 0.0f, 0.0f, 1, 0.5f, null)}), new Auto("fx_mixer_wet", new Seg[] {new Seg(0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 2, 2.0f, null)}), new Auto("fx_mixer_dry", new Seg[] {new Seg(0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 2, 2.0f, null)}), new Auto("out_gain", new Seg[] {new Seg(0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0, 1.0f, null)})},
                new Auto[] {new Auto("ts_rate", new Seg[] {new Seg(0.0f, 0.75f, 2.0f, 1.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("lp_cutoff_freq", new Seg[] {new Seg(0.25f, 0.75f, 200.0f, 20000.0f, 0.0f, 0.0f, 2, 4.0f, null)}), new Auto("hp_cutoff_freq", new Seg[] {new Seg(0.25f, 0.75f, 300.0f, 20.0f, 0.0f, 0.0f, 1, 0.5f, null)}), new Auto("out_gain", new Seg[] {new Seg(0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0, 1.0f, null)})}),
        new Style(16, "Pop - Delay out", 4.0f,
                new String[] {"TIME_STRETCHING", "LOW_PASS", "AUX_DELAY", "SEND_MIXER", "RECEIVE_MIXER", "RECEIVE_MIXER", "INPUT_GAIN"}, new String[] {"TIME_STRETCHING", "HI_PASS", "RECEIVE_MIXER"},
                new Auto[] {new Auto("ts_rate", new Seg[] {new Seg(0.0f, 0.5f, 1.0f, 2.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("lp_cutoff_freq", new Seg[] {new Seg(0.125f, 0.25f, 20000.0f, 3500.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("fx_delay_dry_wet", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 100.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("fx_delay_delay_time", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1, 2.0f, "beat_length")}), new Auto("fx_delay_lp_cutoff_frequency", new Seg[] {new Seg(0.0f, 0.0f, 3500.0f, 3500.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("fx_delay_feedback", new Seg[] {new Seg(0.0f, 0.0f, 60.0f, 60.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("send_mixer_gain", new Seg[] {new Seg(0.0f, 0.25f, 0.0f, 0.5f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("fx_mixer_wet", new Seg[] {new Seg(0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 2, 2.0f, null)}), new Auto("out_gain", new Seg[] {new Seg(0.375f, 0.5f, 1.0f, 0.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("player_gain", new Seg[] {new Seg(0.25f, 0.25f, 1.0f, 0.0f, 0.0f, 0.0f, 0, 1.0f, null)})},
                new Auto[] {new Auto("ts_rate", new Seg[] {new Seg(0.0f, 0.5f, 2.0f, 1.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("hp_cutoff_freq", new Seg[] {new Seg(0.1875f, 0.4375f, 5200.0f, 20.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("out_gain", new Seg[] {new Seg(0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0, 1.0f, null)})}),
        new Style(17, "Dance - Repeater", 18.0f,
                new String[] {"TIME_STRETCHING", "REMIX_FX"}, new String[] {"TIME_STRETCHING", "REMIX_FX", "RECEIVE_MIXER"},
                new Auto[] {new Auto("ts_rate", new Seg[] {new Seg(0.0f, 1.0f, 1.0f, 2.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("remixfx_beats_per_minute", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1, 2.0f, "bpm")}), new Auto("remixfx_filter_a_on", new Seg[] {new Seg(0.45f, 0.45f, 0.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_a_cutoff", new Seg[] {new Seg(0.45f, 0.8889f, 20.0f, 1000.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_a_resonance", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_a_type", new Seg[] {new Seg(0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_b_on", new Seg[] {new Seg(0.5556f, 0.5556f, 0.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_b_cutoff", new Seg[] {new Seg(0.8333f, 0.8611f, 20000.0f, 2111.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_b_cutoff", new Seg[] {new Seg(0.8611f, 0.8889f, 2111.0f, 20.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_b_resonance", new Seg[] {new Seg(0.8611f, 0.8611f, 0.3f, 0.3f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_b_type", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_repeater_on", new Seg[] {new Seg(0.4444f, 0.4444f, 0.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_repeater_on", new Seg[] {new Seg(0.8889f, 0.8889f, 1.0f, 0.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_repeater_rate", new Seg[] {new Seg(0.4444f, 0.4444f, 1.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_repeater_rate", new Seg[] {new Seg(0.6667f, 0.6667f, 2.0f, 2.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_repeater_rate", new Seg[] {new Seg(0.7778f, 0.7778f, 3.0f, 3.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_repeater_rate", new Seg[] {new Seg(0.8333f, 0.8333f, 4.0f, 4.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_repeater_rate", new Seg[] {new Seg(0.8611f, 0.8611f, 5.0f, 5.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_repeater_mix", new Seg[] {new Seg(0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_reverb_on", new Seg[] {new Seg(0.6389f, 0.6389f, 0.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_reverb_time", new Seg[] {new Seg(0.0f, 0.0f, 6.0f, 6.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_reverb_send", new Seg[] {new Seg(0.6667f, 0.7778f, 0.0f, 0.56f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_reverb_dry", new Seg[] {new Seg(0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_reverb_wet", new Seg[] {new Seg(0.0f, 0.0f, 0.7f, 0.7f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_flanger_on", new Seg[] {new Seg(0.6667f, 0.6667f, 0.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_flanger_rate", new Seg[] {new Seg(0.0f, 0.0f, 2.0f, 2.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_flanger_mix", new Seg[] {new Seg(0.6722f, 0.8333f, 0.0f, 0.45f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_flanger_feedback", new Seg[] {new Seg(0.0f, 0.0f, 0.52f, 0.52f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_flanger_depth", new Seg[] {new Seg(0.0f, 0.0f, 0.32f, 0.32f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_flanger_center", new Seg[] {new Seg(0.0f, 0.0f, 0.51f, 0.51f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_lfo_phase_sync", new Seg[] {new Seg(0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)})},
                new Auto[] {new Auto("ts_rate", new Seg[] {new Seg(0.0f, 1.0f, 2.0f, 1.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("remixfx_beats_per_minute", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1, 2.0f, "bpm")}), new Auto("remixfx_filter_a_on", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_a_cutoff", new Seg[] {new Seg(0.0f, 0.2222f, 1250.0f, 950.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_a_cutoff", new Seg[] {new Seg(0.2222f, 0.3889f, 950.0f, 200.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_a_cutoff", new Seg[] {new Seg(0.3889f, 0.5556f, 200.0f, 20.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_a_resonance", new Seg[] {new Seg(0.0f, 0.0f, 0.3f, 0.3f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_a_type", new Seg[] {new Seg(0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_b_on", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_b_cutoff", new Seg[] {new Seg(0.0f, 0.4444f, 70.0f, 1600.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_b_cutoff", new Seg[] {new Seg(0.4444f, 0.8889f, 1600.0f, 20000.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_b_resonance", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_b_type", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("out_gain", new Seg[] {new Seg(0.0f, 0.4444f, 0.0f, 0.4467f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("out_gain", new Seg[] {new Seg(0.4444f, 0.7778f, 0.4467f, 1.0f, 0.0f, 0.0f, 0, 1.0f, null)})}),
        new Style(18, "Hiphop - Looping", 4.0f,
                new String[] {"TIME_STRETCHING", "REMIX_FX"}, new String[] {"TIME_STRETCHING", "REMIX_FX", "RECEIVE_MIXER"},
                new Auto[] {new Auto("ts_rate", new Seg[] {new Seg(0.0f, 1.0f, 1.0f, 2.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("remixfx_lfo_phase_sync", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_repeater_on", new Seg[] {new Seg(0.5f, 0.5f, 0.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_repeater_rate", new Seg[] {new Seg(0.0f, 0.0f, 2.0f, 2.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_repeater_mix", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_beats_per_minute", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1, 2.0f, "bpm")}), new Auto("remixfx_filter_a_on", new Seg[] {new Seg(0.525f, 0.525f, 0.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_a_cutoff", new Seg[] {new Seg(0.525f, 1.0f, 20000.0f, 20.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_a_type", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1, 2.0f, null)})},
                new Auto[] {new Auto("ts_rate", new Seg[] {new Seg(0.0f, 1.0f, 2.0f, 1.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("remixfx_lfo_phase_sync", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_beats_per_minute", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1, 2.0f, "bpm")}), new Auto("remixfx_filter_a_on", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_a_cutoff", new Seg[] {new Seg(0.0f, 0.75f, 2000.0f, 20.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_a_type", new Seg[] {new Seg(0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("out_gain", new Seg[] {new Seg(0.0f, 0.75f, 0.3162f, 1.0f, 0.0f, 0.0f, 2, 0.5f, null)})}),
        new Style(19, "Dance - Any Key", 8.0f,
                new String[] {"TIME_STRETCHING", "REMIX_FX"}, new String[] {"TIME_STRETCHING", "REMIX_FX", "RECEIVE_MIXER"},
                new Auto[] {new Auto("ts_rate", new Seg[] {new Seg(0.0f, 1.0f, 1.0f, 2.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("remixfx_beats_per_minute", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1, 2.0f, "bpm")}), new Auto("remixfx_filter_a_on", new Seg[] {new Seg(0.2813f, 0.2813f, 0.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_a_cutoff", new Seg[] {new Seg(0.25f, 0.625f, 20.0f, 1031.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_a_resonance", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_a_type", new Seg[] {new Seg(0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_b_on", new Seg[] {new Seg(0.2656f, 0.2656f, 0.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_b_cutoff", new Seg[] {new Seg(0.2656f, 0.375f, 20000.0f, 2375.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_b_cutoff", new Seg[] {new Seg(0.375f, 0.875f, 2375.0f, 20.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("remixfx_filter_b_resonance", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_b_type", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_repeater_on", new Seg[] {new Seg(0.25f, 0.25f, 0.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_repeater_rate", new Seg[] {new Seg(0.0f, 0.0f, 2.0f, 2.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_repeater_mix", new Seg[] {new Seg(0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_reverb_on", new Seg[] {new Seg(0.5f, 0.5f, 0.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_reverb_time", new Seg[] {new Seg(0.0f, 0.0f, 18.0f, 18.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_reverb_send", new Seg[] {new Seg(0.5f, 0.75f, 0.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_reverb_dry", new Seg[] {new Seg(0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_reverb_wet", new Seg[] {new Seg(0.0f, 0.0f, 0.815f, 0.815f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_delay_on", new Seg[] {new Seg(0.375f, 0.375f, 0.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_delay_rate", new Seg[] {new Seg(0.0f, 0.0f, 7.0f, 7.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_delay_feedback", new Seg[] {new Seg(0.0f, 0.0f, 0.5f, 0.5f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_delay_level", new Seg[] {new Seg(0.625f, 0.875f, 0.0f, 0.5f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_gater_on", new Seg[] {new Seg(0.375f, 0.375f, 0.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_gater_rate", new Seg[] {new Seg(0.0f, 0.0f, 7.0f, 7.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_gater_depth", new Seg[] {new Seg(0.25f, 1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_gater_pw", new Seg[] {new Seg(0.0f, 0.0f, 0.5f, 0.5f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_gater_noise_level", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_gater_noise_cutoff", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_flanger_on", new Seg[] {new Seg(0.5f, 0.5f, 0.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_flanger_rate", new Seg[] {new Seg(0.0f, 0.0f, 2.0f, 2.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_flanger_mix", new Seg[] {new Seg(0.5f, 1.0f, 0.0f, 0.63f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_flanger_feedback", new Seg[] {new Seg(0.0f, 0.0f, 0.5f, 0.5f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_flanger_depth", new Seg[] {new Seg(0.0f, 0.0f, 0.3f, 0.3f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_flanger_center", new Seg[] {new Seg(0.0f, 0.0f, 0.5f, 0.5f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_lfo_phase_sync", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)})},
                new Auto[] {new Auto("ts_rate", new Seg[] {new Seg(0.0f, 1.0f, 2.0f, 1.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("remixfx_beats_per_minute", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1, 2.0f, "bpm")}), new Auto("remixfx_filter_a_on", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_a_cutoff", new Seg[] {new Seg(0.0f, 0.5f, 1730.0f, 20.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_a_resonance", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_a_type", new Seg[] {new Seg(0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_b_on", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_b_cutoff", new Seg[] {new Seg(0.0f, 0.5f, 20.0f, 20000.0f, 0.0f, 0.0f, 2, 4.0f, null)}), new Auto("remixfx_filter_b_resonance", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_b_type", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("out_gain", new Seg[] {new Seg(0.0f, 0.5f, 0.2239f, 1.0f, 0.0f, 0.0f, 0, 1.0f, null)})}),
        new Style(20, "Pop - Reverb", 4.0f,
                new String[] {"TIME_STRETCHING", "REMIX_FX", "RECEIVE_MIXER"}, new String[] {"TIME_STRETCHING", "REMIX_FX", "RECEIVE_MIXER"},
                new Auto[] {new Auto("ts_rate", new Seg[] {new Seg(0.0f, 1.0f, 1.0f, 2.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("remixfx_beats_per_minute", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1, 2.0f, "bpm")}), new Auto("remixfx_filter_a_on", new Seg[] {new Seg(0.25f, 0.25f, 0.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_a_cutoff", new Seg[] {new Seg(0.25f, 0.75f, 20000.0f, 20.0f, 0.0f, 0.0f, 1, 4.0f, null)}), new Auto("remixfx_filter_a_resonance", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_a_type", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_b_on", new Seg[] {new Seg(0.2813f, 0.2813f, 0.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_b_cutoff", new Seg[] {new Seg(0.2813f, 0.5f, 20.0f, 600.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("remixfx_filter_b_cutoff", new Seg[] {new Seg(0.5f, 0.75f, 600.0f, 927.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("remixfx_filter_b_resonance", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_b_type", new Seg[] {new Seg(0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_reverb_on", new Seg[] {new Seg(0.3125f, 0.3125f, 0.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_reverb_time", new Seg[] {new Seg(0.0f, 0.0f, 6.0f, 6.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_reverb_send", new Seg[] {new Seg(0.3125f, 0.525f, 0.0f, 1.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("remixfx_reverb_dry", new Seg[] {new Seg(0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_reverb_wet", new Seg[] {new Seg(0.0f, 0.0f, 0.8f, 0.8f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_lfo_phase_sync", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("out_gain", new Seg[] {new Seg(0.375f, 0.75f, 1.0f, 0.4217f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("out_gain", new Seg[] {new Seg(0.75f, 1.0f, 0.4217f, 0.0f, 0.0f, 0.0f, 0, 1.0f, null)})},
                new Auto[] {new Auto("ts_rate", new Seg[] {new Seg(0.0f, 1.0f, 2.0f, 1.0f, 0.0f, 0.0f, 0, 1.0f, null)}), new Auto("remixfx_beats_per_minute", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1, 2.0f, "bpm")}), new Auto("remixfx_filter_a_on", new Seg[] {new Seg(0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_a_cutoff", new Seg[] {new Seg(0.25f, 0.75f, 3300.0f, 20.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_a_resonance", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_a_type", new Seg[] {new Seg(0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_b_on", new Seg[] {new Seg(0.25f, 0.25f, 0.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_b_cutoff", new Seg[] {new Seg(0.25f, 0.75f, 20.0f, 20000.0f, 0.0f, 0.0f, 2, 4.0f, null)}), new Auto("remixfx_filter_b_resonance", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("remixfx_filter_b_type", new Seg[] {new Seg(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1, 2.0f, null)}), new Auto("out_gain", new Seg[] {new Seg(0.25f, 0.75f, 0.0f, 1.0f, 0.0f, 0.0f, 1, 2.0f, null)})}),
    };
}
