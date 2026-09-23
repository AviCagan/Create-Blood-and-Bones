package com.avicagan.bloodandbones.machine;

/**
 * What a carcass machine does to the carcass parts over it. All four share one block and block entity
 * class; the kind decides the work, the pace and the stress.
 */
public enum MachineKind {
    /** Tears limbs off, then grinds every loose piece down into its yields. */
    MANGLER("mangler", 8.0, 1.0F),
    /** Drops a heavy blade through any limb over it: one stroke takes it off. Never the head. */
    GUILLOTINE("guillotine", 6.0, 1.5F),
    /** Takes heads off, one stroke each, and sometimes keeps the skull whole. */
    BEHEADER("beheader", 4.0, 1.0F),
    /** Strips the hide off whatever carcass is over it, a stroke at a time. */
    DEGLOVER("deglover", 4.0, 0.5F);

    public final String id;
    /** Stress impact per RPM. */
    public final double stress;
    /** How long a stroke takes, as a share of the base time. */
    public final float pace;

    MachineKind(String id, double stress, float pace) {
        this.id = id;
        this.stress = stress;
        this.pace = pace;
    }
}
