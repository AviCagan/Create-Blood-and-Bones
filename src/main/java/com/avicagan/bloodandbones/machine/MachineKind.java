package com.avicagan.bloodandbones.machine;

/**
 * What a carcass machine does to the carcass parts over it. All four share one block and block entity
 * class; the kind decides the work, the pace and the stress. The brief's shape: the Mangler is the fastest way
 * through a whole carcass (one stroke a piece); the Beheader is the fastest and cheapest machine; the Deglover
 * costs the most stress and gains nothing from turning fast, so a slow shaft is the sensible one.
 */
public enum MachineKind {
    /** Tears each limb off and grinds it in the same stroke, then grinds the body: one stroke a piece. */
    MANGLER("mangler", 8.0, 0.5F, 256),
    /** Winds its blade up while it turns; a redstone pulse drops it through one limb. Never the head. */
    GUILLOTINE("guillotine", 4.0, 1.0F, 256),
    /** Takes heads off as they come, one quick stroke each, and sometimes keeps the skull whole. */
    BEHEADER("beheader", 2.0, 0.25F, 256),
    /** Rolls the hide off whatever carcass is over it, a stroke at a time; its rollers work no faster above 32 RPM. */
    DEGLOVER("deglover", 16.0, 0.5F, 32);

    public final String id;
    /** Stress impact per RPM (this and the pace are the server config's defaults, which are read). */
    public final double stress;
    /** How long a stroke (or the Guillotine's wind-up) takes, as a share of the base time. */
    public final float pace;
    /** Turning faster than this makes it work no faster (it still costs the stress). */
    public final int maxRpm;

    MachineKind(String id, double stress, float pace, int maxRpm) {
        this.id = id;
        this.stress = stress;
        this.pace = pace;
        this.maxRpm = maxRpm;
    }
}
