package dev.t1m3.qplayer.audio.mix;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LyricOverlap {

    public static final int MIN_RUN_WORDS = 2;

    public static final class Match {

        public final int aLine;

        public final int bLine;

        public final int aRunStart;

        public final int bRunStart;

        public final int runWords;

        public final int aLineWords;

        public final int bLineWords;

        public final int aHandoverWord;

        public final int bHandoverWord;

        public final double aHandoverSecs;

        public final double aRunStartSecs;

        public final double aRunEndSecs;

        public final double bHandoverSecs;

        Match(int aLine, int bLine, int aRunStart, int bRunStart, int runWords,
              int aLineWords, int bLineWords, int aHandoverWord, int bHandoverWord,
              double aHandoverSecs, double bHandoverSecs,
              double aRunStartSecs, double aRunEndSecs) {
            this.aLine = aLine;
            this.bLine = bLine;
            this.aRunStart = aRunStart;
            this.bRunStart = bRunStart;
            this.runWords = runWords;
            this.aLineWords = aLineWords;
            this.bLineWords = bLineWords;
            this.aHandoverWord = aHandoverWord;
            this.bHandoverWord = bHandoverWord;
            this.aHandoverSecs = aHandoverSecs;
            this.bHandoverSecs = bHandoverSecs;
            this.aRunStartSecs = aRunStartSecs;
            this.aRunEndSecs = aRunEndSecs;
        }

        public boolean strong() {
            return aLine >= 0 && runWords >= MIN_RUN_WORDS;
        }

        public double offsetSecs() {
            return aHandoverSecs - bHandoverSecs;
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT,
                    "repeat A#%d:%d+%d <-> B#%d:%d+%d - handover at word %d/%d and %d/%d"
                            + "(%.3f s / %.3f s, offset %+.3f s)",
                    Integer.valueOf(aLine), Integer.valueOf(aRunStart), Integer.valueOf(runWords),
                    Integer.valueOf(bLine), Integer.valueOf(bRunStart), Integer.valueOf(runWords),
                    Integer.valueOf(aHandoverWord), Integer.valueOf(aLineWords),
                    Integer.valueOf(bHandoverWord), Integer.valueOf(bLineWords),
                    Double.valueOf(aHandoverSecs), Double.valueOf(bHandoverSecs),
                    Double.valueOf(offsetSecs()));
        }
    }

    private static final Match NONE = new Match(-1, -1, 0, 0, 0, 0, 0, 0, 0, 0.0, 0.0, 0.0, 0.0);

    public static Match best(AutoCue.LyricSpans a, double aFrom, double aTo,
                             AutoCue.LyricSpans b, double bFrom, double bTo) {
        if (a == null || b == null || !a.hasTexts() || !b.hasTexts()) {
            return NONE;
        }
        Match best = NONE;
        for (int i = 0; i < a.starts.length; i++) {
            double ta = a.starts[i];
            if (ta > aTo) {
                continue;
            }

            double aEnd = a.ends != null && i < a.ends.length && a.ends[i] > ta
                    ? a.ends[i] : ta;
            if (aEnd < aFrom) {
                continue;
            }
            List<String> wa = englishWords(a.textAt(i));
            if (wa.size() < MIN_RUN_WORDS) {
                continue;
            }
            for (int j = 0; j < b.starts.length; j++) {
                double tb = b.starts[j];
                if (tb > bTo) {
                    continue;
                }
                double bEnd = b.ends != null && j < b.ends.length && b.ends[j] > tb
                        ? b.ends[j] : tb;
                if (bEnd < bFrom) {
                    continue;
                }
                List<String> wb = englishWords(b.textAt(j));
                if (wb.size() < MIN_RUN_WORDS) {
                    continue;
                }
                int[] run = longestCommonRun(wa, wb);
                int words = run[2];
                if (words < MIN_RUN_WORDS) {
                    continue;
                }

                int aHand = run[0] + (words + 1) / 2;
                int bHand = run[1] + (words + 1) / 2;
                double aSecs = wordTimeSecs(ta, aEnd, wa.size(), aHand);
                double bSecs = wordTimeSecs(tb, bEnd, wb.size(), bHand);
                double aRunFrom = wordTimeSecs(ta, aEnd, wa.size(), run[0]);
                double aRunTo = wordTimeSecs(ta, aEnd, wa.size(), run[0] + words);
                boolean better = words > best.runWords
                        || (words == best.runWords && best.aLine < 0);
                if (better) {
                    best = new Match(i, j, run[0], run[1], words, wa.size(), wb.size(),
                            aHand, bHand, aSecs, bSecs, aRunFrom, aRunTo);
                }
            }
        }
        return best;
    }

    static double wordTimeSecs(double lineStart, double lineEnd, int words, int word) {
        if (words <= 0) {
            return lineStart;
        }
        double span = Math.max(0.0, lineEnd - lineStart);
        return lineStart + span * (word / (double) words);
    }

    static List<String> englishWords(String line) {
        List<String> out = new ArrayList<String>();
        if (line == null) {
            return out;
        }
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (isCjk(c)) {
                return new ArrayList<String>();
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (Character.isLetterOrDigit(c) || c == 0x27) {
                sb.append(Character.toLowerCase(c));
            } else {
                if (sb.length() > 0) {
                    out.add(sb.toString());
                    sb.setLength(0);
                }
            }
        }
        if (sb.length() > 0) {
            out.add(sb.toString());
        }
        return out;
    }

    private static boolean isCjk(char c) {
        Character.UnicodeBlock b = Character.UnicodeBlock.of(c);
        return b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || b == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || b == Character.UnicodeBlock.HIRAGANA
                || b == Character.UnicodeBlock.KATAKANA
                || b == Character.UnicodeBlock.HANGUL_SYLLABLES;
    }

    static int[] longestCommonRun(List<String> a, List<String> b) {
        int bestLen = 0;
        int bestA = 0;
        int bestB = 0;
        int[] prev = new int[b.size() + 1];
        int[] cur = new int[b.size() + 1];
        for (int i = 1; i <= a.size(); i++) {
            for (int j = 1; j <= b.size(); j++) {
                if (a.get(i - 1).equals(b.get(j - 1))) {
                    cur[j] = prev[j - 1] + 1;
                    if (cur[j] > bestLen) {
                        bestLen = cur[j];
                        bestA = i - cur[j];
                        bestB = j - cur[j];
                    }
                } else {
                    cur[j] = 0;
                }
            }
            int[] swap = prev;
            prev = cur;
            cur = swap;
            java.util.Arrays.fill(cur, 0);
        }
        return new int[] {bestA, bestB, bestLen};
    }

    private LyricOverlap() {
    }
}
