package com.avicagan.bloodandbones.carcass;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.avicagan.bloodandbones.carcass.rig.RigManager;
import com.avicagan.bloodandbones.network.DragSyncPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
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
    /** Spring gains per unit of carcass weight (a solid block weighs 1.0). */
    private static final double STIFFNESS = 120.0;
    private static final double DAMPING = 24.0; // about critical damping for the spring above, so no bounce
    private static final double MAX_FORCE = 60.0;
    private static final net.minecraft.resources.ResourceLocation SLOWDOWN_ID = BloodAndBones.asResource("dragging");

    public static final class Drag {
        public final UUID player;
        public final UUID carcass;
        public final String bone;
        public final UUID subLevel;
        public final Vector3d anchorPlot;
        public final float weight;
        /** The dragging player, refreshed every tick; not looked up by UUID because test players are not in the level. */
        @Nullable
        Player playerEntity;

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

    public static boolean isDraggingCarcass(UUID carcassId) {
        for (Drag drag : DRAGS.values()) {
            if (drag.carcass.equals(carcassId)) {
                return true;
            }
        }
        return false;
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
        if (carcass.resting) {
            // unfold first; the click lands on the torso, so hook the torso where it was clicked
            Vector3d hitWorld = hitLocation != null && serverSubLevel.getPlot().contains(hitLocation)
                    ? serverSubLevel.logicalPose().transformPosition(new Vector3d(hitLocation.x, hitLocation.y, hitLocation.z), new Vector3d())
                    : serverSubLevel.logicalPose().transformPosition(new Vector3d(plotPos.getX() + 0.5, plotPos.getY() + 0.5, plotPos.getZ() + 0.5), new Vector3d());
            java.util.Map<String, ServerSubLevel> unfolded = CarcassRest.split(level, carcass);
            if (unfolded == null) {
                return false;
            }
            ServerSubLevel torso = unfolded.get(carcass.rootBone);
            if (torso == null) {
                return false;
            }
            Vector3d anchor = torso.logicalPose().transformPositionInverse(hitWorld, new Vector3d());
            if (!torso.getPlot().contains(anchor)) {
                BlockPos c = torso.getPlot().getCenterBlock();
                anchor.set(c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5);
            }
            float weight = RigManager.all().values().stream().filter(rig -> rig.entity().equals(carcass.entity)).map(Rig::weight).findFirst().orElse(1.0F);
            Drag drag = new Drag(player.getUUID(), carcass.id, carcass.rootBone, torso.getUniqueId(), anchor, weight);
            drag.playerEntity = player;
            DRAGS.put(player.getUUID(), drag);
            applySlowdown(player, dragPenalty(carcass, weight));
            PacketDistributor.sendToPlayersInDimension(level, new DragSyncPayload(player.getUUID(), Optional.of(drag.subLevel), new Vector3d(drag.anchorPlot)));
            return true;
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
        drag.playerEntity = player;
        DRAGS.put(player.getUUID(), drag);
        applySlowdown(player, dragPenalty(carcass, weight));
        PacketDistributor.sendToPlayersInDimension(level, new DragSyncPayload(player.getUUID(), Optional.of(drag.subLevel), new Vector3d(drag.anchorPlot)));
        return true;
    }

    public static void stop(ServerLevel level, Player player) {
        Drag drag = DRAGS.remove(player.getUUID());
        removeSlowdown(player);
        if (drag != null) {
            PacketDistributor.sendToPlayersInDimension(level, DragSyncPayload.ended(player.getUUID()));
        }
    }

    public static void stopAll() {
        DRAGS.clear();
    }

    /** Called once per server tick for every player: release rules and client sync. */
    public static void tick(ServerLevel level, Player player) {
        Drag drag = DRAGS.get(player.getUUID());
        if (drag == null) {
            return;
        }
        if (player.isDeadOrDying() || player.isSpectator() || player.level() != level) {
            stop(level, player);
            return;
        }
        drag.playerEntity = player;
        ServerSubLevel subLevel = resolve(level, drag);
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (subLevel == null || container == null) {
            stop(level, player);
            return;
        }
        Vector3d target = target(player, 1.0);
        Vector3d hookWorld = subLevel.logicalPose().transformPosition(drag.anchorPlot, new Vector3d());
        if (hookWorld.distance(target) > MAX_DISTANCE) {
            stop(level, player);
            return;
        }
        if (level.getGameTime() % 40 == 0) {
            PacketDistributor.sendToPlayersInDimension(level, new DragSyncPayload(player.getUUID(), Optional.of(drag.subLevel), new Vector3d(drag.anchorPlot)));
        }
    }

    /** Called every physics substep with the player's position interpolated to the substep. */
    public static void physicsTick(ServerLevel level, double partial, double timeStep) {
        if (DRAGS.isEmpty()) {
            return;
        }
        SubLevelPhysicsSystem physics = SubLevelPhysicsSystem.get(level);
        if (physics == null) {
            return;
        }
        for (Drag drag : DRAGS.values()) {
            Player player = drag.playerEntity != null ? drag.playerEntity : level.getPlayerByUUID(drag.player);
            if (player == null || player.level() != level) {
                continue;
            }
            ServerSubLevel subLevel = resolve(level, drag);
            if (subLevel == null) {
                continue;
            }
            pull(drag, subLevel, player, partial, timeStep, physics);
            physics.getPipeline().wakeUp(subLevel);
        }
    }

    @Nullable
    private static ServerSubLevel resolve(ServerLevel level, Drag drag) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }
        SubLevel subLevel = container.getSubLevel(drag.subLevel);
        return subLevel instanceof ServerSubLevel serverSubLevel && !serverSubLevel.isRemoved() ? serverSubLevel : null;
    }

    /**
     * The tether is a spring applied as impulses every physics substep, not a joint: Sable's
     * {@code applyImpulseAtPoint} takes an impulse in the body's local frame at a plot-space point
     * (verified by the impulse probe test), which gives a smooth, fully predictable pull.
     */
    private static void pull(Drag drag, ServerSubLevel subLevel, Player player, double partial, double timeStep, SubLevelPhysicsSystem physics) {
        RigidBodyHandle handle = physics.getPhysicsHandle(subLevel);
        Pose3d pose = subLevel.logicalPose();
        Vector3d target = target(player, partial);
        Vector3d hook = pose.transformPosition(drag.anchorPlot, new Vector3d());
        // velocity of the hooked point: body velocity plus spin about the center of mass
        Vector3d linear = handle.getLinearVelocity(new Vector3d());
        Vector3d angular = handle.getAngularVelocity(new Vector3d());
        Vector3d arm = new Vector3d(hook).sub(pose.position());
        Vector3d hookVelocity = new Vector3d(angular).cross(arm).add(linear);

        double weight = Math.max(0.05, drag.weight);
        double stiffness = STIFFNESS * weight;
        double damping = DAMPING * weight;
        double maxForce = MAX_FORCE * weight;
        Vector3d force = new Vector3d(target).sub(hook).mul(stiffness).sub(new Vector3d(hookVelocity).mul(damping));
        double magnitude = force.length();
        if (magnitude > maxForce) {
            force.mul(maxForce / magnitude);
        }
        // impulse over this substep, queued through Sable's force groups (the path its own lift blocks use),
        // expressed in the body's local frame at the hooked plot point
        Vector3d impulse = force.mul(timeStep);
        pose.orientation().transformInverse(impulse);
        subLevel.getOrCreateQueuedForceGroup(ForceGroups.PROPULSION.get()).applyAndRecordPointForce(drag.anchorPlot, impulse);
    }

    /** A point a little in front of the player's feet, so the carcass drags on the ground behind you. */
    private static Vector3d target(Player player, double partial) {
        double px = Mth.lerp(partial, player.xo, player.getX());
        double py = Mth.lerp(partial, player.yo, player.getY());
        double pz = Mth.lerp(partial, player.zo, player.getZ());
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        if (flat.lengthSqr() < 1.0e-4) {
            flat = Vec3.directionFromRotation(0.0F, player.getYRot());
        }
        flat = flat.normalize().scale(HOLD_DISTANCE);
        double y = py + 0.7 + Math.max(-0.4, Math.min(0.6, look.y));
        return new Vector3d(px + flat.x, y, pz + flat.z);
    }


    public static Vector3d debugTarget(Player player) {
        return target(player, 1.0);
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
