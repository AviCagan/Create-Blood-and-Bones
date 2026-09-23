package com.avicagan.bloodandbones.registry;

import com.avicagan.bloodandbones.BloodAndBones;
import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;

/** Connected textures, as Create's AllSpriteShifts: a block's own sprite and its sheet of joined-up edges. */
public final class BBSpriteShifts {
    public static final CTSpriteShiftEntry BLOODY_CASING = CTSpriteShifter.getCT(AllCTTypes.OMNIDIRECTIONAL,
            BloodAndBones.asResource("block/bloody_casing"), BloodAndBones.asResource("block/bloody_casing_connected"));

    private BBSpriteShifts() {
    }
}
