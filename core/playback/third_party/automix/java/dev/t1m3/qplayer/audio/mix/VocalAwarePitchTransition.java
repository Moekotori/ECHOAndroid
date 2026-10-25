package dev.t1m3.qplayer.audio.mix;

import dev.t1m3.qplayer.audio.core.AudioBuf;
import dev.t1m3.qplayer.audio.dsp.PitchShifter;
import dev.t1m3.qplayer.audio.model.StemSeparator;
import dev.t1m3.qplayer.audio.stem.StemSeparators;
import dev.t1m3.qplayer.util.Logger;

public final class VocalAwarePitchTransition implements Transition {

    private static final long BUDGET_MS = 120_000L;

    private final Transition inner;
    private final int rate;
    private final int channels;
    private final int vocalMode;

    private final boolean autoSeparated;

    private volatile float[][] aInst;
    private volatile float[][] aVoc;
    private volatile float[][] bInst;
    private volatile float[][] bVoc;
    private volatile float semitones;
    private volatile int covered;
    private volatile String note;

    public VocalAwarePitchTransition(Transition inner, int rate, int channels, int vocalMode) {
        this(inner, rate, channels, vocalMode, false);
    }

    public VocalAwarePitchTransition(Transition inner, int rate, int channels, int vocalMode,
                                     boolean autoSeparated) {
        if (inner == null) {
            throw new IllegalArgumentException("wrapped kind must not be null");
        }
        this.inner = inner;
        this.rate = Math.max(rate, 1);
        this.channels = Math.max(channels, 1);
        this.vocalMode = vocalMode;
        this.autoSeparated = autoSeparated;
    }

    public boolean autoSeparated() {
        return autoSeparated;
    }

    public Transition inner() {
        return inner;
    }

    public int vocalMode() {
        return vocalMode;
    }

    public String preparedNote() {
        return note;
    }

    @Override
    public long windowFrames(int sampleRate) {
        return inner.windowFrames(sampleRate);
    }

    @Override
    public String name() {

        return inner.name() + " (vocals keep their pitch)";
    }

    @Override
    public boolean rewritesSamples() {
        return true;
    }

    @Override
    public PitchPlan pitchAt(float progress) {
        return PitchPlan.ZERO;
    }

    @Override
    public TransitionPlan prepare(String outro, String intro, int sampleRate) {
        return prepare(outro, intro, sampleRate, null, null);
    }

    @Override
    public TransitionPlan prepare(String outro, String intro, int sampleRate,
                                  java.util.Map<String, String> outroHeaders,
                                  java.util.Map<String, String> introHeaders) {
        TransitionPlan plan = inner.prepare(outro, intro, sampleRate, outroHeaders, introHeaders);
        long window = inner.windowFrames(sampleRate);

        float shift = plan.pitch == null ? 0.0f : plan.pitch.outro;
        if (window <= 0 || Math.abs(shift) < 0.01f) {
            note = "harmonic blend: no window or no shift amount, this layer does nothing";
            return plan;
        }

        if (!StemSeparators.available(vocalMode)) {
            note = "harmonic blend: cannot get stems (" + StemSeparators.unavailableReason(vocalMode)
                    + ") -> no shift this time (vocals untouched)";
            Logger.warn("[qplayer-engine] {}", note);
            return withoutPitch(plan, note);
        }
        long t0 = System.currentTimeMillis();
        try {

            double fromSecs = windowStartSecs(outro, outroHeaders, plan, window, sampleRate);
            if (fromSecs < 0.0) {
                note = "harmonic blend: cannot read the outgoing track's length, so its tail start is unknown -> no shift this time (vocals untouched)";
                Logger.warn("[qplayer-engine] {}", note);
                return withoutPitch(plan, note);
            }
            float[][] from = Automix.readStereoWindow(outro, outroHeaders,
                    fromSecs, (int) window, sampleRate);
            float[][] to = Automix.readStereoWindow(intro, introHeaders,
                    Math.max(0.0, plan.skipSecs), (int) window, sampleRate);
            if (from == null || to == null) {
                note = "harmonic blend: window material unreadable -> no shift this time (vocals untouched)";
                Logger.warn("[qplayer-engine] {}", note);
                return withoutPitch(plan, note);
            }

            StemSeparator.StemSet setA;
            StemSeparator.StemSet setB;
            if (vocalMode == StemSeparators.DSP) {
                final float[][] fromRef = from;
                final float[][] toRef = to;
                final StemSeparator.StemSet[] slotA = new StemSeparator.StemSet[1];
                Thread worker = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        slotA[0] = separate(fromRef);
                    }
                }, "qplayer-stem-a");
                worker.setDaemon(true);
                worker.start();
                setB = separate(toRef);
                try {
                    worker.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                setA = slotA[0];
            } else {
                setA = separate(from);
                setB = separate(to);
            }
            if (setA == null || setB == null) {
                note = "harmonic blend: vocal removal failed (" + StemSeparators.label(vocalMode) + "）"
                        + " -> no shift this time (vocals untouched)";
                Logger.warn("[qplayer-engine] {}", note);
                return withoutPitch(plan, note);
            }

            int longest = Math.max(lengthOf(stereo(setA.instrumentalLeft, setA.instrumentalRight)),
                    Math.max(lengthOf(stereo(setB.instrumentalLeft, setB.instrumentalRight)),
                            Math.max(lengthOf(stereo(setA.vocalsLeft, setA.vocalsRight)),
                                    lengthOf(stereo(setB.vocalsLeft, setB.vocalsRight)))));
            aInst = padTo(stereo(setA.instrumentalLeft, setA.instrumentalRight), longest);
            aVoc = padTo(stereo(setA.vocalsLeft, setA.vocalsRight), longest);
            bInst = padTo(stereo(setB.instrumentalLeft, setB.instrumentalRight), longest);
            bVoc = padTo(stereo(setB.vocalsLeft, setB.vocalsRight), longest);
            semitones = shift;

            PrepareStage.mark("offline shift (outgoing instrumental)");

            synchronized (shifterLock) {
                aInst = shifted(aInst, shift);
            }
            covered = Math.min(lengthOf(aInst), Math.min(lengthOf(bInst),
                    Math.min(lengthOf(aVoc), lengthOf(bVoc))));
            long ms = System.currentTimeMillis() - t0;
            note = "harmonic blend: shifting only the outgoing instrumental by " + String.format(java.util.Locale.ROOT, "%+.2f", Float.valueOf(shift))
                    + " semitones (the incoming track stays as it is), both vocals stay as they are (" + StemSeparators.label(vocalMode)
                    + (autoSeparated ? ", stem separation is off -> the engine uses DSP automatically" : "")
                    + "，" + ms + " ms）";
            Logger.debug("[qplayer-engine] {}", note);
        } catch (RuntimeException e) {
            clear();
            note = "harmonic blend: preparing the shifted material failed (" + e + ") -> no shift this time (vocals untouched)";
            Logger.warn("[qplayer-engine] {}", note);
        }
        double elapsed = (System.currentTimeMillis() - t0) / 1000.0;
        if (elapsed * 1000.0 > BUDGET_MS) {
            clear();
            note = "harmonic blend: preparing the shifted material blew the budget (" + elapsed + " s) -> no shift this time (vocals untouched)";
        }

        if (aInst == null || bInst == null) {
            return withoutPitch(plan, note);
        }
        return new TransitionPlan(plan.skipSecs, PitchPlan.ZERO, plan.introStartAt, note,
                plan.outroTailSecs);
    }

    @Override
    public int render(AudioBuf out, AudioBuf from, AudioBuf to, float progress) {
        float[][] ai = aInst;
        float[][] av = aVoc;
        float[][] bi = bInst;
        float[][] bv = bVoc;
        if (ai == null || bi == null || av == null || bv == null) {
            return inner.render(out, from, to, progress);
        }
        int capacity = Math.min(out.capacityFrames(), covered - cursor);
        if (capacity <= 0) {
            return inner.render(out, from, to, progress);
        }
        int n = capacity;
        long window = Math.max(inner.windowFrames(rate), 1L);
        float perFrame = 1.0f / (float) window;
        int chs = Math.min(out.channels(), channels);
        for (int ch = 0; ch < chs; ch++) {
            java.nio.FloatBuffer dst = out.channelWriter(ch);
            float[] a = ch < ai.length ? ai[ch] : ai[0];
            float[] avc = ch < av.length ? av[ch] : av[0];
            float[] b = ch < bi.length ? bi[ch] : bi[0];
            float[] bvc = ch < bv.length ? bv[ch] : bv[0];
            for (int i = 0; i < n; i++) {
                int idx = cursor + i;
                float p = Automix.minF32(progress + (float) i * perFrame, 1.0f);
                float g = Automix.smoothstep(0.0f, 1.0f, p);
                double outGain = Math.cos(g * Math.PI / 2);
                double inGain = Math.sin(g * Math.PI / 2);

                dst.put(i, (float) (a[idx] * outGain + b[idx] * inGain
                        + avc[idx] * outGain + bvc[idx] * inGain));
            }
        }
        out.setFrames(n);
        cursor += n;
        return n;
    }

    private int cursor;
    private final Object shifterLock = new Object();

    private void clear() {
        aInst = null;
        aVoc = null;
        bInst = null;
        bVoc = null;
        covered = 0;
        cursor = 0;
    }

    private static double windowStartSecs(String outro, java.util.Map<String, String> headers,
                                          TransitionPlan plan, long window, int sampleRate) {
        try {
            double windowSecs = (double) window / (double) Math.max(sampleRate, 1);
            Automix.Mono tail = Automix.readMono(outro, headers, true, windowSecs);
            if (tail.durationSecs > 0.0) {
                return Math.max(0.0, tail.durationSecs - plan.outroTailSecs - windowSecs);
            }
        } catch (RuntimeException e) {
            Logger.warn("harmonic blend: cannot read the outgoing track length ({})", e.toString());
        }
        return -1.0;
    }

    private static TransitionPlan withoutPitch(TransitionPlan plan, String note) {
        return new TransitionPlan(plan.skipSecs, PitchPlan.ZERO, plan.introStartAt, note,
                plan.outroTailSecs);
    }

    private static int measuredLag(AudioBuf in, AudioBuf out, int total, int written, int guess) {
        int usable = Math.min(total, written);
        int search = 4096;
        if (usable < 4 * search) {
            return guess;
        }
        float[] a = flatten(in, 0);
        float[] b = flatten(out, 0);
        int best = guess;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int lag = -search; lag <= search; lag += 8) {
            double score = correlation(a, b, lag);
            if (score > bestScore) {
                bestScore = score;
                best = lag;
            }
        }
        for (int lag = best - 8; lag <= best + 8; lag++) {
            double score = correlation(a, b, lag);
            if (score > bestScore) {
                bestScore = score;
                best = lag;
            }
        }
        return best;
    }

    private static float[] flatten(AudioBuf buf, int channel) {
        int frames = buf.frames();
        float[] out = new float[frames];
        java.nio.FloatBuffer r = buf.channel(Math.min(channel, buf.channels() - 1));
        for (int i = 0; i < frames; i++) {
            out[i] = r.get(i);
        }
        return out;
    }

    private static double correlation(float[] a, float[] b, int lag) {
        double num = 0.0;
        double na = 0.0;
        double nb = 0.0;
        for (int i = 0; i < a.length; i += 64) {
            int j = i + lag;
            if (j < 0 || j >= b.length) {
                continue;
            }
            num += (double) a[i] * b[j];
            na += (double) a[i] * a[i];
            nb += (double) b[j] * b[j];
        }
        double den = Math.sqrt(na * nb);
        return den > 0.0 ? num / den : Double.NEGATIVE_INFINITY;
    }

    private static float[][] padTo(float[][] pcm, int frames) {
        if (pcm.length == 0 || pcm[0].length >= frames) {
            return pcm;
        }
        float[][] out = new float[pcm.length][frames];
        for (int ch = 0; ch < pcm.length; ch++) {
            System.arraycopy(pcm[ch], 0, out[ch], 0, pcm[ch].length);
        }
        return out;
    }

    private static int lengthOf(float[][] pcm) {
        return pcm == null || pcm.length == 0 ? 0 : pcm[0].length;
    }

    private static float[][] stereo(float[] left, float[] right) {
        return new float[][]{left, right};
    }

    private StemSeparator.StemSet separate(float[][] pcm) {
        StemSeparator sep = null;
        try {

            PrepareStage.mark("vocal removal / stems (" + StemSeparators.label(vocalMode) + "）");
            sep = StemSeparators.forMode(vocalMode);
            return sep.separate(pcm[0], pcm[1], rate);
        } catch (RuntimeException e) {
            Logger.warn("harmonic blend: separation failed ({})", e.toString());
            return null;
        } finally {
            if (sep != null) {
                try {
                    sep.close();
                } catch (RuntimeException ignored) {

                }
            }
        }
    }

    private float[][] shifted(float[][] pcm, float semitones) {
        int lag = Math.max(0, shifterLag(semitones));
        return shiftRaw(pcm, semitones, lag, lag);
    }

    private volatile long shifterLagKey = Long.MIN_VALUE;
    private volatile int shifterLagFrames;

    private int shifterLag(float semitones) {
        long key = (long) Float.floatToIntBits(semitones);
        if (shifterLagKey == key) {
            return shifterLagFrames;
        }
        int measured = measureShifterLag(semitones);
        shifterLagFrames = measured;
        shifterLagKey = key;
        Logger.debug("harmonic blend: measured pitch-shifter offset {} frames ({} ms, {} semitones)",
                Integer.valueOf(measured),
                String.format(java.util.Locale.ROOT, "%.2f",
                        Double.valueOf(measured * 1000.0 / Math.max(1, rate))),
                Float.valueOf(semitones));
        return measured;
    }

    private int measureShifterLag(float semitones) {
        int probeFrames = (int) Math.max(1L, Math.round(0.5 * rate));
        int pulseAt = (int) Math.round(0.1 * rate);
        float[][] probe = new float[2][probeFrames];
        for (int i = -4; i <= 4; i++) {
            int at = pulseAt + i;
            if (at >= 0 && at < probeFrames) {
                probe[0][at] = 0.6f * (1.0f - Math.abs(i) / 6.0f);
            }
        }
        float[][] shiftedProbe = shiftRaw(probe, semitones, 0, 0);
        long best = -1L;
        float peak = 0.0f;
        for (int i = 0; i < shiftedProbe[0].length; i++) {
            float v = Math.abs(shiftedProbe[0][i]);
            if (v > peak) {
                peak = v;
                best = i;
            }
        }
        if (best < 0L || peak <= 1e-6f) {
            return 0;
        }

        int half = (int) Math.round(0.04 * rate);
        int from = (int) Math.max(0L, best - half);
        int to = (int) Math.min(shiftedProbe[0].length, best + half);
        double num = 0.0;
        double den = 0.0;
        for (int i = from; i < to; i++) {
            double e = (double) shiftedProbe[0][i] * shiftedProbe[0][i];
            num += e * i;
            den += e;
        }
        double centroid = den > 0.0 ? num / den : best;
        return (int) Math.round(centroid - pulseAt);
    }

    private float[][] shiftRaw(float[][] pcm, float semitones, int extraLead, int skip) {
        PitchShifter ps = new PitchShifter(2, rate, semitones, 4096);
        try {
            int n = pcm[0].length;
            int total = n + Math.max(0, extraLead);
            AudioBuf in = new AudioBuf(dev.t1m3.qplayer.audio.core.ChannelLayout.STEREO, total);
            for (int ch = 0; ch < 2; ch++) {
                java.nio.FloatBuffer w = in.channelWriter(ch);
                for (int i = 0; i < n; i++) {
                    w.put(i, pcm.length > ch ? pcm[ch][i] : pcm[0][i]);
                }
                for (int i = n; i < total; i++) {
                    w.put(i, 0.0f);
                }
            }
            in.setFrames(total);
            AudioBuf out = new AudioBuf(dev.t1m3.qplayer.audio.core.ChannelLayout.STEREO, total);
            int fed = 0;
            int written = 0;
            while (written < total) {
                int before = fed;
                while (fed < total) {
                    int took = ps.pushFrom(in, fed);
                    if (took <= 0) {
                        break;
                    }
                    fed += took;
                }
                int got = fed >= total ? ps.drain(out, written) : ps.pull(out, written);
                written += got;
                if (got <= 0 && fed == before) {
                    break;
                }
            }
            float[][] result = new float[2][n];
            float[] backing = out.backingArray();
            for (int ch = 0; ch < 2; ch++) {
                int base = out.channelBase(ch);
                for (int i = 0; i < n; i++) {
                    int src = i + skip;
                    result[ch][i] = src >= 0 && src < written ? backing[base + src] : 0.0f;
                }
            }
            if (written < total) {
                Logger.warn("the shift produced only {} / {} frames", Integer.valueOf(written),
                        Integer.valueOf(total));
            }
            return result;
        } catch (RuntimeException e) {
            Logger.warn("shift failed ({}), this track stays as it is", e.toString());
            return pcm;
        }
    }

}
