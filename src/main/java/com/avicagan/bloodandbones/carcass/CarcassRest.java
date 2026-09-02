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
 * The resting form. A carcass that has lain still for a while folds its limbs into the torso's sub-level:
 * the limb bodies are removed, their poses relative to the torso are remembered, coarse collision cells
 * for them are added to the torso's plot, and the torso's root cell draws every part. Hooking, dragging
 * or hitting it splits it back into a ragdoll at exactly the remembered poses.
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
            rest(level, carcass);
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
        Optional<Rig> maybeRig = RigManager.all().values().stream().filter(r -> r.entity().equals(carcass.entity)).findFirst();
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
        Map<BlockPos, int[]> cellSizes = new LinkedHashMap<>();

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
            mergedParts.add(new CarcassPartBlockEntity.MergedPart(bone.name(), bone.part(), new Vector3f(bone.boxMin()),
                    new Vector3f((float) relPos.x, (float) relPos.y, (float) relPos.z), new Quaternionf(relRot)));

            // coarse collision cells in the torso's plot where the limb's box mostly fills a block
            Vector3d torsoOriginOffset = CarcassAssembler.originOffset(torsoBone);
            Vector3d boneMin = new Vector3d(bone.boxMin()).div(16.0);
            Vector3d boneMax = new Vector3d(bone.boxMax()).div(16.0);
            Vector3d[] corners = new Vector3d[8];
            Vector3d lo = new Vector3d(Double.MAX_VALUE);
            Vector3d hi = new Vector3d(-Double.MAX_VALUE);
            for (int i = 0; i < 8; i++) {
                Vector3d c = new Vector3d((i & 1) == 0 ? boneMin.x : boneMax.x, (i & 2) == 0 ? boneMin.y : boneMax.y, (i & 4) == 0 ? boneMin.z : boneMax.z);
                relRot.transform(c).add(relPos).add(torsoOriginOffset).add(center.getX(), center.getY(), center.getZ());
                corners[i] = c;
                lo.min(c);
                hi.max(c);
            }
            Quaterniond relInverse = new Quaterniond(relRot).invert();
            for (int x = (int) Math.floor(lo.x); x <= (int) Math.floor(hi.x); x++) {
                for (int y = (int) Math.floor(lo.y); y <= (int) Math.floor(hi.y); y++) {
                    for (int z = (int) Math.floor(lo.z); z <= (int) Math.floor(hi.z); z++) {
                        BlockPos cell = new BlockPos(x, y, z);
                        if (!level.getBlockState(cell).isAir()) {
                            continue; // one of the torso's own cells
                        }
                        int[] extent = extent(cell, relPos, relInverse, torsoOriginOffset, center, boneMin, boneMax);
                        if (extent == null) {
                            continue;
                        }
                        int[] existing = cellSizes.get(cell);
                        if (existing == null) {
                            carcass.restCells.add(cell);
                            cellSizes.put(cell, extent);
                        } else {
                            // two limbs share this cell (a pair of legs): the cell covers both
                            existing[0] = Math.max(existing[0], extent[0]);
                            existing[1] = Math.max(existing[1], extent[1]);
                            existing[2] = Math.max(existing[2], extent[2]);
                        }
                    }
                }
            }
            toRemove.add(limb);
        }

        // place the cells (creating plot chunks as needed)
        Block block = BBBlocks.CARCASS_PART.get();
        for (BlockPos cell : carcass.restCells) {
            ChunkPos chunk = new ChunkPos(cell);
            if (plot.getChunkHolder(plot.toLocal(chunk)) == null) {
                plot.newEmptyChunk(chunk);
            }
            int[] size = cellSizes.getOrDefault(cell, new int[]{16, 16, 16});
            level.setBlock(cell, CarcassPartBlock.stateFor(block, size[0], size[1], size[2]), Block.UPDATE_ALL);
            if (level.getBlockEntity(cell) instanceof CarcassPartBlockEntity be) {
                be.configureFiller(carcass.id, torsoBone);
            }
        }

        // drop the limb bodies
        data.mergingLimbs = true;
        try {
            for (ServerSubLevel limb : toRemove) {
                container.removeSubLevel(limb, SubLevelRemovalReason.REMOVED);
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

    /**
     * The part of this block cell the limb's box fills, sampled on a 4x4x4 grid, as a box size in pixels from
     * the cell's minimum corner (a cell's collision shape always starts at that corner). Null when the limb
     * does not reach into the cell.
     */
    @Nullable
    private static int[] extent(BlockPos cell, Vector3d relPos, Quaterniond relInverse, Vector3d torsoOriginOffset, BlockPos center,
                                Vector3d boneMin, Vector3d boneMax) {
        int maxI = -1;
        int maxJ = -1;
        int maxK = -1;
        Vector3d p = new Vector3d();
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                for (int k = 0; k < 4; k++) {
                    p.set(cell.getX() + (i + 0.5) / 4.0, cell.getY() + (j + 0.5) / 4.0, cell.getZ() + (k + 0.5) / 4.0);
                    // torso plot -> torso bone frame -> limb bone frame
                    p.sub(center.getX(), center.getY(), center.getZ()).sub(torsoOriginOffset).sub(relPos);
                    relInverse.transform(p);
                    if (p.x >= boneMin.x && p.x <= boneMax.x && p.y >= boneMin.y && p.y <= boneMax.y && p.z >= boneMin.z && p.z <= boneMax.z) {
                        maxI = Math.max(maxI, i);
                        maxJ = Math.max(maxJ, j);
                        maxK = Math.max(maxK, k);
                    }
                }
            }
        }
        if (maxI < 0) {
            return null;
        }
        return new int[]{(maxI + 1) * 4, (maxJ + 1) * 4, (maxK + 1) * 4};
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

    private static void unlock(CarcassSavedData.Carcass carcass) {
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
        Optional<Rig> maybeRig = RigManager.all().values().stream().filter(r -> r.entity().equals(carcass.entity)).findFirst();
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

        unlock(carcass);
        // the rest cells go first so the torso is back to its own shape
        for (BlockPos cell : carcass.restCells) {
            if (level.getBlockState(cell).is(BBBlocks.CARCASS_PART.get())) {
                level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        carcass.restCells.clear();

        Pose3d torsoPose = torso.logicalPose();
        Vector3d torsoOriginWorld = torsoPose.transformPosition(CarcassAssembler.boneOriginInPlot(torso, torsoBone), new Vector3d());
        BlockPos staging = CarcassAssembler.findStaging(level, BlockPos.containing(torsoOriginWorld.x, torsoOriginWorld.y, torsoOriginWorld.z), rig);
        if (staging == null) {
            BloodAndBones.LOGGER.warn("No staging space to unfold carcass {}", carcass.id);
            return null;
        }
        for (Bone bone : rig.bones()) {
            if (bone == torsoBone) {
                continue;
            }
            CarcassSavedData.RestPose rest = carcass.restPoses.get(bone.name());
            if (rest == null) {
                continue;
            }
            ServerSubLevel limb = CarcassAssembler.assembleBone(level, staging, carcass.id, rig, bone);
            if (limb == null) {
                BloodAndBones.LOGGER.warn("Could not re-assemble {} of carcass {}", bone.name(), carcass.id);
                continue;
            }
            Vector3d origin = new Vector3d(torsoPose.orientation().transform(new Vector3d(rest.position()))).add(torsoOriginWorld);
            Quaterniond orientation = new Quaterniond(torsoPose.orientation()).mul(rest.orientation());
            CarcassAssembler.pose(pipeline, limb, bone, origin, orientation);
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
