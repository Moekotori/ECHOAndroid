package dev.t1m3.qplayer.audio.mix;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class FlowPlanner {

    public static final float[] CURVE_AT = {
            0.00f, 0.15f, 0.22f, 0.32f, 0.40f, 0.55f, 0.65f, 0.80f, 1.00f
    };

    public static final float[] CURVE_HEIGHT = {
            0.10f, 0.60f, 0.25f, 0.45f, 0.35f, 0.75f, 0.40f, 1.00f, 0.05f
    };

    static final float CURVE_FLOOR = 10.0f;

    static final float CURVE_SPAN = 80.0f;

    static final float W_FIT = 1.5f;

    static final float W_TEMPO = 1.0f;

    static final float W_LEVEL = 0.5f;

    static final float TEMPO_SCALE = 100.0f;

    static final float LEVEL_SCALE = 50.0f;

    static final float FIT_W_TEMPO = 0.4f;

    static final float FIT_W_LEVEL = 0.4f;

    static final float FIT_W_TONE = 0.2f;

    private FlowPlanner() {
    }

    public static final class Plan {

        public final int[] order;

        public final int movable;

        public final float[] idealCurve;

        public final float[] actualCurve;

        public final double cost;

        Plan(int[] order, int movable, float[] idealCurve, float[] actualCurve, double cost) {
            this.order = order;
            this.movable = movable;
            this.idealCurve = idealCurve;
            this.actualCurve = actualCurve;
            this.cost = cost;
        }

        public boolean isIdentity() {
            for (int i = 0; i < order.length; i++) {
                if (order[i] != i) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT,
                    "Plan { %d tracks placeable, cost %.2f, order %s }", movable, cost,
                    java.util.Arrays.toString(order));
        }
    }

    public static Plan plan(List<FlowFeatures> slots, FlowFeatures anchor) {
        int n = slots == null ? 0 : slots.size();
        int[] order = new int[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        if (n == 0) {
            return new Plan(order, 0, new float[0], new float[0], 0.0);
        }

        List<Integer> movable = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (slots.get(i) != null) {
                movable.add(i);
            }
        }
        int m = movable.size();
        if (m < 2) {

            return new Plan(order, m, new float[m], new float[m], 0.0);
        }

        float[] fit = fitScores(slots, movable);
        float[] ideal = idealCurve(m);
        boolean[] used = new boolean[m];
        int[] pick = new int[m];

        int first = -1;
        double firstCost = Double.MAX_VALUE;
        for (int j = 0; j < m; j++) {
            double c = W_FIT * Math.abs(fit[j] - ideal[0]) + transitionCost(anchor, slots.get(movable.get(j)));
            if (c < firstCost) {
                firstCost = c;
                first = j;
            }
        }
        pick[0] = first;
        used[first] = true;

        double total = firstCost;
        FlowFeatures prev = slots.get(movable.get(first));
        for (int pos = 1; pos < m; pos++) {
            int best = -1;
            double bestCost = Double.MAX_VALUE;
            for (int j = 0; j < m; j++) {
                if (used[j]) {
                    continue;
                }
                FlowFeatures candidate = slots.get(movable.get(j));

                double c = W_FIT * Math.abs(fit[j] - ideal[pos]) + transitionCost(prev, candidate);
                if (c < bestCost) {
                    bestCost = c;
                    best = j;
                }
            }
            pick[pos] = best;
            used[best] = true;
            total += bestCost;
            prev = slots.get(movable.get(best));
        }

        float[] actual = new float[m];
        for (int pos = 0; pos < m; pos++) {
            order[movable.get(pos)] = movable.get(pick[pos]);
            actual[pos] = fit[pick[pos]];
        }
        return new Plan(order, m, ideal, actual, total);
    }

    public static double costOf(List<FlowFeatures> ordered, FlowFeatures anchor) {
        if (ordered == null || ordered.isEmpty()) {
            return 0.0;
        }
        int n = ordered.size();
        float[] ideal = idealCurve(n);
        List<Integer> all = new ArrayList<>(n);
        List<FlowFeatures> filled = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            FlowFeatures f = ordered.get(i);

            filled.add(f == null ? ZERO : f);
            all.add(i);
        }
        float[] fit = fitScores(filled, all);

        double total = 0.0;
        FlowFeatures prev = anchor;
        for (int i = 0; i < n; i++) {
            FlowFeatures f = ordered.get(i);
            if (f != null) {
                total += W_FIT * Math.abs(fit[i] - ideal[i]);
            }
            total += transitionCost(prev, f);
            prev = f;
        }
        return total;
    }

    private static final FlowFeatures ZERO =
            new FlowFeatures(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0);

    public static float[] idealCurve(int n) {
        float[] out = new float[Math.max(n, 0)];
        for (int i = 0; i < out.length; i++) {
            float x = (n <= 1) ? 0.5f : (float) i / (float) (n - 1);
            out[i] = curveAt(x) * CURVE_SPAN + CURVE_FLOOR;
        }
        return out;
    }

    static float curveAt(float x) {
        if (x <= CURVE_AT[0]) {
            return CURVE_HEIGHT[0];
        }
        int last = CURVE_AT.length - 1;
        if (x >= CURVE_AT[last]) {
            return CURVE_HEIGHT[last];
        }
        for (int i = 0; i < last; i++) {
            float left = CURVE_AT[i];
            float right = CURVE_AT[i + 1];
            if (x >= left && x <= right) {
                float t = (x - left) / Math.max(1e-9f, right - left);
                return CURVE_HEIGHT[i] * (1.0f - t) + CURVE_HEIGHT[i + 1] * t;
            }
        }
        return CURVE_HEIGHT[last];
    }

    static float[] fitScores(List<FlowFeatures> slots, List<Integer> movable) {
        float maxTempo = 1.0f;
        float maxLevel = 1e-9f;
        float maxTone = 1e-9f;
        for (int j = 0; j < movable.size(); j++) {
            FlowFeatures f = slots.get(movable.get(j));
            maxTempo = Math.max(maxTempo, f.tempo);
            maxLevel = Math.max(maxLevel, f.level);
            maxTone = Math.max(maxTone, f.tone);
        }
        float[] out = new float[movable.size()];
        for (int j = 0; j < out.length; j++) {
            FlowFeatures f = slots.get(movable.get(j));
            float score = FIT_W_TEMPO * (f.tempo / maxTempo)
                    + FIT_W_LEVEL * (f.level / maxLevel)
                    + FIT_W_TONE * (f.tone / maxTone);
            out[j] = clamp(score * 100.0f, 0.0f, 100.0f);
        }
        return out;
    }

    static double transitionCost(FlowFeatures prev, FlowFeatures next) {
        if (prev == null || next == null) {
            return 0.0;
        }
        double cost = 0.0;
        float a = prev.outgoingTempo();
        float b = next.incomingTempo();
        if (a > 0.0f && b > 0.0f) {
            float hi = Math.max(a, b);
            float lo = Math.max(Math.min(a, b), 1e-3f);
            float ratio = hi / lo;
            cost += W_TEMPO * Math.min(Math.abs(ratio - 1.0f), Math.abs(ratio - 2.0f)) * TEMPO_SCALE;
        }
        float outLevel = prev.outgoingLevel();
        if (!Float.isNaN(outLevel)) {
            cost += W_LEVEL * Math.abs(next.incomingLevel() - outLevel) * LEVEL_SCALE;
        }
        return cost;
    }

    private static float clamp(float v, float lo, float hi) {
        if (v < lo) {
            return lo;
        }
        if (v > hi) {
            return hi;
        }
        return v;
    }
}
