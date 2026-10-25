package dev.t1m3.qplayer.audio.core;

public enum BitDepth {

    INT16,

    INT24,

    INT32,

    FLOAT32,

    FLOAT64;

    public int bits() {
        switch (this) {
            case INT16: return 16;
            case INT24: return 24;
            case INT32: return 32;
            case FLOAT32: return 32;
            default: return 64;
        }
    }

    public boolean isInteger() {
        return this == INT16 || this == INT24 || this == INT32;
    }

    public boolean exactInF32() {
        return this == INT16 || this == INT24;
    }

    public static BitDepth fromBits(int bits) {
        if (bits >= 1 && bits <= 16) return INT16;
        if (bits >= 17 && bits <= 24) return INT24;
        if (bits >= 25 && bits <= 64) return INT32;
        return null;
    }

    public String label() {
        switch (this) {
            case INT16: return "16 bit";
            case INT24: return "24 bit";
            case INT32: return "32 bit";
            case FLOAT32: return "32 bit float";
            default: return "64 bit float";
        }
    }

    @Override
    public String toString() {
        return label();
    }
}
