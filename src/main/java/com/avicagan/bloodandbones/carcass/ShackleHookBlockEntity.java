package com.avicagan.bloodandbones.carcass;

import com.avicagan.bloodandbones.BloodAndBones;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Holds one carcass limb on the hook with a ball joint to the world. The joint lives only in memory and
 * is rebuilt every tick it is missing, so hanging survives reloads and chunk unloads.
 */
public class ShackleHookBlockEntity extends BlockEntity {
    /** Occupied, loaded hooks per level, driven every physics substep. */
    private static final java.util.Map<ServerLevel, java.util.Set<ShackleHookBlockEntity>> ACTIVE = new java.util.WeakHashMap<>();
    /** Torque spring gains per unit of torso mass: turns the hanging body belly-out and damps its swing. */
    private static final double TURN_STIFFNESS = 30.0;
    private static final double TURN_DAMPING = 7.0;

    /** Called every physics substep: applies the orientation torque to every hanging carcass in the level. */
    public static void physicsTick(ServerLevel level, double timeStep) {
        java.util.Set<ShackleHookBlockEntity> hooks = ACTIVE.get(level);
        if (hooks == null || hooks.isEmpty()) {
            return;
        }
        dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem physics = dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(level);
        if (physics == null) {
            return;
        }
        for (ShackleHookBlockEntity hook : hooks.toArray(new ShackleHookBlockEntity[0])) {
            if (hook.isRemoved() || !hook.isOccupied()) {
                hooks.remove(hook);
                continue;
            }
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            SubLevel subLevel = container == null ? null : container.getSubLevel(hook.subLevelId);
            if (!(subLevel instanceof ServerSubLevel body) || body.isRemoved()) {
                continue;
            }
            if (hook.hoisting >= 0) {
                hook.hoist(level, body, physics, timeStep);
            }
            hook.turn(body, physics, timeStep);
        }
    }

    /** Blocks a second the hook hoists a body up to its tip before holding it fast. */
    public static final double HOIST_SPEED = 3.0;
    /** How near the tip the hooked point must come before the hook holds it fast. */
    private static final double HOIST_REACH = 0.3;
    /** Ticks a hoist may take: a body caught under something is held fast from wherever it got to. */
    private static final int HOIST_TICKS = 80;
    /** How hard the hoist corrects the hooked point's speed, per second, and the most it pulls, as accelerations. */
    private static final double HOIST_GAIN = 12.0;
    private static final double HOIST_MAX = 40.0;

    /**
     * Draw the hooked point toward the tip at no more than {@link #HOIST_SPEED}, carrying the whole carcass's weight: a
     * push at the hooked point, as a local impulse over this substep. (A ball joint made straight away snapped a body
     * lying a few blocks off up to the tip in a tick, and threw anyone standing by it tens of blocks.)
     */
    private void hoist(ServerLevel level, ServerSubLevel body, dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem physics, double timeStep) {
        Vec3 tip = ShackleHookBlock.tip(worldPosition, getBlockState());
        dev.ryanhcode.sable.companion.math.Pose3d pose = body.logicalPose();
        Vector3d anchor = pose.transformPosition(new Vector3d(anchorPlot), new Vector3d());
        Vector3d toTip = new Vector3d(tip.x, tip.y, tip.z).sub(anchor);
        double gap = toTip.length();
        Vector3d wanted = gap < 1.0e-6 ? new Vector3d() : toTip.mul(Math.min(HOIST_SPEED, gap * 6.0) / gap);
        dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle handle = physics.getPhysicsHandle(body);
        Vector3d linear = handle.getLinearVelocity(new Vector3d());
        Vector3d angular = handle.getAngularVelocity(new Vector3d());
        Vector3d pointVelocity = new Vector3d(angular).cross(new Vector3d(anchor).sub(pose.position())).add(linear);
        double mass = hoistedMass(level, body);
        Vector3d force = wanted.sub(pointVelocity).mul(HOIST_GAIN * mass)
                .sub(dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData.getGravity(level).mul(mass));
        if (force.length() > HOIST_MAX * mass) {
            force.normalize(HOIST_MAX * mass);
        }
        Vector3d impulse = force.mul(timeStep);
        pose.orientation().transformInverse(impulse);
        body.getOrCreateQueuedForceGroup(dev.ryanhcode.sable.api.physics.force.ForceGroups.PROPULSION.get()).applyAndRecordPointForce(anchorPlot, impulse);
        physics.getPipeline().wakeUp(body);
    }

    /** The weight the hook lifts: every body of the carcass, which hang from the torso it holds. */
    private double hoistedMass(ServerLevel level, ServerSubLevel torso) {
        CarcassSavedData.Carcass carcass = carcassId == null ? null : CarcassSavedData.get(level).carcass(carcassId);
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        double total = 0.0;
        if (carcass != null && container != null) {
            for (UUID id : carcass.bones.values()) {
                if (container.getSubLevel(id) instanceof ServerSubLevel body && !body.isRemoved()) {
                    total += body.getMassTracker().getMass();
                }
            }
        }
        return Math.max(total, Math.max(0.05, torso.getMassTracker().getMass()));
    }

    /** Whether the hooked point has come up near enough to the tip to be held fast. */
    private boolean hoisted(ServerLevel level) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null || subLevelId == null || !(container.getSubLevel(subLevelId) instanceof ServerSubLevel body) || body.isRemoved()) {
            return true;
        }
        Vec3 tip = ShackleHookBlock.tip(worldPosition, getBlockState());
        return body.logicalPose().transformPosition(new Vector3d(anchorPlot), new Vector3d()).distance(tip.x, tip.y, tip.z) <= HOIST_REACH;
    }

    /** Spring torque toward the hanging orientation, as a local angular impulse over this substep. */
    private void turn(ServerSubLevel body, dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem physics, double timeStep) {
        Quaterniond current = new Quaterniond(body.logicalPose().orientation());
        Quaterniond wanted = hangingOrientation(outX, outZ);
        // rotation that takes the current orientation to the wanted one, in world space
        Quaterniond error = new Quaterniond(wanted).mul(new Quaterniond(current).invert()).normalize();
        if (error.w < 0) {
            error.set(-error.x, -error.y, -error.z, -error.w);
        }
        double angle = 2.0 * Math.acos(Math.min(1.0, error.w));
        Vector3d axis = new Vector3d(error.x, error.y, error.z);
        if (axis.lengthSquared() > 1.0e-10) {
            axis.normalize();
        } else {
            axis.set(0.0, 1.0, 0.0);
            angle = 0.0;
        }
        dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle handle = physics.getPhysicsHandle(body);
        Vector3d angular = handle.getAngularVelocity(new Vector3d());
        double mass = Math.max(0.05, body.getMassTracker().getMass());
        Vector3d torque = new Vector3d(axis).mul(angle * TURN_STIFFNESS * mass).sub(new Vector3d(angular).mul(TURN_DAMPING * mass));
        Vector3d impulse = torque.mul(timeStep);
        current.invert().transform(impulse); // local frame
        handle.applyLinearAndAngularImpulse(new Vector3d(), impulse);
    }

    /** True if any loaded hook in the level holds this carcass. */
    public static boolean isHanging(ServerLevel level, UUID carcassId) {
        java.util.Set<ShackleHookBlockEntity> hooks = ACTIVE.get(level);
        if (hooks == null) {
            return false;
        }
        for (ShackleHookBlockEntity hook : hooks) {
            if (!hook.isRemoved() && carcassId.equals(hook.carcassId)) {
                return true;
            }
        }
        return false;
    }

    private void activate(ServerLevel level) {
        ACTIVE.computeIfAbsent(level, l -> java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>())).add(this);
    }

    private void deactivate() {
        if (level instanceof ServerLevel serverLevel) {
            java.util.Set<ShackleHookBlockEntity> hooks = ACTIVE.get(serverLevel);
            if (hooks != null) {
                hooks.remove(this);
            }
        }
    }

    @Nullable
    private UUID carcassId;
    @Nullable
    private UUID subLevelId;
    private final Vector3d anchorPlot = new Vector3d();
    private String bone = "";
    /** Horizontal direction the belly should face while hanging. */
    private double outX = 0.0;
    private double outZ = 1.0;
    @Nullable
    private GenericConstraintHandle joint;
    /** Ticks since the hook began hoisting its body up to the tip, or -1 once it holds it fast (or holds nothing). Not saved. */
    private int hoisting = -1;

    /**
     * The torso-side anchor of the head joint: where the neck meets the body. When the head hangs off the torso through
     * another piece (a wolf's upper body, a ravager's neck), it is where that piece meets the torso, on the way to the
     * head; with the head cut off, where a neck or upper body meets it. Otherwise any joint off the torso, and for a
     * torso with none, its own center.
     */
    public static Vector3d neckJunction(CarcassSavedData.Carcass carcass, ServerSubLevel torso) {
        CarcassJoints.Spec towardHead = jointTowardHead(carcass);
        if (towardHead != null) {
            return towardHead.anchorParent(torso);
        }
        for (CarcassJoints.Spec joint : carcass.joints) {
            String child = joint.child().toLowerCase(java.util.Locale.ROOT);
            if (joint.parent().equals(carcass.rootBone) && (child.contains("neck") || child.contains("upper"))) {
                return joint.anchorParent(torso);
            }
        }
        for (CarcassJoints.Spec joint : carcass.joints) {
            if (joint.parent().equals(carcass.rootBone)) {
                return joint.anchorParent(torso);
            }
        }
        BlockPos c = torso.getPlot().getCenterBlock();
        return new Vector3d(c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5);
    }

    /** The joint off the torso on the way to a head still attached, following the joints up from the head; or null. */
    @Nullable
    public static CarcassJoints.Spec jointTowardHead(CarcassSavedData.Carcass carcass) {
        java.util.Map<String, CarcassJoints.Spec> byChild = new java.util.HashMap<>();
        for (CarcassJoints.Spec joint : carcass.joints) {
            byChild.put(joint.child(), joint);
        }
        for (CarcassJoints.Spec joint : carcass.joints) {
            if (!joint.child().toLowerCase(java.util.Locale.ROOT).contains("head")) {
                continue;
            }
            CarcassJoints.Spec step = joint;
            // up from the head, no further than there are joints (a broken record never loops)
            for (int i = 0; i <= carcass.joints.size() && step != null; i++) {
                if (step.parent().equals(carcass.rootBone)) {
                    return step;
                }
                step = byChild.get(step.parent());
            }
        }
        return null;
    }

    /**
     * World orientation for a hanging torso: the model's head end (part-local -y) points up and its belly
     * (part-local -z) faces {@code out}. Columns of the rotation are the world images of the local axes.
     */
    public static Quaterniond hangingOrientation(double outX, double outZ) {
        Vector3d belly = new Vector3d(outX, 0.0, outZ).normalize();
        Vector3d imageY = new Vector3d(0.0, -1.0, 0.0);      // local +y (rear) points down
        Vector3d imageZ = new Vector3d(belly).negate();      // local +z (back) faces away from out
        Vector3d imageX = new Vector3d(imageY).cross(imageZ); // right-handed
        org.joml.Matrix3d m = new org.joml.Matrix3d(imageX, imageY, imageZ);
        return new Quaterniond().setFromNormalized(m);
    }

    public ShackleHookBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public boolean isOccupied() {
        return subLevelId != null;
    }

    @Nullable
    public UUID hookedSubLevel() {
        return subLevelId;
    }

    public Vector3d hookedAnchor() {
        return anchorPlot;
    }

    public String hookedBone() {
        return bone;
    }

    /** Meat Hook click: hang the limb the player is dragging, or let the hanging carcass down. */
    public void toggle(ServerLevel level, Player player) {
        if (isOccupied()) {
            release(level);
            return;
        }
        hang(level, player);
    }

    /**
     * Hang the carcass this one is dragging (a player, or a hauler minion) on the free hook, by its torso.
     *
     * @return whether it went up
     */
    public boolean hang(ServerLevel level, net.minecraft.world.entity.LivingEntity player) {
        CarcassDrag.Drag drag = CarcassDrag.current(player);
        if (drag == null || isOccupied()) {
            return false;
        }
        CarcassSavedData.Carcass carcass = CarcassSavedData.get(level).carcass(drag.carcass);
        if (carcass == null) {
            return false;
        }
        CarcassDrag.stop(level, player);
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        SubLevel torsoSubLevel = container == null ? null : container.getSubLevel(carcass.bones.get(carcass.rootBone));
        if (!(torsoSubLevel instanceof ServerSubLevel torso) || torso.isRemoved()) {
            return false;
        }
        // Always hang by the torso, hooked where the neck meets it, belly facing out from the mount
        // (or toward whoever hung it on a ceiling hook).
        carcassId = carcass.id;
        subLevelId = carcass.bones.get(carcass.rootBone);
        bone = carcass.rootBone;
        anchorPlot.set(neckJunction(carcass, torso));
        net.minecraft.core.Direction mount = getBlockState().getValue(ShackleHookBlock.FACING);
        Vec3 out;
        if (mount.getAxis().isHorizontal()) {
            out = Vec3.atLowerCornerOf(mount.getOpposite().getNormal());
        } else {
            Vec3 toPlayer = new Vec3(player.getX() - worldPosition.getX() - 0.5, 0.0, player.getZ() - worldPosition.getZ() - 0.5);
            out = toPlayer.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : toPlayer.normalize();
        }
        outX = out.x;
        outZ = out.z;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        // a hook in the world hoists the body up before it holds it fast; one on a ship holds it at once
        Vec3 tip = ShackleHookBlock.tip(worldPosition, getBlockState());
        if (dev.ryanhcode.sable.Sable.HELPER.getContaining(level, worldPosition) == null
                && torso.logicalPose().transformPosition(new Vector3d(anchorPlot), new Vector3d()).distance(tip.x, tip.y, tip.z) > HOIST_REACH) {
            hoisting = 0;
            activate(level);
            container.physicsSystem().getPipeline().wakeUp(torso);
        } else {
            attach(level, false);
        }
        return true;
    }

    public void release(ServerLevel level) {
        hoisting = -1;
        deactivate();
        if (joint != null && joint.isValid()) {
            joint.remove();
        }
        joint = null;
        carcassId = null;
        subLevelId = null;
        bone = "";
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    /** Server tick: keep the joint alive while the limb is loaded. */
    public static void tick(Level level, BlockPos pos, BlockState state, ShackleHookBlockEntity hook) {
        if (!(level instanceof ServerLevel serverLevel) || !hook.isOccupied()) {
            return;
        }
        if (hook.hoisting >= 0) {
            if (hook.hoisted(serverLevel) || ++hook.hoisting > HOIST_TICKS) {
                hook.hoisting = -1;
                hook.attach(serverLevel, false);
            }
            return;
        }
        if (hook.joint != null && hook.joint.isValid()) {
            return;
        }
        hook.joint = null;
        if (hook.loadedAt != null && !hook.loadedAt.equals(pos)) {
            // saved somewhere else: a Create contraption carried the hook's data but not the body, which
            // fell where the hook was. Let it go rather than yank it across to the hook's new place.
            hook.loadedAt = null;
            hook.release(serverLevel);
            return;
        }
        hook.loadedAt = null;
        hook.attach(serverLevel, true);
    }

    /** Farthest a hung body may have swung while its hook was unloaded and still be taken back, in blocks. */
    public static final double REJOIN_REACH = 8.0;

    /** Where the hook was when it was saved, as read back; null once checked. */
    @Nullable
    private BlockPos loadedAt;

    /** @param rejoin taking back a body it already held, rather than hooking a new one */
    private void attach(ServerLevel level, boolean rejoin) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null || subLevelId == null) {
            return;
        }
        SubLevel subLevel = container.getSubLevel(subLevelId);
        if (!(subLevel instanceof ServerSubLevel serverSubLevel) || serverSubLevel.isRemoved()) {
            return;
        }
        Vec3 tip = ShackleHookBlock.tip(worldPosition, getBlockState());
        // a body that swung far off while the hook was unloaded is let go, not pulled back through walls
        // (only for a hook in the world: on a ship the tip is in the ship's own coordinates)
        if (rejoin && dev.ryanhcode.sable.Sable.HELPER.getContaining(level, worldPosition) == null) {
            Vector3d anchorWorld = serverSubLevel.logicalPose().transformPosition(new Vector3d(anchorPlot), new Vector3d());
            if (anchorWorld.distance(tip.x, tip.y, tip.z) > REJOIN_REACH) {
                release(level);
                return;
            }
        }
        // A ball joint pinning the neck junction to the hook tip; the belly-out turn is a torque spring
        // applied every physics substep (see physicsTick), not a joint motor.
        GenericConstraintConfiguration config = new GenericConstraintConfiguration(
                new Vector3d(tip.x, tip.y, tip.z), new Vector3d(anchorPlot), new Quaterniond(), new Quaterniond(),
                EnumSet.of(ConstraintJointAxis.LINEAR_X, ConstraintJointAxis.LINEAR_Y, ConstraintJointAxis.LINEAR_Z));
        try {
            joint = container.physicsSystem().getPipeline().addConstraint(null, serverSubLevel, config);
        } catch (IllegalArgumentException e) {
            BloodAndBones.LOGGER.warn("Shackle hook at {} could not hang limb: {}", worldPosition, e.getMessage());
            return;
        }
        if (joint != null) {
            container.physicsSystem().getPipeline().wakeUp(serverSubLevel);
            activate(level);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        deactivate();
        if (joint != null && joint.isValid()) {
            joint.remove();
        }
        joint = null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (carcassId != null) {
            tag.putUUID("Carcass", carcassId);
        }
        if (subLevelId != null) {
            tag.putUUID("SubLevel", subLevelId);
        }
        tag.putString("Bone", bone);
        // Create rewrites x/y/z when a contraption puts the hook down elsewhere, but not this
        tag.putLong("HookPos", worldPosition.asLong());
        tag.putDouble("AnchorX", anchorPlot.x);
        tag.putDouble("AnchorY", anchorPlot.y);
        tag.putDouble("AnchorZ", anchorPlot.z);
        tag.putDouble("OutX", outX);
        tag.putDouble("OutZ", outZ);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        carcassId = tag.hasUUID("Carcass") ? tag.getUUID("Carcass") : null;
        subLevelId = tag.hasUUID("SubLevel") ? tag.getUUID("SubLevel") : null;
        bone = tag.getString("Bone");
        anchorPlot.set(tag.getDouble("AnchorX"), tag.getDouble("AnchorY"), tag.getDouble("AnchorZ"));
        loadedAt = tag.contains("HookPos") ? BlockPos.of(tag.getLong("HookPos")) : null;
        outX = tag.getDouble("OutX");
        outZ = tag.contains("OutZ") ? tag.getDouble("OutZ") : 1.0;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
