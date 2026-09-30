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
import org.joml.Vector3dc;
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
    /** How fast a kill's blow sets a cow-sized carcass moving as a whole, in blocks per second. */
    private static final double BLOW_SPEED = 2.6;
    /** A cow's weight in Sable mass units; lighter animals are knocked faster, heavier ones slower. */
    private static final double REFERENCE_WEIGHT = 0.8;
    /**
     * The fastest a blow sets any carcass moving: a chicken or a rabbit is knocked a little faster than a cow, not flung.
     * The whole blow lands on one point, so at the 5 the shove allowed the light ones tumbled four blocks and more.
     */
    private static final double BLOW_MAX_SPEED = 3.0;
    /**
     * The fastest a blow sets the spot it lands on moving, in blocks a second. Sized for the whole carcass, a blow landing
     * on a light part of it (a head, a leg) would set that part alone moving many times as fast as the carcass (a cow's head
     * at 22 blocks a second, a leg at 45) and yank the body after it by its joint: held to this, a blow to the head snaps
     * the head back and moves the rest a little. A blow to the torso, which carries most of the weight, is not held back.
     */
    public static final double BLOW_MAX_STRUCK = 7.0;
    /**
     * The share of what a light part struck could not take that goes on into the body, where that part's limb joins it:
     * enough to rock it, not so much that every blow to a face slides the animal as far as one to its body (at 0.4 a cow
     * struck in the face slid 1.7 blocks, as it did before a blow was held to what the part could take).
     */
    private static final double CARRIED_ON = 0.2;
    /** How far a killer's blow reaches along their look, in blocks. */
    private static final double BLOW_REACH = 8.0;

    private CarcassAssembler() {
    }

    /**
     * Builds the carcass where the mob stands, at rest. The killing blow is applied separately by
     * {@link #blow} once the client has had a moment to see the carcass (see {@link CarcassHandover}).
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
        // a baby is built from its kind's baby rig, if the kind has one; otherwise it dies as usual
        boolean baby = entity.isBaby();
        // a slime's model is scaled by its size and the rig drawn at 4: the smallest (1) is the rig's baby
        // shape, a quarter of it; the sizes between split and die as usual
        if (entity instanceof net.minecraft.world.entity.monster.Slime slime) {
            if (slime.getSize() == 1) {
                baby = true;
            } else if (slime.getSize() < 4) {
                return null;
            }
        }
        Optional<Rig> maybeRig = RigManager.forEntity(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()), baby);
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

        // some models hang below the mob's feet (a ghast's tentacles): lift the body so nothing starts in the ground
        double lowest = Double.MAX_VALUE;
        for (Bone bone : rig.bones()) {
            Quaterniond orientation = new Quaterniond(g).mul(new Quaterniond(bone.rotation()));
            Vector3d origin = g.transform(new Vector3d(bone.offset()).div(16.0)).add(base);
            for (int i = 0; i < 8; i++) {
                Vector3d corner = new Vector3d((i & 1) == 0 ? bone.boxMin().x : bone.boxMax().x, (i & 2) == 0 ? bone.boxMin().y : bone.boxMax().y,
                        (i & 4) == 0 ? bone.boxMin().z : bone.boxMax().z).div(16.0);
                lowest = Math.min(lowest, orientation.transform(corner).add(origin).y);
            }
        }
        if (lowest < feet.y) {
            // only out of the ground: a ghast shot down in open air keeps its tentacles hanging
            BlockPos.MutableBlockPos probe = BlockPos.containing(feet.x, feet.y - 0.01, feet.z).mutable();
            double ground = Double.NEGATIVE_INFINITY;
            for (int y = probe.getY(); y >= (int) Math.floor(lowest); y--) {
                probe.setY(y);
                net.minecraft.world.phys.shapes.VoxelShape shape = level.getBlockState(probe).getCollisionShape(level, probe);
                if (!shape.isEmpty()) {
                    ground = y + shape.max(net.minecraft.core.Direction.Axis.Y);
                    break;
                }
            }
            if (ground > lowest) {
                base.y += ground - lowest + 0.01;
            }
        }

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
        carcass.baby = baby;
        carcass.look = appearance;
        carcass.traits.putAll(CarcassLook.traits(entity));
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
            // where the blow landed: the first part the killer's look meets (or, glancing past, the nearest to it), and the
            // point on it, kept in that body's own plot so the handover's hold does not move it
            Vec3 eye = attacker.getEyePosition();
            Vec3 look = attacker.getLookAngle();
            CarcassAim.Hit hit = CarcassAim.first(level, carcass, rig, eye, look, BLOW_REACH);
            if (hit == null) {
                hit = CarcassAim.nearest(level, carcass, rig, eye, look);
            }
            if (hit != null) {
                carcass.hitBone = hit.bone();
                carcass.hitPoint = subLevels.get(hit.bone()).logicalPose().transformPositionInverse(hit.point(), new Vector3d());
            }
        }

        CarcassSavedData.get(level).add(carcass);
        BloodAndBones.LOGGER.debug("Assembled {} carcass {} with {} bones and {} joints", rig.entity(), carcassId, subLevels.size(), carcass.liveJoints.size());
        return carcass;
    }

    /**
     * The killing blow: one impulse at the point where it landed, on the part it landed on, along the killer's look
     * (lifted a little, as a swung hook would), sized so the carcass as a whole is set moving at {@link #BLOW_SPEED} for
     * a cow, a little faster for lighter animals (up to {@link #BLOW_MAX_SPEED}) and slower for heavier ones, by what its
     * bodies really weigh (a golem's plate is knocked slower than flesh its size), but never so hard that the spot it
     * lands on goes faster than {@link #BLOW_MAX_STRUCK}. Nothing else is pushed: the joints carry the blow to the rest
     * of the body, so a blow to the flank rolls it away over its feet, one from behind pitches it forward, and one to a
     * leg sweeps that leg. The mob's own hit knockback is deliberately not carried over; it would launch light carcasses.
     *
     * @param look the killer's look direction. With no point recorded by a kill (a carcass built without a killer, as the
     *             tests and the showcase build them), the blow lands where a stand-in killer would land it: one standing
     *             two blocks back along the look, a player's eye height off the ground, swinging at the torso's middle.
     */
    public static void blow(ServerLevel level, CarcassSavedData.Carcass carcass, Vec3 look) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        Rig rig = RigManager.forCarcass(carcass).orElse(null);
        if (container == null || rig == null) {
            return;
        }
        SubLevelPhysicsSystem physics = container.physicsSystem();
        ServerSubLevel hit = carcass.hitBone == null || carcass.hitPoint == null ? null
                : container.getSubLevel(carcass.bones.get(carcass.hitBone)) instanceof ServerSubLevel body && !body.isRemoved() ? body : null;
        Vector3d point = hit == null ? null : new Vector3d(carcass.hitPoint);
        if (hit == null) {
            Vector3d middle = boneWorldPosition(level, carcass, carcass.rootBone);
            if (middle == null) {
                return;
            }
            Vec3 flat = new Vec3(look.x, 0.0, look.z).lengthSqr() < 1.0e-6 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(look.x, 0.0, look.z).normalize();
            Vec3 eye = new Vec3(middle.x, lowest(container, carcass, rig) + STAND_IN_EYE, middle.z).subtract(flat.scale(2.0));
            Vec3 way = new Vec3(middle.x, middle.y, middle.z).subtract(eye).normalize();
            CarcassAim.Hit along = CarcassAim.first(level, carcass, rig, eye, way, BLOW_REACH);
            if (along == null) {
                along = CarcassAim.nearest(level, carcass, rig, eye, way);
            }
            if (along == null || !(container.getSubLevel(carcass.bones.get(along.bone())) instanceof ServerSubLevel body) || body.isRemoved()) {
                return;
            }
            hit = body;
            point = body.logicalPose().transformPositionInverse(along.point(), new Vector3d());
        }
        double mass = 0.0;
        for (UUID id : carcass.bones.values()) {
            if (container.getSubLevel(id) instanceof ServerSubLevel body && !body.isRemoved()) {
                mass += body.getMassTracker().getMass();
                physics.getPipeline().wakeUp(body);
            }
        }
        Vec3 dir = new Vec3(look.x, Math.max(look.y, 0.0) + 0.2, look.z).normalize();
        double speed = Math.max(0.5, Math.min(BLOW_MAX_SPEED, BLOW_SPEED / Math.sqrt(Math.max(mass, 0.01) / REFERENCE_WEIGHT)));
        Vector3d whole = new Vector3d(dir.x, dir.y, dir.z).mul(mass * speed);
        Vector3d given = impulseAt(physics, hit, point, whole, BLOW_MAX_STRUCK);
        // what a light part struck could not take goes on through its joints into the body, where they meet it: a blow
        // to an arm or a face still rocks the body, it does not glance off (held to what the part could take, a zombie or a
        // villager struck from the side on its arm was left standing, and a polar bear struck in the face moved four
        // hundredths of a block)
        UUID struck = hit.getUniqueId();
        CarcassJoints.Spec into = jointIntoTorso(carcass, carcass.bones.entrySet().stream()
                .filter(e -> e.getValue().equals(struck)).map(Map.Entry::getKey).findFirst().orElse(null));
        if (into != null && container.getSubLevel(carcass.bones.get(carcass.rootBone)) instanceof ServerSubLevel torso && !torso.isRemoved()) {
            Vector3d rest = new Vector3d(whole).sub(given).mul(CARRIED_ON);
            if (rest.dot(whole) > 0.0) {
                impulseAt(physics, torso, into.anchorParent(torso), rest, BLOW_MAX_STRUCK);
            }
        }
    }

    /**
     * The joint by which a part hangs from the torso, however many joints out it is (a lower leg by its leg's hip), or
     * null for the torso itself or a part with no way back to it.
     */
    @Nullable
    static CarcassJoints.Spec jointIntoTorso(CarcassSavedData.Carcass carcass, @Nullable String bone) {
        String at = bone;
        for (int hops = 0; at != null && !at.equals(carcass.rootBone) && hops < 64; hops++) {
            CarcassJoints.Spec up = null;
            for (CarcassJoints.Spec spec : carcass.joints) {
                if (spec.child().equals(at)) {
                    up = spec;
                    break;
                }
            }
            if (up == null) {
                return null;
            }
            if (up.parent().equals(carcass.rootBone)) {
                return up;
            }
            at = up.parent();
        }
        return null;
    }

    /**
     * An impulse at a point of a body, given as the change of velocity it makes: its mass takes the push and its inertia
     * about its centre of mass the turn. Sable's own impulse at a point goes through the mass the physics engine holds
     * for the body, which a body made this tick does not have until the engine next steps (the impulse then does
     * nothing: a carcass knocked as it is built, or a leg knocked as a lying carcass unfolds, would not move); Sable's
     * mass data, which that mass is set from, is there at once. It is made smaller if it would set the point it lands on
     * moving faster than {@code maxSpeed}: how fast that point goes is the push over the body's mass and the turn about
     * its centre of mass, so a light body, or one struck far out from its middle, takes less.
     *
     * @param point    where it lands, in the body's plot
     * @param impulse  the impulse, in the world
     * @param maxSpeed the fastest it may set the point moving, in blocks a second
     * @return the impulse given, in the world
     */
    public static Vector3d impulseAt(SubLevelPhysicsSystem physics, ServerSubLevel body, Vector3dc point, Vector3dc impulse, double maxSpeed) {
        dev.ryanhcode.sable.api.physics.mass.MassData mass = body.getMassTracker();
        if (mass.isInvalid()) {
            return new Vector3d();
        }
        org.joml.Quaterniondc turn = body.logicalPose().orientation();
        Vector3d local = turn.transformInverse(new Vector3d(impulse), new Vector3d());
        Vector3d arm = new Vector3d(point).sub(mass.getCenterOfMass());
        Vector3d spin = mass.getInverseInertiaTensor().transform(arm.cross(local, new Vector3d()), new Vector3d());
        // the point's change of velocity: the body's own, and the turn's about its centre of mass
        double pointSpeed = new Vector3d(local).mul(mass.getInverseMass()).add(new Vector3d(spin).cross(arm)).length();
        double scale = pointSpeed > maxSpeed ? maxSpeed / pointSpeed : 1.0;
        Vector3d given = new Vector3d(impulse).mul(scale);
        physics.getPhysicsHandle(body).addLinearAndAngularVelocity(new Vector3d(given).mul(mass.getInverseMass()), turn.transform(spin.mul(scale), new Vector3d()));
        return given;
    }

    /** A player's eye height, for a stand-in killer. */
    private static final double STAND_IN_EYE = 1.62;

    /** The lowest corner of any of a carcass's bodies, in the world: where it stands. */
    private static double lowest(ServerSubLevelContainer container, CarcassSavedData.Carcass carcass, Rig rig) {
        double lowest = Double.MAX_VALUE;
        for (Map.Entry<String, UUID> entry : carcass.bones.entrySet()) {
            Bone bone = rig.bone(entry.getKey()).orElse(null);
            if (bone == null || !(container.getSubLevel(entry.getValue()) instanceof ServerSubLevel body) || body.isRemoved()) {
                continue;
            }
            Vector3d[] box = CarcassAim.plotBox(body, bone);
            for (int i = 0; i < 8; i++) {
                Vector3d corner = new Vector3d((i & 1) == 0 ? box[0].x : box[1].x, (i & 2) == 0 ? box[0].y : box[1].y, (i & 4) == 0 ? box[0].z : box[1].z);
                lowest = Math.min(lowest, body.logicalPose().transformPosition(corner).y);
            }
        }
        return lowest;
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
        // flesh, bone or plate, as its group says: each is a block of its own, weighed by Sable's data
        Tissue tissue = Tissue.of(rig.entity());
        List<BlockPos> blocks = new ArrayList<>();
        for (int i = 0; i < cells[0]; i++) {
            for (int j = 0; j < cells[1]; j++) {
                for (int k = 0; k < cells[2]; k++) {
                    BlockPos pos = staging.offset(i, j, k);
                    BlockState state = cellState(tissue, Math.min(16, sx - 16 * i), Math.min(16, sy - 16 * j), Math.min(16, sz - 16 * k));
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
            if (level.getBlockState(pos).getBlock() instanceof CarcassPartBlock) {
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

    /**
     * A single bone as a carcass of its own, put down at a world point (a piece carried in the hand).
     *
     * @return the new record, or null if there was no room
     */
    @Nullable
    public static CarcassSavedData.Carcass assemblePiece(ServerLevel level, Rig rig, Bone bone, CarcassLook look, float freshness, Vec3 at, float yaw) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }
        BlockPos staging = findStaging(level, BlockPos.containing(at), rig);
        if (staging == null) {
            return null;
        }
        UUID carcassId = UUID.randomUUID();
        ServerSubLevel subLevel = assembleBone(level, staging, carcassId, rig, bone, look, true);
        if (subLevel == null) {
            return null;
        }
        PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        // lay it down the way it hung on the animal, facing the way the player faces, box bottom at the point
        Quaterniond g = new Quaterniond().rotationY(Math.toRadians(180.0 - yaw)).rotateZ(Math.PI);
        Quaterniond orientation = new Quaterniond(g).mul(new Quaterniond(bone.rotation()));
        Vector3d origin = new Vector3d(at.x, at.y, at.z);
        pose(pipeline, subLevel, bone, origin, orientation);
        // lift so the lowest corner of the box sits on the point
        Vector3d min = new Vector3d(bone.boxMin()).div(16.0);
        Vector3d max = new Vector3d(bone.boxMax()).div(16.0);
        double lowest = Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            Vector3d c = new Vector3d((i & 1) == 0 ? min.x : max.x, (i & 2) == 0 ? min.y : max.y, (i & 4) == 0 ? min.z : max.z);
            lowest = Math.min(lowest, orientation.transform(c).y);
        }
        origin.y += -lowest + 0.02;
        pose(pipeline, subLevel, bone, origin, orientation);
        if (level.getBlockEntity(subLevel.getPlot().getCenterBlock()) instanceof CarcassPartBlockEntity root) {
            root.setFreshness(freshness);
        }
        CarcassSavedData.Carcass carcass = new CarcassSavedData.Carcass(carcassId, rig.entity(), bone.name());
        carcass.look = look;
        carcass.freshness = freshness;
        carcass.bones.put(bone.name(), subLevel.getUniqueId());
        CarcassSavedData.get(level).add(carcass);
        return carcass;
    }

    /**
     * A carcass put back together from pieces of one animal (a whole carcass taken off a spit raw): the root piece set
     * down as {@link #assemblePiece} sets one down, and every other piece at its place on the living mob round it, joined
     * to its parent as it was, the whole of it lifted so no leg starts in the ground. It is built folded (each piece a
     * rest pose, the way CarcassRest remembers a still body) and unfolded at once, so it falls and settles as one body. A
     * piece whose way back to the root is missing is left out; the caller sets it down on its own.
     *
     * @return the new record, holding the pieces that went back on, or null if there was no room
     */
    @Nullable
    public static CarcassSavedData.Carcass assembleWhole(ServerLevel level, Rig rig, String root, java.util.Collection<String> pieces, boolean baby,
                                                         CarcassLook look, float freshness, Vec3 at, float yaw) {
        Bone torso = rig.bone(root).orElse(null);
        if (torso == null) {
            return null;
        }
        // the pieces that still hang together through their parents back to the root
        List<Bone> joined = new java.util.ArrayList<>();
        for (String name : pieces) {
            Bone bone = rig.bone(name).orElse(null);
            Bone cursor = bone;
            for (int hops = 0; cursor != null && !cursor.name().equals(root) && hops < 64; hops++) {
                cursor = cursor.parent().filter(pieces::contains).flatMap(rig::bone).orElse(null);
            }
            if (bone != null && cursor != null && !bone.name().equals(root)) {
                joined.add(bone);
            }
        }
        // how far below the root's own lowest corner the lowest of them hangs, with the root laid as a piece is
        Quaterniond g = new Quaterniond().rotationY(Math.toRadians(180.0 - yaw)).rotateZ(Math.PI);
        double rootLowest = lowestCorner(g, torso, new Vector3d());
        double lowest = rootLowest;
        for (Bone bone : joined) {
            Vector3d origin = g.transform(new Vector3d(bone.offset()).sub(new Vector3d(torso.offset())).div(16.0));
            lowest = Math.min(lowest, lowestCorner(g, bone, origin));
        }
        CarcassSavedData.Carcass carcass = assemblePiece(level, rig, torso, look, freshness, at.add(0.0, rootLowest - lowest, 0.0), yaw);
        if (carcass == null || joined.isEmpty()) {
            return carcass;
        }
        carcass.baby = baby;
        Quaterniond rootInverse = new Quaterniond(torso.rotation()).invert();
        for (Bone bone : joined) {
            // the piece's frame relative to the root's, on the living mob: what jointSpec reads its joint from
            Vector3d position = rootInverse.transform(new Vector3d(bone.offset()).sub(new Vector3d(torso.offset())).div(16.0));
            Quaterniond orientation = new Quaterniond(rootInverse).mul(new Quaterniond(bone.rotation()));
            carcass.restPoses.put(bone.name(), new CarcassSavedData.RestPose(position, orientation));
            bone.parent().flatMap(rig::bone).ifPresent(parent -> carcass.joints.add(jointSpec(parent, bone)));
        }
        carcass.resting = true;
        if (CarcassRest.split(level, carcass) == null) {
            // no room to unfold it: the root alone, and the rest set down by the caller
            carcass.restPoses.clear();
            carcass.joints.clear();
            carcass.resting = false;
        }
        return carcass;
    }

    /** The lowest a bone's box reaches, laid with {@code g} and its origin at {@code origin}. */
    private static double lowestCorner(Quaterniond g, Bone bone, Vector3d origin) {
        Quaterniond orientation = new Quaterniond(g).mul(new Quaterniond(bone.rotation()));
        double lowest = Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            Vector3d corner = new Vector3d((i & 1) == 0 ? bone.boxMin().x : bone.boxMax().x, (i & 2) == 0 ? bone.boxMin().y : bone.boxMax().y,
                    (i & 4) == 0 ? bone.boxMin().z : bone.boxMax().z).div(16.0);
            lowest = Math.min(lowest, orientation.transform(corner).add(origin).y);
        }
        return lowest;
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
        Rig rig = RigManager.forCarcass(carcass).orElse(null);
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

    /**
     * The sizes, in pixels, of the cells a bone is built of: whole blocks, and what is left over along each side.
     * PhysicsPropertiesProvider weighs every size the rigs make this way for each tissue.
     */
    public static java.util.Set<List<Integer>> cellSizes(Bone bone) {
        int[] cells = cellCounts(bone);
        int[] size = {pixels(bone.boxSize().x), pixels(bone.boxSize().y), pixels(bone.boxSize().z)};
        java.util.Set<List<Integer>> out = new java.util.LinkedHashSet<>();
        for (int i = 0; i < cells[0]; i++) {
            for (int j = 0; j < cells[1]; j++) {
                for (int k = 0; k < cells[2]; k++) {
                    out.add(List.of(Math.min(16, size[0] - 16 * i), Math.min(16, size[1] - 16 * j), Math.min(16, size[2] - 16 * k)));
                }
            }
        }
        return out;
    }

    /**
     * A carcass cell of a tissue and a size. Sable's data weighs every size of flesh, but bone and plate only the sizes
     * the rigs make (PhysicsPropertiesProvider: each size weighed costs every world and every joining client time to
     * load); a size left out (a datapack's own rig, of a skeleton or a golem) would weigh as a whole block of it, so that
     * cell is made of flesh instead, weighed by its size.
     */
    public static BlockState cellState(Tissue tissue, int sizeX, int sizeY, int sizeZ) {
        BlockState state = CarcassPartBlock.stateFor(BBBlocks.carcassPart(tissue), sizeX, sizeY, sizeZ);
        return CarcassPartBlock.weighed(state) ? state : CarcassPartBlock.stateFor(BBBlocks.carcassPart(Tissue.FLESH), sizeX, sizeY, sizeZ);
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
