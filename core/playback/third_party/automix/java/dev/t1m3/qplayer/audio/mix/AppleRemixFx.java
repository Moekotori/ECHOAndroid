package dev.t1m3.qplayer.audio.mix;

public final class AppleRemixFx {

    public static final class State {
        public boolean repeaterOn;
        public float repeaterRate = 1f;
        public float repeaterMix = 1f;
        public boolean filterAOn;

        public boolean filterAHigh;
        public float filterACutoff = 20000f;
        public boolean filterBOn;

        public boolean filterBHigh;
        public float filterBCutoff = 20000f;
        public boolean reverbOn;
        public float reverbSend;
        public boolean flangerOn;
        public float flangerMix;

        void reset() {
            repeaterOn = false;
            repeaterRate = 1f;
            repeaterMix = 1f;
            filterAOn = false;
            filterACutoff = 20000f;
            filterBOn = false;
            filterBCutoff = 20000f;
            reverbOn = false;
            reverbSend = 0f;
            flangerOn = false;
            flangerMix = 0f;
        }
    }

    private AppleRemixFx() {
    }

    private static final int DANCE_REPEATER_ID = 17;

    private static AppleTransitionSpec.Style repeater() {
        return AppleStyleMap.byAppleId(DANCE_REPEATER_ID);
    }

    private static final java.util.Map<String, AppleTransitionSpec.Auto> LANES =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static AppleTransitionSpec.Auto lane(String key) {
        return lane(true, key);
    }

    private static AppleTransitionSpec.Auto lane(boolean outgoing, String key) {
        String ck = (outgoing ? "o:" : "i:") + key;
        AppleTransitionSpec.Auto cached = LANES.get(ck);
        if (cached != null) {
            return cached;
        }
        AppleTransitionSpec.Style s = repeater();
        if (s == null) {
            return null;
        }
        AppleTransitionSpec.Auto[] ops = outgoing ? s.outgoing : s.incoming;
        int n = 0;
        for (AppleTransitionSpec.Auto x : ops) {
            if (key.equals(x.key)) {
                n += x.segs.length;
            }
        }
        if (n == 0) {
            return null;
        }
        AppleTransitionSpec.Seg[] segs = new AppleTransitionSpec.Seg[n];
        int at = 0;
        for (AppleTransitionSpec.Auto x : ops) {
            if (key.equals(x.key)) {
                for (AppleTransitionSpec.Seg seg : x.segs) {
                    segs[at++] = seg;
                }
            }
        }
        AppleTransitionSpec.Auto merged = newMerged(key, segs);
        LANES.put(ck, merged);
        return merged;
    }

    private static AppleTransitionSpec.Auto newMerged(String key, AppleTransitionSpec.Seg[] segs) {
        return AppleTransitionSpec.merged(key, segs);
    }

    private static float vSide(boolean outgoing, String key, float p, float fallback) {
        AppleTransitionSpec.Auto a = lane(outgoing, key);
        return a == null ? fallback : AppleTransitionSpec.valueAt(a, p, 0f);
    }

    private static float v(AppleTransitionSpec.Style s, String key, float p, float fallback) {
        AppleTransitionSpec.Auto a = lane(key);
        return a == null ? fallback : AppleTransitionSpec.valueAt(a, p, 0f);
    }

    public static float reverbTimeSecs() {
        AppleTransitionSpec.Style s = repeater();
        return v(s, "remixfx_reverb_time", 1f, 6f);
    }

    public static float flangerRateHz() {
        return v(repeater(), "remixfx_flanger_rate", 1f, 2f);
    }

    public static float flangerDepth() {
        return v(repeater(), "remixfx_flanger_depth", 1f, 0.32f);
    }

    public static float flangerCenter() {
        return v(repeater(), "remixfx_flanger_center", 1f, 0.51f);
    }

    public static float flangerFeedback() {
        return v(repeater(), "remixfx_flanger_feedback", 1f, 0.52f);
    }

    public static float reverbWet() {
        AppleTransitionSpec.Style s = repeater();
        return v(s, "remixfx_reverb_wet", 1f, 0.7f);
    }

    public static void fillIncoming(State out, float progress) {
        out.reset();
        AppleTransitionSpec.Style s = repeater();
        if (s == null) {
            return;
        }
        float p = progress < 0f ? 0f : (progress > 1f ? 1f : progress);
        out.repeaterOn = vSide(false, "remixfx_repeater_on", p, 0f) > 0.5f;
        out.repeaterRate = vSide(false, "remixfx_repeater_rate", p, 1f);
        out.repeaterMix = vSide(false, "remixfx_repeater_mix", p, 1f);
        out.filterAOn = vSide(false, "remixfx_filter_a_on", p, 0f) > 0.5f;
        out.filterACutoff = vSide(false, "remixfx_filter_a_cutoff", p, 20000f);
        out.filterAHigh = vSide(false, "remixfx_filter_a_type", p, 0f) >= 0.5f;
        out.filterBOn = vSide(false, "remixfx_filter_b_on", p, 0f) > 0.5f;
        out.filterBCutoff = vSide(false, "remixfx_filter_b_cutoff", p, 20000f);
        out.filterBHigh = vSide(false, "remixfx_filter_b_type", p, 0f) >= 0.5f;
        out.reverbOn = vSide(false, "remixfx_reverb_on", p, 0f) > 0.5f;
        out.reverbSend = vSide(false, "remixfx_reverb_send", p, 0f);
        out.flangerOn = vSide(false, "remixfx_flanger_on", p, 0f) > 0.5f;
        out.flangerMix = vSide(false, "remixfx_flanger_mix", p, 0f);
    }

    public static void fill(State out, float progress) {
        out.reset();
        AppleTransitionSpec.Style s = repeater();
        if (s == null) {
            return;
        }
        float p = progress < 0f ? 0f : (progress > 1f ? 1f : progress);
        out.repeaterOn = v(s, "remixfx_repeater_on", p, 0f) > 0.5f;
        out.repeaterRate = v(s, "remixfx_repeater_rate", p, 1f);
        out.repeaterMix = v(s, "remixfx_repeater_mix", p, 1f);
        out.filterAOn = v(s, "remixfx_filter_a_on", p, 0f) > 0.5f;
        out.filterACutoff = v(s, "remixfx_filter_a_cutoff", p, 20000f);
        out.filterAHigh = v(s, "remixfx_filter_a_type", p, 0f) >= 0.5f;
        out.filterBOn = v(s, "remixfx_filter_b_on", p, 0f) > 0.5f;
        out.filterBCutoff = v(s, "remixfx_filter_b_cutoff", p, 20000f);
        out.filterBHigh = v(s, "remixfx_filter_b_type", p, 0f) >= 0.5f;
        out.reverbOn = v(s, "remixfx_reverb_on", p, 0f) > 0.5f;
        out.reverbSend = v(s, "remixfx_reverb_send", p, 0f);
        out.flangerOn = v(s, "remixfx_flanger_on", p, 0f) > 0.5f;
        out.flangerMix = v(s, "remixfx_flanger_mix", p, 0f);
    }
}
