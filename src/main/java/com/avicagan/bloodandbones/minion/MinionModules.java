package com.avicagan.bloodandbones.minion;

import com.avicagan.bloodandbones.cyber.Coupler;
import com.avicagan.bloodandbones.cyber.CouplerBlock;
import com.avicagan.bloodandbones.cyber.Module;
import com.simibubi.create.content.kinetics.base.IRotate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A brass minion's one module socket (docs/PARTS-AND-TRAITS.md section 6.6): a player's cybernetic module, run at its
 * baseline with no throttle, the thing flesh cannot do. Magnet Coil: loose items within 6 blocks drift to it. Analytical
 * Lens: it sees its targets through walls. Rotational Coupler: standing by the end of a machine's shaft, it drives it
 * at 16 RPM (256 su), as a player's arm does on a tap. Piston Ram: its blows throw hard. Gyroscopic Stabilizer: no
 * fall damage. Barometric Vent: it drifts down slowly. The Grappling Spool needs aiming, and is not for minions.
 */
public final class MinionModules {
    public static final List<Module> FITS = List.of(Module.MAGNET_COIL, Module.ANALYTICAL_LENS, Module.ROTATIONAL_COUPLER, Module.PISTON_RAM,
            Module.GYROSCOPIC_STABILIZER, Module.BAROMETRIC_VENT);
    public static final double MAGNET_RADIUS = 6.0;
    public static final double LENS_RANGE = 24.0;
    public static final int COUPLER_RPM = Coupler.BASE_RPM;
    public static final float RAM_KNOCKBACK = 1.5F;
    /** How fast a Barometric Vent lets it fall. */
    public static final double VENT_FALL = -0.12;

    private MinionModules() {
    }

    /** Every tick, on the server, while it is awake. */
    static void tick(MinionEntity minion, @Nullable Module module) {
        if (module != Module.ROTATIONAL_COUPLER && Coupler.coupled(minion)) {
            Coupler.release(minion);
        }
        if (module == null) {
            return;
        }
        switch (module) {
            case MAGNET_COIL -> {
                if (minion.tickCount % 2 == 0) {
                    Vec3 at = minion.position().add(0.0, minion.getBbHeight() * 0.5, 0.0);
                    for (ItemEntity item : minion.level().getEntitiesOfClass(ItemEntity.class, minion.getBoundingBox().inflate(MAGNET_RADIUS))) {
                        Vec3 pull = at.subtract(item.position());
                        if (pull.lengthSqr() > 1.0) {
                            item.setDeltaMovement(item.getDeltaMovement().scale(0.8).add(pull.normalize().scale(0.12)));
                        }
                    }
                }
            }
            case ROTATIONAL_COUPLER -> {
                if (minion.tickCount % 10 == 0) {
                    couple(minion);
                }
            }
            case BAROMETRIC_VENT -> {
                if (!minion.onGround() && minion.getDeltaMovement().y < VENT_FALL) {
                    minion.setDeltaMovement(minion.getDeltaMovement().x, VENT_FALL, minion.getDeltaMovement().z);
                    minion.resetFallDistance();
                }
            }
            default -> {
            }
        }
    }

    /**
     * Standing still beside the end of a machine's shaft (at its feet or at its middle), it couples into it; moving off,
     * it lets go.
     */
    private static void couple(MinionEntity minion) {
        Level level = minion.level();
        if (minion.getDeltaMovement().horizontalDistanceSqr() > 1.0E-3) {
            Coupler.release(minion);
            return;
        }
        BlockPos feet = minion.blockPosition();
        for (BlockPos base : List.of(feet, BlockPos.containing(minion.getX(), minion.getY() + minion.getBbHeight() * 0.5, minion.getZ()))) {
            BlockState here = level.getBlockState(base);
            boolean ours = here.getBlock() instanceof CouplerBlock && Coupler.owns(minion.getUUID(), level, base);
            if (!here.canBeReplaced() && !ours) {
                continue;
            }
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos machinePos = base.relative(side);
                BlockState machine = level.getBlockState(machinePos);
                if (machine.getBlock() instanceof IRotate rotate && !(machine.getBlock() instanceof CouplerBlock)
                        && rotate.hasShaftTowards(level, machinePos, machine, side.getOpposite())) {
                    Coupler.couple(minion, base, side, COUPLER_RPM);
                    return;
                }
            }
        }
        Coupler.release(minion);
    }
}
