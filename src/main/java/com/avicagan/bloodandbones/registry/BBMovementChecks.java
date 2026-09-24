package com.avicagan.bloodandbones.registry;

import com.avicagan.bloodandbones.carcass.ShackleHookBlock;
import com.avicagan.bloodandbones.cooking.ButcherHookBlock;
import com.avicagan.bloodandbones.decoration.BonePileBlock;
import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.api.contraption.BlockMovementChecks.CheckResult;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

/**
 * How the hooks and the Bone Pile ride on Create contraptions. The hooks hang off one face of another
 * block, and a bone pile lies on the one below (as a carpet does), so a contraption that moves that block
 * takes them along (like a wall torch), and all are brittle: placed last and taken first, so they are never
 * left without their support mid-way. A single layer of bones has no collision; being brittle, it still moves.
 * Create counts a brittle block as holding up nothing on any side, so a pile would not push the block in
 * front of it; like Create's carpets, only its top holds nothing up (unless the pile is full).
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
            if (state.getBlock() instanceof BonePileBlock) {
                return CheckResult.of(direction == Direction.DOWN);
            }
            return CheckResult.PASS;
        });
        BlockMovementChecks.registerBrittleCheck(state -> state.getBlock() instanceof ShackleHookBlock || state.getBlock() instanceof ButcherHookBlock
                || state.getBlock() instanceof BonePileBlock ? CheckResult.SUCCESS : CheckResult.PASS);
        BlockMovementChecks.registerNotSupportiveCheck((state, direction) -> state.getBlock() instanceof BonePileBlock
                ? CheckResult.of(direction == Direction.UP && state.getValue(BonePileBlock.LAYERS) < BonePileBlock.MAX_LAYERS) : CheckResult.PASS);
        // a Rotational Coupler's shaft end belongs to the arm driving it, not the machine: it stays behind (and goes)
        BlockMovementChecks.registerMovementNecessaryCheck((state, level, pos) -> state.getBlock() instanceof com.avicagan.bloodandbones.cyber.CouplerBlock
                ? CheckResult.FAIL : CheckResult.PASS);
    }
}
