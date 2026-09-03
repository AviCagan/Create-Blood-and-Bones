package com.avicagan.bloodandbones.carcass;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Blood, as particles from the server. A dark red dust for the spray and drips, a few chunks of the
 * redstone block texture for the gore. (Bloodless mode on the client does not filter these yet.)
 */
public final class Blood {
    private static final DustParticleOptions DROP = new DustParticleOptions(new Vector3f(0.55F, 0.02F, 0.02F), 1.2F);
    private static final DustParticleOptions DARK = new DustParticleOptions(new Vector3f(0.30F, 0.0F, 0.0F), 1.5F);
    private static final BlockParticleOption GORE = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState());

    private Blood() {
    }

    /** A spray along a direction, for the killing blow. */
    public static void spray(ServerLevel level, Vector3d at, Vector3d direction, int amount) {
        Vector3d d = new Vector3d(direction).normalize();
        level.sendParticles(DROP, at.x, at.y, at.z, amount, d.x * 0.3, 0.15, d.z * 0.3, 0.35);
        level.sendParticles(GORE, at.x, at.y, at.z, Math.max(1, amount / 4), d.x * 0.2, 0.1, d.z * 0.2, 0.25);
    }

    /** A burst from a point, for a hook going in or a cut. */
    public static void burst(ServerLevel level, Vector3d at, int amount) {
        level.sendParticles(DROP, at.x, at.y, at.z, amount, 0.15, 0.15, 0.15, 0.2);
        level.sendParticles(GORE, at.x, at.y, at.z, Math.max(1, amount / 3), 0.1, 0.1, 0.1, 0.15);
    }

    /** A drop or two falling from a wound. */
    public static void drip(ServerLevel level, Vector3d at) {
        level.sendParticles(DARK, at.x, at.y, at.z, 1, 0.05, 0.0, 0.05, 0.02);
    }
}
