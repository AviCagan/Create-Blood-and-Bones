package com.avicagan.bloodandbones.machine;

import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

/**
 * The part filter of a work table (the Butcher's Table, the Surgical Rig): on the edge of the table top, on whichever
 * side a player looks at it from, as the Basin's is. The top itself is where the work lies.
 */
public class TableFilterSlot extends ValueBoxTransform.Sided {
    /** Height of the middle of the table top's edge, in pixels. */
    private final float height;
    /** Whether the table has the slot at all in this state (the Surgery Table only with its Surgical Rig). */
    private final Predicate<BlockState> active;

    public TableFilterSlot(float height, Predicate<BlockState> active) {
        this.height = height;
        this.active = active;
    }

    @Override
    protected Vec3 getSouthLocation() {
        return VecHelper.voxelSpace(8, height, 16.05);
    }

    @Override
    protected boolean isSideActive(BlockState state, Direction direction) {
        return direction.getAxis().isHorizontal() && active.test(state);
    }
}
