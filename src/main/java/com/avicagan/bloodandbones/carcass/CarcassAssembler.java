package com.avicagan.bloodandbones.carcass;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.rig.Bone;
import com.avicagan.bloodandbones.carcass.rig.JointSpec;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.avicagan.bloodandbones.carcass.rig.RigManager;
import com.avicagan.bloodandbones.registry.BBBlocks;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.ChunkPos;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns a dying mob into a jointed set of Sable sub-levels, one per rig bone, posed exactly where the
 * mob's model parts were at the moment of death.
 * <p>
 * Coordinate spaces: vanilla renders a model-space pixel point {@code P} at
 * {@code feet + (0, 1.501, 0) + G * P / 16} with {@code G = rotY(180 - bodyYaw) * rotZ(180)}. Each bone's
 * sub-level therefore gets orientation {@code G * boneRotation} and is positioned so that the bone's own
 * origin lands at {@code feet + (0, 1.501, 0) + G * boneOffset / 16}. Inside the sub-level the box's
 * minimum corner sits on the corner of the plot's center block, matching {@link CarcassPartBlock}.
 */
public final class CarcassAssembler {
    /** Shove speed for a cow-sized animal, in blocks per second. */
    private static final double SHOVE_SPEED = 2.6;
    /** A cow's weight in Sable mass units; lighter animals get shoved faster, heavier ones slower. */
    private static final double REFERENCE_WEIGHT = 0.8;

    private CarcassAssembler() {
    }

    /** The bone whose origin lies closest to the attacker's line of sight: where the killing blow landed. */
    private static String boneNearestRay(Entity attacker, Map<String, Vector3d> origins) {
        Vec3 eye = attacker.getEyePosition();
        Vec3 look = attacker.getLookAngle().normalize();
        String best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Map.Entry<String, Vector3d> entry : origins.entrySet()) {
            Vector3d o = entry.getValue();
            double rx = o.x - eye.x;
            double ry = o.y - eye.y;
            double rz = o.z - eye.z;
            double along = rx * look.x + ry * look.y + rz * look.z;
            double perpendicular = Math.sqrt(Math.max(0.0, rx * rx + ry * ry + rz * rz - along * along));
            if (perpendicular < bestDistance) {
                bestDistance = perpendicular;
                best = entry.getKey();
            }
        }
        return best;
    }

    /**
     * Builds the carcass where the mob stands, at rest. The kill shove is applied separately by
     * {@link #shove} once the client has had a moment to see the carcass (see {@link CarcassHandover}).
     *
     * @return the carcass record, or null if this mob has no rig or there was no room
     */
    @Nullable
    public static CarcassSavedData.Carcass assemble(LivingEntity entity, @Nullable Entity attacker) {
        return assemble(entity, attacker, true);
    }

    /**
     * @param drawNow whether the cells are told what to draw right away; a handover leaves them blank until
     *                the dead mob goes, so the client never sees both at once
     */
    @Nullable
    public static CarcassSavedData.Carcass assemble(LivingEntity entity, @Nullable Entity attacker, boolean drawNow) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return null;
        }
        if (entity.isBaby()) {
            return null;
        }
        Optional<Rig> maybeRig = RigManager.forEntity(entity.getType());
        if (maybeRig.isEmpty()) {
            return null;
        }
        Rig rig = maybeRig.get();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }
        SubLevelPhysicsSystem physics = container.physicsSystem();
        PhysicsPipeline pipeline = physics.getPipeline();

        BlockPos staging = findStaging(level, entity.blockPosition(), rig);
        if (staging == null) {
            BloodAndBones.LOGGER.warn("No free staging space above {} for a carcass", entity.blockPosition());
            return null;
        }

        UUID carcassId = UUID.randomUUID();
        Vec3 feet = entity.position();
        // vanilla's 1.501 lift is applied after the renderer's model scale, so it scales too
        Vector3d base = new Vector3d(feet.x, feet.y + 1.501 * rig.scale(), feet.z);
        Quaterniond g = new Quaterniond().rotationY(Math.toRadians(180.0 - entity.yBodyRot)).rotateZ(Math.PI);

        CarcassLook appearance = CarcassLook.of(entity, rig);
        Map<String, ServerSubLevel> subLevels = new LinkedHashMap<>();
        Map<String, Vector3d> origins = new LinkedHashMap<>();
        for (Bone bone : rig.bones()) {
            ServerSubLevel subLevel = assembleBone(level, staging, carcassId, rig, bone, appearance, drawNow);
            if (subLevel == null) {
                for (ServerSubLevel created : subLevels.values()) {
                    container.removeSubLevel(created, SubLevelRemovalReason.REMOVED);
                }
                return null;
            }

            Quaterniond orientation = new Quaterniond(g).mul(new Quaterniond(bone.rotation()));
            Vector3d origin = g.transform(new Vector3d(bone.offset()).div(16.0)).add(base);
            pose(pipeline, subLevel, bone, origin, orientation);

            subLevels.put(bone.name(), subLevel);
            origins.put(bone.name(), origin);
        }

        CarcassSavedData.Carcass carcass = new CarcassSavedData.Carcass(carcassId, rig.entity(), rig.root().name());
        carcass.look = appearance;
        subLevels.forEach((name, subLevel) -> carcass.bones.put(name, subLevel.getUniqueId()));

        for (Bone bone : rig.bones()) {
            if (bone.parent().isEmpty()) {
                continue;
            }
            Bone parent = rig.bone(bone.parent().get()).orElse(null);
            if (parent == null) {
                BloodAndBones.LOGGER.warn("Rig {}: bone {} has unknown parent {}", rig.entity(), bone.name(), bone.parent().get());
                continue;
            }
            CarcassJoints.Spec spec = jointSpec(parent, bone);
            carcass.joints.add(spec);
            PhysicsConstraintHandle handle = CarcassJoints.attach(pipeline, subLevels.get(parent.name()), subLevels.get(bone.name()), spec);
            if (handle != null) {
                carcass.liveJoints.add(handle);
            } else {
                BloodAndBones.LOGGER.warn("Sable refused joint {} -> {}", parent.name(), bone.name());
            }
        }

        if (attacker != null) {
            carcass.hitBone = boneNearestRay(attacker, origins);
        }

        CarcassSavedData.get(level).add(carcass);
        BloodAndBones.LOGGER.debug("Assembled {} carcass {} with {} bones and {} joints", rig.entity(), carcassId, subLevels.size(), carcass.liveJoints.size());
        return carcass;
    }

    /**
     * Starting motion: a shove sized by the animal's weight, strongest on the limb the killing blow hit.
     * The mob's own hit knockback is deliberately not carried over; it would launch light carcasses.
     *
     * @param look the killer's look direction
     */
    public static void shove(ServerLevel level, CarcassSavedData.Carcass carcass, Vec3 look) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        Rig rig = RigManager.forEntity(carcass.entity).orElse(null);
        if (container == null || rig == null) {
            return;
        }
        SubLevelPhysicsSystem physics = container.physicsSystem();
        Vec3 dir = new Vec3(look.x, Math.max(look.y, 0.0) + 0.2, look.z).normalize();
        double speed = Math.max(0.5, Math.min(5.0, SHOVE_SPEED / Math.sqrt(Math.max(rig.weight(), 0.01) / REFERENCE_WEIGHT)));
        for (Map.Entry<String, UUID> entry : carcass.bones.entrySet()) {
            if (!(container.getSubLevel(entry.getValue()) instanceof ServerSubLevel subLevel) || subLevel.isRemoved()) {
                continue;
            }
            double share = entry.getKey().equals(carcass.hitBone) ? 1.0 : 0.5;
            Vector3d velocity = new Vector3d(dir.x, dir.y, dir.z).mul(speed * share);
            physics.getPhysicsHandle(subLevel).addLinearAndAngularVelocity(velocity, new Vector3d());
            physics.getPipeline().wakeUp(subLevel);
        }
    }

    /**
     * Plot-space point of the bone's own origin: the box minimum corner sits on the plot center block's corner.
     */
    /**
     * Freshly assembled blocks are uploaded to the physics engine before the new body owns them, so a limb
     * can spend a few ticks without any collider and fall through the floor. Sable's own plot loading and
     * recovery code re-uploads every section bound to the body; do the same right after assembly.
     */
    public static void bindColliders(ServerLevel level, ServerSubLevel subLevel) {
        SubLevelPhysicsSystem physics = SubLevelPhysicsSystem.get(level);
        if (physics == null) {
            return;
        }
        PhysicsPipeline pipeline = physics.getPipeline();
        for (PlotChunkHolder holder : subLevel.getPlot().getLoadedChunks()) {
            LevelChunk chunk = holder.getChunk();
            ChunkPos global = chunk.getPos();
            LevelChunkSection[] sections = chunk.getSections();
            for (int i = 0; i < chunk.getSectionsCount(); i++) {
                LevelChunkSection section = sections[i];
                if (!section.hasOnlyAir()) {
                    int sectionY = chunk.getSectionYFromSectionIndex(i);
                    pipeline.handleChunkSectionAddition(section, global.x, sectionY, global.z, true);
                }
            }
        }
        subLevel.updateMergedMassData(1.0F);
        pipeline.onStatsChanged(subLevel);
    }

    public static Vector3d boneOriginInPlot(ServerSubLevel subLevel, Bone bone) {
        BlockPos anchor = subLevel.getPlot().getCenterBlock();
        Vector3f min = bone.boxMin();
        return new Vector3d(anchor.getX() - min.x / 16.0, anchor.getY() - min.y / 16.0, anchor.getZ() - min.z / 16.0);
    }

    /** Offset from a plot's center-block corner to the bone's origin: the box minimum corner sits on that corner. */
    public static Vector3d originOffset(Bone bone) {
        Vector3f min = bone.boxMin();
        return new Vector3d(-min.x / 16.0, -min.y / 16.0, -min.z / 16.0);
    }

    public static CarcassJoints.Spec jointSpec(Bone parent, Bone child) {
        // Child pivot expressed in the parent's part-local frame, in blocks.
        Vector3d relative = new Vector3d(child.offset()).sub(new Vector3d(parent.offset()));
        new Quaterniond(parent.rotation()).invert().transform(relative);
        relative.div(16.0);

        // Joint frame = the child's rest frame. Relative to the parent that is parentRot^-1 * childRot.
        Quaterniond frame1 = new Quaterniond(parent.rotation()).invert().mul(new Quaterniond(child.rotation()));
        Quaterniond frame2 = new Quaterniond();

        JointSpec joint = child.jointOrDefault();
        Vector3f min = new Vector3f(joint.minDegrees()).mul((float) (Math.PI / 180.0));
        Vector3f max = new Vector3f(joint.maxDegrees()).mul((float) (Math.PI / 180.0));
        return new CarcassJoints.Spec(parent.name(), child.name(), originOffset(parent), originOffset(child), relative,
                frame1, frame2, min, max, joint.damping(), joint.stiffness(), joint.contacts());
    }

    /**
     * Moves a freshly assembled bone sub-level so that its bone origin lands at {@code origin} with
     * orientation {@code orientation}.
     */
    public static void pose(PhysicsPipeline pipeline, ServerSubLevel subLevel, Bone bone, Vector3d origin, Quaterniond orientation) {
        Pose3d pose = subLevel.logicalPose();
        pose.orientation().set(orientation);
        Vector3d current = pose.transformPosition(boneOriginInPlot(subLevel, bone), new Vector3d());
        pose.position().add(new Vector3d(origin).sub(current));
        pipeline.teleport(subLevel, pose.position(), pose.orientation());
        subLevel.updateLastPose();
    }

    /**
     * Places the bone's block cells in the world at the staging spot and hands them to Sable.
     */
    @Nullable
    public static ServerSubLevel assembleBone(ServerLevel level, BlockPos staging, UUID carcassId, Rig rig, Bone bone, CarcassLook look) {
        return assembleBone(level, staging, carcassId, rig, bone, look, true);
    }

    @Nullable
    public static ServerSubLevel assembleBone(ServerLevel level, BlockPos staging, UUID carcassId, Rig rig, Bone bone, CarcassLook look, boolean drawNow) {
        int[] cells = cellCounts(bone);
        int sx = pixels(bone.boxSize().x);
        int sy = pixels(bone.boxSize().y);
        int sz = pixels(bone.boxSize().z);
        Block block = BBBlocks.CARCASS_PART.get();
        List<BlockPos> blocks = new ArrayList<>();
        for (int i = 0; i < cells[0]; i++) {
            for (int j = 0; j < cells[1]; j++) {
                for (int k = 0; k < cells[2]; k++) {
                    BlockPos pos = staging.offset(i, j, k);
                    BlockState state = CarcassPartBlock.stateFor(block,
                            Math.min(16, sx - 16 * i), Math.min(16, sy - 16 * j), Math.min(16, sz - 16 * k));
                    level.setBlock(pos, state, Block.UPDATE_ALL);
                    blocks.add(pos);
                }
            }
        }
        BoundingBox3i bounds = new BoundingBox3i(staging, staging.offset(cells[0] - 1, cells[1] - 1, cells[2] - 1));
        ServerSubLevel subLevel;
        try {
            subLevel = SubLevelAssemblyHelper.assembleBlocks(level, staging, blocks, bounds);
            if (subLevel != null && !subLevel.isRemoved()) {
                // the cells are configured only now that they are in their plot: for the tick they spend at
                // the staging spot high above the mob nothing must draw there
                if (drawNow) {
                    configureCells(level, subLevel, carcassId, rig, bone, look, false);
                }
                bindColliders(level, subLevel);
            }
        } catch (RuntimeException e) {
            BloodAndBones.LOGGER.error("Sable failed to assemble bone {} of {}", bone.name(), rig.entity(), e);
            subLevel = null;
        }
        for (BlockPos pos : blocks) {
            if (level.getBlockState(pos).is(block)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        if (subLevel == null || subLevel.isRemoved()) {
            return null;
        }
        return subLevel;
    }

    /** Tell a limb's cells which carcass, bone and look they are; optionally push that to clients now. */
    public static void configureCells(ServerLevel level, ServerSubLevel subLevel, UUID carcassId, Rig rig, Bone bone, CarcassLook look, boolean notify) {
        int[] cells = cellCounts(bone);
        BlockPos center = subLevel.getPlot().getCenterBlock();
        for (int i = 0; i < cells[0]; i++) {
            for (int j = 0; j < cells[1]; j++) {
                for (int k = 0; k < cells[2]; k++) {
                    BlockPos pos = center.offset(i, j, k);
                    if (level.getBlockEntity(pos) instanceof CarcassPartBlockEntity be) {
                        if (i == 0 && j == 0 && k == 0) {
                            be.configureRoot(carcassId, rig, bone, look);
                        } else {
                            be.configureFiller(carcassId, bone);
                        }
                        if (notify) {
                            level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), Block.UPDATE_CLIENTS);
                        }
                    }
                }
            }
        }
    }

    /** World position of a bone's body (its centre of mass), or null if it is not loaded. */
    @Nullable
    public static Vector3d boneWorldPosition(ServerLevel level, CarcassSavedData.Carcass carcass, @Nullable String bone) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        UUID id = bone == null ? null : carcass.bones.get(bone);
        if (id == null) {
            id = carcass.bones.get(carcass.rootBone);
        }
        if (container == null || id == null || !(container.getSubLevel(id) instanceof ServerSubLevel subLevel) || subLevel.isRemoved()) {
            return null;
        }
        return new Vector3d(subLevel.logicalPose().position());
    }

    /** Configure every limb of a carcass whose cells were left blank at assembly. */
    public static void configureCells(ServerLevel level, CarcassSavedData.Carcass carcass) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        Rig rig = RigManager.forEntity(carcass.entity).orElse(null);
        if (container == null || rig == null) {
            return;
        }
        for (Map.Entry<String, UUID> entry : carcass.bones.entrySet()) {
            Bone bone = rig.bone(entry.getKey()).orElse(null);
            if (bone != null && container.getSubLevel(entry.getValue()) instanceof ServerSubLevel subLevel && !subLevel.isRemoved()) {
                configureCells(level, subLevel, carcass.id, rig, bone, carcass.look, true);
            }
        }
    }

    private static int pixels(float size) {
        return Math.max(1, Math.round(size));
    }

    private static int[] cellCounts(Bone bone) {
        Vector3f size = bone.boxSize();
        return new int[]{
                Math.max(1, (pixels(size.x) + 15) / 16),
                Math.max(1, (pixels(size.y) + 15) / 16),
                Math.max(1, (pixels(size.z) + 15) / 16)};
    }

    /**
     * Finds air high above the mob where the block cells can be placed for a moment before Sable moves
     * them into their own plot.
     */
    @Nullable
    public static BlockPos findStaging(ServerLevel level, BlockPos near, Rig rig) {
        int span = 1;
        for (Bone bone : rig.bones()) {
            int[] cells = cellCounts(bone);
            span = Math.max(span, Math.max(cells[0], Math.max(cells[1], cells[2])));
        }
        int top = level.getMaxBuildHeight() - 1 - span;
        for (int y = top; y > level.getMinBuildHeight() + 1; y -= span + 2) {
            BlockPos candidate = new BlockPos(near.getX(), y, near.getZ());
            if (isClear(level, candidate, span)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean isClear(ServerLevel level, BlockPos origin, int span) {
        for (int i = 0; i < span; i++) {
            for (int j = 0; j < span; j++) {
                for (int k = 0; k < span; k++) {
                    if (!level.getBlockState(origin.offset(i, j, k)).isAir()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
