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
    private CarcassAssembler() {
    }

    public static boolean assemble(LivingEntity entity, @Nullable Entity attacker) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return false;
        }
        if (entity.isBaby()) {
            return false;
        }
        Optional<Rig> maybeRig = RigManager.forEntity(entity.getType());
        if (maybeRig.isEmpty()) {
            return false;
        }
        Rig rig = maybeRig.get();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return false;
        }
        SubLevelPhysicsSystem physics = container.physicsSystem();
        PhysicsPipeline pipeline = physics.getPipeline();

        BlockPos staging = findStaging(level, entity.blockPosition(), rig);
        if (staging == null) {
            BloodAndBones.LOGGER.warn("No free staging space above {} for a carcass", entity.blockPosition());
            return false;
        }

        UUID carcassId = UUID.randomUUID();
        Vec3 feet = entity.position();
        Vector3d base = new Vector3d(feet.x, feet.y + 1.501, feet.z);
        Quaterniond g = new Quaterniond().rotationY(Math.toRadians(180.0 - entity.yBodyRot)).rotateZ(Math.PI);

        Map<String, ServerSubLevel> subLevels = new LinkedHashMap<>();
        for (Bone bone : rig.bones()) {
            ServerSubLevel subLevel = assembleBone(level, staging, carcassId, rig, bone);
            if (subLevel == null) {
                for (ServerSubLevel created : subLevels.values()) {
                    container.removeSubLevel(created, SubLevelRemovalReason.REMOVED);
                }
                return false;
            }

            Quaterniond orientation = new Quaterniond(g).mul(new Quaterniond(bone.rotation()));
            Vector3d origin = g.transform(new Vector3d(bone.offset()).div(16.0)).add(base);

            Pose3d pose = subLevel.logicalPose();
            pose.orientation().set(orientation);
            Vector3d current = pose.transformPosition(boneOriginInPlot(subLevel, bone), new Vector3d());
            pose.position().add(origin.sub(current));
            pipeline.teleport(subLevel, pose.position(), pose.orientation());
            subLevel.updateLastPose();

            subLevels.put(bone.name(), subLevel);
        }

        CarcassSavedData.Carcass carcass = new CarcassSavedData.Carcass(carcassId, rig.entity(), rig.root().name());
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
            CarcassJoints.Spec spec = jointSpec(parent, subLevels.get(parent.name()), bone, subLevels.get(bone.name()));
            carcass.joints.add(spec);
            PhysicsConstraintHandle handle = CarcassJoints.attach(pipeline, subLevels.get(parent.name()), subLevels.get(bone.name()), spec);
            if (handle != null) {
                carcass.liveJoints.add(handle);
            } else {
                BloodAndBones.LOGGER.warn("Sable refused joint {} -> {}", parent.name(), bone.name());
            }
        }

        Vec3 velocity = entity.getDeltaMovement().scale(20.0);
        if (attacker != null) {
            velocity = velocity.add(attacker.getLookAngle().scale(3.0));
        }
        Vector3d linear = new Vector3d(velocity.x, velocity.y, velocity.z);
        for (ServerSubLevel subLevel : subLevels.values()) {
            RigidBodyHandle handle = physics.getPhysicsHandle(subLevel);
            handle.addLinearAndAngularVelocity(linear, new Vector3d());
        }

        CarcassSavedData.get(level).add(carcass);
        BloodAndBones.LOGGER.debug("Assembled {} carcass {} with {} bones and {} joints", rig.entity(), carcassId, subLevels.size(), carcass.liveJoints.size());
        return true;
    }

    /**
     * Plot-space point of the bone's own origin: the box minimum corner sits on the plot center block's corner.
     */
    private static Vector3d boneOriginInPlot(ServerSubLevel subLevel, Bone bone) {
        BlockPos anchor = subLevel.getPlot().getCenterBlock();
        Vector3f min = bone.boxMin();
        return new Vector3d(anchor.getX() - min.x / 16.0, anchor.getY() - min.y / 16.0, anchor.getZ() - min.z / 16.0);
    }

    private static CarcassJoints.Spec jointSpec(Bone parent, ServerSubLevel parentSubLevel, Bone child, ServerSubLevel childSubLevel) {
        // Child pivot expressed in the parent's part-local pixels, then in the parent's plot space.
        Vector3d relative = new Vector3d(child.offset()).sub(new Vector3d(parent.offset()));
        new Quaterniond(parent.rotation()).invert().transform(relative);
        Vector3d anchorParent = boneOriginInPlot(parentSubLevel, parent).add(relative.div(16.0));
        Vector3d anchorChild = boneOriginInPlot(childSubLevel, child);

        // Joint frame = the child's rest frame. Relative to the parent that is parentRot^-1 * childRot.
        Quaterniond frame1 = new Quaterniond(parent.rotation()).invert().mul(new Quaterniond(child.rotation()));
        Quaterniond frame2 = new Quaterniond();

        JointSpec joint = child.jointOrDefault();
        Vector3f min = new Vector3f(joint.minDegrees()).mul((float) (Math.PI / 180.0));
        Vector3f max = new Vector3f(joint.maxDegrees()).mul((float) (Math.PI / 180.0));
        return new CarcassJoints.Spec(parent.name(), child.name(), anchorParent, anchorChild, frame1, frame2,
                min, max, joint.damping(), joint.stiffness());
    }

    /**
     * Places the bone's block cells in the world at the staging spot and hands them to Sable.
     */
    @Nullable
    private static ServerSubLevel assembleBone(ServerLevel level, BlockPos staging, UUID carcassId, Rig rig, Bone bone) {
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
                    if (level.getBlockEntity(pos) instanceof CarcassPartBlockEntity be) {
                        if (i == 0 && j == 0 && k == 0) {
                            be.configureRoot(carcassId, rig, bone);
                        } else {
                            be.configureFiller(carcassId, bone);
                        }
                    }
                    blocks.add(pos);
                }
            }
        }
        BoundingBox3i bounds = new BoundingBox3i(staging, staging.offset(cells[0] - 1, cells[1] - 1, cells[2] - 1));
        ServerSubLevel subLevel;
        try {
            subLevel = SubLevelAssemblyHelper.assembleBlocks(level, staging, blocks, bounds);
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
    private static BlockPos findStaging(ServerLevel level, BlockPos near, Rig rig) {
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
