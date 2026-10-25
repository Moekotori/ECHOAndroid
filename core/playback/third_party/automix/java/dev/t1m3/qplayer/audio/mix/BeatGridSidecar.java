package dev.t1m3.qplayer.audio.mix;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Locale;

public final class BeatGridSidecar {

    public static final String SUFFIX = ".beats";

    public static final int VERSION = 1;

    static final String MAGIC = "qplayer-beatgrid";

    public final String fileHash;

    public final double bpm;

    public final double[] beats;

    public final double[] downbeats;

    public final double confidence;

    public BeatGridSidecar(String fileHash, double bpm, double[] beats, double[] downbeats,
                           double confidence) {
        this.fileHash = fileHash;
        this.bpm = bpm;
        this.beats = beats == null ? new double[0] : beats;
        this.downbeats = downbeats == null ? new double[0] : downbeats;
        this.confidence = confidence;
    }

    public String toText() {
        StringBuilder sb = new StringBuilder(256 + beats.length * 12);
        sb.append(MAGIC).append(' ').append(VERSION).append('\n');
        sb.append("fileHash ").append(fileHash).append('\n');
        sb.append("bpm ").append(fmt(bpm)).append('\n');
        sb.append("confidence ").append(fmt(confidence)).append('\n');
        sb.append("beats");
        for (double b : beats) {
            sb.append(' ').append(fmt(b));
        }
        sb.append('\n');
        sb.append("downbeats");
        for (double d : downbeats) {
            sb.append(' ').append(fmt(d));
        }
        sb.append('\n');
        return sb.toString();
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.6f", Double.valueOf(v));
    }

    public static BeatGridSidecar parse(String text) {
        if (text == null) {
            return null;
        }
        String hash = null;
        double bpm = Double.NaN;
        double confidence = Double.NaN;
        double[] beats = null;
        double[] downbeats = null;
        boolean header = false;
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int sp = line.indexOf(' ');
            String key = sp < 0 ? line : line.substring(0, sp);
            String rest = sp < 0 ? "" : line.substring(sp + 1).trim();
            if (MAGIC.equals(key)) {
                if (!String.valueOf(VERSION).equals(rest)) {
                    return null;
                }
                header = true;
            } else if ("fileHash".equals(key)) {
                hash = rest;
            } else if ("bpm".equals(key)) {
                bpm = parseDouble(rest);
            } else if ("confidence".equals(key)) {
                confidence = parseDouble(rest);
            } else if ("beats".equals(key)) {
                beats = parseArray(rest);
            } else if ("downbeats".equals(key)) {
                downbeats = parseArray(rest);
            }

        }
        if (!header || hash == null || hash.isEmpty() || Double.isNaN(bpm) || bpm <= 0.0
                || Double.isNaN(confidence) || beats == null || beats.length < 2) {
            return null;
        }
        for (int i = 1; i < beats.length; i++) {
            if (beats[i] <= beats[i - 1]) {
                return null;
            }
        }
        return new BeatGridSidecar(hash, bpm, beats, downbeats, confidence);
    }

    private static double parseDouble(String s) {
        try {
            return Double.parseDouble(s);
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }

    private static double[] parseArray(String s) {
        if (s.isEmpty()) {
            return new double[0];
        }
        String[] parts = s.split("\\s+");
        double[] out = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = parseDouble(parts[i]);
            if (Double.isNaN(out[i])) {
                return null;
            }
        }
        return out;
    }

    public static BeatGridSidecar read(Path audioPath) {
        try {
            Path side = sidecarFor(audioPath);
            if (!Files.isRegularFile(side)) {
                return null;
            }
            BeatGridSidecar parsed = parse(new String(Files.readAllBytes(side),
                    StandardCharsets.UTF_8));
            if (parsed == null) {
                return null;
            }
            String hash = hashOf(audioPath);
            if (hash == null || !hash.equals(parsed.fileHash)) {
                return null;
            }
            return parsed;
        } catch (RuntimeException | IOException e) {
            return null;
        }
    }

    public static boolean write(Path audioPath, BeatGridSidecar grid) {
        try {
            Files.write(sidecarFor(audioPath), grid.toText().getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (RuntimeException | IOException e) {
            return false;
        }
    }

    public static Path sidecarFor(Path audioPath) {
        return audioPath.resolveSibling(audioPath.getFileName().toString() + SUFFIX);
    }

    public static String hashOf(Path audioPath) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[1 << 16];
            try (java.io.InputStream in = Files.newInputStream(audioPath)) {
                int n;
                while ((n = in.read(buf)) > 0) {
                    md.update(buf, 0, n);
                }
            }
            StringBuilder sb = new StringBuilder(64);
            for (byte b : md.digest()) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (RuntimeException | IOException | java.security.NoSuchAlgorithmException e) {
            return null;
        }
    }
}
