package dev.t1m3.qplayer.audio.dsp;

import dev.t1m3.qplayer.audio.core.AudioBuf;
import dev.t1m3.qplayer.audio.core.ChannelLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class BinauralRenderer {

    private final int rate;

    private final List<Source> sources;

    private final EarState[] state;

    private final float outputGain;

    public BinauralRenderer(ChannelLayout layout, int rate) {
        this(sourcesFor(layout), rate);
    }

    private BinauralRenderer(List<Source> sources, int rate) {
        if (sources == null) {

            throw new IllegalArgumentException("source table must not be null");
        }
        int r = Math.max(rate, 1);

        int maxDelay = (int) Math.ceil((double) ((float) r * 0.0015f)) + 4;

        List<Source> srcs = new ArrayList<Source>(sources);
        EarState[] st = new EarState[srcs.size() * 2];
        for (int i = 0; i < srcs.size(); i++) {
            Source s = srcs.get(i);

            for (int e = 0; e < 2; e++) {
                boolean earRight = e == 1;
                st[i * 2 + e] = new EarState(
                        new DelayLine(maxDelay),

                        OnePole.lowPass(r, s.cutoffHz(earRight)),
                        s.delaySecs(earRight) * (float) r,
                        s.gain(earRight));
            }
        }

        float extra = Math.max((float) srcs.size() - 2.0f, 0.0f);
        float gain = extra > 0.0f
                ? Gain.dbToLinear(-3.0f * (float) (Math.log((double) (1.0f + extra)) / Math.log(2.0)))
                : 1.0f;

        this.rate = r;
        this.sources = Collections.unmodifiableList(srcs);
        this.state = st;
        this.outputGain = gain;
    }

    public static BinauralRenderer withSources(List<Source> sources, int rate) {
        return new BinauralRenderer(sources, rate);
    }

    public static List<Source> sourcesFor(ChannelLayout layout) {
        switch (layout.count()) {
            case 1:
                return Collections.singletonList(Source.at(0.0f));
            case 2:

                return fixed(Source.at(-30.0f), Source.at(30.0f));
            case 6:

                return fixed(
                        Source.at(-30.0f),
                        Source.at(30.0f),
                        Source.at(0.0f),
                        Source.lfe(),
                        Source.at(-110.0f),
                        Source.at(110.0f));
            case 8:

                return fixed(
                        Source.at(-30.0f),
                        Source.at(30.0f),
                        Source.at(0.0f),
                        Source.lfe(),
                        Source.at(-135.0f),
                        Source.at(135.0f),
                        Source.at(-90.0f),
                        Source.at(90.0f));
            default:

                int n = Math.max(layout.count(), 1);
                List<Source> list = new ArrayList<Source>(n);
                for (int i = 0; i < n; i++) {
                    if (n == 1) {
                        list.add(Source.at(0.0f));
                    } else {
                        float t = (float) i / (float) (n - 1);
                        list.add(Source.at(-180.0f + 360.0f * t));
                    }
                }
                return Collections.unmodifiableList(list);
        }
    }

    private static List<Source> fixed(Source... s) {
        return Collections.unmodifiableList(Arrays.asList(s));
    }

    public List<Source> sources() {
        return sources;
    }

    public float outputGain() {
        return outputGain;
    }

    public int rate() {
        return rate;
    }

    public String describe(ChannelLayout input) {
        int locatable = 0;
        for (int i = 0; i < sources.size(); i++) {
            if (!sources.get(i).nonDirectional) {
                locatable++;
            }
        }
        return "binaural render (sphere-head model, " + input + " -> stereo, " + locatable + " channel localisation)";
    }

    public void reset() {
        for (int i = 0; i < state.length; i++) {
            state[i].delay.reset();
            state[i].shadow.reset();
        }
    }

    public int render(AudioBuf src, AudioBuf out) {
        int frames = Math.min(src.frames(), out.capacityFrames());
        if (frames == 0 || out.channels() == 0) {
            out.setFrames(0);
            return 0;
        }

        int srcCh = src.channels();
        int ns = sources.size();
        int outCh = out.channels();

        float[] srcData = src.backingArray();
        int srcStride = src.capacityFrames();
        float[] outData = out.backingArray();
        int outStride = out.capacityFrames();

        for (int n = 0; n < frames; n++) {
            float l = 0.0f;
            float r = 0.0f;

            for (int i = 0; i < ns; i++) {

                float x = srcData[(i < srcCh ? i : 0) * srcStride + n];

                for (int e = 0; e < 2; e++) {
                    EarState st = state[i * 2 + e];
                    float y = st.delay.process(x, st.delaySamples);
                    y = st.shadow.process(y) * st.gain;
                    if (e == 1) {
                        r += y;
                    } else {
                        l += y;
                    }
                }
            }

            float lo = l * outputGain;
            float ro = r * outputGain;
            if (outCh == 0) {

            } else if (outCh == 1) {

                outData[n] = (lo + ro) * 0.5f;
            } else {
                outData[n] = lo;
                outData[outStride + n] = ro;
            }
        }

        out.setFrames(frames);
        return frames;
    }

    @Override
    public String toString() {
        return "BinauralRenderer{rate=" + rate + ", sources=" + sources.size()
                + ", outputGain=" + outputGain + '}';
    }

    private static final class DelayLine {

        private final float[] buf;
        private int pos;

        DelayLine(int capacity) {

            this.buf = new float[Math.max(capacity, 4)];
            this.pos = 0;
        }

        void reset() {
            Arrays.fill(buf, 0.0f);
            this.pos = 0;
        }

        float process(float x, float d) {
            int n = buf.length;
            buf[pos] = x;

            float dc = d;
            if (dc < 0.0f) {
                dc = 0.0f;
            } else if (dc > (float) (n - 2)) {
                dc = (float) (n - 2);
            }
            float rf = (float) pos - dc;
            while (rf < 0.0f) {
                rf += (float) n;
            }
            int i0 = (int) Math.floor((double) rf);
            float frac = rf - (float) i0;
            float a = buf[i0 % n];
            float b = buf[(i0 + 1) % n];

            pos += 1;
            if (pos == n) {
                pos = 0;
            }
            return a + (b - a) * frac;
        }
    }

    private static final class EarState {

        final DelayLine delay;
        final OnePole shadow;

        final float delaySamples;
        final float gain;

        EarState(DelayLine delay, OnePole shadow, float delaySamples, float gain) {
            this.delay = delay;
            this.shadow = shadow;
            this.delaySamples = delaySamples;
            this.gain = gain;
        }
    }
}
