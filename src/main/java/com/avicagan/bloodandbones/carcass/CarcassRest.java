package com.avicagan.bloodandbones.carcass;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.rig.Bone;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.avicagan.bloodandbones.carcass.rig.RigManager;
import com.avicagan.bloodandbones.registry.BBBlocks;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The resting form. A carcass that has lain still for a while folds into one body: the limb bodies are
 * removed, their poses relative to the torso are remembered, and the torso's root cell draws every part.
 * Only the torso's own cells collide while it rests (extra cells for the limbs looked like blocks and
 * were not worth it). Hooking, punching or losing its footing splits it back into a ragdoll at exactly
 * the remembered poses.
 */
public final class CarcassRest {
    /** Ticks of stillness before folding. */
    public static final int STILL_TICKS = 60;
    private static final double STILL_LINEAR = 0.05;
    private static final double STILL_ANGULAR = 0.1;

    private CarcassRest() {
    }

    /** Called every tick from the torso's root cell while the carcass is a ragdoll. */
    public static void tick(ServerLevel level, CarcassSavedData.Carcass carcass) {
        if (carcass.resting) {
            return;
        }
        if (isHeld(level, carcass)) {
            carcass.stillTicks = 0;
            return;
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }
        SubLevelPhysicsSystem physics = container.physicsSystem();
        Vector3d linear = new Vector3d();
        Vector3d angular = new Vector3d();
        for (UUID id : carcass.bones.values()) {
            SubLevel subLevel = container.getSubLevel(id);
            if (!(subLevel instanceof ServerSubLevel serverSubLevel) || serverSubLevel.isRemoved()) {
                carcass.stillTicks = 0;
                return;
            }
            RigidBodyHandle handle = physics.getPhysicsHandle(serverSubLevel);
            handle.getLinearVelocity(linear);
            handle.getAngularVelocity(angular);
            if (linear.length() > STILL_LINEAR || angular.length() > STILL_ANGULAR) {
                carcass.stillTicks = 0;
                return;
            }
        }
        carcass.stillTicks++;
        if (carcass.stillTicks >= STILL_TICKS) {
            // this runs inside Sable's loop over every sub-level; removing bodies here would mutate that
            // list mid-walk, so the fold itself waits for the end of the level tick
            PENDING.computeIfAbsent(level, l -> new java.util.LinkedHashSet<>()).add(carcass.id);
        }
    }

    /** Carcasses that have earned their rest this tick, folded from the level tick. */
    private static final Map<ServerLevel, java.util.Set<UUID>> PENDING = new java.util.WeakHashMap<>();

    /** End of level tick: fold whatever went still, unfold whatever lost its footing. */
    public static void levelTick(ServerLevel level) {
        java.util.Set<UUID> pending = PENDING.get(level);
        if (pending != null && !pending.isEmpty()) {
            CarcassSavedData data = CarcassSavedData.get(level);
            for (UUID id : List.copyOf(pending)) {
                CarcassSavedData.Carcass carcass = data.carcass(id);
                if (carcass != null && !carcass.resting && !isHeld(level, carcass)) {
                    rest(level, carcass);
                }
            }
            pending.clear();
        }
        java.util.Set<UUID> pendingSplit = PENDING_SPLIT.get(level);
        if (pendingSplit != null && !pendingSplit.isEmpty()) {
            CarcassSavedData data = CarcassSavedData.get(level);
            for (UUID id : List.copyOf(pendingSplit)) {
                CarcassSavedData.Carcass carcass = data.carcass(id);
                if (carcass != null && carcass.resting) {
                    split(level, carcass);
                }
            }
            pendingSplit.clear();
        }
    }

    /** True while a player drags any limb or a hook holds the carcass. */
    private static boolean isHeld(ServerLevel level, CarcassSavedData.Carcass carcass) {
        if (CarcassDrag.isDraggingCarcass(carcass.id)) {
            return true;
        }
        return ShackleHookBlockEntity.isHanging(level, carcass.id);
    }

    /**
     * Fold the ragdoll into the torso.
     */
    public static boolean rest(ServerLevel level, CarcassSavedData.Carcass carcass) {
        if (carcass.resting) {
            return true;
        }
        Optional<Rig> maybeRig = RigManager.forEntity(carcass.entity);
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (maybeRig.isEmpty() || container == null) {
            return false;
        }
        Rig rig = maybeRig.get();
        Bone torsoBone = rig.root();
        SubLevel torsoSub = container.getSubLevel(carcass.bones.get(torsoBone.name()));
        if (!(torsoSub instanceof ServerSubLevel torso) || torso.isRemoved()) {
            return false;
        }
        CarcassSavedData data = CarcassSavedData.get(level);
        PhysicsPipeline pipeline = container.physicsSystem().getPipeline();

        // remove the joints first: their bodies are about to go
        for (PhysicsConstraintHandle handle : carcass.liveJoints) {
            if (handle.isValid()) {
                handle.remove();
            }
        }
        carcass.liveJoints.clear();

        Pose3d torsoPose = torso.logicalPose();
        Vector3d torsoOriginPlot = CarcassAssembler.boneOriginInPlot(torso, torsoBone);
        Vector3d torsoOriginWorld = torsoPose.transformPosition(torsoOriginPlot, new Vector3d());
        Quaterniond torsoInverse = new Quaterniond(torsoPose.orientation()).invert();

        List<CarcassPartBlockEntity.MergedPart> mergedParts = new ArrayList<>();
        List<ServerSubLevel> toRemove = new ArrayList<>();
        carcass.restPoses.clear();
        carcass.restCells.clear();
        BlockPos center = torso.getPlot().getCenterBlock();
        LevelPlot plot = torso.getPlot();

        for (Bone bone : rig.bones()) {
            if (bone == torsoBone) {
                continue;
            }
            SubLevel limbSub = container.getSubLevel(carcass.bones.get(bone.name()));
            if (!(limbSub instanceof ServerSubLevel limb) || limb.isRemoved()) {
                continue;
            }
            Pose3d limbPose = limb.logicalPose();
            Vector3d originWorld = limbPose.transformPosition(CarcassAssembler.boneOriginInPlot(limb, bone), new Vector3d());
            // limb bone frame relative to the torso bone frame
            Vector3d relPos = torsoInverse.transform(new Vector3d(originWorld).sub(torsoOriginWorld));
            Quaterniond relRot = new Quaterniond(torsoInverse).mul(limbPose.orientation());
            carcass.restPoses.put(bone.name(), new CarcassSavedData.RestPose(relPos, relRot));
            mergedParts.add(new CarcassPartBlockEntity.MergedPart(bone.name(),
                    new Vector3f((float) relPos.x, (float) relPos.y, (float) relPos.z), new Quaternionf(relRot)));

            toRemove.add(limb);
        }

        // drop the limb bodies; the rest poses now stand in for them
        data.mergingLimbs = true;
        try {
            for (ServerSubLevel limb : toRemove) {
                container.removeSubLevel(limb, SubLevelRemovalReason.REMOVED);
                carcass.bones.values().remove(limb.getUniqueId());
            }
        } finally {
            data.mergingLimbs = false;
        }

        carcass.resting = true;
        carcass.stillTicks = 0;
        lock(level, carcass, torso);
        data.setDirty();
        if (level.getBlockEntity(center) instanceof CarcassPartBlockEntity root) {
            root.setMerged(mergedParts);
            level.sendBlockUpdated(center, level.getBlockState(center), level.getBlockState(center), Block.UPDATE_CLIENTS);
        }
        pipeline.wakeUp(torso);
        BloodAndBones.LOGGER.debug("Carcass {} folded into its torso with {} rest cells", carcass.id, carcass.restCells.size());
        return true;
    }

    /** A limb's box in the torso plot's space: an oriented box, for exact overlap tests with block cells. */
    static final class Obb {
        final Vector3d center = new Vector3d();
        final Vector3d[] axes = {new Vector3d(), new Vector3d(), new Vector3d()};
        final double[] half = new double[3];
        final Vector3d lo = new Vector3d();
        final Vector3d hi = new Vector3d();

        /** @param origin the limb bone frame's origin offset in plot space (torso origin + plot corner) */
        static Obb of(Bone bone, Vector3d relPos, Quaterniond relRot, Vector3d origin) {
            Obb obb = new Obb();
            Vector3d min = new Vector3d(bone.boxMin()).div(16.0);
            Vector3d max = new Vector3d(bone.boxMax()).div(16.0);
            Vector3d localCenter = new Vector3d(min).add(max).mul(0.5);
            obb.half[0] = (max.x - min.x) * 0.5;
            obb.half[1] = (max.y - min.y) * 0.5;
            obb.half[2] = (max.z - min.z) * 0.5;
            relRot.transform(localCenter, obb.center).add(relPos).add(origin);
            relRot.transform(new Vector3d(1, 0, 0), obb.axes[0]);
            relRot.transform(new Vector3d(0, 1, 0), obb.axes[1]);
            relRot.transform(new Vector3d(0, 0, 1), obb.axes[2]);
            double rx = 0;
            double ry = 0;
            double rz = 0;
            for (int i = 0; i < 3; i++) {
                rx += Math.abs(obb.axes[i].x) * obb.half[i];
                ry += Math.abs(obb.axes[i].y) * obb.half[i];
                rz += Math.abs(obb.axes[i].z) * obb.half[i];
            }
            obb.lo.set(obb.center).sub(rx, ry, rz);
            obb.hi.set(obb.center).add(rx, ry, rz);
            return obb;
        }

        /**
         * The box, in pixels from the cell's minimum corner, that this cell needs to cover the part of the
         * limb inside it (a cell's collision shape always starts at that corner), or null when the limb
         * does not reach into the cell at all.
         */
        @Nullable
        int[] extentIn(BlockPos cell) {
            Vector3d cellCenter = new Vector3d(cell.getX() + 0.5, cell.getY() + 0.5, cell.getZ() + 0.5);
            if (!overlaps(cellCenter)) {
                return null;
            }
            // clip the box's own bounds to the cell
            double ex = Math.min(hi.x, cell.getX() + 1.0) - cell.getX();
            double ey = Math.min(hi.y, cell.getY() + 1.0) - cell.getY();
            double ez = Math.min(hi.z, cell.getZ() + 1.0) - cell.getZ();
            return new int[]{pixels(ex), pixels(ey), pixels(ez)};
        }

        private static int pixels(double blocks) {
            return Math.max(1, Math.min(16, (int) Math.ceil(blocks * 16.0 - 1.0E-6)));
        }

        /** Separating axis test between this box and the unit cube centred at {@code c}. */
        private boolean overlaps(Vector3d c) {
            Vector3d d = new Vector3d(center).sub(c);
            Vector3d[] world = {new Vector3d(1, 0, 0), new Vector3d(0, 1, 0), new Vector3d(0, 0, 1)};
            for (Vector3d axis : world) {
                if (separated(axis, d)) {
                    return false;
                }
            }
            for (Vector3d axis : axes) {
                if (separated(axis, d)) {
                    return false;
                }
            }
            for (Vector3d a : world) {
                for (Vector3d b : axes) {
                    Vector3d axis = new Vector3d(a).cross(b);
                    if (axis.lengthSquared() > 1.0E-8 && separated(axis, d)) {
                        return false;
                    }
                }
            }
            return true;
        }

        private boolean separated(Vector3d axis, Vector3d d) {
            double cube = 0.5 * (Math.abs(axis.x) + Math.abs(axis.y) + Math.abs(axis.z));
            double box = 0;
            for (int i = 0; i < 3; i++) {
                box += Math.abs(axes[i].dot(axis)) * half[i];
            }
            return Math.abs(d.dot(axis)) > cube + box;
        }
    }

    /** Cells in the torso's plot that belong to this carcass but are named after a limb: rest cells. */
    private static List<BlockPos> strayCells(ServerLevel level, ServerSubLevel torso, CarcassSavedData.Carcass carcass, Bone torsoBone) {
        List<BlockPos> stray = new ArrayList<>();
        for (var holder : torso.getPlot().getLoadedChunks()) {
            for (var entry : holder.getChunk().getBlockEntities().entrySet()) {
                if (entry.getValue() instanceof CarcassPartBlockEntity be && carcass.id.equals(be.carcassId())
                        && !be.bone().equals(torsoBone.name()) && level.getBlockState(entry.getKey()).is(BBBlocks.CARCASS_PART.get())) {
                    stray.add(entry.getKey().immutable());
                }
            }
        }
        return stray;
    }

    /** Ticks between looks at whether the resting body still has something under it. */
    private static final int SUPPORT_INTERVAL = 20;

    /**
     * While resting the body is pinned in place; if the ground under it goes (mined out, exploded), it
     * unfolds so gravity can have it again.
     */
    public static void tickResting(ServerLevel level, CarcassSavedData.Carcass carcass, ServerSubLevel torso) {
        if (Math.floorMod(level.getGameTime() + carcass.id.hashCode(), SUPPORT_INTERVAL) != 0) {
            return;
        }
        if (!isSupported(level, carcass, torso)) {
            // like the fold, the unfold must not run inside Sable's walk over its bodies
            BloodAndBones.LOGGER.debug("Carcass {} lost its support, unfolding", carcass.id);
            PENDING_SPLIT.computeIfAbsent(level, l -> new java.util.LinkedHashSet<>()).add(carcass.id);
        }
    }

    /** Resting carcasses that lost their footing this tick, unfolded from the level tick. */
    private static final Map<ServerLevel, java.util.Set<UUID>> PENDING_SPLIT = new java.util.WeakHashMap<>();

    /**
     * Something solid within a fifth of a block under the lowest corner of the body or its rest cells.
     * (A carcass propped up on folded legs rests on its leg cells, so those count.)
     */
    static boolean isSupported(ServerLevel level, CarcassSavedData.Carcass carcass, ServerSubLevel torso) {
        Optional<Rig> maybeRig = RigManager.forEntity(carcass.entity);
        if (maybeRig.isEmpty()) {
            return true;
        }
        Bone torsoBone = maybeRig.get().root();
        Pose3d pose = torso.logicalPose();
        BlockPos center = torso.getPlot().getCenterBlock();
        List<Vector3d> corners = new ArrayList<>();
        Vector3d min = new Vector3d(torsoBone.boxMin()).div(16.0).add(CarcassAssembler.originOffset(torsoBone)).add(center.getX(), center.getY(), center.getZ());
        Vector3d max = new Vector3d(torsoBone.boxMax()).div(16.0).add(CarcassAssembler.originOffset(torsoBone)).add(center.getX(), center.getY(), center.getZ());
        addCorners(corners, pose, min, max);
        // the folded limbs have no cells of their own, but a carcass propped on its legs rests on them
        Vector3d torsoOriginPlot = CarcassAssembler.boneOriginInPlot(torso, torsoBone);
        for (Map.Entry<String, CarcassSavedData.RestPose> entry : carcass.restPoses.entrySet()) {
            Bone bone = maybeRig.get().bone(entry.getKey()).orElse(null);
            if (bone == null) {
                continue;
            }
            Vector3d bMin = new Vector3d(bone.boxMin()).div(16.0);
            Vector3d bMax = new Vector3d(bone.boxMax()).div(16.0);
            for (int i = 0; i < 8; i++) {
                Vector3d c = new Vector3d((i & 1) == 0 ? bMin.x : bMax.x, (i & 2) == 0 ? bMin.y : bMax.y, (i & 4) == 0 ? bMin.z : bMax.z);
                entry.getValue().orientation().transform(c).add(entry.getValue().position()).add(torsoOriginPlot);
                corners.add(pose.transformPosition(c, new Vector3d()));
            }
        }
        double lowest = Double.MAX_VALUE;
        for (Vector3d corner : corners) {
            lowest = Math.min(lowest, corner.y);
        }
        for (Vector3d corner : corners) {
            if (corner.y > lowest + 0.25) {
                continue;
            }
            BlockPos below = BlockPos.containing(corner.x, corner.y - 0.2, corner.z);
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static void addCorners(List<Vector3d> out, Pose3d pose, Vector3d min, Vector3d max) {
        for (int i = 0; i < 8; i++) {
            Vector3d c = new Vector3d((i & 1) == 0 ? min.x : max.x, (i & 2) == 0 ? min.y : max.y, (i & 4) == 0 ? min.z : max.z);
            out.add(pose.transformPosition(c, new Vector3d()));
        }
    }

    /**
     * A resting carcass was hit: unfold it and shove the limb that took the blow.
     *
     * @return true if it unfolded
     */
    public static boolean disturb(ServerLevel level, CarcassSavedData.Carcass carcass, String bone, Vector3d direction, double speed) {
        Map<String, ServerSubLevel> bodies = split(level, carcass);
        if (bodies == null) {
            return false;
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        ServerSubLevel hit = bodies.getOrDefault(bone, bodies.get(carcass.rootBone));
        if (container != null && hit != null && !hit.isRemoved()) {
            container.physicsSystem().getPhysicsHandle(hit).addLinearAndAngularVelocity(new Vector3d(direction).normalize().mul(speed), new Vector3d());
        }
        return true;
    }

    /**
     * Pin the merged body where it is with a fully locked world joint: with its limbs gone it would
     * otherwise settle differently, and the remembered limb poses only hold if the torso does not move.
     */
    public static void lock(ServerLevel level, CarcassSavedData.Carcass carcass, ServerSubLevel torso) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null || torso.isRemoved()) {
            return;
        }
        if (carcass.restLock != null && carcass.restLock.isValid()) {
            carcass.restLock.remove();
        }
        carcass.restLock = null;
        BlockPos center = torso.getPlot().getCenterBlock();
        Vector3d plotPoint = new Vector3d(center.getX(), center.getY(), center.getZ());
        Pose3d pose = torso.logicalPose();
        Vector3d worldPoint = pose.transformPosition(plotPoint, new Vector3d());
        GenericConstraintConfiguration config = new GenericConstraintConfiguration(
                worldPoint, plotPoint, new Quaterniond(pose.orientation()), new Quaterniond(),
                EnumSet.allOf(ConstraintJointAxis.class));
        try {
            carcass.restLock = container.physicsSystem().getPipeline().addConstraint(null, torso, config);
        } catch (IllegalArgumentException e) {
            BloodAndBones.LOGGER.warn("Could not pin resting carcass {}: {}", carcass.id, e.getMessage());
        }
    }

    public static void unlock(CarcassSavedData.Carcass carcass) {
        if (carcass.restLock != null && carcass.restLock.isValid()) {
            carcass.restLock.remove();
        }
        carcass.restLock = null;
    }

    /**
     * Unfold: re-assemble every merged limb at its remembered pose and re-join it.
     *
     * @return bone name -> sub-level, or null on failure
     */
    @Nullable
    public static Map<String, ServerSubLevel> split(ServerLevel level, CarcassSavedData.Carcass carcass) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        Optional<Rig> maybeRig = RigManager.forEntity(carcass.entity);
        if (container == null || maybeRig.isEmpty()) {
            return null;
        }
        Rig rig = maybeRig.get();
        Bone torsoBone = rig.root();
        SubLevel torsoSub = container.getSubLevel(carcass.bones.get(torsoBone.name()));
        if (!(torsoSub instanceof ServerSubLevel torso) || torso.isRemoved()) {
            return null;
        }
        Map<String, ServerSubLevel> subLevels = new LinkedHashMap<>();
        subLevels.put(torsoBone.name(), torso);
        if (!carcass.resting) {
            for (Map.Entry<String, UUID> entry : carcass.bones.entrySet()) {
                SubLevel s = container.getSubLevel(entry.getValue());
                if (s instanceof ServerSubLevel ss) {
                    subLevels.put(entry.getKey(), ss);
                }
            }
            return subLevels;
        }
        PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        CarcassSavedData data = CarcassSavedData.get(level);

        Pose3d torsoPose = torso.logicalPose();
        Vector3d torsoOriginWorld = torsoPose.transformPosition(CarcassAssembler.boneOriginInPlot(torso, torsoBone), new Vector3d());
        // find room for the limbs before taking anything apart, so a failure leaves the rest intact
        BlockPos staging = CarcassAssembler.findStaging(level, BlockPos.containing(torsoOriginWorld.x, torsoOriginWorld.y, torsoOriginWorld.z), rig);
        if (staging == null) {
            BloodAndBones.LOGGER.warn("No staging space to unfold carcass {}", carcass.id);
            return null;
        }

        unlock(carcass);
        // the rest cells go first so the torso is back to its own shape; also any cell named after a limb
        // that the saved list does not know about (older saves lost the list)
        for (BlockPos cell : carcass.restCells) {
            if (level.getBlockState(cell).is(BBBlocks.CARCASS_PART.get())) {
                level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        carcass.restCells.clear();
        for (BlockPos cell : strayCells(level, torso, carcass, torsoBone)) {
            level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }

        for (Bone bone : rig.bones()) {
            if (bone == torsoBone) {
                continue;
            }
            CarcassSavedData.RestPose rest = carcass.restPoses.get(bone.name());
            if (rest == null) {
                continue;
            }
            ServerSubLevel limb = CarcassAssembler.assembleBone(level, staging, carcass.id, rig, bone, carcass.look);
            if (limb == null) {
                // the limb is lost: forget it and its joints rather than keep a dead reference
                BloodAndBones.LOGGER.warn("Could not re-assemble {} of carcass {}", bone.name(), carcass.id);
                carcass.bones.remove(bone.name());
                carcass.joints.removeIf(joint -> joint.parent().equals(bone.name()) || joint.child().equals(bone.name()));
                continue;
            }
            Vector3d origin = new Vector3d(torsoPose.orientation().transform(new Vector3d(rest.position()))).add(torsoOriginWorld);
            Quaterniond orientation = new Quaterniond(torsoPose.orientation()).mul(rest.orientation());
            CarcassAssembler.pose(pipeline, limb, bone, origin, orientation);
            if (level.getBlockEntity(limb.getPlot().getCenterBlock()) instanceof CarcassPartBlockEntity limbRoot) {
                limbRoot.setFreshness(carcass.freshness);
            }
            subLevels.put(bone.name(), limb);
            carcass.bones.put(bone.name(), limb.getUniqueId());
        }
        carcass.restPoses.clear();
        carcass.resting = false;
        carcass.stillTicks = 0;

        for (PhysicsConstraintHandle handle : carcass.liveJoints) {
            if (handle.isValid()) {
                handle.remove();
            }
        }
        carcass.liveJoints.clear();
        for (CarcassJoints.Spec joint : carcass.joints) {
            ServerSubLevel parent = subLevels.get(joint.parent());
            ServerSubLevel child = subLevels.get(joint.child());
            if (parent == null || child == null) {
                continue;
            }
            PhysicsConstraintHandle handle = CarcassJoints.attach(pipeline, parent, child, joint);
            if (handle != null) {
                carcass.liveJoints.add(handle);
            }
        }
        data.setDirty();
        BlockPos center = torso.getPlot().getCenterBlock();
        if (level.getBlockEntity(center) instanceof CarcassPartBlockEntity root) {
            root.setMerged(List.of());
            level.sendBlockUpdated(center, level.getBlockState(center), level.getBlockState(center), Block.UPDATE_CLIENTS);
        }
        for (ServerSubLevel s : subLevels.values()) {
            pipeline.wakeUp(s);
        }
        BloodAndBones.LOGGER.debug("Carcass {} unfolded into {} bodies", carcass.id, subLevels.size());
        return subLevels;
    }
}
