package dev.t1m3.qplayer.audio.mix;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class StemMaterials {

    static final int MAX_ENTRIES = 32;

    private static final Map<String, StemMaterial> CACHE =
            new LinkedHashMap<String, StemMaterial>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, StemMaterial> eldest) {
                    return size() > MAX_ENTRIES;
                }
            };

    private static final AtomicLong SEPARATIONS = new AtomicLong();

    private StemMaterials() {
    }

    public static long separations() {
        return SEPARATIONS.get();
    }

    public static void reset() {
        synchronized (CACHE) {
            CACHE.clear();
        }
        SEPARATIONS.set(0L);
    }

    public static StemMaterial forWindow(String path, Map<String, String> headers,
                                        double startSecs, int frames, int rate, int mode) {
        if (path == null || frames <= 0 || rate <= 0) {
            return null;
        }
        String key = path + '\u0000' + startSecs + '\u0000' + frames + '\u0000' + rate
                + '\u0000' + mode;
        synchronized (CACHE) {
            StemMaterial cached = CACHE.get(key);
            if (cached != null) {
                return cached;
            }
        }
        float[][] stems = VocalSeparationTransition.stemsWindow(
                path, headers, startSecs, frames, rate, mode);
        if (stems == null || stems.length < 4 || stems[0] == null) {
            return null;
        }
        SEPARATIONS.incrementAndGet();
        float[] vocals = new float[stems[2].length];
        for (int i = 0; i < vocals.length; i++) {
            vocals[i] = 0.5f * (stems[2][i] + stems[3][i]);
        }
        float[][] instrumental = new float[][] {stems[0], stems[1]};
        StemMaterial material = new StemMaterial(instrumental, vocals);
        synchronized (CACHE) {
            CACHE.put(key, material);
        }
        return material;
    }
}
