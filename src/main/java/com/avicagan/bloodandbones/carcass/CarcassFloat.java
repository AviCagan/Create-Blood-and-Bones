package com.avicagan.bloodandbones.carcass;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.FluidState;
import org.joml.Vector3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A carcass in water: whether it floats or sinks is its weight class's {@code buoyancy} (ARCHITECTURE 5: light classes
 * float, heavy ones sink), and it is slowed by the water as its size says.
 * <p>
 * Sable's own handling of a sub-level in water does not fit a carcass. Its lift is one figure per block for every mob (a
 * block's {@code sable:volume}), so ours is off (0 in PhysicsPropertiesProvider). Its drag counts each cell of a body as a
 * whole block of water pushed aside, however small the cell, and it works that drag out once a game tick from the speed
 * the body has then and holds it through the whole tick: a chicken's head, a hundredth of a block of flesh, was pushed back
 * six times as hard as it was moving, so each tick it went the other way faster, shook itself apart and was flung through
 * the floor. Undoing that drag exactly each tick was tried, and the least mismatch grew the same way. So a carcass body in
 * water keeps its speed from Sable while Sable works out its drag: at the end of each tick its speed and turning are put
 * aside and it stands still, and on the first physics step of the next they are given back (Sable, seeing it still, drags
 * it not at all). The water slows it instead by its real size, a share of its speed taken away each step, which can never
 * overshoot. The lift goes through the middle of the body's mass, so it never turns it: the joints and the other bodies do.
 */
public final class CarcassFloat {
    /** How many heights through a body are tried for water, to tell how much of it is under. */
    private static final int SAMPLES = 5;
    /** A body's bounds (in blocks, summed over the three sides) under which Sable tries eight points a cell, not one. */
    private static final int SABLE_FINE_BELOW = 10;
    /** Share of its speed a body wholly under water loses each second, and of its turning. */
    private static final double WATER_DRAG = 2.0;
    private static final double WATER_TURN_DRAG = 3.0;

    /** What is under water of each body, worked out once a game tick (the physics steps several times in one). */
    private static final Map<UUID, Wet> WET = new ConcurrentHashMap<>();

    /**
     * @param share   how much of the body is under, 0 to 1, from heights through its middle
     * @param touches whether any of its cells is where Sable would drag it (the eighths of its blocks it tries)
     */
    private record Wet(long tick, double share, boolean touches) {
    }

    /** Speeds put aside at the end of a tick, by world and body, to give back on the next one's first step. */
    private static final Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, Map<UUID, Vector3d[]>> ASIDE = new ConcurrentHashMap<>();

    private CarcassFloat() {
    }

    /** Called every physics substep, beside the drag and the hooks. */
    public static void physicsTick(ServerLevel level, double timeStep) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }
        SubLevelPhysicsSystem physics = container.physicsSystem();
        Vector3d gravity = dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData.getGravity(level);
        long tick = level.getGameTime();
        for (CarcassSavedData.Carcass carcass : CarcassSavedData.get(level).all()) {
            float buoyancy = -1.0F;
            for (UUID id : carcass.bones.values()) {
                if (!(container.getSubLevel(id) instanceof ServerSubLevel body) || body.isRemoved() || body.getMassTracker().isInvalid()) {
                    continue;
                }
                Wet wet = wet(level, body, id, tick);
                if (wet.share() <= 0.0 && !wet.touches()) {
                    continue;
                }
                if (buoyancy < 0.0F) {
                    buoyancy = CarcassBody.weightClass(carcass).buoyancy();
                }
                water(physics, body, wet, gravity, buoyancy, timeStep);
            }
        }
        if (tick % 200 == 0) {
            WET.values().removeIf(w -> w.tick() < tick - 40);
        }
    }

    private static void water(SubLevelPhysicsSystem physics, ServerSubLevel body, Wet wet, Vector3d gravity, float buoyancy, double timeStep) {
        RigidBodyHandle handle = physics.getPhysicsHandle(body);
        double mass = body.getMassTracker().getMass();
        Pose3d pose = body.logicalPose();
        Vector3d middlePlot = new Vector3d(body.getMassTracker().getCenterOfMass());
        var forces = body.getOrCreateQueuedForceGroup(dev.ryanhcode.sable.api.physics.force.ForceGroups.PROPULSION.get());
        // the water slows it by its real size: a share of its speed and its turning, never more than all of it
        if (wet.share() > 0.0) {
            Vector3d linear = handle.getLinearVelocity(new Vector3d());
            Vector3d angular = handle.getAngularVelocity(new Vector3d());
            double slow = Math.min(1.0, WATER_DRAG * wet.share() * timeStep);
            double turn = Math.min(1.0, WATER_TURN_DRAG * wet.share() * timeStep);
            physics.getPipeline().addLinearAndAngularVelocity(body, linear.mul(-slow), angular.mul(-turn));
        }
        // the lift: straight up, against gravity, its class's share of its weight for the part of it under
        if (wet.share() > 0.0) {
            Vector3d up = new Vector3d(gravity).mul(-buoyancy * mass * wet.share() * timeStep);
            forces.applyAndRecordPointForce(middlePlot, pose.orientation().transformInverse(up, new Vector3d()));
        }
        physics.getPipeline().wakeUp(body);
    }

    /**
     * Called after every physics substep: after a tick's last, each carcass body in water has its speed and turning put
     * aside, so Sable, working out its water drag before the next, sees it still.
     */
    public static void postPhysicsTick(ServerLevel level, SubLevelPhysicsSystem physics) {
        if (physics.getPartialPhysicsTick() < 0.999) {
            return;
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }
        long tick = level.getGameTime();
        for (CarcassSavedData.Carcass carcass : CarcassSavedData.get(level).all()) {
            for (UUID id : carcass.bones.values()) {
                Wet wet = WET.get(id);
                if (wet == null || wet.tick() != tick || !wet.touches() && wet.share() <= 0.0
                        || !(container.getSubLevel(id) instanceof ServerSubLevel body) || body.isRemoved()) {
                    continue;
                }
                RigidBodyHandle handle = physics.getPhysicsHandle(body);
                Vector3d linear = handle.getLinearVelocity(new Vector3d());
                Vector3d angular = handle.getAngularVelocity(new Vector3d());
                ASIDE.computeIfAbsent(level.dimension(), k -> new ConcurrentHashMap<>()).put(id, new Vector3d[]{linear, angular});
                physics.getPipeline().addLinearAndAngularVelocity(body, new Vector3d(linear).negate(), new Vector3d(angular).negate());
            }
        }
    }

    /**
     * The speeds put aside at the end of the last tick, given back on this one's first physics step before anything else
     * reads them (the drag, the hooks, the trolleys: CarcassEvents#onPrePhysicsTick calls this first).
     */
    public static void giveBack(ServerLevel level, SubLevelPhysicsSystem physics) {
        Map<UUID, Vector3d[]> aside = ASIDE.get(level.dimension());
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (aside == null || aside.isEmpty() || container == null) {
            return;
        }
        aside.forEach((id, speeds) -> {
            if (container.getSubLevel(id) instanceof ServerSubLevel body && !body.isRemoved()) {
                physics.getPipeline().addLinearAndAngularVelocity(body, speeds[0], speeds[1]);
            }
        });
        aside.clear();
    }

    /**
     * A body's speed and turning as they really are between ticks: for a carcass in water, those put aside from Sable's
     * drag (its own read as still until the next physics step). What reads a carcass's motion in a game tick (whether it
     * has come to rest, whether it has landed) reads it here.
     */
    public static void velocity(ServerLevel level, SubLevelPhysicsSystem physics, ServerSubLevel body, Vector3d linear, Vector3d angular) {
        Map<UUID, Vector3d[]> aside = ASIDE.get(level.dimension());
        Vector3d[] speeds = aside == null ? null : aside.get(body.getUniqueId());
        if (speeds != null) {
            linear.set(speeds[0]);
            angular.set(speeds[1]);
            return;
        }
        RigidBodyHandle handle = physics.getPhysicsHandle(body);
        handle.getLinearVelocity(linear);
        handle.getAngularVelocity(angular);
    }

    /** Scale what was put aside for a body, if anything was (a hauler steadying a carcass in water); false if nothing was. */
    public static boolean scaleAside(ServerLevel level, UUID body, double factor) {
        Map<UUID, Vector3d[]> aside = ASIDE.get(level.dimension());
        Vector3d[] speeds = aside == null ? null : aside.get(body);
        if (speeds == null) {
            return false;
        }
        speeds[0].mul(factor);
        speeds[1].mul(factor);
        return true;
    }

    /** What of a body is under water or another liquid this tick, worked out on its first physics step. */
    private static Wet wet(ServerLevel level, ServerSubLevel body, UUID id, long tick) {
        Wet known = WET.get(id);
        if (known != null && known.tick() == tick) {
            return known;
        }
        Pose3d pose = body.logicalPose();
        Vector3d middle = pose.transformPosition(new Vector3d(body.getMassTracker().getCenterOfMass()), new Vector3d());
        // half its height: from its volume as if a cube, which is near enough for how deep it sits
        double half = Math.max(0.05, Math.cbrt(Math.max(1.0e-4, body.getMassTracker().getMass())) / 2.0);
        int wetCount = 0;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int i = 0; i < SAMPLES; i++) {
            double y = middle.y - half + (i + 0.5) * (2.0 * half / SAMPLES);
            at.set(middle.x, y, middle.z);
            if (!level.isLoaded(at)) {
                break;
            }
            FluidState fluid = level.getFluidState(at);
            if (!fluid.isEmpty() && y - at.getY() <= fluid.getHeight(level, at)) {
                wetCount++;
            }
        }
        Wet wet = new Wet(tick, wetCount / (double) SAMPLES, touches(level, body, pose));
        WET.put(id, wet);
        return wet;
    }

    /** Whether any of a body's cells is where Sable would drag it: any of the eighths of its blocks it tries in a liquid. */
    private static boolean touches(ServerLevel level, ServerSubLevel body, Pose3d pose) {
        BoundingBox3ic bounds = body.getPlot().getBoundingBox();
        if (bounds == null) {
            return false;
        }
        boolean fine = (bounds.maxX() - bounds.minX()) + (bounds.maxY() - bounds.minY()) + (bounds.maxZ() - bounds.minZ()) < SABLE_FINE_BELOW;
        double size = fine ? 0.25 : 0.5;
        BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos water = new BlockPos.MutableBlockPos();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    cell.set(x, y, z);
                    if (!(level.getBlockState(cell).getBlock() instanceof CarcassPartBlock)) {
                        continue;
                    }
                    for (int i = 0; i < (fine ? 8 : 1); i++) {
                        Vector3d plot = fine
                                ? new Vector3d(x + 0.5 + ((i & 1) * 2 - 1) * 0.25, y + 0.5 + (((i >> 1) & 1) * 2 - 1) * 0.25, z + 0.5 + (((i >> 2) & 1) * 2 - 1) * 0.25)
                                : new Vector3d(x + 0.5, y + 0.5, z + 0.5);
                        Vector3d world = pose.transformPosition(plot, new Vector3d());
                        for (int wx = (int) Math.floor(world.x - size); wx <= (int) Math.floor(world.x + size); wx++) {
                            for (int wy = (int) Math.floor(world.y - size); wy <= (int) Math.floor(world.y + size); wy++) {
                                for (int wz = (int) Math.floor(world.z - size); wz <= (int) Math.floor(world.z + size); wz++) {
                                    water.set(wx, wy, wz);
                                    if (level.isLoaded(water) && !level.getFluidState(water).isEmpty()
                                            && span(world.x - size, world.x + size, wx) * span(world.y - size, world.y + size, wy) * span(world.z - size, world.z + size, wz) > 0.0) {
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    /** How much of [from, to] lies in the block from {@code block} to {@code block + 1}. */
    private static double span(double from, double to, int block) {
        return Math.max(0.0, Math.min(to, block + 1.0) - Math.max(from, block));
    }
}
