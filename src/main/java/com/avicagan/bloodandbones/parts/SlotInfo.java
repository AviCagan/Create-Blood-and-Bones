package com.avicagan.bloodandbones.parts;

/**
 * A bone's slot, with its form (a wing, a pair of arms, a tentacle, a flipper, a shell) and its sub-slot
 * (front, mid, hind), either empty.
 */
public record SlotInfo(PartSlot slot, String form, String sub) {
    public static SlotInfo of(PartSlot slot) {
        return new SlotInfo(slot, "", "");
    }

    /** The data key it answers to, most specific: "leg.hind", "arm.wing", or just "leg". */
    public String key() {
        String base = slot.getSerializedName();
        if (!sub.isEmpty()) {
            return base + "." + sub;
        }
        return form.isEmpty() ? base : base + "." + form;
    }
}
