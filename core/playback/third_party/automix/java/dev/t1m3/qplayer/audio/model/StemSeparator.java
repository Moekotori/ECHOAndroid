package dev.t1m3.qplayer.audio.model;

public interface StemSeparator extends AutoCloseable {
    StemSet separate(float[] left, float[] right, int sampleRate);

    @Override
    void close();

    final class StemSet {
        public final float[] vocalsLeft;
        public final float[] vocalsRight;
        public final float[] instrumentalLeft;
        public final float[] instrumentalRight;

        public StemSet(
                float[] vocalsLeft,
                float[] vocalsRight,
                float[] instrumentalLeft,
                float[] instrumentalRight) {
            int n = vocalsLeft == null ? 0 : vocalsLeft.length;
            if (vocalsRight == null
                    || vocalsRight.length != n
                    || instrumentalLeft == null
                    || instrumentalLeft.length != n
                    || instrumentalRight == null
                    || instrumentalRight.length != n) {
                throw new IllegalArgumentException(
                        "all four stems must have the same length (vocals/instrumental ×"
                            + " left/right), any length mismatch misaligns the seam");
            }
            this.vocalsLeft = vocalsLeft;
            this.vocalsRight = vocalsRight;
            this.instrumentalLeft = instrumentalLeft;
            this.instrumentalRight = instrumentalRight;
        }

        public int frames() {
            return vocalsLeft.length;
        }
    }
}
