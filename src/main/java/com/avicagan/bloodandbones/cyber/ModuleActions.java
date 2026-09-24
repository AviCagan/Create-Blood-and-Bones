package com.avicagan.bloodandbones.cyber;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.body.BodyEffects;
import com.avicagan.bloodandbones.carcass.CarcassAssembler;
import com.avicagan.bloodandbones.carcass.CarcassDrag;
import com.avicagan.bloodandbones.carcass.CarcassPartBlockEntity;
import com.avicagan.bloodandbones.carcass.CarcassRest;
import com.avicagan.bloodandbones.carcass.CarcassSavedData;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.avicagan.bloodandbones.carcass.rig.RigManager;
import com.avicagan.bloodandbones.registry.BBSounds;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * What each module does. Every one has a cheap baseline (a tap of the throttle, or always on) and a ramp
 * the throttle buys with soul blood; see {@link Throttle}.
 */
public final class ModuleActions {
    // ---- Piston Ram
    /** Knockback of a tap, and what full spool adds. */
    public static final float RAM_KNOCKBACK = 1.5F;
    public static final float RAM_KNOCKBACK_RAMP = 3.5F;
    public static final float RAM_DAMAGE = 2.0F;
    public static final float RAM_DAMAGE_RAMP = 6.0F;
    /** Upward speed, blocks a tick, of a blow to the ground: a tap is a big hop, full spool about fifteen blocks. */
    public static final double LAUNCH = 0.6;
    public static final double LAUNCH_RAMP = 1.2;
    /** How steeply down the look must be for a ram to strike the ground (degrees below level). */
    public static final float DOWNWARD = 50.0F;

    // ---- Barometric Vent
    /** Ticks of hover for a tap, and what full spool adds (kept under the four seconds a server lets anyone float). */
    public static final int HOVER_TICKS = 20;
    public static final int HOVER_RAMP = 50;
    /** How fast a hover sinks, blocks a tick: just over what the server counts as floating. */
    public static final double HOVER_SINK = 0.04;
    /** The puff of lift when the vent fires. */
    public static final double HOVER_LIFT = 0.35;

    // ---- Gyroscopic Stabilizer
    /** mB of soul blood for each block fallen past the safe distance. */
    public static final int STABILIZER_MB_PER_BLOCK = 10;

    // ---- Magnet Coil
    /** Blocks of pull always on, and what full spool adds. */
    public static final double MAGNET_RADIUS = 3.0;
    public static final double MAGNET_RAMP = 13.0;
    /** The spool at which the coil takes hold of carcasses. */
    public static final float MAGNET_CARCASS = 0.75F;

    // ---- Grappling Spool
    public static final double GRAPPLE_RANGE = 12.0;
    public static final double GRAPPLE_RANGE_RAMP = 20.0;
    /** Reel speed, blocks a tick. */
    public static final double REEL = 0.9;
    public static final double REEL_RAMP = 1.2;
    /**
     * What a player can pull: a mob no bigger than this many of a player's volume, or a carcass whose rig
     * weighs no more than this. Anything heavier pulls the player to it instead.
     */
    public static final double LIGHT_VOLUME = 1.5;
    public static final float LIGHT_CARCASS = 1.5F;
    /** Reel speed above which hitting a wall hurts. */
    public static final double SLAM_SPEED = 0.8;

    /** A player on a reel (pulled to what they hooked) or hovering. */
    static final class Motion {
        final boolean hover;
        final long start;
        final long until;
        @Nullable final Entity anchorEntity;
        final Vec3 anchor;
        final double speed;

        Motion(boolean hover, long start, long until, @Nullable Entity anchorEntity, Vec3 anchor, double speed) {
            this.hover = hover;
            this.start = start;
            this.until = until;
            this.anchorEntity = anchorEntity;
            this.anchor = anchor;
            this.speed = speed;
        }

        Vec3 anchor() {
            return anchorEntity != null ? anchorEntity.getBoundingBox().getCenter() : anchor;
        }
    }

    /** Something a player hooked and is reeling in: a mob or a carcass. */
    static final class Haul {
        @Nullable final LivingEntity mob;
        @Nullable final UUID carcass;
        @Nullable final BlockPos plot;
        final double speed;
        final long until;

        Haul(@Nullable LivingEntity mob, @Nullable UUID carcass, @Nullable BlockPos plot, double speed, long until) {
            this.mob = mob;
            this.carcass = carcass;
            this.plot = plot;
            this.speed = speed;
            this.until = until;
        }
    }

    private static final Map<Player, Motion> MOTIONS = new WeakHashMap<>();
    private static final Map<Player, Haul> HAULS = new WeakHashMap<>();

    private ModuleActions() {
    }

    /** A firing module let go at this spool. */
    static void fire(Player player, Module module, float level) {
        switch (module) {
            case PISTON_RAM -> pistonRam(player, level);
            case BAROMETRIC_VENT -> vent(player, level);
            case GRAPPLING_SPOOL -> grapple(player, level);
            default -> {
            }
        }
    }

    /** A held module, every tick while held. */
    static void hold(Player player, Module module, float level) {
        switch (module) {
            case MAGNET_COIL -> magnet(player, MAGNET_RADIUS + MAGNET_RAMP * level, 0.25 + 0.5 * level, level >= MAGNET_CARCASS);
            case ROTATIONAL_COUPLER -> Coupler.hold(player, level);
            default -> {
            }
        }
    }

    /** A held module let go. */
    static void stop(Player player, Module module) {
        if (module == Module.ROTATIONAL_COUPLER) {
            Coupler.stop(player);
        }
    }

    /** Every tick, for every player: the always-on modules, and any reel or hover under way. */
    public static void tick(Player player) {
        if (player.tickCount % 4 == 0 && Throttle.spooling(player) != Module.MAGNET_COIL && Modules.has(player, Module.MAGNET_COIL)) {
            magnet(player, MAGNET_RADIUS, 0.2, false);
        }
        Motion motion = MOTIONS.get(player);
        if (motion != null) {
            moveOnServer(player, motion);
        }
        Haul haul = HAULS.get(player);
        if (haul != null) {
            haul(player, haul);
        }
    }

    // ---- Piston Ram

    static void pistonRam(Player player, float level) {
        ServerLevel world = ((ServerLevel) player.level());
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double reach = player.entityInteractionRange() + 1.0;
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, eye.add(look.scale(reach)),
                player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0),
                e -> e.isPickable() && !e.isSpectator() && e != player && e != player.getVehicle(), reach * reach);
        if (hit != null && hit.getEntity() instanceof LivingEntity target && mayHit(player, target)) {
            target.hurt(player.damageSources().playerAttack(player), RAM_DAMAGE + RAM_DAMAGE_RAMP * level);
            target.knockback(RAM_KNOCKBACK + RAM_KNOCKBACK_RAMP * level, -look.x, -look.z);
            target.setDeltaMovement(target.getDeltaMovement().add(0.0, 0.15 + 0.3 * level, 0.0));
            target.hurtMarked = true;
            Vec3 at = hit.getLocation();
            world.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            world.playSound(null, player.blockPosition(), SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 1.0F, 0.6F + 0.4F * level);
            world.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.8F);
            return;
        }
        BlockHitResult ground = world.clip(new ClipContext(eye, eye.add(look.scale(player.getBbHeight() + 1.2)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        if (player.getXRot() >= DOWNWARD && ground.getType() == HitResult.Type.BLOCK) {
            // a blow to the ground throws you up, and a little the way you face
            double up = LAUNCH + LAUNCH_RAMP * level;
            Vec3 flat = new Vec3(look.x, 0.0, look.z);
            Vec3 push = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize().scale(0.15 + 0.35 * level);
            player.setDeltaMovement(player.getDeltaMovement().x + push.x, up, player.getDeltaMovement().z + push.z);
            player.hurtMarked = true;
            player.resetFallDistance();
            Vec3 at = ground.getLocation();
            world.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.1, at.z, 1, 0, 0, 0, 0);
            world.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.1, at.z, 12, 0.4, 0.05, 0.4, 0.05);
            world.playSound(null, player.blockPosition(), SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 1.0F, 0.5F + 0.3F * level);
            world.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.3F + 0.4F * level, 1.6F);
            return;
        }
        // a blow at nothing: the piston kicks, and that is all
        world.playSound(null, player.blockPosition(), SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 0.8F, 0.9F);
    }

    /** Other players only where the server allows fighting. */
    private static boolean mayHit(Player player, Entity target) {
        return !(target instanceof Player) || player.getServer() != null && player.getServer().isPvpAllowed() && player.canHarmPlayer((Player) target);
    }

    // ---- Barometric Vent

    static void vent(Player player, float level) {
        long now = player.level().getGameTime();
        Motion motion = new Motion(true, now, now + HOVER_TICKS + Math.round(HOVER_RAMP * level), null, Vec3.ZERO, 0.0);
        MOTIONS.put(player, motion);
        player.setDeltaMovement(player.getDeltaMovement().x, Math.max(player.getDeltaMovement().y, HOVER_LIFT), player.getDeltaMovement().z);
        player.hurtMarked = true;
        player.resetFallDistance();
        player.level().playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.5F, 1.8F);
        ((ServerLevel) player.level()).sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 10, 0.3, 0.05, 0.3, 0.02);
        send(player, motion);
    }

    /** Whether a hover is holding this player up now. */
    public static boolean hovering(Player player) {
        Motion motion = MOTIONS.get(player);
        return motion != null && motion.hover && player.level().getGameTime() < motion.until;
    }

    /**
     * One tick of a hover or reel, on whichever side moves the player (their own client; the server for a
     * test player). A hover lets you drift down slowly and never builds up a fall; a reel flies you at the
     * anchor.
     *
     * @return false once it is over
     */
    public static boolean move(Player player, boolean hover, long start, long until, Vec3 anchor, double speed) {
        long now = player.level().getGameTime();
        if (now >= until || player.isSpectator() || !player.isAlive()) {
            return false;
        }
        if (hover) {
            // landing ends it (not in the first moments, while the puff lifts you off), and so does crouching
            if (player.onGround() && now > start + 5 || player.isShiftKeyDown()) {
                return false;
            }
            Vec3 v = player.getDeltaMovement();
            if (v.y < -HOVER_SINK) {
                player.setDeltaMovement(v.x, -HOVER_SINK, v.z);
            }
            player.resetFallDistance();
            return true;
        }
        Vec3 to = anchor.subtract(player.position().add(0.0, player.getBbHeight() * 0.5, 0.0));
        if (to.length() < 1.5) {
            return false;
        }
        player.setDeltaMovement(to.normalize().scale(speed));
        player.resetFallDistance();
        return true;
    }

    private static void moveOnServer(Player player, Motion motion) {
        Vec3 before = player.getDeltaMovement();
        boolean going = move(player, motion.hover, motion.start, motion.until, motion.anchor(), motion.speed);
        if (!motion.hover && going && player.horizontalCollision && before.horizontalDistance() > SLAM_SPEED) {
            // reeled into a wall at speed: it hurts, as flying into one does
            player.hurt(player.damageSources().flyIntoWall(), (float) (before.horizontalDistance() * 10.0 - 3.0));
            going = false;
        }
        if (motion.anchorEntity != null && (!motion.anchorEntity.isAlive() || motion.anchorEntity.level() != player.level())) {
            going = false;
        }
        if (!going) {
            MOTIONS.remove(player);
            send(player, null);
        }
    }

    // ---- Gyroscopic Stabilizer

    /** A fall is paid for in soul blood by the height; what the tank cannot pay for, you take. */
    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide || !Modules.has(player, Module.GYROSCOPIC_STABILIZER)) {
            return;
        }
        double blocks = event.getDistance() - player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
        if (blocks <= 0.0) {
            return;
        }
        int cost = (int) Math.ceil(blocks * STABILIZER_MB_PER_BLOCK * Throttle.efficiency(player));
        int paid = Throttle.fuelled(player) ? BodyEffects.take(player, cost) : 0;
        float unpaid = 1.0F - paid / (float) cost;
        event.setDamageMultiplier(event.getDamageMultiplier() * unpaid);
        if (paid > 0) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.PISTON_CONTRACT, SoundSource.PLAYERS, 0.8F, 1.6F);
            ((ServerLevel) player.level()).sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 0.1, player.getZ(), 6, 0.3, 0.05, 0.3, 0.02);
        }
    }

    // ---- Magnet Coil

    static void magnet(Player player, double radius, double pull, boolean carcasses) {
        Vec3 center = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
        AABB box = player.getBoundingBox().inflate(radius);
        for (Entity e : player.level().getEntities(player, box, e -> e instanceof ItemEntity item && !item.hasPickUpDelay() || e instanceof ExperienceOrb)) {
            Vec3 to = center.subtract(e.position());
            double distance = to.length();
            if (distance > radius || distance < 0.3) {
                continue;
            }
            e.setDeltaMovement(e.getDeltaMovement().scale(0.6).add(to.normalize().scale(pull)));
            e.hurtMarked = true;
        }
        if (carcasses) {
            pullCarcasses(player, center, radius);
        }
    }

    /** At the top of the throttle the coil takes hold of carcasses in reach and draws them in. */
    private static void pullCarcasses(Player player, Vec3 center, double radius) {
        ServerLevel level = ((ServerLevel) player.level());
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }
        for (CarcassSavedData.Carcass carcass : java.util.List.copyOf(CarcassSavedData.get(level).all())) {
            Vector3d at = CarcassAssembler.boneWorldPosition(level, carcass, carcass.rootBone);
            if (at == null || at.distance(center.x, center.y, center.z) > radius || at.distance(center.x, center.y, center.z) < 2.0) {
                continue;
            }
            nudge(level, container, carcass, new Vec3(at.x, at.y, at.z), center, 0.12);
        }
    }

    /** Speed a carcass up toward a point, all its bodies alike (a resting one unfolds first). */
    private static boolean nudge(ServerLevel level, ServerSubLevelContainer container, CarcassSavedData.Carcass carcass, Vec3 from, Vec3 to, double speed) {
        if (carcass.resting && CarcassRest.split(level, carcass) == null) {
            return false;
        }
        Vec3 dir = to.subtract(from).normalize().scale(speed);
        for (UUID id : carcass.bones.values()) {
            if (container.getSubLevel(id) instanceof ServerSubLevel body && !body.isRemoved()) {
                container.physicsSystem().getPhysicsHandle(body).addLinearAndAngularVelocity(new Vector3d(dir.x, dir.y + 0.02, dir.z), new Vector3d());
                container.physicsSystem().getPipeline().wakeUp(body);
            }
        }
        return true;
    }

    // ---- Grappling Spool

    /**
     * Fire the hook along the look. A light thing (a small mob, a light carcass) is reeled in to you; anything
     * heavy (a block, a big mob, a heavy carcass) reels you in to it, whatever it is: hooking a ravager is a
     * bad idea, and a wall at speed hurts.
     */
    static void grapple(Player player, float level) {
        ServerLevel world = ((ServerLevel) player.level());
        double range = GRAPPLE_RANGE + GRAPPLE_RANGE_RAMP * level;
        double speed = REEL + REEL_RAMP * level;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult block = world.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 blockAt = block.getType() == HitResult.Type.BLOCK ? worldPoint(world, block) : null;
        double blockDistance = blockAt == null ? range : blockAt.distanceTo(eye);
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(player, eye, eye.add(look.scale(blockDistance)),
                player.getBoundingBox().expandTowards(look.scale(blockDistance)).inflate(1.0),
                e -> e.isPickable() && !e.isSpectator() && e != player && e != player.getVehicle(), blockDistance * blockDistance);
        long until = world.getGameTime() + (long) Math.ceil(range / speed) + 20;
        world.playSound(null, player.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.0F, 0.7F + 0.3F * level);
        if (entity != null && mayHit(player, entity.getEntity())) {
            Entity target = entity.getEntity();
            if (target instanceof LivingEntity mob && light(player, mob)) {
                HAULS.put(player, new Haul(mob, null, null, speed, until));
                tether(player, target, target.position(), until);
            } else {
                reel(player, target, target.getBoundingBox().getCenter(), speed, until);
            }
            world.playSound(null, target.blockPosition(), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 1.0F, 0.8F);
            return;
        }
        if (blockAt == null) {
            // nothing in reach: the line runs out and comes back
            tether(player, null, end, world.getGameTime() + 6);
            return;
        }
        if (world.getBlockEntity(block.getBlockPos()) instanceof CarcassPartBlockEntity part && part.carcassId() != null) {
            CarcassSavedData.Carcass carcass = CarcassSavedData.get(world).carcass(part.carcassId());
            float weight = carcass == null ? Float.MAX_VALUE : RigManager.forCarcass(carcass).map(Rig::weight).orElse(Float.MAX_VALUE);
            if (carcass != null && weight <= LIGHT_CARCASS) {
                HAULS.put(player, new Haul(null, carcass.id, block.getBlockPos(), speed * 0.5, until + 20));
                tether(player, null, blockAt, until + 20);
                world.playSound(null, BlockPos.containing(blockAt), BBSounds.CARCASS_CUT.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
                return;
            }
        }
        reel(player, null, blockAt, speed, until);
        world.playSound(null, BlockPos.containing(blockAt), SoundEvents.TRIDENT_HIT_GROUND, SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    /** Where a block hit is, in the world (a hit on a carcass or ship comes back in its own space). */
    private static Vec3 worldPoint(ServerLevel level, BlockHitResult hit) {
        SubLevel sub = Sable.HELPER.getContaining(level, hit.getBlockPos());
        if (sub == null) {
            return hit.getLocation();
        }
        Vector3d at = sub.logicalPose().transformPosition(new Vector3d(hit.getLocation().x, hit.getLocation().y, hit.getLocation().z), new Vector3d());
        return new Vec3(at.x, at.y, at.z);
    }

    /** Whether a player can pull this mob in: no bulkier than half again a player. */
    static boolean light(Player player, LivingEntity mob) {
        double own = player.getBbWidth() * player.getBbWidth() * player.getBbHeight();
        double theirs = mob.getBbWidth() * mob.getBbWidth() * mob.getBbHeight();
        return theirs <= own * LIGHT_VOLUME;
    }

    private static void reel(Player player, @Nullable Entity anchorEntity, Vec3 anchor, double speed, long until) {
        Motion motion = new Motion(false, player.level().getGameTime(), until, anchorEntity, anchor, speed);
        MOTIONS.put(player, motion);
        send(player, motion);
    }

    /** Draw the line from the arm to what it hooked, for everyone who sees it. */
    private static void tether(Player player, @Nullable Entity anchorEntity, Vec3 anchor, long until) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new MotionPayload(player.getId(), MotionPayload.TETHER, player.level().getGameTime(), until,
                anchorEntity == null ? -1 : anchorEntity.getId(), anchor.x, anchor.y, anchor.z, 0.0));
    }

    /** A mob or carcass being reeled in, every tick until it arrives. */
    private static void haul(Player player, Haul haul) {
        ServerLevel level = ((ServerLevel) player.level());
        Vec3 to = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
        boolean done = level.getGameTime() >= haul.until;
        if (haul.mob != null) {
            Vec3 diff = to.subtract(haul.mob.position());
            if (!haul.mob.isAlive() || haul.mob.level() != level || diff.length() < 2.0) {
                done = true;
            } else {
                haul.mob.setDeltaMovement(diff.normalize().scale(haul.speed).add(0.0, 0.08, 0.0));
                haul.mob.hurtMarked = true;
                haul.mob.resetFallDistance();
            }
        } else if (haul.carcass != null) {
            CarcassSavedData.Carcass carcass = CarcassSavedData.get(level).carcass(haul.carcass);
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            Vector3d at = carcass == null ? null : CarcassAssembler.boneWorldPosition(level, carcass, carcass.rootBone);
            if (carcass == null || container == null || at == null) {
                done = true;
            } else if (at.distance(to.x, to.y, to.z) < 3.0) {
                // in hand: the Meat Hook's own tether takes it from here
                if (!CarcassDrag.isDragging(player) && haul.plot != null && level.getBlockEntity(haul.plot) instanceof CarcassPartBlockEntity) {
                    CarcassDrag.start(level, player, haul.plot, null);
                }
                done = true;
            } else {
                nudge(level, container, carcass, new Vec3(at.x, at.y, at.z), to, 0.25 * haul.speed);
            }
        }
        if (done) {
            HAULS.remove(player);
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, MotionPayload.ended(player.getId()));
        }
    }

    private static void send(Player player, @Nullable Motion motion) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, motion == null ? MotionPayload.ended(player.getId())
                : new MotionPayload(player.getId(), motion.hover ? MotionPayload.HOVER : MotionPayload.REEL, motion.start, motion.until,
                motion.anchorEntity == null ? -1 : motion.anchorEntity.getId(), motion.anchor.x, motion.anchor.y, motion.anchor.z, motion.speed));
    }

    /** Forget a player's reel, hover and haul (they died, left, or changed world). */
    public static void clear(Player player) {
        MOTIONS.remove(player);
        HAULS.remove(player);
    }

    /**
     * To a player and those who see them: a hover or reel began (the player's own client moves them), a
     * tether is drawn out to what the hook caught, or it all ended.
     */
    public record MotionPayload(int entity, int kind, long start, long until, int anchorEntity, double x, double y, double z, double speed) implements CustomPacketPayload {
        public static final int ENDED = 0;
        public static final int HOVER = 1;
        public static final int REEL = 2;
        public static final int TETHER = 3;
        public static final Type<MotionPayload> TYPE = new Type<>(BloodAndBones.asResource("module_motion"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MotionPayload> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public MotionPayload decode(RegistryFriendlyByteBuf buf) {
                return new MotionPayload(buf.readVarInt(), buf.readByte(), buf.readVarLong(), buf.readVarLong(), buf.readVarInt(), buf.readDouble(), buf.readDouble(),
                        buf.readDouble(), buf.readDouble());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, MotionPayload p) {
                buf.writeVarInt(p.entity);
                buf.writeByte(p.kind);
                buf.writeVarLong(p.start);
                buf.writeVarLong(p.until);
                buf.writeVarInt(p.anchorEntity);
                buf.writeDouble(p.x);
                buf.writeDouble(p.y);
                buf.writeDouble(p.z);
                buf.writeDouble(p.speed);
            }
        };

        static MotionPayload ended(int entity) {
            return new MotionPayload(entity, ENDED, 0L, 0L, -1, 0, 0, 0, 0);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
