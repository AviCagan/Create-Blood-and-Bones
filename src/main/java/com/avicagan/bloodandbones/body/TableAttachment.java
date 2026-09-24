package com.avicagan.bloodandbones.body;

import net.minecraft.util.StringRepresentable;

/**
 * What is fitted to a Surgery Table, which decides its job (as the brief has it: one block, two crafted
 * attachments that change its model and its job). The Surgical Rig is for operating on patients and taking
 * organs out of carcasses; the Assembly Frame is for building minions. A bare table does nothing.
 */
public enum TableAttachment implements StringRepresentable {
    NONE, SURGICAL, ASSEMBLY;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
