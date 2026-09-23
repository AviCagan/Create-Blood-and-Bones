package com.avicagan.bloodandbones.registry;

import com.avicagan.bloodandbones.carcass.ShackleHookBlock;
import com.avicagan.bloodandbones.cooking.ButcherHookBlock;
import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.api.contraption.BlockMovementChecks.CheckResult;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

/**
 * How the hooks ride on Create contraptions. Both hang off one face of another block, so a contraption
 * that moves that block takes the hook along (like a wall torch), and both are brittle: placed last and
 * taken first, so they are never left without their support mid-way.
 */
public final class BBMovementChecks {
    private BBMovementChecks() {
    }

    public static void register() {
        BlockMovementChecks.registerAttachedCheck((state, level, pos, direction) -> {
            if (state.getBlock() instanceof ShackleHookBlock) {
                return CheckResult.of(direction == state.getValue(ShackleHookBlock.FACING));
            }
            if (state.getBlock() instanceof ButcherHookBlock) {
                return CheckResult.of(direction == state.getValue(HorizontalDirectionalBlock.FACING).getOpposite());
            }
            return CheckResult.PASS;
        });
        BlockMovementChecks.registerBrittleCheck(state -> state.getBlock() instanceof ShackleHookBlock || state.getBlock() instanceof ButcherHookBlock
                ? CheckResult.SUCCESS : CheckResult.PASS);
    }
}
