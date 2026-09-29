package com.avicagan.bloodandbones.registry;

import com.avicagan.bloodandbones.BloodAndBones;
import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;

/** Connected textures, as Create's AllSpriteShifts: a block's own sprite and its sheet of joined-up edges. */
public final class BBSpriteShifts {
    public static final CTSpriteShiftEntry BLOODY_CASING = CTSpriteShifter.getCT(AllCTTypes.OMNIDIRECTIONAL,
            BloodAndBones.asResource("block/bloody_casing"), BloodAndBones.asResource("block/bloody_casing_connected"));
    public static final CTSpriteShiftEntry BLOODY_BRASS_CASING = CTSpriteShifter.getCT(AllCTTypes.OMNIDIRECTIONAL,
            BloodAndBones.asResource("block/bloody_brass_casing"), BloodAndBones.asResource("block/bloody_brass_casing_connected"));
    public static final CTSpriteShiftEntry BLOODY_COPPER_CASING = CTSpriteShifter.getCT(AllCTTypes.OMNIDIRECTIONAL,
            BloodAndBones.asResource("block/bloody_copper_casing"), BloodAndBones.asResource("block/bloody_copper_casing_connected"));

    /** Create's train casing, bloody: a side sheet and a top sheet, as Create's own (AllSpriteShifts.RAILWAY_CASING). */
    public static final CTSpriteShiftEntry BLOODY_RAILWAY_CASING = CTSpriteShifter.getCT(AllCTTypes.OMNIDIRECTIONAL,
            BloodAndBones.asResource("block/bloody_railway_casing"), BloodAndBones.asResource("block/bloody_railway_casing_connected"));
    public static final CTSpriteShiftEntry BLOODY_RAILWAY_CASING_SIDE = CTSpriteShifter.getCT(AllCTTypes.OMNIDIRECTIONAL,
            BloodAndBones.asResource("block/bloody_railway_casing_side"), BloodAndBones.asResource("block/bloody_railway_casing_side_connected"));

    private BBSpriteShifts() {
    }
}
