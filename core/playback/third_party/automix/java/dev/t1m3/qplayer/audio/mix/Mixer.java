package dev.t1m3.qplayer.audio.mix;

import dev.t1m3.qplayer.audio.core.AudioBuf;

import java.nio.FloatBuffer;

public final class Mixer {

    private static final float F32_EPSILON = 1.1920929E-7f;

    private Mixer() {
    }

    public static final class Gains {
        public final float from;
        public final float to;

        Gains(float from, float to) {
            this.from = from;
            this.to = to;
        }

        @Override
        public String toString() {
            return "Gains{from=" + from + ", to=" + to + '}';
        }
    }

    public static Gains equalPowerGains(float progress) {
        float t = progress < 0f ? 0f : (progress > 1f ? 1f : progress);
        float angle = t * (float) (Math.PI / 2.0);
        return new Gains((float) Math.cos(angle), (float) Math.sin(angle));
    }

    public static void mapChannels(AudioBuf src, AudioBuf dst) {
        int frames = Math.min(src.frames(), dst.capacityFrames());
        int inCh = src.channels();
        int outCh = dst.channels();
        dst.setFrames(frames);
        if (frames == 0) {
            return;
        }

        if (inCh == outCh) {
            for (int c = 0; c < inCh; c++) {
                copyInto(src.channel(c), dst.channelWriter(c), frames);
            }
            return;
        }

        if (inCh == 1) {

            FloatBuffer mono = src.channel(0);
            for (int c = 0; c < outCh; c++) {
                copyInto(mono, dst.channelWriter(c), frames);
            }
            return;
        }

        if (outCh == 1) {

            float inv = 1f / inCh;
            FloatBuffer dstCh = dst.channelWriter(0);
            for (int i = 0; i < frames; i++) {
                float acc = 0f;
                for (int c = 0; c < inCh; c++) {
                    acc += src.channel(c).get(i);
                }
                dstCh.put(i, acc * inv);
            }
            return;
        }

        int copy = Math.min(outCh, inCh);
        for (int c = 0; c < copy; c++) {
            copyInto(src.channel(c), dst.channelWriter(c), frames);
        }
        for (int c = copy; c < outCh; c++) {
            FloatBuffer dstCh = dst.channelWriter(c);
            for (int i = 0; i < frames; i++) {
                dstCh.put(i, 0f);
            }
        }
    }

    public static void applyStereoWidth(AudioBuf buf, int frames, float midGain, float sideGain) {
        if (buf == null || buf.channels() < 2 || frames <= 0) {
            return;
        }
        if (Math.abs(midGain - 1.0f) < F32_EPSILON && Math.abs(sideGain - 1.0f) < F32_EPSILON) {
            return;
        }
        int n = Math.min(frames, buf.frames());
        FloatBuffer left = buf.channel(0);
        FloatBuffer right = buf.channel(1);
        for (int i = 0; i < n; i++) {
            float l = left.get(i);
            float r = right.get(i);
            float m = (l + r) * 0.5f;
            float sd = (l - r) * 0.5f;
            left.put(i, m * midGain + sd * sideGain);
            right.put(i, m * midGain - sd * sideGain);
        }
    }

    public static void copyWithWidth(AudioBuf src, AudioBuf dst, int frames,
                                     float midGain, float sideGain) {
        int n = Math.min(Math.min(frames, src.frames()), dst.capacityFrames());
        int chs = Math.min(src.channels(), dst.channels());
        if (n <= 0) {
            dst.setFrames(0);
            return;
        }
        boolean width = chs >= 2
                && (Math.abs(midGain - 1.0f) > F32_EPSILON
                        || Math.abs(sideGain - 1.0f) > F32_EPSILON);
        if (!width) {
            for (int c = 0; c < chs; c++) {
                FloatBuffer in = src.channel(c);
                FloatBuffer out = dst.channelWriter(c);
                for (int i = 0; i < n; i++) {
                    out.put(i, in.get(i));
                }
            }
            for (int c = chs; c < dst.channels(); c++) {
                FloatBuffer out = dst.channelWriter(c);
                for (int i = 0; i < n; i++) {
                    out.put(i, 0.0f);
                }
            }
            dst.setFrames(n);
            return;
        }
        FloatBuffer sl = src.channel(0);
        FloatBuffer sr = src.channel(1);
        FloatBuffer dl = dst.channelWriter(0);
        FloatBuffer dr = dst.channelWriter(1);
        for (int i = 0; i < n; i++) {
            float l = sl.get(i);
            float r = sr.get(i);
            float m = (l + r) * 0.5f;
            float sd = (l - r) * 0.5f;
            dl.put(i, m * midGain + sd * sideGain);
            dr.put(i, m * midGain - sd * sideGain);
        }
        for (int c = 2; c < chs; c++) {
            FloatBuffer in = src.channel(c);
            FloatBuffer out = dst.channelWriter(c);
            for (int i = 0; i < n; i++) {
                out.put(i, in.get(i));
            }
        }
        dst.setFrames(n);
    }

    public static int mixTwo(AudioBuf out, AudioBuf a, float ga, AudioBuf b, float gb) {
        int outCh = out.channels();
        int aOk = a.frames();
        int bOk = b.frames();
        int frames = Math.min(Math.max(aOk, bOk), out.capacityFrames());
        if (frames == 0) {
            out.setFrames(0);
            return 0;
        }

        int both = Math.min(Math.min(aOk, bOk), frames);
        boolean aLonger = aOk >= bOk;

        for (int c = 0; c < outCh; c++) {
            FloatBuffer sa = a.channel(c);
            FloatBuffer sb = b.channel(c);
            FloatBuffer dst = out.channelWriter(c);

            for (int i = 0; i < both; i++) {
                dst.put(i, sa.get(i) * ga + sb.get(i) * gb);
            }

            if (aLonger) {
                for (int i = both; i < frames; i++) {
                    dst.put(i, sa.get(i) * ga);
                }
            } else {
                for (int i = both; i < frames; i++) {
                    dst.put(i, sb.get(i) * gb);
                }
            }
        }

        out.setFrames(frames);
        return frames;
    }

    public static int mixTwoMatched(AudioBuf out, AudioBuf a, float ga, AudioBuf b, float gb,
                                    LevelMatcher matcher, float strength) {
        return mixTwoMatched(out, a, ga, b, gb, matcher, strength, null, 0.0f);
    }

    public static int mixTwoMatched(AudioBuf out, AudioBuf a, float ga, AudioBuf b, float gb,
                                    LevelMatcher matcher, float strength,
                                    LowDucker ducker, float duckStrength) {
        boolean level = matcher != null && strength > 0.0f;
        boolean duck = ducker != null && duckStrength > 0.0f;
        if (!level && !duck) {
            return mixTwo(out, a, ga, b, gb);
        }
        int outCh = out.channels();
        int aOk = a.frames();
        int bOk = b.frames();
        int frames = Math.min(Math.max(aOk, bOk), out.capacityFrames());
        if (frames == 0) {
            out.setFrames(0);
            return 0;
        }

        int both = Math.min(Math.min(aOk, bOk), frames);
        boolean aLonger = aOk >= bOk;

        float[] gains = level ? matcher.buffer(both) : null;
        if (level) {
            int gainCh = Math.min(a.channels(), b.channels());
            for (int i = 0; i < both; i++) {
                float pa = 0.0f;
                float pb = 0.0f;
                for (int c = 0; c < gainCh; c++) {
                    float va = a.channel(c).get(i);
                    float vb = b.channel(c).get(i);
                    pa += va * va;
                    pb += vb * vb;
                }
                float inv = gainCh > 0 ? 1.0f / gainCh : 0.0f;
                gains[i] = matcher.gainForFrame(pa * inv, pb * inv, strength);
            }
        }
        float[] duckGains = duck ? ducker.buffer(frames) : null;

        for (int c = 0; c < outCh; c++) {
            FloatBuffer sa = a.channel(c);
            FloatBuffer sb = b.channel(c);
            FloatBuffer dst = out.channelWriter(c);

            for (int i = 0; i < both; i++) {
                float va = sa.get(i);
                float vb = sb.get(i);
                if (duck) {

                    float loA = ducker.lowOf(true, c, va);
                    float loB = ducker.lowOf(false, c, vb);
                    float g;
                    if (c == 0) {
                        ducker.track(loA, loB);
                        g = ducker.gainForFrame(duckStrength);
                        duckGains[i] = g;
                    } else {
                        g = duckGains[i];
                    }
                    va = (va - loA) + loA * g;
                    vb = (vb - loB) + loB * g;
                }
                dst.put(i, va * ga + vb * gb * (level ? gains[i] : 1.0f));
            }

            if (aLonger) {
                for (int i = both; i < frames; i++) {
                    float va = sa.get(i);
                    if (duck) {
                        float loA = ducker.lowOf(true, c, va);
                        float loB = ducker.lowOf(false, c, 0.0f);
                        float g;
                        if (c == 0) {
                            ducker.track(loA, loB);
                            g = ducker.gainForFrame(duckStrength);
                            duckGains[i] = g;
                        } else {
                            g = duckGains[i];
                        }
                        va = (va - loA) + loA * g;
                    }
                    dst.put(i, va * ga);
                }
            } else {
                for (int i = both; i < frames; i++) {
                    float vb = sb.get(i);
                    if (duck) {
                        float loA = ducker.lowOf(true, c, 0.0f);
                        float loB = ducker.lowOf(false, c, vb);
                        float g;
                        if (c == 0) {
                            ducker.track(loA, loB);
                            g = ducker.gainForFrame(duckStrength);
                            duckGains[i] = g;
                        } else {
                            g = duckGains[i];
                        }
                        vb = (vb - loB) + loB * g;
                    }
                    dst.put(i, vb * gb);
                }
            }
        }

        out.setFrames(frames);
        return frames;
    }

    public static int gainInto(AudioBuf out, AudioBuf src, float g) {
        int outCh = out.channels();
        int frames = Math.min(src.frames(), out.capacityFrames());
        boolean unity = Math.abs(g - 1f) < F32_EPSILON;
        for (int c = 0; c < outCh; c++) {
            FloatBuffer s = src.channel(c);
            FloatBuffer dst = out.channelWriter(c);
            if (unity) {
                copyInto(s, dst, frames);
            } else {
                for (int i = 0; i < frames; i++) {
                    dst.put(i, s.get(i) * g);
                }
            }
        }
        out.setFrames(frames);
        return frames;
    }

    private static void copyInto(FloatBuffer src, FloatBuffer dst, int frames) {
        for (int i = 0; i < frames; i++) {
            dst.put(i, src.get(i));
        }
    }
}
