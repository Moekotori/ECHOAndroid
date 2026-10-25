package dev.t1m3.qplayer.audio.studio;

public final class StudioRacks {
    public static final StudioRacks EMPTY =
            new StudioRacks(StudioRack.EMPTY, StudioRack.EMPTY, StudioRack.EMPTY);

    public final StudioRack master;

    public final StudioRack deckA;

    public final StudioRack deckB;

    private StudioRacks(StudioRack master, StudioRack deckA, StudioRack deckB) {
        this.master = master == null ? StudioRack.EMPTY : master;
        this.deckA = deckA == null ? StudioRack.EMPTY : deckA;
        this.deckB = deckB == null ? StudioRack.EMPTY : deckB;
    }

    public static StudioRacks of(StudioRack master, StudioRack deckA, StudioRack deckB) {
        return new StudioRacks(master, deckA, deckB);
    }

    public static StudioRacks master(StudioRack rack) {
        return new StudioRacks(rack, StudioRack.EMPTY, StudioRack.EMPTY);
    }

    public StudioRacks withMaster(StudioRack rack) {
        return new StudioRacks(rack, deckA, deckB);
    }

    public StudioRacks withDeckA(StudioRack rack) {
        return new StudioRacks(master, rack, deckB);
    }

    public StudioRacks withDeckB(StudioRack rack) {
        return new StudioRacks(master, deckA, rack);
    }

    public boolean active() {
        return master.active() || deckA.active() || deckB.active();
    }

    public int activeBands() {
        return master.activeBands() + deckA.activeBands() + deckB.activeBands();
    }

    @Override
    public String toString() {
        return "StudioRacks{master " + master + ", A " + deckA + ", B " + deckB + "}";
    }
}
