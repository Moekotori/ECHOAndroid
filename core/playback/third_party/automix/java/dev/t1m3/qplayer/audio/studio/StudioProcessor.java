package dev.t1m3.qplayer.audio.studio;

import dev.t1m3.qplayer.audio.core.AudioBuf;

public final class StudioProcessor {
    private final RackProcessor master;
    private final RackProcessor deckA;
    private final RackProcessor deckB;

    public StudioProcessor(int rate, int channels) {
        this.master = new RackProcessor(rate, channels);
        this.deckA = new RackProcessor(rate, channels);
        this.deckB = new RackProcessor(rate, channels);
    }

    public void setRacks(StudioRacks racks) {
        StudioRacks r = racks == null ? StudioRacks.EMPTY : racks;
        master.setRack(r.master);
        deckA.setRack(r.deckA);
        deckB.setRack(r.deckB);
    }

    public void setVolume(float volume) {
        master.setVolume(volume);
        deckA.setVolume(volume);
        deckB.setVolume(volume);
    }

    public boolean active() {
        return master.active() || deckA.active() || deckB.active();
    }

    public int latencyFrames() {
        return Math.max(latencyFramesDeckA(), latencyFramesDeckB());
    }

    public int latencyFramesDeckA() {
        return deckA.latencyFrames() + master.latencyFrames();
    }

    public int latencyFramesDeckB() {
        return deckB.latencyFrames() + master.latencyFrames();
    }

    public void processDeckA(AudioBuf buf) {
        deckA.process(buf);
    }

    public void processDeckB(AudioBuf buf) {
        deckB.process(buf);
    }

    public void processMaster(AudioBuf buf) {
        master.process(buf);
    }

    public float gainReductionDb() {
        return Math.max(
                master.gainReductionDb(),
                Math.max(deckA.gainReductionDb(), deckB.gainReductionDb()));
    }

    public void reset() {
        master.reset();
        deckA.reset();
        deckB.reset();
    }

    public int rate() {
        return master.rate();
    }

    public String describe() {
        if (!active()) {
            return "studio: all off (bypass)";
        }
        StringBuilder sb = new StringBuilder("studio: master bus ");
        sb.append(master.activeBands())
                .append(" bands, outro ")
                .append(deckA.activeBands())
                .append(" bands, intro ")
                .append(deckB.activeBands())
                .append(" bands");
        if (latencyFrames() > 0) {
            sb.append(", delay ")
                    .append(latencyFrames())
                    .append(" frames (outro path ")
                    .append(latencyFramesDeckA())
                    .append(" / intro path ")
                    .append(latencyFramesDeckB())
                    .append(")");
        }
        return sb.toString();
    }
}
