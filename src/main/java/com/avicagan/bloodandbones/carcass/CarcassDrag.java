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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dragging a carcass with the Meat Hook: a spring joint between the point you hooked and a spot just in
 * front of your feet, moved every tick. Heavier carcasses slow you down more and yank harder. A hauler minion
 * drags one the same way (docs/PARTS-AND-TRAITS.md section 6.9), the spot held at arm's length back along the
 * line to the carcass, so it trails behind wherever the minion walks.
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
        /** Who drags it: a player, or a hauler minion. */
        public final UUID player;
        /** changes when the hooked limb is cut off into a record of its own */
        public UUID carcass;
        public final String bone;
        public final UUID subLevel;
        public final Vector3d anchorPlot;
        /** which way the hook went in, in plot space: the player's look at the moment of the grab */
        public final Vector3d entryPlot = new Vector3d(0, -1, 0);
        /** The mass on the hook (the bodies of the record it holds), kept up to date as pieces come off it. */
        public float weight;
        /** Its weight class's drag: what the penalty for that mass is multiplied by (1 for every class the mod ships). */
        public float classDrag = 1.0F;
        /** Whoever drags it, refreshed every tick; not looked up by UUID because test players are not in the level. */
        @Nullable
        LivingEntity playerEntity;
        /**
         * For a hauler: the height of the ground it last stood on (never the body it drags), which its hands keep to.
         * Held above its feet as they are, a hauler hopping up a step (onto a Bleeding Rack, say) yanked the body up after
         * it, into itself: it landed on its own body, and with the body pulled up after its rising feet the two were
         * flung together, blocks off the line it was towing along.
         */
        double groundY = Double.NaN;
        /** Where its dragger stood at the last game tick seen, and how many ticks in a row they have stood there. */
        @Nullable
        Vec3 stoodAt;
        long stoodSeen = Long.MIN_VALUE;
        int stoodTicks;

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

    /**
     * Something that drags a carcass by its own rule rather than a player's: a hauler minion, slowed by how fit it is at
     * hauling (docs/NEXT.md 1.2), not by the drag strength a player's traits give.
     */
    public interface Dragger {
        /** Its slowdown dragging a carcass that slows a player by {@code playerSlowdown} (a share of walking speed). */
        float dragSlowdown(float playerSlowdown);
    }

    private CarcassDrag() {
    }

    @Nullable
    public static Drag current(LivingEntity player) {
        return DRAGS.get(player.getUUID());
    }

    public static boolean isDragging(LivingEntity player) {
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

    /**
     * To every player on the server whose client has the mod (the channel is optional, so others get
     * nothing). Every dimension, not just this one: a drag can end after its player went through a portal,
     * and anyone left behind must still hear that it ended.
     */
    private static void broadcast(ServerLevel level, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        for (net.minecraft.server.level.ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.connection.hasChannel(payload.type())) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    /** Limbs moved from one record to another (cut off): drags of those limbs follow them. */
    public static void moved(UUID from, UUID to, java.util.Set<String> bones) {
        for (Drag drag : DRAGS.values()) {
            if (drag.carcass.equals(from) && bones.contains(drag.bone)) {
                drag.carcass = to;
            }
        }
    }

    public static boolean isDraggingCarcass(UUID carcassId) {
        for (Drag drag : DRAGS.values()) {
            if (drag.carcass.equals(carcassId)) {
                return true;
            }
        }
        return false;
    }

    /** The part a carcass is dragged by (the first drag found, if several hold it), or null if nobody drags it. */
    @Nullable
    public static String hookedBone(UUID carcassId) {
        for (Drag drag : DRAGS.values()) {
            if (drag.carcass.equals(carcassId)) {
                return drag.bone;
            }
        }
        return null;
    }

    /** Whether anyone but this dragger has hold of the carcass too (a player's Meat Hook on the body a hauler tows). */
    public static boolean isDraggedByAnother(UUID carcassId, LivingEntity dragger) {
        for (Drag drag : DRAGS.values()) {
            if (drag.carcass.equals(carcassId) && !drag.player.equals(dragger.getUUID())) {
                return true;
            }
        }
        return false;
    }

    public static boolean start(ServerLevel level, LivingEntity player, BlockPos plotPos, @Nullable Vec3 hitLocation) {
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
            // a player hooks the part they aimed at, drawn where it lies; anyone else (a hauler) takes the torso where
            // it was touched
            CarcassAim.Hit aimed = player instanceof Player ? aimAtResting(level, carcass, serverSubLevel, player) : null;
            Vector3d hitWorld = aimed != null ? aimed.point()
                    : hitLocation != null && serverSubLevel.getPlot().contains(hitLocation)
                    ? serverSubLevel.logicalPose().transformPosition(new Vector3d(hitLocation.x, hitLocation.y, hitLocation.z), new Vector3d())
                    : serverSubLevel.logicalPose().transformPosition(new Vector3d(plotPos.getX() + 0.5, plotPos.getY() + 0.5, plotPos.getZ() + 0.5), new Vector3d());
            return startResting(level, player, carcass, aimed != null ? aimed.bone() : carcass.rootBone, hitWorld);
        }
        float weight = attachedMass(SubLevelContainer.getContainer(level), carcass);

        Vector3d anchor;
        if (hitLocation != null && serverSubLevel.getPlot().contains(hitLocation)) {
            anchor = new Vector3d(hitLocation.x, hitLocation.y, hitLocation.z);
        } else {
            anchor = new Vector3d(plotPos.getX() + 0.5, plotPos.getY() + 0.5, plotPos.getZ() + 0.5);
        }

        Drag drag = new Drag(player.getUUID(), carcass.id, part.bone(), serverSubLevel.getUniqueId(), anchor, weight);
        drag.classDrag = CarcassBody.weightClass(carcass).drag();
        drag.playerEntity = player;
        drag.groundY = player.getY();
        drag.entryPlot.set(entryDirection(serverSubLevel, player));
        DRAGS.put(player.getUUID(), drag);
        applySlowdown(player, penaltyFor(drag));
        broadcast(level, sync(drag));
        Blood.wound(level, carcass, serverSubLevel.logicalPose().transformPosition(anchor, new Vector3d()), 10, 1);
        return true;
    }

    /**
     * Unfold a carcass lying still and hook one of its parts at a world point: the part is where it was drawn, so the point
     * lands on it. Blood wells where the hook goes in, as on a carcass already awake.
     */
    private static boolean startResting(ServerLevel level, LivingEntity player, CarcassSavedData.Carcass carcass, String bone, Vector3d hitWorld) {
        java.util.Map<String, ServerSubLevel> unfolded = CarcassRest.split(level, carcass);
        if (unfolded == null) {
            return false;
        }
        ServerSubLevel body = unfolded.get(bone);
        if (body == null) {
            bone = carcass.rootBone;
            body = unfolded.get(bone);
        }
        if (body == null) {
            return false;
        }
        Vector3d anchor = body.logicalPose().transformPositionInverse(hitWorld, new Vector3d());
        if (!body.getPlot().contains(anchor)) {
            BlockPos c = body.getPlot().getCenterBlock();
            anchor.set(c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5);
        }
        float weight = attachedMass(SubLevelContainer.getContainer(level), carcass);
        Drag drag = new Drag(player.getUUID(), carcass.id, bone, body.getUniqueId(), anchor, weight);
        drag.classDrag = CarcassBody.weightClass(carcass).drag();
        drag.playerEntity = player;
        drag.groundY = player.getY();
        drag.entryPlot.set(entryDirection(body, player));
        DRAGS.put(player.getUUID(), drag);
        applySlowdown(player, penaltyFor(drag));
        broadcast(level, sync(drag));
        Blood.wound(level, carcass, hitWorld, 10, 1);
        return true;
    }

    /** How far a Meat Hook reaches to hook a carcass, as a hand reaches a block. */
    public static final double HOOK_REACH = 5.0;

    /** Where a player's look first meets a resting carcass, its folded parts as drawn; or null. */
    @Nullable
    static CarcassAim.Hit aimAtResting(ServerLevel level, CarcassSavedData.Carcass carcass, ServerSubLevel torso, LivingEntity player) {
        Rig rig = RigManager.forCarcass(carcass).orElse(null);
        if (rig == null || !(level.getBlockEntity(torso.getPlot().getCenterBlock()) instanceof CarcassPartBlockEntity root)) {
            return null;
        }
        com.avicagan.bloodandbones.carcass.rig.Bone torsoBone = rig.bone(carcass.rootBone).orElse(rig.root());
        return CarcassAim.resting(torso, rig, torsoBone, root.merged(), player.getEyePosition(), player.getLookAngle(), HOOK_REACH);
    }

    /**
     * How far a Meat Hook reaches along a player's look: to the first block in the way (a wall, a closed door, the deck of
     * a ship), and never past {@link #HOOK_REACH}. Worked out from a ray cast of the player's own, the same on either side,
     * never from the block a use names: a use in the air names none, and one on a block of a Sable sub-level (a ship, a
     * body) names where it hit in that sub-level's plot, far from the player. Sable's ray cast meets the blocks of every
     * sub-level too, and gives such a hit in its plot, so it is brought out into the world before it is measured. Plants
     * and other blocks with nothing to collide with let the hook through.
     */
    public static double hookReach(Player player) {
        net.minecraft.world.level.Level level = player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(HOOK_REACH));
        net.minecraft.world.phys.BlockHitResult hit = level.clip(new net.minecraft.world.level.ClipContext(eye, end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
            return HOOK_REACH;
        }
        return Math.min(HOOK_REACH, eye.distanceTo(Sable.HELPER.projectOutOfSubLevel(level, hit.getLocation())));
    }

    /**
     * A Meat Hook used where no carcass cell was hit: a part folded into a carcass lying still has no cells of its own,
     * so a look at a drawn leg or head passes through it to the ground behind (or to nothing). If the look meets such a
     * part before any block ({@link #hookReach}), that part is hooked, or, while dragging, the drag lets go, as a click on
     * a carcass does. Behind a wall, a door or a ship's side, nothing happens.
     *
     * @return whether a resting carcass was in the way (the use is then spent on it)
     */
    public static boolean useOnDrawn(ServerLevel level, Player player) {
        CarcassAim.RestingHit aimed = CarcassAim.nearestResting(level, player.getEyePosition(), player.getLookAngle(), hookReach(player));
        CarcassSavedData.Carcass carcass = aimed == null ? null : CarcassSavedData.get(level).carcass(aimed.root().carcassId());
        if (carcass == null || !carcass.resting) {
            return false;
        }
        if (isDragging(player)) {
            stop(level, player);
            return true;
        }
        startResting(level, player, carcass, aimed.hit().bone(), aimed.hit().point());
        return true;
    }

    /** What the hook is really pulling: the mass of the bodies in this record (a severed leg is not a cow). */
    private static float attachedMass(ServerSubLevelContainer container, CarcassSavedData.Carcass carcass) {
        double total = 0.0;
        for (UUID id : carcass.bones.values()) {
            if (container.getSubLevel(id) instanceof ServerSubLevel body && !body.isRemoved()) {
                total += body.getMassTracker().getMass();
            }
        }
        if (total <= 0.0) {
            return RigManager.forCarcass(carcass).map(Rig::weight).orElse(1.0F);
        }
        return (float) total;
    }

    /** The player's look direction, turned into the limb's own frame: the way the hook was pushed in. */
    private static Vector3d entryDirection(ServerSubLevel subLevel, LivingEntity player) {
        Vec3 look = player.getLookAngle();
        Vector3d entry = new Vector3d(look.x, look.y, look.z);
        subLevel.logicalPose().orientation().transformInverse(entry);
        return entry.normalize();
    }

    private static DragSyncPayload sync(Drag drag) {
        return new DragSyncPayload(drag.player, Optional.of(drag.subLevel), new Vector3d(drag.anchorPlot), new Vector3d(drag.entryPlot));
    }

    public static void stop(ServerLevel level, LivingEntity player) {
        Drag drag = DRAGS.remove(player.getUUID());
        removeSlowdown(player);
        if (drag != null) {
            broadcast(level, DragSyncPayload.ended(player.getUUID()));
        }
    }

    public static void stopAll() {
        DRAGS.clear();
    }

    /**
     * Called once per server tick for everyone dragging (players from their tick, everyone else from
     * {@link #tickOthers}): release rules and client sync.
     */
    public static void tick(ServerLevel level, LivingEntity player) {
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
        if (!(player instanceof Player) && player.onGround() && !isStandingOnCarcass(level, player, drag)) {
            drag.groundY = player.getY();
        }
        if (drag.stoodSeen != level.getGameTime()) {
            drag.stoodSeen = level.getGameTime();
            drag.stoodTicks = drag.stoodAt != null && drag.stoodAt.distanceToSqr(player.position()) < STOOD_STILL * STOOD_STILL ? drag.stoodTicks + 1 : 0;
            drag.stoodAt = player.position();
        }
        Vector3d hookWorld = subLevel.logicalPose().transformPosition(drag.anchorPlot, new Vector3d());
        Vector3d target = target(drag, player, hookWorld, 1.0);
        if (hookWorld.distance(target) > MAX_DISTANCE) {
            stop(level, player);
            return;
        }
        if (level.getGameTime() % 40 == 0) {
            broadcast(level, sync(drag));
        }
        if (level.getGameTime() % 5 == 0) {
            // a piece cut off what is on the hook (or the hooked piece cut away from its body) changes what is being hauled
            CarcassSavedData.Carcass held = CarcassSavedData.get(level).carcass(drag.carcass);
            if (held != null) {
                float now = attachedMass(container, held);
                if (Math.abs(now - drag.weight) > 1.0e-3 * Math.max(1.0F, drag.weight)) {
                    drag.weight = now;
                    applySlowdown(player, penaltyFor(drag));
                }
            }
        }
        if (level.getGameTime() % 6 == 0) {
            CarcassSavedData.Carcass dragged = CarcassSavedData.get(level).carcass(drag.carcass);
            if (dragged != null && Blood.bloody(dragged)) {
                Vector3d wound = subLevel.logicalPose().transformPosition(drag.anchorPlot, new Vector3d());
                Blood.drip(level, wound, Blood.soul(dragged));
                // a trail: now and then a drop reaches the ground and stays
                if (level.getGameTime() % 24 == 0) {
                    Blood.stain(level, wound, 1, Blood.soul(dragged));
                }
            }
        }
    }

    /**
     * Once a server tick, the drags of everyone who is not a player (hauler minions), which no player tick keeps: one
     * whose dragger is gone (unloaded, killed) lets go.
     */
    public static void tickOthers(ServerLevel level) {
        for (Drag drag : List.copyOf(DRAGS.values())) {
            LivingEntity by = drag.playerEntity;
            if (by instanceof Player) {
                continue;
            }
            if (by == null || by.isRemoved() || by.level() != level) {
                if (by == null || by.level() == level) {
                    DRAGS.remove(drag.player);
                    if (by != null) {
                        removeSlowdown(by);
                    }
                    broadcast(level, DragSyncPayload.ended(drag.player));
                }
                continue;
            }
            tick(level, by);
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
            LivingEntity player = drag.playerEntity != null ? drag.playerEntity : level.getPlayerByUUID(drag.player);
            if (player == null || player.level() != level) {
                continue;
            }
            ServerSubLevel subLevel = resolve(level, drag);
            if (subLevel == null) {
                continue;
            }
            if (isStandingOnCarcass(level, player, drag)) {
                continue; // no pulling the ground out from under your own feet, or riding it
            }
            boolean against = isAgainstPlayer(subLevel, player);
            pull(drag, subLevel, player, partial, timeStep, physics, against);
            if (!against) {
                aim(level, drag, subLevel, player, partial, timeStep, physics);
            }
            steadyTurn(level, drag, timeStep, physics);
            physics.getPipeline().wakeUp(subLevel);
        }
    }

    /**
     * Whether a body belongs to the carcass this entity drags. Such a body never pushes its own dragger: Sable leaves it
     * out when it moves them (SubLevelEntityCollisionMixin), so they walk through it as through tall grass. Pushed by it,
     * a player walking on into what they held in front of them was carried along by it, each step it carried them moved
     * the point it was pulled to on, and it carried them some twenty blocks after they had stopped (and could climb onto
     * them and lift them). On the server it goes by the drag itself; on a client, where only the hooked body is known,
     * by the carcass its cells say they belong to.
     */
    public static boolean isDraggedBy(net.minecraft.world.entity.Entity entity, SubLevel body) {
        if (!(entity instanceof LivingEntity)) {
            return false;
        }
        net.minecraft.world.level.Level level = entity.level();
        if (level instanceof ServerLevel server) {
            Drag drag = DRAGS.get(entity.getUUID());
            if (drag == null) {
                return false;
            }
            if (drag.subLevel.equals(body.getUniqueId())) {
                return true;
            }
            CarcassSavedData.Carcass carcass = CarcassSavedData.get(server).carcassOfSubLevel(body.getUniqueId());
            return carcass != null && carcass.id.equals(drag.carcass);
        }
        com.avicagan.bloodandbones.client.ClientDragState.Drag drag = com.avicagan.bloodandbones.client.ClientDragState.all().get(entity.getUUID());
        if (drag == null) {
            return false;
        }
        if (drag.subLevel().equals(body.getUniqueId())) {
            return true;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        SubLevel hooked = container == null ? null : container.getSubLevel(drag.subLevel());
        UUID dragged = hooked == null ? null : carcassOf(level, hooked);
        return dragged != null && dragged.equals(carcassOf(level, body));
    }

    /** The bodies given, less any of the carcass this entity drags (all of them, as given, when it drags nothing). */
    public static Iterable<SubLevel> withoutWhatTheyDrag(net.minecraft.world.entity.Entity entity, Iterable<SubLevel> bodies) {
        boolean drags = entity.level().isClientSide ? com.avicagan.bloodandbones.client.ClientDragState.all().containsKey(entity.getUUID())
                : DRAGS.containsKey(entity.getUUID());
        if (!drags) {
            return bodies;
        }
        List<SubLevel> kept = new java.util.ArrayList<>();
        for (SubLevel body : bodies) {
            if (!isDraggedBy(entity, body)) {
                kept.add(body);
            }
        }
        return kept;
    }

    /** The carcass a body's cells say they belong to (its root cell, at the middle of its plot), on either side. */
    @Nullable
    private static UUID carcassOf(net.minecraft.world.level.Level level, SubLevel body) {
        return level.getBlockEntity(body.getPlot().getCenterBlock()) instanceof CarcassPartBlockEntity cell ? cell.carcassId() : null;
    }

    /** The hooked body is already touching the player: pulling any harder would only shove them. */
    private static boolean isAgainstPlayer(ServerSubLevel subLevel, LivingEntity player) {
        return subLevel.boundingBox().intersects(player.getBoundingBox().inflate(0.15));
    }

    /** The dragger's feet, across all the width it stands on (a broad hauler's reach well past a player's), are on (or in) one of the carcass's bodies. */
    private static boolean isStandingOnCarcass(ServerLevel level, LivingEntity player, Drag drag) {
        if (!player.onGround()) {
            return false;
        }
        CarcassSavedData.Carcass carcass = CarcassSavedData.get(level).carcassOfSubLevel(drag.subLevel);
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (carcass == null || container == null) {
            return false;
        }
        double half = player.getBbWidth() / 2.0;
        net.minecraft.world.phys.AABB feet = new net.minecraft.world.phys.AABB(player.getX() - half, player.getY() - 0.2, player.getZ() - half,
                player.getX() + half, player.getY() + 0.1, player.getZ() + half);
        for (UUID id : carcass.bones.values()) {
            SubLevel bone = container.getSubLevel(id);
            if (bone instanceof ServerSubLevel serverBone && !serverBone.isRemoved() && serverBone.boundingBox().intersects(feet)) {
                return true;
            }
        }
        return false;
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
     * <p>
     * While the hooked body touches its dragger ({@code against}), the spring never pulls it further into them, which
     * would only shove them; but its damping stays, and so does a spring that pushes it back out to arm's length. With
     * everything let go the moment it touched, a carcass pulled in towards a dragger who had stopped slid on into them
     * and lay against them short of where it was pulled to (meatHookDragsByLeg, about 1 run in 10). Since what someone
     * drags no longer collides with them, it can slide on into them, and with only the damping left it then lay there,
     * in them, for good (meatHookDragsByBody, 0.76 blocks short, about 1 run in 25): so once they have stood still a
     * moment, the spring draws it back out to arm's length. Not while they move: drawn out from someone walking past it,
     * a cow dragged by a hind leg the way its head points was pushed off round the wrong way and did not come round
     * rear first (hindLegHookComesRoundRearFirst, 4 runs in 30).
     */
    private static void pull(Drag drag, ServerSubLevel subLevel, LivingEntity player, double partial, double timeStep, SubLevelPhysicsSystem physics,
                             boolean against) {
        RigidBodyHandle handle = physics.getPhysicsHandle(subLevel);
        Pose3d pose = subLevel.logicalPose();
        Vector3d hook = pose.transformPosition(drag.anchorPlot, new Vector3d());
        Vector3d target = target(drag, player, hook, partial);
        // velocity of the hooked point: body velocity plus spin about the center of mass
        Vector3d linear = handle.getLinearVelocity(new Vector3d());
        Vector3d angular = handle.getAngularVelocity(new Vector3d());
        Vector3d arm = new Vector3d(hook).sub(pose.position());
        Vector3d hookVelocity = new Vector3d(angular).cross(arm).add(linear);

        double weight = Math.max(0.05, drag.weight);
        double stiffness = STIFFNESS * weight;
        double damping = DAMPING * weight;
        double maxForce = MAX_FORCE * weight;
        double gap = target.distance(hook);
        if (gap < 0.6) {
            damping *= 2.0; // settle instead of overshooting into the player
        }
        Vector3d force;
        Vector3d fromDragger = new Vector3d(hook.x - player.getX(), 0.0, hook.z - player.getZ());
        Vector3d out = new Vector3d(target).sub(hook);
        if (against && drag.stoodTicks >= STILL_BEFORE_OUT && out.x * fromDragger.x + out.z * fromDragger.z > 0.0) {
            // lying in someone standing still: back out to arm's length, away from them
            force = out.mul(stiffness).sub(new Vector3d(hookVelocity).mul(damping));
        } else if (against) {
            // no pull at all, only the damping, of how fast it closes on its dragger as they move (a tick's step, a second's worth)
            Vector3d dragger = new Vector3d(player.getX() - player.xo, player.getY() - player.yo, player.getZ() - player.zo).mul(20.0);
            force = new Vector3d(hookVelocity).sub(dragger).mul(-damping);
        } else {
            force = new Vector3d(target).sub(hook).mul(stiffness).sub(new Vector3d(hookVelocity).mul(damping));
        }
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

    /** Torque per unit mass turning the hooked limb to point at the hand, and killing its swing. */
    private static final double AIM_STIFFNESS = 25.0;
    private static final double AIM_DAMPING = 6.0;
    /** Share of the carcass's turning (about the upright) that a drag takes away each second, at its torso. */
    private static final double TURN_DAMPING = 6.0;

    /**
     * The damping to go with the aim spring's reach. The spring is sized by all the mass on the hook, so once the limb is
     * at its joint's limit it swings the whole carcass round; its damping kills only the limb's own swing, and the
     * carcass's turn went almost undamped. A cow dragged by a hind leg the way its head pointed came round fast, swung on
     * past rear first and rocked there, or came to lie crosswise: now and then it was still 60 to 100 degrees off the way
     * it went while dragged (hindLegHookComesRoundRearFirst, about 1 run in 40 to 80). Sized by the whole carcass at the
     * limb, the damping would fling the light limb about while it swings free within its joint, so the turn is damped where
     * the carcass carries it, at its torso, and only about the upright, so it still tumbles and rolls as it is pulled.
     * Repeated 80 times, the worst run of each drag test is now 30 degrees off, not 103.
     */
    private static void steadyTurn(ServerLevel level, Drag drag, double timeStep, SubLevelPhysicsSystem physics) {
        CarcassSavedData.Carcass carcass = CarcassSavedData.get(level).carcass(drag.carcass);
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        UUID root = carcass == null ? null : carcass.bones.get(carcass.rootBone);
        if (root == null || container == null || !(container.getSubLevel(root) instanceof ServerSubLevel torso) || torso.isRemoved()) {
            return;
        }
        Vector3d turning = physics.getPhysicsHandle(torso).getAngularVelocity(new Vector3d());
        double share = Math.min(0.5, TURN_DAMPING * timeStep);
        physics.getPipeline().addLinearAndAngularVelocity(torso, new Vector3d(), new Vector3d(0.0, -turning.y * share, 0.0));
    }

    /**
     * The grabbed limb leads: a torque spring turns it so the line from its own joint to the hook points at
     * the tether target, and angular damping stops it flailing about the joint. The rest of the body then follows
     * the limb through the joints, and the spring, sized by the whole carcass, turns the body with it once the limb
     * reaches its joint's limit. The torso has no joint above it and only gets the damping.
     */
    private static void aim(ServerLevel level, Drag drag, ServerSubLevel subLevel, LivingEntity player, double partial, double timeStep, SubLevelPhysicsSystem physics) {
        RigidBodyHandle handle = physics.getPhysicsHandle(subLevel);
        Pose3d pose = subLevel.logicalPose();
        double mass = Math.max(0.02, subLevel.getMassTracker().getMass());
        Vector3d angular = handle.getAngularVelocity(new Vector3d());
        Vector3d torque = new Vector3d(angular).mul(-AIM_DAMPING * mass);

        CarcassSavedData.Carcass carcass = CarcassSavedData.get(level).carcass(drag.carcass);
        CarcassJoints.Spec spec = null;
        if (carcass != null) {
            for (CarcassJoints.Spec joint : carcass.joints) {
                if (joint.child().equals(drag.bone)) {
                    spec = joint;
                    break;
                }
            }
        }
        if (spec != null) {
            Vector3d joint = pose.transformPosition(spec.anchorChild(subLevel), new Vector3d());
            Vector3d hook = pose.transformPosition(drag.anchorPlot, new Vector3d());
            Vector3d now = new Vector3d(hook).sub(joint);
            Vector3d want = target(drag, player, hook, partial).sub(joint);
            if (now.lengthSquared() > 1.0e-4 && want.lengthSquared() > 1.0e-4) {
                now.normalize();
                want.normalize();
                Vector3d axis = new Vector3d(now).cross(want);
                double angle = Math.acos(Math.max(-1.0, Math.min(1.0, now.dot(want))));
                if (axis.lengthSquared() > 1.0e-8) {
                    axis.normalize();
                    // sized by all that hangs on the hook, not the limb alone: past its joint's limit the turn carries on into
                    // the body, so the hooked limb swings the carcass round behind it (sized by the limb, it could only swing
                    // itself, and a carcass dragged by a hind leg went on head first)
                    torque.add(new Vector3d(axis).mul(angle * AIM_STIFFNESS * Math.max(mass, drag.weight)));
                }
            }
        }
        Vector3d impulse = torque.mul(timeStep);
        pose.orientation().transformInverse(impulse); // local frame
        handle.applyLinearAndAngularImpulse(new Vector3d(), impulse);
    }

    /** How little a dragger may move in a tick and still stand still, and how many ticks before a body in them is drawn out. */
    private static final double STOOD_STILL = 0.01;
    private static final int STILL_BEFORE_OUT = 10;

    /** How far round from straight ahead a hook may be and still be held along the look, and past which it trails. */
    private static final double HELD_AHEAD = 0.5;
    private static final double TRAILS_BEHIND = -0.2;

    /**
     * Where the hook is pulled to. A player facing what they have hooked holds it a little in front of their feet, along
     * their look, so they can aim it (lift it onto a rack, swing it under a hook). A player walking away from it drags it:
     * it trails at arm's length back along the line to the hook, at hand height, so the hooked part leads and the rest of
     * the body comes round behind it (hooked by a hind leg it comes round rear first; by the head, head first). In
     * between, the two blend, so turning round with it never jerks it across. Anyone else (a hauler) always drags it
     * that way, low down over the ground it walks on (not over its feet as they leave the ground in a hop).
     * <p>
     * Held always in front, as it once was, a carcass walked away from was pulled towards a point past its dragger's feet,
     * so it bumped along against their heels (the drag holds off while it touches them) and never came round.
     */
    private static Vector3d target(@Nullable Drag drag, LivingEntity player, Vector3d hook, double partial) {
        double px = Mth.lerp(partial, player.xo, player.getX());
        double py = Mth.lerp(partial, player.yo, player.getY());
        double pz = Mth.lerp(partial, player.zo, player.getZ());
        double trail = HOLD_DISTANCE + player.getBbWidth() / 2.0;
        Vec3 back = new Vec3(hook.x - px, 0.0, hook.z - pz);
        if (!(player instanceof Player)) {
            if (drag != null && !Double.isNaN(drag.groundY)) {
                py = Math.min(py, drag.groundY);
            }
            if (back.lengthSqr() < 1.0e-4) {
                back = Vec3.directionFromRotation(0.0F, player.yBodyRot + 180.0F);
            }
            back = back.normalize().scale(trail);
            return new Vector3d(px + back.x, py + Math.min(0.7, player.getBbHeight() * 0.5), pz + back.z);
        }
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        if (flat.lengthSqr() < 1.0e-4) {
            flat = Vec3.directionFromRotation(0.0F, player.getYRot());
        }
        flat = flat.normalize();
        // how much the hook lies ahead of them: 1 straight ahead, -1 straight behind
        double ahead = back.lengthSqr() < 1.0e-4 ? 1.0 : flat.dot(back.normalize());
        double held = Math.max(0.0, Math.min(1.0, (ahead - TRAILS_BEHIND) / (HELD_AHEAD - TRAILS_BEHIND)));
        Vec3 way = held >= 1.0 || back.lengthSqr() < 1.0e-4 ? flat : back.normalize().scale(1.0 - held).add(flat.scale(held));
        if (way.lengthSqr() < 1.0e-4) {
            way = flat;
        }
        way = way.normalize().scale(HOLD_DISTANCE * held + trail * (1.0 - held));
        double y = py + 0.7 + held * Math.max(-0.4, Math.min(0.6, look.y));
        return new Vector3d(px + way.x, y, pz + way.z);
    }

    /** Where this dragger's hook is pulled to now (for tests): as the drag has it, from where the hooked point is. */
    public static Vector3d debugTarget(LivingEntity player) {
        Drag drag = DRAGS.get(player.getUUID());
        Vector3d hook = new Vector3d(player.getX(), player.getY(), player.getZ()).add(player.getLookAngle().x, 0.0, player.getLookAngle().z);
        if (drag != null && player.level() instanceof ServerLevel level) {
            ServerSubLevel held = resolve(level, drag);
            if (held != null) {
                hook = held.logicalPose().transformPosition(drag.anchorPlot, new Vector3d());
            }
        }
        return target(drag, player, hook, 1.0);
    }

    /**
     * A whole chicken's mass and a whole ravager's, the two ends the brief gives the penalty for, and their penalties: the
     * defaults of the server config's drag_light_* and drag_heavy_*, which are read.
     */
    public static final double CHICKEN_MASS = 0.127;
    public static final double RAVAGER_MASS = 5.98;
    public static final double CHICKEN_PENALTY = 0.05;
    public static final double RAVAGER_PENALTY = 0.55;

    /**
     * The share of walking speed lost while dragging this much mass (the bodies actually on the hook, so a severed leg
     * costs what a leg weighs, not what its cow did): the brief's "roughly 5% for a chicken up to 55% for a ravager".
     * Between the two it rises with the logarithm of the mass, so each doubling costs the same few points more (a cow
     * about 29%, a horse 37%, an iron golem, of plate, 47%); below a chicken it falls in proportion to the mass (a rabbit
     * about 1%), and nothing costs more than a ravager. It moves onto weight classes when those come (docs/BRIEF-AUDIT.md
     * package 2).
     */
    public static float penaltyFor(double mass) {
        // the two ends are the server config's (drag_light_* and drag_heavy_*), these constants their defaults
        double light = com.avicagan.bloodandbones.config.BBServerConfig.dragLightMass();
        double lightPenalty = com.avicagan.bloodandbones.config.BBServerConfig.dragLightPenalty();
        double heavy = Math.max(light * 1.0001, com.avicagan.bloodandbones.config.BBServerConfig.dragHeavyMass());
        double heavyPenalty = com.avicagan.bloodandbones.config.BBServerConfig.dragHeavyPenalty();
        if (mass <= light) {
            return (float) (lightPenalty * Math.max(0.0, mass) / light);
        }
        double along = Math.log(mass / light) / Math.log(heavy / light);
        return (float) Math.min(heavyPenalty, lightPenalty + (heavyPenalty - lightPenalty) * along);
    }

    /** What this drag costs: the curve at the mass on its hook, times its weight class's drag. */
    private static float penaltyFor(Drag drag) {
        return penaltyFor(drag.weight, drag.classDrag);
    }

    /** The curve at a mass, times a weight class's drag. */
    public static float penaltyFor(double mass, float classDrag) {
        return Math.min(1.0F, penaltyFor(mass) * classDrag);
    }

    /** The penalty the dragger has now, before their drag strength eases it; 0 when they drag nothing. */
    public static float penalty(LivingEntity player) {
        Drag drag = DRAGS.get(player.getUUID());
        return drag == null ? 0.0F : penaltyFor(drag);
    }

    private static void applySlowdown(LivingEntity player, float penalty) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        speed.removeModifier(SLOWDOWN_ID);
        // a strong player (the drag strength traits give) feels less of it; a hauler minion by its own rule (its fitness)
        AttributeInstance strength = player.getAttribute(com.avicagan.bloodandbones.registry.BBAttributes.DRAG_STRENGTH);
        float eased = player instanceof Dragger dragger ? dragger.dragSlowdown(penalty)
                : strength == null ? penalty : penalty * (1.0F - (float) Math.min(0.75, strength.getValue()));
        speed.addTransientModifier(new AttributeModifier(SLOWDOWN_ID, -eased, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static void removeSlowdown(LivingEntity player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SLOWDOWN_ID);
        }
    }

    static String describe(CarcassSavedData.Carcass carcass) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(BuiltInRegistries.ENTITY_TYPE.get(carcass.entity)).toString();
    }
}
