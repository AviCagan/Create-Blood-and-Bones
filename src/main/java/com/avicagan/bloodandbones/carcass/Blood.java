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

    /** A drop letting go of a wound. */
    public static void drip(ServerLevel level, Vector3d at) {
        level.sendParticles(BBParticles.BLOOD_DROP.get(), at.x, at.y, at.z, 0, (RANDOM.nextDouble() - 0.5) * 0.02, 0.0, (RANDOM.nextDouble() - 0.5) * 0.02, 1.0);
    }
}
