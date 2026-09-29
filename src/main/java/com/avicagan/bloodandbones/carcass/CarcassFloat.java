package com.avicagan.bloodandbones.carcass;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FluidState;
import org.joml.Vector3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whether a carcass floats or sinks is its weight class's {@code buoyancy} (ARCHITECTURE 5: light classes float, heavy
 * ones sink): each of its bodies in water is lifted by that share of its own weight times how much of it is under. Sable's
 * own lift for our blocks is off (their {@code sable:volume} is 0 in PhysicsPropertiesProvider), since a block's lift in
 * Sable is one figure for every mob, and a small cell was lifted as hard as a whole block; Sable's water drag still slows
 * them. The lift goes through the middle of the body's mass, so it never turns it: the joints and the other bodies do.
 */
public final class CarcassFloat {
    /** How many heights through a body are tried for water, to tell how much of it is under. */
    private static final int SAMPLES = 5;

    /** Each body's share under water, worked out once a game tick (the physics steps several times in one). */
    private static final Map<UUID, Under> UNDER = new ConcurrentHashMap<>();

    private record Under(long tick, double share) {
    }

    private CarcassFloat() {
    }

    /** Called every physics substep, beside the drag and the hooks. */
    public static void physicsTick(ServerLevel level, double timeStep) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }
        Vector3d gravity = dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData.getGravity(level);
        long tick = level.getGameTime();
        for (CarcassSavedData.Carcass carcass : CarcassSavedData.get(level).all()) {
            float buoyancy = -1.0F;
            for (UUID id : carcass.bones.values()) {
                if (!(container.getSubLevel(id) instanceof ServerSubLevel body) || body.isRemoved() || body.getMassTracker().isInvalid()) {
                    continue;
                }
                double share = under(level, body, id, tick);
                if (share <= 0.0) {
                    continue;
                }
                if (buoyancy < 0.0F) {
                    buoyancy = CarcassBody.weightClass(carcass).buoyancy();
                }
                double mass = body.getMassTracker().getMass();
                // straight up, against gravity, as much as the water it puts aside weighs
                Vector3d force = new Vector3d(gravity).mul(-buoyancy * mass * share);
                Pose3d pose = body.logicalPose();
                Vector3d impulse = pose.orientation().transformInverse(force.mul(timeStep), new Vector3d());
                Vector3d middle = new Vector3d(body.getMassTracker().getCenterOfMass());
                body.getOrCreateQueuedForceGroup(dev.ryanhcode.sable.api.physics.force.ForceGroups.PROPULSION.get()).applyAndRecordPointForce(middle, impulse);
            }
        }
        if (tick % 200 == 0) {
            UNDER.values().removeIf(u -> u.tick() < tick - 40);
        }
    }

    /** How much of a body is under water, 0 to 1: water tried at heights through its middle, as deep as it is tall. */
    static double under(ServerLevel level, ServerSubLevel body, UUID id, long tick) {
        Under known = UNDER.get(id);
        if (known != null && known.tick() == tick) {
            return known.share();
        }
        Pose3d pose = body.logicalPose();
        Vector3d middle = pose.transformPosition(new Vector3d(body.getMassTracker().getCenterOfMass()), new Vector3d());
        // half its height: from its volume as if a cube, which is near enough for how deep it sits
        double half = Math.max(0.05, Math.cbrt(Math.max(1.0e-4, body.getMassTracker().getMass())) / 2.0);
        int wet = 0;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int i = 0; i < SAMPLES; i++) {
            double y = middle.y - half + (i + 0.5) * (2.0 * half / SAMPLES);
            at.set(middle.x, y, middle.z);
            if (!level.isLoaded(at)) {
                break;
            }
            FluidState fluid = level.getFluidState(at);
            if (fluid.is(FluidTags.WATER) && y - at.getY() <= fluid.getHeight(level, at)) {
                wet++;
            }
        }
        double share = wet / (double) SAMPLES;
        UNDER.put(id, new Under(tick, share));
        return share;
    }
}
