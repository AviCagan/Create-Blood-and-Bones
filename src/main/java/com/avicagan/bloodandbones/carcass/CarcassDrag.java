package com.avicagan.bloodandbones.carcass;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.avicagan.bloodandbones.carcass.rig.RigManager;
import com.avicagan.bloodandbones.network.DragSyncPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.FreeConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.FreeConstraintHandle;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dragging a carcass with the Meat Hook: a spring joint between the point you hooked and a spot just in
 * front of your feet, moved every tick. Heavier carcasses slow you down more and yank harder.
 */
public final class CarcassDrag {
    private static final double HOLD_DISTANCE = 1.1;
    private static final double MAX_DISTANCE = 6.0;
    private static final net.minecraft.resources.ResourceLocation SLOWDOWN_ID = BloodAndBones.asResource("dragging");

    public static final class Drag {
        public final UUID player;
        public final UUID carcass;
        public final String bone;
        public final UUID subLevel;
        public final Vector3d anchorPlot;
        public final float weight;
        @Nullable
        FreeConstraintHandle handle;
        double stiffness;
        double damping;
        double maxForce;

        Drag(UUID player, UUID carcass, String bone, UUID subLevel, Vector3d anchorPlot, float weight) {
            this.player = player;
            this.carcass = carcass;
            this.bone = bone;
            this.subLevel = subLevel;
            this.anchorPlot = anchorPlot;
            this.weight = weight;
        }
    }

    private static final Map<UUID, Drag> DRAGS = new ConcurrentHashMap<>();

    private CarcassDrag() {
    }

    @Nullable
    public static Drag current(Player player) {
        return DRAGS.get(player.getUUID());
    }

    public static boolean isDragging(Player player) {
        return DRAGS.containsKey(player.getUUID());
    }

    /**
     * Right-click with the Meat Hook: start dragging the clicked limb, or let go of whatever is being dragged.
     *
     * @param plotPos      the clicked limb cell, in its sub-level's plot space
     * @param hitLocation  where on the cell the click landed, in plot space (may be off-plot; then the cell center is used)
     */
    public static boolean toggle(ServerLevel level, Player player, BlockPos plotPos, @Nullable Vec3 hitLocation) {
        if (isDragging(player)) {
            stop(level, player);
            return true;
        }
        return start(level, player, plotPos, hitLocation);
    }

    public static boolean start(ServerLevel level, Player player, BlockPos plotPos, @Nullable Vec3 hitLocation) {
        if (!(level.getBlockEntity(plotPos) instanceof CarcassPartBlockEntity part) || part.carcassId() == null) {
            return false;
        }
        SubLevel subLevel = Sable.HELPER.getContaining(level, plotPos);
        if (!(subLevel instanceof ServerSubLevel serverSubLevel) || serverSubLevel.isRemoved()) {
            return false;
        }
        CarcassSavedData.Carcass carcass = CarcassSavedData.get(level).carcass(part.carcassId());
        if (carcass == null) {
            return false;
        }
        float weight = RigManager.all().values().stream()
                .filter(rig -> rig.entity().equals(carcass.entity))
                .map(Rig::weight).findFirst().orElse(1.0F);

        Vector3d anchor;
        if (hitLocation != null && serverSubLevel.getPlot().contains(hitLocation)) {
            anchor = new Vector3d(hitLocation.x, hitLocation.y, hitLocation.z);
        } else {
            anchor = new Vector3d(plotPos.getX() + 0.5, plotPos.getY() + 0.5, plotPos.getZ() + 0.5);
        }

        Drag drag = new Drag(player.getUUID(), carcass.id, part.bone(), serverSubLevel.getUniqueId(), anchor, weight);
        if (!attach(level, player, drag, serverSubLevel)) {
            return false;
        }
        DRAGS.put(player.getUUID(), drag);
        applySlowdown(player, dragPenalty(carcass, weight));
        PacketDistributor.sendToPlayersInDimension(level, new DragSyncPayload(player.getUUID(), Optional.of(drag.subLevel), new Vector3d(drag.anchorPlot)));
        return true;
    }

    public static void stop(ServerLevel level, Player player) {
        Drag drag = DRAGS.remove(player.getUUID());
        removeSlowdown(player);
        if (drag != null && drag.handle != null && drag.handle.isValid()) {
            drag.handle.remove();
        }
        if (drag != null) {
            PacketDistributor.sendToPlayersInDimension(level, DragSyncPayload.ended(player.getUUID()));
        }
    }

    public static void stopAll() {
        DRAGS.clear();
    }

    /** Called once per server tick for every player. Moves the tether target and enforces the release rules. */
    public static void tick(ServerLevel level, Player player) {
        Drag drag = DRAGS.get(player.getUUID());
        if (drag == null) {
            return;
        }
        if (player.isDeadOrDying() || player.isSpectator() || player.level() != level) {
            stop(level, player);
            return;
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            stop(level, player);
            return;
        }
        SubLevel subLevel = container.getSubLevel(drag.subLevel);
        if (!(subLevel instanceof ServerSubLevel serverSubLevel) || serverSubLevel.isRemoved()) {
            stop(level, player);
            return;
        }
        Vector3d target = target(player);
        Vector3d hookWorld = serverSubLevel.logicalPose().transformPosition(drag.anchorPlot, new Vector3d());
        if (hookWorld.distance(target) > MAX_DISTANCE) {
            stop(level, player);
            return;
        }
        // Motor targets only take effect on a freshly made joint, so the tether is rebuilt every tick,
        // the same way Aeronautics' physics staff and handle do it.
        if (drag.handle != null && drag.handle.isValid()) {
            drag.handle.remove();
        }
        drag.handle = null;
        if (!attach(level, player, drag, serverSubLevel)) {
            stop(level, player);
            return;
        }
        // A resting limb goes to sleep in the physics engine; moving its tether must wake it.
        container.physicsSystem().getPipeline().wakeUp(serverSubLevel);
        if (level.getGameTime() % 40 == 0) {
            PacketDistributor.sendToPlayersInDimension(level, new DragSyncPayload(player.getUUID(), Optional.of(drag.subLevel), new Vector3d(drag.anchorPlot)));
        }
    }

    private static void aim(Drag drag, Vector3d target) {
        drag.handle.setMotor(ConstraintJointAxis.LINEAR_X, target.x, drag.stiffness, drag.damping, true, drag.maxForce);
        drag.handle.setMotor(ConstraintJointAxis.LINEAR_Y, target.y, drag.stiffness, drag.damping, true, drag.maxForce);
        drag.handle.setMotor(ConstraintJointAxis.LINEAR_Z, target.z, drag.stiffness, drag.damping, true, drag.maxForce);
    }

    private static boolean attach(ServerLevel level, Player player, Drag drag, ServerSubLevel subLevel) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return false;
        }
        PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        // Same shape as Aeronautics' physics staff: a free joint between the world origin and the hooked point.
        // The linear motors then drive the hooked point toward absolute world coordinates.
        FreeConstraintConfiguration config = new FreeConstraintConfiguration(JOMLConversion.ZERO, drag.anchorPlot, new Quaterniond());
        FreeConstraintHandle handle;
        try {
            handle = pipeline.addConstraint(null, subLevel, config);
        } catch (IllegalArgumentException e) {
            BloodAndBones.LOGGER.warn("Could not tether carcass limb: {}", e.getMessage());
            return false;
        }
        if (handle == null) {
            return false;
        }
        double mass = Math.max(0.05, subLevel.getMassTracker().getMass());
        drag.stiffness = 140.0 * mass;
        drag.damping = 18.0 * mass;
        drag.maxForce = 70.0 * Math.max(mass, drag.weight * 0.5);
        for (ConstraintJointAxis axis : ConstraintJointAxis.ANGULAR) {
            handle.setMotor(axis, 0.0, 0.0, 0.5 * mass, false, 0.0);
        }
        drag.handle = handle;
        aim(drag, target(player));
        drag.handle = handle;
        return true;
    }

    public static Vector3d debugTarget(Player player) {
        return target(player);
    }

    /** A point a little in front of the player's feet, so the carcass drags on the ground behind you. */
    private static Vector3d target(Player player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        if (flat.lengthSqr() < 1.0e-4) {
            flat = Vec3.directionFromRotation(0.0F, player.getYRot());
        }
        flat = flat.normalize().scale(HOLD_DISTANCE);
        double y = player.getY() + 0.7 + Math.max(-0.4, Math.min(0.6, look.y));
        return new Vector3d(player.getX() + flat.x, y, player.getZ() + flat.z);
    }

    private static float dragPenalty(CarcassSavedData.Carcass carcass, float weight) {
        Optional<Rig> rig = RigManager.all().values().stream().filter(r -> r.entity().equals(carcass.entity)).findFirst();
        return rig.map(Rig::dragPenalty).orElseGet(() -> (float) Math.max(0.05, Math.min(0.55, 0.05 + 0.5 * Math.pow(weight / 3.0, 0.6))));
    }

    private static void applySlowdown(Player player, float penalty) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        speed.removeModifier(SLOWDOWN_ID);
        speed.addTransientModifier(new AttributeModifier(SLOWDOWN_ID, -penalty, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static void removeSlowdown(Player player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SLOWDOWN_ID);
        }
    }

    static String describe(CarcassSavedData.Carcass carcass) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(BuiltInRegistries.ENTITY_TYPE.get(carcass.entity)).toString();
    }
}
