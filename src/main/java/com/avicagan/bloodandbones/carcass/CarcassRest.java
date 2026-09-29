package com.avicagan.bloodandbones.carcass;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.rig.Bone;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.avicagan.bloodandbones.carcass.rig.RigManager;
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
            carcass.settledAt.clear();
            return;
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }
        SubLevelPhysicsSystem physics = container.physicsSystem();
        Vector3d linear = new Vector3d();
        Vector3d angular = new Vector3d();
        boolean still = true;
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
                still = false;
            }
        }
        // thin, light limbs (a spider's legs) can twitch against the ground for ever without going anywhere: a carcass
        // that has stayed where it lies for a while rests all the same
        boolean stayedPut = stayedPut(container, carcass);
        if (!still && !stayedPut) {
            carcass.stillTicks = 0;
            return;
        }
        if (carcass.unfoldedUnsupported != null) {
            UUID torsoId = carcass.bones.get(carcass.rootBone);
            if (torsoId != null && container.getSubLevel(torsoId) instanceof ServerSubLevel torso
                    && torso.logicalPose().position().distance(carcass.unfoldedUnsupported) < UNSUPPORTED_MOVE) {
                // lying just as it lay when the ground check failed: folding would only unfold it again
                carcass.stillTicks = 0;
                return;
            }
            carcass.unfoldedUnsupported = null;
        }
        carcass.stillTicks = still ? carcass.stillTicks + 1 : STILL_TICKS;
        if (carcass.stillTicks >= STILL_TICKS) {
            // this runs inside Sable's loop over every sub-level; removing bodies here would mutate that
            // list mid-walk, so the fold itself waits for the end of the level tick
            PENDING.computeIfAbsent(level, l -> new java.util.LinkedHashSet<>()).add(carcass.id);
        }
    }

    /** How far a body may twitch from where it lay and still count as lying there; the torso must keep closer. */
    private static final double TWITCH_REACH = 0.25;
    private static final double TWITCH_TORSO = 0.1;
    /** Ticks a carcass must have stayed where it lies, however it twitches, before it rests anyway. */
    public static final int TWITCH_TICKS = 100;

    /**
     * Whether every body of the carcass has stayed near where it was {@link #TWITCH_TICKS} ago. The count starts
     * again from where the bodies are now whenever one goes further.
     */
    private static boolean stayedPut(ServerSubLevelContainer container, CarcassSavedData.Carcass carcass) {
        boolean near = carcass.settledAt.keySet().equals(carcass.bones.keySet());
        for (Map.Entry<String, UUID> entry : carcass.bones.entrySet()) {
            Vector3d from = carcass.settledAt.get(entry.getKey());
            double reach = entry.getKey().equals(carcass.rootBone) ? TWITCH_TORSO : TWITCH_REACH;
            if (!near || from == null || !(container.getSubLevel(entry.getValue()) instanceof ServerSubLevel body)
                    || body.logicalPose().position().distance(from) > reach) {
                near = false;
                break;
            }
        }
        if (!near) {
            carcass.settledAt.clear();
            carcass.settledTicks = 0;
            for (Map.Entry<String, UUID> entry : carcass.bones.entrySet()) {
                if (container.getSubLevel(entry.getValue()) instanceof ServerSubLevel body) {
                    carcass.settledAt.put(entry.getKey(), new Vector3d(body.logicalPose().position()));
                }
            }
            return false;
        }
        return ++carcass.settledTicks >= TWITCH_TICKS;
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
    public static boolean isHeld(ServerLevel level, CarcassSavedData.Carcass carcass) {
        if (CarcassDrag.isDraggingCarcass(carcass.id)) {
            return true;
        }
        return ShackleHookBlockEntity.isHanging(level, carcass.id)
                || com.avicagan.bloodandbones.carcass.trolley.ShackleTrolleyEntity.isHanging(level, carcass.id);
    }

    /**
     * Fold the ragdoll into the torso.
     */
    public static boolean rest(ServerLevel level, CarcassSavedData.Carcass carcass) {
        if (carcass.resting) {
            return true;
        }
        Optional<Rig> maybeRig = RigManager.forCarcass(carcass);
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (maybeRig.isEmpty() || container == null) {
            return false;
        }
        Rig rig = maybeRig.get();
        Bone torsoBone = rig.bone(carcass.rootBone).orElse(rig.root());
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
        carcass.settledAt.clear();
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
                        && !be.bone().equals(torsoBone.name()) && level.getBlockState(entry.getKey()).getBlock() instanceof CarcassPartBlock) {
                    stray.add(entry.getKey().immutable());
                }
            }
        }
        return stray;
    }

    /** How far an unfolded-for-support body must move before it may fold again, in blocks. */
    private static final double UNSUPPORTED_MOVE = 0.5;

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
            carcass.unfoldedUnsupported = new Vector3d(torso.logicalPose().position());
            PENDING_SPLIT.computeIfAbsent(level, l -> new java.util.LinkedHashSet<>()).add(carcass.id);
        }
    }

    /** Resting carcasses that lost their footing this tick, unfolded from the level tick. */
    private static final Map<ServerLevel, java.util.Set<UUID>> PENDING_SPLIT = new java.util.WeakHashMap<>();

    /**
     * Something solid within a fifth of a block under any corner of the body or of its folded limbs: a block of the world,
     * or of a ship's deck or anything else of Sable's (read in its own plot), another carcass included. (A carcass propped
     * up on folded legs rests on them, so those count; one lying over a Bleeding Rack rests on its torso with its legs
     * hanging clear of the floor, which is why it is any corner and not only the lowest: judged by its dangling legs alone
     * it was found unsupported the moment it folded, and so never rested or bled into the rack.)
     */
    static boolean isSupported(ServerLevel level, CarcassSavedData.Carcass carcass, ServerSubLevel torso) {
        if (RigManager.forCarcass(carcass).isEmpty()) {
            return true;
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        for (Vector3d corner : corners(carcass, torso)) {
            BlockPos below = BlockPos.containing(corner.x, corner.y - SUPPORT_REACH, corner.z);
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()
                    || container != null && underIn(level, container, corner, torso, true) != null) {
                return true;
            }
        }
        return false;
    }

    /** How far under a corner something must be to hold it up, in blocks. */
    private static final double SUPPORT_REACH = 0.2;

    /**
     * The sub-level with something solid a fifth of a block under {@code corner}, other than {@code self}: a ship's deck,
     * or (when {@code carcasses}) another carcass's body too. Null if none.
     */
    @Nullable
    private static ServerSubLevel underIn(ServerLevel level, ServerSubLevelContainer container, Vector3d corner, ServerSubLevel self, boolean carcasses) {
        Vector3d point = new Vector3d(corner.x, corner.y - SUPPORT_REACH, corner.z);
        dev.ryanhcode.sable.companion.math.BoundingBox3d reach =
                new dev.ryanhcode.sable.companion.math.BoundingBox3d(point.x - 0.05, point.y - 0.05, point.z - 0.05, point.x + 0.05, point.y + 0.05, point.z + 0.05);
        Vector3d local = new Vector3d();
        for (SubLevel other : container.queryIntersecting(reach)) {
            if (other == self || other.isRemoved() || !(other instanceof ServerSubLevel deck)) {
                continue;
            }
            deck.logicalPose().transformPositionInverse(point, local);
            BlockPos below = BlockPos.containing(local.x, local.y, local.z);
            BlockState state = level.getBlockState(below);
            if ((carcasses || !(state.getBlock() instanceof CarcassPartBlock)) && !state.getCollisionShape(level, below).isEmpty()) {
                return deck;
            }
        }
        return null;
    }

    /**
     * The deck a resting carcass lies on: a sub-level that is not a carcass, under any corner of it; null if it lies on
     * the world (or only on other carcasses, which may unfold and go).
     */
    @Nullable
    static ServerSubLevel deckUnder(ServerLevel level, CarcassSavedData.Carcass carcass, ServerSubLevel torso) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }
        for (Vector3d corner : corners(carcass, torso)) {
            ServerSubLevel deck = underIn(level, container, corner, torso, false);
            if (deck != null) {
                return deck;
            }
        }
        return null;
    }

    /** Every corner of the torso's box and of each folded limb's, in the world. */
    private static List<Vector3d> corners(CarcassSavedData.Carcass carcass, ServerSubLevel torso) {
        Optional<Rig> maybeRig = RigManager.forCarcass(carcass);
        if (maybeRig.isEmpty()) {
            return List.of();
        }
        Bone torsoBone = maybeRig.get().bone(carcass.rootBone).orElse(maybeRig.get().root());
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
        return corners;
    }

    private static void addCorners(List<Vector3d> out, Pose3d pose, Vector3d min, Vector3d max) {
        for (int i = 0; i < 8; i++) {
            Vector3d c = new Vector3d((i & 1) == 0 ? min.x : max.x, (i & 2) == 0 ? min.y : max.y, (i & 4) == 0 ? min.z : max.z);
            out.add(pose.transformPosition(c, new Vector3d()));
        }
    }

    /** A player punched the drawn limbs of a resting carcass (reported by their client). */
    public static void punch(ServerLevel level, net.minecraft.world.entity.player.Player player, UUID carcassId) {
        CarcassSavedData.Carcass carcass = CarcassSavedData.get(level).carcass(carcassId);
        if (carcass == null || !carcass.resting) {
            return;
        }
        Vector3d torso = CarcassAssembler.boneWorldPosition(level, carcass, carcass.rootBone);
        if (torso == null || torso.distance(player.getX(), player.getY(), player.getZ()) > 6.0) {
            return;
        }
        disturb(level, carcass, player, carcass.rootBone);
    }

    /**
     * How hard a full-strength punch pushes (an impulse, in Sable's mass units times blocks a second): what sets a cow
     * moving at 1.5 blocks a second as a whole. The same push whatever it lands on, so weight tells: a ravager is barely
     * moved (a fifth of a block a second), and a chicken goes as fast as {@link #PUNCH_MAX_STRUCK} lets the spot punched go.
     */
    public static final double PUNCH = 1.2;
    /** The fastest a punch sets the spot it lands on moving, in blocks a second: a punched leg swings, it is not flung. */
    public static final double PUNCH_MAX_STRUCK = 3.0;

    /**
     * A resting carcass was hit: unfold it and knock it where the blow landed, judged on its parts as they were drawn
     * (a punch to a drawn leg lands on that leg).
     *
     * @param struck the part to knock if the blow's line meets none (the cell that was hit)
     * @return true if it unfolded
     */
    public static boolean disturb(ServerLevel level, CarcassSavedData.Carcass carcass, net.minecraft.world.entity.LivingEntity by, String struck) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        CarcassAim.Hit hit = container != null && container.getSubLevel(carcass.bones.get(carcass.rootBone)) instanceof ServerSubLevel torso && !torso.isRemoved()
                ? CarcassDrag.aimAtResting(level, carcass, torso, by) : null;
        Map<String, ServerSubLevel> bodies = split(level, carcass);
        if (bodies == null) {
            return false;
        }
        knock(level, carcass, hit != null ? hit.bone() : struck, hit != null ? hit.point() : null, by.getLookAngle(), strength(by));
        return true;
    }

    /** How hard someone hits: a player's swing is weaker until it has recharged, as it is on a mob. */
    public static double strength(net.minecraft.world.entity.LivingEntity by) {
        return by instanceof net.minecraft.world.entity.player.Player player ? 0.25 + 0.75 * player.getAttackStrengthScale(0.5F) : 1.0;
    }

    /**
     * A blow to a carcass that is awake (lying, dragged or hung): one impulse at the point it landed, on the part it landed
     * on, along the blow (lifted a little), of {@link #PUNCH} times its strength whatever it lands on, but never so hard that
     * the spot it lands on goes faster than {@link #PUNCH_MAX_STRUCK}. The joints carry it to the rest, so a hung carcass is
     * knocked away on its hook and its legs trail after.
     *
     * @param point where it landed, in the world; the middle of the part if not known
     * @return the impulse given, in the world (none if there was nothing to knock)
     */
    public static Vector3d knock(ServerLevel level, CarcassSavedData.Carcass carcass, String bone, @Nullable Vector3d point, net.minecraft.world.phys.Vec3 look,
                                 double strength) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return new Vector3d();
        }
        UUID id = carcass.bones.getOrDefault(bone, carcass.bones.get(carcass.rootBone));
        if (!(container.getSubLevel(id) instanceof ServerSubLevel hit) || hit.isRemoved()) {
            return new Vector3d();
        }
        SubLevelPhysicsSystem physics = container.physicsSystem();
        for (UUID each : carcass.bones.values()) {
            if (container.getSubLevel(each) instanceof ServerSubLevel body && !body.isRemoved()) {
                physics.getPipeline().wakeUp(body);
            }
        }
        Vector3d at = point == null ? new Vector3d(hit.getMassTracker().getCenterOfMass() == null ? hit.logicalPose().rotationPoint()
                : hit.getMassTracker().getCenterOfMass()) : hit.logicalPose().transformPositionInverse(point, new Vector3d());
        // a leg just unfolded is a new body, so the change of velocity is worked out here (CarcassAssembler#impulseAt)
        Vector3d given = CarcassAssembler.impulseAt(physics, hit, at, new Vector3d(look.x, Math.max(look.y, 0.0) + 0.2, look.z).normalize().mul(PUNCH * strength),
                PUNCH_MAX_STRUCK);
        // an awake carcass counts its stillness afresh
        carcass.stillTicks = 0;
        carcass.settledAt.clear();
        return given;
    }

    /**
     * Pin the merged body where it is with a fully locked joint: with its limbs gone it would otherwise settle
     * differently, and the remembered limb poses only hold if the torso does not move. It is pinned to what it lies on:
     * the world, or a ship's deck, so that it goes where the deck goes (pinned to the world, it hung still in the air
     * while the deck moved on under it).
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
        ServerSubLevel deck = deckUnder(level, carcass, torso);
        carcass.restDeck = deck == null ? null : deck.getUniqueId();
        // the same point and turn, in the deck's own plot and frame when it lies on one
        Vector3d deckPoint = deck == null ? worldPoint : deck.logicalPose().transformPositionInverse(worldPoint, new Vector3d());
        Quaterniond deckTurn = deck == null ? new Quaterniond(pose.orientation())
                : new Quaterniond(deck.logicalPose().orientation()).invert().mul(pose.orientation());
        GenericConstraintConfiguration config = new GenericConstraintConfiguration(
                deckPoint, plotPoint, deckTurn, new Quaterniond(), EnumSet.allOf(ConstraintJointAxis.class));
        try {
            carcass.restLock = container.physicsSystem().getPipeline().addConstraint(deck, torso, config);
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
        Optional<Rig> maybeRig = RigManager.forCarcass(carcass);
        if (container == null || maybeRig.isEmpty()) {
            return null;
        }
        Rig rig = maybeRig.get();
        Bone torsoBone = rig.bone(carcass.rootBone).orElse(rig.root());
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
            if (level.getBlockState(cell).getBlock() instanceof CarcassPartBlock) {
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
        carcass.settledAt.clear();
        // the limbs' cells are new: they need the look, freshness and cut ends now, not at the next refresh
        CarcassRot.sync(level, carcass, null);

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
