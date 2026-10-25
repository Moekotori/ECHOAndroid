package dev.t1m3.qplayer.audio.core;

import java.util.concurrent.atomic.AtomicInteger;

public final class RingBuffer {

    private static final int MIN_CAPACITY = 256;

    private static final int MAX_CAPACITY = 1 << 30;

    private RingBuffer() {
    }

    public static final class Ring {
        public final Producer producer;
        public final Consumer consumer;

        private Ring(Producer producer, Consumer consumer) {
            this.producer = producer;
            this.consumer = consumer;
        }
    }

    public static Ring create(int minCapacity) {
        if (minCapacity > MAX_CAPACITY) {
            throw AudioException.config("ring buffer capacity too large: " + minCapacity + " (limit " + MAX_CAPACITY + "）");
        }
        int capacity = Math.max(minCapacity, MIN_CAPACITY);
        int pow = 1;
        while (pow < capacity) {
            pow <<= 1;
        }
        Shared shared = new Shared(pow);
        return new Ring(new Producer(shared), new Consumer(shared));
    }

    private static final class Shared {

        final float[] data;

        final int mask;

        final AtomicInteger write = new AtomicInteger();

        final AtomicInteger read = new AtomicInteger();

        volatile long flushEpoch;

        long seenFlushEpoch;

        Shared(int capacity) {
            this.data = new float[capacity];
            this.mask = capacity - 1;
        }

        int capacity() {
            return mask + 1;
        }
    }

    public static final class Producer {
        private final Shared shared;

        private Producer(Shared shared) {
            this.shared = shared;
        }

        public int capacity() {
            return shared.capacity();
        }

        public int free() {
            int write = shared.write.get();
            int read = shared.read.get();
            return shared.capacity() - (write - read);
        }

        public int push(float[] src) {
            return push(src, 0, src.length);
        }

        public int push(float[] src, int offset, int length) {
            int capacity = shared.capacity();
            int write = shared.write.get();
            int read = shared.read.get();
            int used = write - read;
            int free = capacity - used;
            int n = Math.min(length, free);
            if (n <= 0) {
                return 0;
            }

            int mask = shared.mask;
            int start = write & mask;
            int first = Math.min(n, capacity - start);

            System.arraycopy(src, offset, shared.data, start, first);
            if (n > first) {
                System.arraycopy(src, offset + first, shared.data, 0, n - first);
            }

            shared.write.set(write + n);
            return n;
        }

        public void flush() {
            shared.flushEpoch = shared.flushEpoch + 1L;
        }
    }

    public static final class Consumer {
        private final Shared shared;

        private Consumer(Shared shared) {
            this.shared = shared;
        }

        public int capacity() {
            return shared.capacity();
        }

        public int available() {
            return shared.write.get() - shared.read.get();
        }

        public int pop(float[] out) {
            return pop(out, 0, out.length);
        }

        public int pop(float[] out, int offset, int length) {

            discardIfFlushed();
            int capacity = shared.capacity();
            int read = shared.read.get();
            int write = shared.write.get();
            int available = write - read;
            int n = Math.min(length, available);
            if (n <= 0) {
                return 0;
            }

            int mask = shared.mask;
            int start = read & mask;
            int first = Math.min(n, capacity - start);

            System.arraycopy(shared.data, start, out, offset, first);
            if (n > first) {
                System.arraycopy(shared.data, 0, out, offset + first, n - first);
            }

            shared.read.set(read + n);
            return n;
        }

        public int popOrSilence(float[] out) {
            int got = pop(out);
            if (got < out.length) {
                java.util.Arrays.fill(out, got, out.length, 0f);
            }
            return got;
        }

        private void discardIfFlushed() {
            long epoch = shared.flushEpoch;
            if (epoch == shared.seenFlushEpoch) {
                return;
            }
            shared.seenFlushEpoch = epoch;

            shared.read.set(shared.write.get());
        }
    }
}
