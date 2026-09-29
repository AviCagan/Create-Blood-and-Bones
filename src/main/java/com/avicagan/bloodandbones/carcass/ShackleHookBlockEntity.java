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
    /**
     * Torque spring gains per unit of torso mass that turn a hung body belly-out: about the upright only, so how it tilts is
     * left to gravity and to what is still on it (a leg cut off changes how it hangs).
     */
    private static final double TURN_STIFFNESS = 10.0;
    private static final double TURN_DAMPING = 3.0;
    /** A little drag on its swing, per unit of torso mass: knocked, it swings a few times and has settled in a few seconds. */
    private static final double SWING_DAMPING = 1.0;
    /**
     * The least mass the turn spring and the hoist work with. They must use a body's own mass: with a floor of 0.05 here,
     * the turn spring was nearly three times too stiff for a rabbit's torso (0.018) and spun it at 60 radians a second,
     * so it could not be hoisted either.
     */
    public static final double MIN_MASS = 1.0e-4;

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
                hoistTurn(body, physics, timeStep, hook.outX, hook.outZ);
            } else {
                hook.turn(body, physics, timeStep);
            }
        }
    }

    /** Blocks a second the hook hoists a body up to its tip before holding it fast. */
    public static final double HOIST_SPEED = 3.0;
    /** How near the tip the hooked point must come to count as up: a hoist that gets it this near is held at the tip. */
    public static final double HOIST_REACH = 0.3;
    /**
     * How near the tip the hook hoists the hooked point on to before it holds it fast, when it can. The joint takes up
     * what is left in a single physics substep: made from 0.3 blocks off, it jerked the point up at some 12 blocks a
     * second, which set a light body (a rabbit) spinning at up to 5 radians a second on its hook, and the fastest torso of
     * a hung dozen near the 10 blocks a second a hoist must stay under. A body within HOIST_REACH that has stopped
     * coming nearer for {@link #HOIST_STALL} ticks (pressed against something) is held at the tip all the same. A
     * Shackle Trolley holds its body at HOIST_REACH, as before.
     */
    public static final double HOLD_REACH = 0.05;
    public static final int HOIST_STALL = 10;

    /**
     * One tick of a hoist's progress: whether a body {@code gap} from where it hangs is up to be held there, given the
     * nearest it has come so far ({@code best[0]}) and the ticks since it last came nearer ({@code best[1]}), which this
     * keeps.
     */
    private static boolean hoistedUp(double gap, double[] best) {
        if (gap < best[0] - 0.01) {
            best[0] = gap;
            best[1] = 0;
        } else {
            best[1]++;
        }
        return gap <= HOLD_REACH || gap <= HOIST_REACH && best[1] >= HOIST_STALL;
    }
    /**
     * Ticks a hoist may take. A body still short of the tip by then (caught under something) is held fast where it got
     * to, not pulled the rest of the way at once.
     */
    public static final int HOIST_TICKS = 80;
    /** How hard the hoist corrects the hooked point's speed, per second, and the most it pulls, as accelerations. */
    private static final double HOIST_GAIN = 12.0;
    private static final double HOIST_MAX = 40.0;

    private void hoist(ServerLevel level, ServerSubLevel body, dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem physics, double timeStep) {
        Vec3 tip = ShackleHookBlock.tip(worldPosition, getBlockState());
        hoist(level, body, anchorPlot, new Vector3d(tip.x, tip.y, tip.z), hoistedMass(level, carcassId, body), physics, timeStep);
    }

    /**
     * Draw a body's hooked point toward where it is to hang at no more than {@link #HOIST_SPEED}, carrying the whole
     * carcass's weight: a push at the hooked point, as a local impulse over this substep. (A ball joint made straight
     * away snapped a body lying a few blocks off up in a tick, and threw anyone standing by it tens of blocks.) The
     * Shackle Trolley hoists to its chain the same way.
     */
    public static void hoist(ServerLevel level, ServerSubLevel body, Vector3d anchorPlot, Vector3d to, double mass,
                             dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem physics, double timeStep) {
        dev.ryanhcode.sable.companion.math.Pose3d pose = body.logicalPose();
        Vector3d anchor = pose.transformPosition(new Vector3d(anchorPlot), new Vector3d());
        Vector3d toTip = new Vector3d(to).sub(anchor);
        double gap = toTip.length();
        Vector3d wanted = gap < 1.0e-6 ? new Vector3d() : toTip.mul(Math.min(HOIST_SPEED, gap * 6.0) / gap);
        dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle handle = physics.getPhysicsHandle(body);
        Vector3d linear = handle.getLinearVelocity(new Vector3d());
        Vector3d angular = handle.getAngularVelocity(new Vector3d());
        Vector3d pointVelocity = new Vector3d(angular).cross(new Vector3d(anchor).sub(pose.position())).add(linear);
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

    /** The weight a hoist lifts: every body of the carcass, which hang from the torso it holds. */
    public static double hoistedMass(ServerLevel level, @Nullable UUID carcassId, ServerSubLevel torso) {
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
        return Math.max(total, Math.max(MIN_MASS, torso.getMassTracker().getMass()));
    }

    /** Where the hooked point is in the world now, or null while its body is not loaded. */
    @Nullable
    private Vector3d hookedPoint(ServerLevel level) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null || subLevelId == null || !(container.getSubLevel(subLevelId) instanceof ServerSubLevel body) || body.isRemoved()) {
            return null;
        }
        return body.logicalPose().transformPosition(new Vector3d(anchorPlot), new Vector3d());
    }

    /** Spring torque toward the hanging orientation, as a local angular impulse over this substep. */
    private void turn(ServerSubLevel body, dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem physics, double timeStep) {
        hangTurn(body, physics, timeStep, outX, outZ);
    }

    /** Torque gains per unit of torso mass while a body is hoisted: the belly-out spring, and the drag on every turn. */
    private static final double HOIST_TURN_STIFFNESS = 30.0;
    private static final double HOIST_TURN_DAMPING = 7.0;

    /**
     * What turns a body while it is hoisted (a Shackle Hook's, a trolley's): a stiff spring about the upright that turns
     * it belly-out, and a heavy drag on every turn, so it tips into the hang its weight gives it slowly, without swinging
     * or twisting, and arrives there nearly still. Once the hook holds it, hangTurn takes over and it hangs loose.
     * Hoisted as it hangs, with only a light drag, a body lifted from lying on its side came up twisting and settled into
     * one of two hangs, and was still swaying seconds later. Held in the pose a rigid body would hang in (head end straight
     * up) and let go at the tip, it swung some 12 degrees either side of where its weight hangs it (about 29 degrees
     * belly-down, hung by the neck), and was still swinging five seconds on.
     */
    public static void hoistTurn(ServerSubLevel body, dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem physics, double timeStep, double outX, double outZ) {
        Quaterniond current = new Quaterniond(body.logicalPose().orientation());
        Vector3d belly = current.transform(new Vector3d(0.0, 0.0, -1.0));
        double yaw = 0.0;
        if (belly.x * belly.x + belly.z * belly.z > 1.0e-4 && outX * outX + outZ * outZ > 1.0e-8) {
            yaw = Math.atan2(belly.z * outX - belly.x * outZ, belly.x * outX + belly.z * outZ);
        }
        turn(body, physics, timeStep, current, yaw, HOIST_TURN_STIFFNESS, HOIST_TURN_DAMPING, HOIST_TURN_DAMPING);
    }

    /**
     * What holds a hung body (a Shackle Hook's, a trolley's): a spring about the upright that turns its belly (the torso's
     * part-local -z) to face {@code out}, and a little drag on its swing. Nothing holds its tilt: it hangs from the hook
     * however its weight takes it, head end up as it hangs by the neck, everything below loose, a knock swinging it and a
     * leg cut off leaving it lower on the side that kept its leg.
     */
    public static void hangTurn(ServerSubLevel body, dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem physics, double timeStep, double outX, double outZ) {
        Quaterniond current = new Quaterniond(body.logicalPose().orientation());
        Vector3d belly = current.transform(new Vector3d(0.0, 0.0, -1.0));
        Vector3d out = new Vector3d(outX, 0.0, outZ);
        double yaw = 0.0;
        if (belly.x * belly.x + belly.z * belly.z > 1.0e-4 && out.lengthSquared() > 1.0e-8) {
            // the turn about the upright that takes the belly, seen from above, to face out
            yaw = Math.atan2(belly.z * out.x - belly.x * out.z, belly.x * out.x + belly.z * out.z);
        }
        turn(body, physics, timeStep, current, yaw, TURN_STIFFNESS, TURN_DAMPING, SWING_DAMPING);
    }

    /**
     * A spring of {@code stiffness} about the upright through {@code yaw}, a drag of {@code upDamping} on turning about the
     * upright and of {@code swingDamping} on every other turn, all per unit of the body's mass, as an angular impulse over
     * this substep. The drag never does more in a substep than stop the turn, nor the spring more than turn it half the way
     * back: the gains go by mass, a small body turns far more readily for its mass than a big one (a rabbit's torso about
     * its length some ten times as readily as a cow's), and a physics substep is a fortieth of a second, so on a small body
     * they could otherwise overshoot, back and forth. (The spin a hung rabbit showed was the hook's joint jerking it up
     * its last 0.3 blocks, HOLD_REACH; this is a guard, not that fix.)
     */
    private static void turn(ServerSubLevel body, dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem physics, double timeStep, Quaterniond current,
                             double yaw, double stiffness, double upDamping, double swingDamping) {
        dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle handle = physics.getPhysicsHandle(body);
        Vector3d angular = handle.getAngularVelocity(new Vector3d());
        double mass = Math.max(MIN_MASS, body.getMassTracker().getMass());
        org.joml.Matrix3dc toTurn = body.getMassTracker().getInverseInertiaTensor();
        Quaterniond toLocal = new Quaterniond(current).invert();
        // in the body's own frame, where its inertia is
        Vector3d spin = toLocal.transform(new Vector3d(angular));
        Vector3d drag = toLocal.transform(new Vector3d(-angular.x * swingDamping, -angular.y * upDamping, -angular.z * swingDamping).mul(mass * timeStep));
        double along = spin.dot(toTurn.transform(new Vector3d(drag)));
        double spinSquared = spin.lengthSquared();
        if (along < -spinSquared && spinSquared > 0.0) {
            drag.mul(spinSquared / -along);
        }
        Vector3d spring = toLocal.transform(new Vector3d(0.0, yaw * stiffness * mass * timeStep, 0.0));
        double springTurn = toTurn.transform(new Vector3d(spring)).length();
        double most = 0.5 * Math.abs(yaw) / timeStep;
        if (springTurn > most && springTurn > 0.0) {
            spring.mul(most / springTurn);
        }
        handle.applyLinearAndAngularImpulse(new Vector3d(), drag.add(spring));
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
    /**
     * Ticks since the hook began hoisting its body up to the tip, or -1 once it holds it fast (or holds nothing). Not
     * saved: a hook read back with its body off the tip hoists it again (tick).
     */
    private int hoisting = -1;
    /** The nearest the hoisted point has come to the tip, and the ticks since it last came nearer (hoistedUp). */
    private final double[] hoistBest = {Double.MAX_VALUE, 0};

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
        // a hook in the world hoists the body up before it holds it fast; one on a ship holds it at once. Hoisted by its
        // neck junction with nothing holding its tilt (hangTurn), it comes up hanging from that point as its weight takes it.
        Vec3 tip = ShackleHookBlock.tip(worldPosition, getBlockState());
        if (dev.ryanhcode.sable.Sable.HELPER.getContaining(level, worldPosition) == null
                && torso.logicalPose().transformPosition(new Vector3d(anchorPlot), new Vector3d()).distance(tip.x, tip.y, tip.z) > HOLD_REACH) {
            startHoist(level, torso);
        } else {
            attach(level, false, null);
        }
        return true;
    }

    private void startHoist(ServerLevel level, ServerSubLevel torso) {
        hoisting = 0;
        hoistBest[0] = Double.MAX_VALUE;
        hoistBest[1] = 0;
        activate(level);
        SubLevelContainer.getContainer(level).physicsSystem().getPipeline().wakeUp(torso);
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
            Vector3d at = hook.hookedPoint(serverLevel);
            Vec3 tip = ShackleHookBlock.tip(pos, state);
            if (at == null) {
                // its body is not loaded: taken back up (or let go) once it is, below
                hook.hoisting = -1;
            } else if (hoistedUp(at.distance(tip.x, tip.y, tip.z), hook.hoistBest)) {
                hook.hoisting = -1;
                hook.attach(serverLevel, false, null);
            } else if (++hook.hoisting > HOIST_TICKS) {
                // caught under something: held fast where it got to, not pulled the rest of the way at once
                hook.hoisting = -1;
                hook.attach(serverLevel, false, at);
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
        // Taking back a body it held before: the hook was saved or unloaded part way up, or the body was held where it
        // got to, or swung while its hook was unloaded. One off the tip is hoisted up again, not snapped there.
        Vector3d at = hook.hookedPoint(serverLevel);
        Vec3 tip = ShackleHookBlock.tip(pos, state);
        if (at != null && dev.ryanhcode.sable.Sable.HELPER.getContaining(serverLevel, pos) == null
                && at.distance(tip.x, tip.y, tip.z) > HOLD_REACH && at.distance(tip.x, tip.y, tip.z) <= REJOIN_REACH
                && SubLevelContainer.getContainer(serverLevel).getSubLevel(hook.subLevelId) instanceof ServerSubLevel torso) {
            hook.startHoist(serverLevel, torso);
            return;
        }
        hook.attach(serverLevel, true, null);
    }

    /** Farthest a hung body may have swung while its hook was unloaded and still be taken back, in blocks. */
    public static final double REJOIN_REACH = 8.0;

    /** Where the hook was when it was saved, as read back; null once checked. */
    @Nullable
    private BlockPos loadedAt;

    /**
     * @param rejoin taking back a body it already held, rather than hooking a new one
     * @param at     where in the world to hold the hooked point, or null for the tip
     */
    private void attach(ServerLevel level, boolean rejoin, @Nullable Vector3d at) {
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
                at != null ? new Vector3d(at) : new Vector3d(tip.x, tip.y, tip.z), new Vector3d(anchorPlot), new Quaterniond(), new Quaterniond(),
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
