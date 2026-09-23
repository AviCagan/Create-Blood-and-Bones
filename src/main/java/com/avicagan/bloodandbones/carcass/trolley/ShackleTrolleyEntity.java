package com.avicagan.bloodandbones.carcass.trolley;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.CarcassJoints;
import com.avicagan.bloodandbones.carcass.CarcassSavedData;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A Shackle Hook riding a Create chain conveyor. Server-authoritative: every tick it advances a
 * {@link ChainCursor} over the conveyors' public geometry, and every Sable physics substep it slides the world
 * end of a ball joint (the carcass's neck junction) along the chain by re-setting the joint's first frame.
 */
public class ShackleTrolleyEntity extends Entity {
    /** Hook point below the chain line: where Create hangs packages (ChainConveyorBlockEntity#tickBoxVisuals). */
    public static final double HANG = 9 / 16.0;
    private static final double TURN_STIFFNESS = 30.0;
    private static final double TURN_DAMPING = 7.0;
    private static final Quaterniond IDENTITY = new Quaterniond();

    /** Every trolley loaded in a server level (added/removed with the entity). */
    private static final Map<ServerLevel, Set<ShackleTrolleyEntity>> LOADED = new WeakHashMap<>();

    private static final EntityDataAccessor<Optional<UUID>> DATA_SUB_LEVEL =
            SynchedEntityData.defineId(ShackleTrolleyEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    /** Plot coordinates are huge; a Vector3f would lose whole blocks, so the anchor syncs as doubles in a tag. */
    private static final EntityDataAccessor<CompoundTag> DATA_ANCHOR =
            SynchedEntityData.defineId(ShackleTrolleyEntity.class, EntityDataSerializers.COMPOUND_TAG);

    @Nullable
    private ChainCursor cursor;
    @Nullable
    private UUID carcassId;
    @Nullable
    private UUID subLevelId;
    private final Vector3d anchorPlot = new Vector3d();
    private double outX = 0.0;
    private double outZ = 1.0;
    private boolean parked;

    // transient: rebuilt after load / chunk reload
    @Nullable
    private GenericConstraintHandle joint;
    @Nullable
    private Vec3 prevAnchor;
    @Nullable
    private Vec3 anchor;

    public ShackleTrolleyEntity(EntityType<? extends ShackleTrolleyEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /** Server: put a new trolley on the chain carrying the given carcass by its torso. */
    public static ShackleTrolleyEntity create(EntityType<? extends ShackleTrolleyEntity> type, ServerLevel level,
                                              ChainCursor cursor, CarcassSavedData.Carcass carcass, ServerSubLevel torso) {
        ShackleTrolleyEntity trolley = new ShackleTrolleyEntity(type, level);
        trolley.cursor = cursor;
        trolley.carcassId = carcass.id;
        trolley.subLevelId = carcass.bones.get(carcass.rootBone);
        trolley.anchorPlot.set(com.avicagan.bloodandbones.carcass.ShackleHookBlockEntity.neckJunction(carcass, torso));
        ChainConveyorBlockEntity be = ChainCursor.conveyorAt(level, cursor.conveyor);
        Vec3 start = be == null ? null : cursor.chainPoint(be);
        if (start != null) {
            trolley.setPos(start);
            trolley.anchor = trolley.prevAnchor = start.subtract(0, HANG, 0);
        }
        trolley.syncCarcass();
        return trolley;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SUB_LEVEL, Optional.empty());
        builder.define(DATA_ANCHOR, new CompoundTag());
    }

    private void syncCarcass() {
        entityData.set(DATA_SUB_LEVEL, Optional.ofNullable(subLevelId));
        CompoundTag tag = new CompoundTag();
        tag.putDouble("X", anchorPlot.x);
        tag.putDouble("Y", anchorPlot.y);
        tag.putDouble("Z", anchorPlot.z);
        entityData.set(DATA_ANCHOR, tag);
    }

    /** Client: the carcass body this trolley holds, for the renderer. */
    public Optional<UUID> clientSubLevel() {
        return entityData.get(DATA_SUB_LEVEL);
    }

    /** Client: the neck junction in the carcass's plot coordinates, for the renderer. */
    public Vector3d clientAnchorPlot() {
        CompoundTag tag = entityData.get(DATA_ANCHOR);
        return new Vector3d(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));
    }

    // ---------------------------------------------------------------- server tick

    /**
     * LevelTickEvent.Pre: fired by MinecraftServer#tickChildren before ServerLevel#tick, whose HEAD runs Sable's
     * physics substeps. Advancing here means this tick's substeps already slide toward this tick's chain point.
     * (Advancing in Entity#tick instead leaves the carcass exactly one tick of travel behind the trolley.)
     */
    public static void advanceAll(ServerLevel level) {
        Set<ShackleTrolleyEntity> trolleys = LOADED.get(level);
        if (trolleys == null) {
            return;
        }
        for (ShackleTrolleyEntity trolley : trolleys.toArray(new ShackleTrolleyEntity[0])) {
            if (!trolley.isRemoved()) {
                trolley.advance(level);
            }
        }
    }

    private void advance(ServerLevel level) {
        if (cursor == null) {
            return;
        }
        switch (cursor.advance(level, parked)) {
            case DERAILED -> {
                dropCarcass(level); // conveyor broken or chain removed
                return;
            }
            case WAITING -> {
                prevAnchor = anchor; // a conveyor we need is not loaded: hold still
                return;
            }
            case ARRIVED -> parked = true; // a frogport matching our address: wait until resume()
            default -> {
            }
        }
        ChainConveyorBlockEntity be = ChainCursor.conveyorAt(level, cursor.conveyor);
        Vec3 point = be == null ? null : cursor.chainPoint(be);
        if (point == null) {
            prevAnchor = anchor;
            return;
        }
        Vec3 heading = cursor.heading(be);
        // belly faces sideways out of the line of travel, so the body swings fore-and-aft on stops
        outX = -heading.z;
        outZ = heading.x;
        setPos(point);
        setYRot((float) Math.toDegrees(Math.atan2(-heading.x, heading.z)));
        Vec3 next = point.subtract(0, HANG, 0);
        prevAnchor = anchor == null ? next : anchor;
        anchor = next;
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel level && (joint == null || !joint.isValid())) {
            joint = null;
            attach(level); // the joint is memory-only: (re)build it after spawn, load, or the carcass reloading
        }
    }

    @Nullable
    public ChainCursor cursor() {
        return cursor;
    }

    /** Where the joint's world end is this tick (null before the first tick on a loaded chain). */
    @Nullable
    public Vec3 anchor() {
        return anchor;
    }

    public Vector3d anchorPlot() {
        return anchorPlot;
    }

    /** Leave a frogport stop: forget the address so the same port does not catch us again. */
    public void resume() {
        if (cursor != null) {
            cursor.address = "";
        }
        parked = false;
    }

    // ---------------------------------------------------------------- physics substeps

    /** From CarcassEvents#onPrePhysicsTick, next to ShackleHookBlockEntity.physicsTick. */
    public static void physicsTick(ServerLevel level, double partial, double timeStep) {
        Set<ShackleTrolleyEntity> trolleys = LOADED.get(level);
        if (trolleys == null || trolleys.isEmpty()) {
            return;
        }
        SubLevelPhysicsSystem physics = SubLevelPhysicsSystem.get(level);
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (physics == null || container == null) {
            return;
        }
        for (ShackleTrolleyEntity trolley : trolleys.toArray(new ShackleTrolleyEntity[0])) {
            if (trolley.isRemoved() || trolley.joint == null || !trolley.joint.isValid() || trolley.anchor == null) {
                continue;
            }
            SubLevel subLevel = container.getSubLevel(trolley.subLevelId);
            if (!(subLevel instanceof ServerSubLevel body) || body.isRemoved()) {
                continue;
            }
            // advanceAll ran in LevelTickEvent.Pre, so this slides from last tick's point to this tick's.
            Vec3 from = trolley.prevAnchor == null ? trolley.anchor : trolley.prevAnchor;
            Vec3 at = from.lerp(trolley.anchor, partial);
            // The ground-side frame of a world-anchored joint is in world coordinates, and Sable re-reads it at
            // every step (sable_rapier joints.rs tick(): local_anchor1 = pos_a - 0 for the ground body).
            trolley.joint.setFrame1(new Vector3d(at.x, at.y, at.z), IDENTITY);
            if (!trolley.anchor.equals(from)) {
                physics.getPipeline().wakeUp(body);
            }
            trolley.turn(body, physics, timeStep);
        }
    }

    /** Same spring as ShackleHookBlockEntity#turn: belly toward (outX, outZ), swing damped. */
    private void turn(ServerSubLevel body, SubLevelPhysicsSystem physics, double timeStep) {
        Quaterniond current = new Quaterniond(body.logicalPose().orientation());
        Quaterniond wanted = com.avicagan.bloodandbones.carcass.ShackleHookBlockEntity.hangingOrientation(outX, outZ);
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
        RigidBodyHandle handle = physics.getPhysicsHandle(body);
        Vector3d angular = handle.getAngularVelocity(new Vector3d());
        double mass = Math.max(0.05, body.getMassTracker().getMass());
        Vector3d torque = new Vector3d(axis).mul(angle * TURN_STIFFNESS * mass).sub(new Vector3d(angular).mul(TURN_DAMPING * mass));
        Vector3d impulse = torque.mul(timeStep);
        current.invert().transform(impulse);
        handle.applyLinearAndAngularImpulse(new Vector3d(), impulse);
    }


    // ---------------------------------------------------------------- joint

    private void attach(ServerLevel level) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null || subLevelId == null || anchor == null) {
            return;
        }
        SubLevel subLevel = container.getSubLevel(subLevelId);
        if (!(subLevel instanceof ServerSubLevel body) || body.isRemoved()) {
            return;
        }
        // Identical to ShackleHookBlockEntity#attach: ball joint, world (null) body first, carcass torso second.
        GenericConstraintConfiguration config = new GenericConstraintConfiguration(
                new Vector3d(anchor.x, anchor.y, anchor.z), new Vector3d(anchorPlot), new Quaterniond(), new Quaterniond(),
                EnumSet.of(ConstraintJointAxis.LINEAR_X, ConstraintJointAxis.LINEAR_Y, ConstraintJointAxis.LINEAR_Z));
        try {
            joint = container.physicsSystem().getPipeline().addConstraint(null, body, config);
        } catch (IllegalArgumentException e) {
            // e.g. a chain conveyor that itself sits on a Sable sub-level: its positions are plot coordinates
            BloodAndBones.LOGGER.warn("Shackle trolley {} could not hang carcass: {}", getUUID(), e.getMessage());
            return;
        }
        if (joint != null) {
            container.physicsSystem().getPipeline().wakeUp(body);
        }
    }

    private void detachJoint() {
        if (joint != null && joint.isValid()) {
            joint.remove();
        }
        joint = null;
    }

    /** Let the carcass fall and remove the trolley (drop a trolley item here if it is craftable). */
    public void dropCarcass(ServerLevel level) {
        detachJoint();
        carcassId = null;
        subLevelId = null;
        discard();
    }

    /** CarcassRest#isHeld and CarcassBleeding should OR this with ShackleHookBlockEntity.isHanging. */
    public static boolean isHanging(ServerLevel level, UUID carcassId) {
        Set<ShackleTrolleyEntity> trolleys = LOADED.get(level);
        if (trolleys == null) {
            return false;
        }
        for (ShackleTrolleyEntity trolley : trolleys) {
            if (!trolley.isRemoved() && trolley.joint != null && carcassId.equals(trolley.carcassId)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel(); // spawn and chunk load alike
        if (level() instanceof ServerLevel level) {
            LOADED.computeIfAbsent(level, l -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(this);
        }
    }

    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel(); // also runs on chunk unload: the joint is memory-only and rebuilt by tick()
        if (level() instanceof ServerLevel level) {
            Set<ShackleTrolleyEntity> trolleys = LOADED.get(level);
            if (trolleys != null) {
                trolleys.remove(this);
            }
        }
        detachJoint();
    }

    // ---------------------------------------------------------------- interaction

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level() instanceof ServerLevel level && !isRemoved()) {
            dropCarcass(level);
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- save

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        cursor = tag.contains("Cursor") ? ChainCursor.load(tag.getCompound("Cursor")) : null;
        carcassId = tag.hasUUID("Carcass") ? tag.getUUID("Carcass") : null;
        subLevelId = tag.hasUUID("SubLevel") ? tag.getUUID("SubLevel") : null;
        anchorPlot.set(tag.getDouble("AnchorX"), tag.getDouble("AnchorY"), tag.getDouble("AnchorZ"));
        parked = tag.getBoolean("Parked");
        syncCarcass();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (cursor != null) {
            tag.put("Cursor", cursor.save());
        }
        if (carcassId != null) {
            tag.putUUID("Carcass", carcassId);
        }
        if (subLevelId != null) {
            tag.putUUID("SubLevel", subLevelId);
        }
        tag.putDouble("AnchorX", anchorPlot.x);
        tag.putDouble("AnchorY", anchorPlot.y);
        tag.putDouble("AnchorZ", anchorPlot.z);
        tag.putBoolean("Parked", parked);
    }
}
