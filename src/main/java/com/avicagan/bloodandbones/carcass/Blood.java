package com.avicagan.bloodandbones.carcass;

import com.avicagan.bloodandbones.registry.BBParticles;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;

import java.util.Random;

/**
 * Blood, as particles from the server: drops that fall, land and lie there. A spray throws them along a
 * direction, a burst in every direction, a drip lets one go.
 */
public final class Blood {
    private static final Random RANDOM = new Random();

    private Blood() {
    }

    /** A spray along a direction, for the killing blow. */
    public static void spray(ServerLevel level, Vector3d at, Vector3d direction, int amount) {
        Vector3d d = new Vector3d(direction).normalize();
        for (int i = 0; i < amount; i++) {
            double spread = 0.35;
            level.sendParticles(BBParticles.BLOOD_DROP.get(), at.x, at.y, at.z, 0,
                    d.x * 0.5 + (RANDOM.nextDouble() - 0.5) * spread, 0.25 + RANDOM.nextDouble() * 0.3, d.z * 0.5 + (RANDOM.nextDouble() - 0.5) * spread, 1.0);
        }
    }

    /** A burst from a point, for a hook going in or a cut. */
    public static void burst(ServerLevel level, Vector3d at, int amount) {
        for (int i = 0; i < amount; i++) {
            level.sendParticles(BBParticles.BLOOD_DROP.get(), at.x, at.y, at.z, 0,
                    (RANDOM.nextDouble() - 0.5) * 0.4, 0.1 + RANDOM.nextDouble() * 0.3, (RANDOM.nextDouble() - 0.5) * 0.4, 1.0);
        }
    }

    /** Farthest blood falls looking for ground to stain. */
    public static final int STAIN_REACH = 12;

    /** Whether this carcass's mob has blood at all (skeletons, golems, spirits and slimes do not). */
    public static boolean bloody(CarcassSavedData.Carcass carcass) {
        return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(carcass.entity)
                .map(type -> !type.is(com.avicagan.bloodandbones.registry.BBTags.BLOODLESS)).orElse(true);
    }

    /**
     * Blood reaching the ground below a point: a stain on the first solid top surface it falls to (through
     * air only; grass, water and the like take none), or a bigger, wet again one where one already is.
     *
     * @param amount 1 (a few drops) to 4 (a pool)
     */
    public static void stain(ServerLevel level, Vector3d at, int amount) {
        net.minecraft.world.level.block.state.BlockState stain = com.avicagan.bloodandbones.registry.BBBlocks.BLOOD_STAIN.get().defaultBlockState();
        net.minecraft.core.BlockPos.MutableBlockPos pos = net.minecraft.core.BlockPos.containing(at.x, at.y, at.z).mutable();
        for (int i = 0; i <= STAIN_REACH; i++, pos.move(net.minecraft.core.Direction.DOWN)) {
            if (!level.isLoaded(pos)) {
                return;
            }
            net.minecraft.world.level.block.state.BlockState here = level.getBlockState(pos);
            if (here.is(stain.getBlock()) || (here.isAir() && stain.canSurvive(level, pos))) {
                com.avicagan.bloodandbones.bleeding.BloodStainBlock.splash(level, pos.immutable(), stain, amount);
                return;
            }
            if (!here.isAir()) {
                return;
            }
        }
    }

    /** Scraps of meat thrown out from a point, for a piece hacked or ground apart. */
    public static void gibs(ServerLevel level, Vector3d at, int amount) {
        for (int i = 0; i < amount; i++) {
            level.sendParticles(BBParticles.GIB.get(), at.x, at.y, at.z, 0,
                    (RANDOM.nextDouble() - 0.5) * 0.5, 0.2 + RANDOM.nextDouble() * 0.35, (RANDOM.nextDouble() - 0.5) * 0.5, 1.0);
        }
    }

    /** A wound: a burst of drops and a stain under it, for a mob that bleeds; big ones throw scraps of meat. */
    public static void wound(ServerLevel level, CarcassSavedData.Carcass carcass, Vector3d at, int drops, int stain) {
        if (!bloody(carcass)) {
            return;
        }
        burst(level, at, drops);
        if (drops >= 20) {
            gibs(level, at, drops / 4);
        }
        if (stain > 0) {
            stain(level, at, stain);
        }
    }

    /** A drop letting go of a wound. */
    public static void drip(ServerLevel level, Vector3d at) {
        level.sendParticles(BBParticles.BLOOD_DROP.get(), at.x, at.y, at.z, 0, (RANDOM.nextDouble() - 0.5) * 0.02, 0.0, (RANDOM.nextDouble() - 0.5) * 0.02, 1.0);
    }
}
