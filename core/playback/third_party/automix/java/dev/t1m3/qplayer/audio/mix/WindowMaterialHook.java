package dev.t1m3.qplayer.audio.mix;

import java.util.Map;

public final class WindowMaterialHook implements MaterialHook {

    private final WindowTicket ticket;

    private final java.util.Map<Long, dev.t1m3.qplayer.audio.model.StemSeparator.StemSet>
            stemCache = new java.util.LinkedHashMap<Long,
                    dev.t1m3.qplayer.audio.model.StemSeparator.StemSet>(8, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(
                        java.util.Map.Entry<Long,
                                dev.t1m3.qplayer.audio.model.StemSeparator.StemSet> eldest) {
                    return size() > 8;
                }
            };

    private final double[] duckRanges;
    private final int vocalMode;
    private final double anchorSecs;
    private final double targetLufs;

    public WindowMaterialHook(double[] duckRanges, double anchorSecs, int vocalMode,
                              double targetLufs) {
        this.duckRanges = duckRanges == null ? null : duckRanges.clone();
        this.anchorSecs = anchorSecs;
        this.vocalMode = vocalMode;
        this.targetLufs = targetLufs;
        this.ticket = null;
        this.rate = 48000;
    }

    private final int rate;

    public WindowMaterialHook(WindowTicket ticket, int vocalMode) {
        this(ticket, vocalMode, 48000);
    }

    public WindowMaterialHook(WindowTicket ticket, int vocalMode, int rate) {
        this.rate = rate > 0 ? rate : 48000;
        this.ticket = ticket;
        this.vocalMode = vocalMode;
        this.duckRanges = null;
        this.anchorSecs = ticket == null ? 0.0 : ticket.anchorSecs;
        this.targetLufs = Double.NaN;
    }

    @Override
    public void onDecoded(dev.t1m3.qplayer.audio.core.AudioBuf buf, int frames,
                          double startSecs) {
        WindowTicket t = ticket;
        if (t == null || buf == null || frames <= 0 || !t.doesAnything()) {
            return;
        }
        int chans = buf.channels();
        int cap = buf.capacityFrames();
        float[] data = buf.backingArray();
        if (chans <= 0 || cap <= 0 || data == null || data.length < chans * cap) {
            return;
        }
        float[][] planar = new float[chans][];
        for (int c = 0; c < chans; c++) {
            planar[c] = java.util.Arrays.copyOfRange(data, c * cap, c * cap + frames);
        }

        t.applyLoudness(planar, frames);

        if (vocalMode != 0 && chans >= 2) {
            dev.t1m3.qplayer.audio.model.StemSeparator sep = separatorFor(vocalMode);
            if (sep != null) {

                long key = Math.round(startSecs * 1000.0);
                dev.t1m3.qplayer.audio.model.StemSeparator.StemSet cached;
                synchronized (stemCache) {
                    cached = stemCache.get(Long.valueOf(key));
                }
                if (cached == null) {
                    try {
                        cached = sep.separate(planar[0], planar[1], rate);
                    } catch (RuntimeException e) {
                        cached = null;
                    }
                    if (cached != null) {
                        separations++;
                        synchronized (stemCache) {
                            stemCache.put(Long.valueOf(key), cached);
                        }
                    }
                }
                if (cached != null) {
                    applyVocalsWith(t, planar, frames, startSecs, cached);
                }
            }
        }
        for (int c = 0; c < chans; c++) {
            System.arraycopy(planar[c], 0, data, c * cap, frames);
        }
    }

    private void applyVocalsWith(WindowTicket t, float[][] planar, int frames, double startSecs,
                                 dev.t1m3.qplayer.audio.model.StemSeparator.StemSet set) {
        if (set == null || set.vocalsLeft == null || set.instrumentalLeft == null) {
            return;
        }
        float[] left = planar[0];
        float[] right = planar.length > 1 ? planar[1] : null;
        int n = Math.min(frames, Math.min(left.length, set.vocalsLeft.length));
        for (int i = 0; i < n; i++) {
            double timeline = (startSecs - t.anchorSecs) + i / (double) rate;
            float gain = t.vocalGainAt(timeline, rate);
            if (gain == 1.0f) {
                continue;
            }
            float vocL = set.vocalsLeft[i];
            float vocR = set.vocalsRight == null ? vocL : set.vocalsRight[i];
            float insL = set.instrumentalLeft[i];
            float insR = set.instrumentalRight == null ? insL : set.instrumentalRight[i];
            left[i] = (float) (insL + vocL * gain);
            if (right != null) {
                right[i] = (float) (insR + vocR * gain);
            }
        }
    }

    private static dev.t1m3.qplayer.audio.model.StemSeparator separatorFor(int mode) {
        try {
            return dev.t1m3.qplayer.audio.stem.StemSeparators.forMode(mode);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private long separations;

    long separations() {
        return separations;
    }

    double[] duckRanges() {
        return duckRanges == null ? null : duckRanges.clone();
    }

    public boolean doesAnything() {
        boolean duck = vocalMode != 0 && duckRanges != null && duckRanges.length >= 2;
        return duck || !Double.isNaN(targetLufs);
    }

    @Override
    public float[][] transform(String source, Map<String, String> headers,
                               double startSecs, int frames, int rate) {
        if (!doesAnything()) {
            return null;
        }
        return WindowMaterial.process(source, headers, startSecs, frames, rate, vocalMode,
                duckRanges, anchorSecs, targetLufs);
    }
}
