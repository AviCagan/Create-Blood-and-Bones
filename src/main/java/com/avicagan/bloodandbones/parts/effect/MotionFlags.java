package com.avicagan.bloodandbones.parts.effect;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.config.BBServerConfig;
import com.avicagan.bloodandbones.minion.MinionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.event.EventHooks;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * What the movement flags do (docs/PARTS-AND-TRAITS.md section 5.6). A player's movement is worked out on their own
 * client, and the server only checks it, so climbing and bouncing run the same code on both sides from the armour both
 * see ({@link MotionEffects#flag}), or the server would pull the player back. Their state (a bounce owed, a cushioned
 * landing) is kept per creature object, so the two sides of a single-player game never share it.
 */
public final class MotionFlags {
    /** A clinging climber slides down no faster than this a tick. */
    public static final double CLING = 0.05;
    /** A strong climber holding jump goes up this fast a tick. */
    public static final double CLIMB = 0.2;
    /** Bouncers bounce from falls at least this long, back up this share of the way. */
    public static final float BOUNCE_FROM = 2.0F;
    public static final double BOUNCE_BACK = 0.6;
    /** Blocks a trampling minion goes straight through. */
    public static final TagKey<Block> TRAMPLEABLE = TagKey.create(Registries.BLOCK, BloodAndBones.asResource("trampleable"));

    /** The upward speed each bouncer is owed on its next tick (the landing itself zeroes it). */
    private static final Map<LivingEntity, Double> BOUNCES = Collections.synchronizedMap(new WeakHashMap<>());
    /** When each cushioned creature was thrown: no fall damage until it next lands. */
    private static final Map<LivingEntity, Long> CUSHIONED = Collections.synchronizedMap(new WeakHashMap<>());

    private MotionFlags() {
    }

    /**
     * A player's movement flags, at the start of each of their ticks, on their client and on the server alike: climbing,
     * a bounce owed, and a cushion let go once they are down.
     *
     * @param jumpHeld whether jump is held (the client knows; the server never does, which changes nothing it checks)
     */
    public static void moveTick(LivingEntity host, boolean jumpHeld) {
        climb(host, jumpHeld);
        settle(host);
    }

    /**
     * Climb (strength 1 clings, sliding down slowly; 2 or more climbs while jump is held), against any wall beside the
     * host while it is off the ground. Its fall is forgotten while it holds on.
     *
     * @return whether it held on
     */
    public static boolean climb(LivingEntity host, boolean jumpHeld) {
        int strength = MotionEffects.flag(host, FlagEffect.CLIMB);
        if (strength <= 0 || host.onGround() || host.isPassenger() || host.isSpectator() || host.isFallFlying() || host.isInWater()
                || host instanceof Player player && player.getAbilities().flying || !againstWall(host)) {
            return false;
        }
        Vec3 motion = host.getDeltaMovement();
        double y = strength >= 2 && jumpHeld ? Math.max(motion.y, CLIMB) : Math.max(motion.y, -CLING);
        host.setDeltaMovement(motion.x, y, motion.z);
        host.resetFallDistance();
        if (host.tickCount % 8 == 0) {
            // a wet, sticky pull off the wall; the climber hears it here, everyone else from the server
            host.level().playSound(host instanceof Player p ? p : null, host.getX(), host.getY(), host.getZ(), SoundEvents.HONEY_BLOCK_STEP,
                    host.getSoundSource(), 0.4F, 0.7F + host.getRandom().nextFloat() * 0.2F);
        }
        return true;
    }

    /**
     * Whether a wall stands right beside the host: a solid block just past its sides, above its feet and below its head.
     * Worked out from the world and not from the last move, since the server never moves a player itself.
     */
    public static boolean againstWall(LivingEntity host) {
        AABB box = host.getBoundingBox();
        double trim = Math.min(0.1, box.getYsize() * 0.25);
        AABB beside = new AABB(box.minX - 0.08, box.minY + trim, box.minZ - 0.08, box.maxX + 0.08, box.maxY - trim, box.maxZ + 0.08);
        return !host.level().noBlockCollision(host, beside);
    }

    /**
     * A landing a bouncer bounces from, from the fall's length alone so both sides agree: back up {@link #BOUNCE_BACK}
     * of the way, owed on its next tick.
     *
     * @return whether it bounces (and so takes no damage)
     */
    public static boolean bounce(LivingEntity host, float distance) {
        if (distance < BOUNCE_FROM || host.isSuppressingBounce() || MotionEffects.flag(host, FlagEffect.BOUNCE) <= 0) {
            return false;
        }
        // speed to rise h against gravity 0.08 a tick, a little more for the air slowing it
        double up = Math.min(1.6, Math.sqrt(2.0 * 0.08 * distance * BOUNCE_BACK) * 1.1);
        BOUNCES.put(host, up);
        Vec3 motion = host.getDeltaMovement();
        host.setDeltaMovement(motion.x, up, motion.z);
        if (host.level() instanceof ServerLevel level) {
            level.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.SLIME_BLOCK_FALL, host.getSoundSource(), 1.0F, 0.6F);
            level.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.SLIME_SQUISH, host.getSoundSource(), 0.8F, 0.5F);
        }
        return true;
    }

    /** No fall damage for this creature until it next lands. */
    public static void cushion(LivingEntity host) {
        CUSHIONED.put(host, host.level().getGameTime());
    }

    /** Whether it is cushioned now; landing uses it up. */
    public static boolean landCushioned(LivingEntity host) {
        return !CUSHIONED.isEmpty() && CUSHIONED.remove(host) != null;
    }

    /** A bounce owed goes on now, the landing having stopped it; a cushion is let go once the host is down a moment. */
    public static void settle(LivingEntity host) {
        if (!BOUNCES.isEmpty()) {
            Double up = BOUNCES.remove(host);
            if (up != null) {
                Vec3 motion = host.getDeltaMovement();
                host.setDeltaMovement(motion.x, up, motion.z);
                host.hasImpulse = true;
            }
        }
        if (!CUSHIONED.isEmpty() && host.onGround()) {
            Long since = CUSHIONED.get(host);
            if (since != null && host.level().getGameTime() - since > 10) {
                CUSHIONED.remove(host);
            }
        }
    }

    /**
     * A trampling minion breaks the trampleable blocks it walks into and through (leaves, grass, flowers...), dropping
     * them, only where the server's {@code minion_block_damage} and mobGriefing both allow it.
     *
     * @return how many it broke
     */
    public static int trample(MinionEntity minion) {
        if (!(minion.level() instanceof ServerLevel level) || minion.poweredDown() || MotionEffects.flag(minion, FlagEffect.TRAMPLE) <= 0
                || !BBServerConfig.minionBlockDamage() || !EventHooks.canEntityGrief(level, minion)) {
            return 0;
        }
        int broke = 0;
        AABB box = minion.getBoundingBox().inflate(0.2);
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            if (level.getBlockState(pos).is(TRAMPLEABLE) && level.destroyBlock(pos, true, minion)) {
                broke++;
            }
        }
        if (broke > 0) {
            level.playSound(null, minion.getX(), minion.getY(), minion.getZ(), SoundEvents.SLIME_BLOCK_STEP, minion.getSoundSource(), 0.8F, 0.6F);
        }
        return broke;
    }

    /**
     * Whether a lava-walking minion is standing on the lava's surface now: in a lava source's top half with no more
     * lava over it, as a strider stands.
     */
    public static boolean onLava(MinionEntity minion) {
        if (!minion.isInLava() || MotionEffects.flag(minion, FlagEffect.LAVA_WALK) <= 0) {
            return false;
        }
        BlockPos at = minion.blockPosition();
        return CollisionContext.of(minion).isAbove(LiquidBlock.STABLE_SHAPE, at, true) && !minion.level().getFluidState(at.above()).is(FluidTags.LAVA);
    }

    /**
     * A lava-walking minion after it moves, as a strider floats: on the surface it has its footing; sunk below it (a
     * drop, a push), it bobs back up.
     */
    public static void floatOnLava(MinionEntity minion) {
        if (!minion.isInLava() || MotionEffects.flag(minion, FlagEffect.LAVA_WALK) <= 0) {
            return;
        }
        if (onLava(minion)) {
            minion.setOnGround(true);
        } else {
            minion.setDeltaMovement(minion.getDeltaMovement().scale(0.5).add(0.0, 0.05, 0.0));
        }
    }

    /** Forget every bounce and cushion (the server stopped). */
    static void forgetAll() {
        BOUNCES.clear();
        CUSHIONED.clear();
    }
}
