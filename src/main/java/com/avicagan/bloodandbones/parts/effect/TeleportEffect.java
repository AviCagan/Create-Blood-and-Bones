package com.avicagan.bloodandbones.parts.effect;

import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.parts.TraitContext;
import com.avicagan.bloodandbones.parts.TraitEffect;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;

/**
 * A blink (docs/PARTS-AND-TRAITS.md section 5.4, teleport). Every mode lands the way an enderman does: the spot is
 * dropped to the first solid ground below it and taken only if it fits and is dry ({@code LivingEntity#randomTeleport}),
 * and {@code EntityTeleportEvent.EnderEntity} may stop or move it first, as it may an enderman's.
 *
 * @param mode   "random": anywhere within {@code radius}, sixteen tries as a chorus fruit; "look": up to {@code radius}
 *               along the host's look, short of what it looks at; "behind_target": behind the other; "to_owner": a
 *               minion that follows its maker (a companion or bodyguard) to beside them
 * @param radius how far, at the trait's level
 * @param who    "self", or "other": the attacker, victim or target is sent instead
 * @param beyond "to_owner" only goes when its maker is further than this
 */
public record TeleportEffect(String mode, LevelBasedValue radius, String who, float beyond) implements TraitEffect.Effect {
    public static final MapCodec<TeleportEffect> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("mode").forGetter(TeleportEffect::mode),
            LevelBasedValue.CODEC.optionalFieldOf("radius", LevelBasedValue.constant(8.0F)).forGetter(TeleportEffect::radius),
            Codec.STRING.optionalFieldOf("who", "self").forGetter(TeleportEffect::who),
            Codec.FLOAT.optionalFieldOf("beyond", 0.0F).forGetter(TeleportEffect::beyond)
    ).apply(i, TeleportEffect::new));

    @Override
    public MapCodec<? extends TraitEffect.Effect> codec() {
        return CODEC;
    }

    @Override
    public void run(TraitContext ctx) {
        boolean sendOther = "other".equals(who);
        LivingEntity mover = sendOther ? ctx.other() : ctx.host();
        LivingEntity mark = sendOther ? ctx.host() : ctx.other();
        if (mover == null || !mover.isAlive()) {
            return;
        }
        float r = Math.max(1.0F, ctx.levelled(radius));
        switch (mode) {
            case "random" -> random(mover, r);
            case "look" -> look(mover, r);
            case "behind_target" -> {
                if (mark != null && mark != mover) {
                    behind(mover, mark);
                }
            }
            case "to_owner" -> {
                if (mover instanceof MinionEntity minion) {
                    toOwner(minion, beyond);
                }
            }
            default -> {
            }
        }
    }

    /** Anywhere within the radius, sixteen tries. */
    public static boolean random(LivingEntity mover, float radius) {
        RandomSource random = mover.getRandom();
        ServerLevel level = (ServerLevel) mover.level();
        for (int i = 0; i < 16; i++) {
            double x = mover.getX() + (random.nextDouble() - 0.5) * 2.0 * radius;
            // a whole block up or down, so it lands standing on the ground and not a little above it
            double y = Mth.clamp(Math.floor(mover.getY()) + random.nextInt(Mth.ceil(radius) * 2 + 1) - Mth.ceil(radius), level.getMinBuildHeight(),
                    level.getMinBuildHeight() + level.getLogicalHeight() - 1);
            double z = mover.getZ() + (random.nextDouble() - 0.5) * 2.0 * radius;
            if (to(mover, x, y, z)) {
                return true;
            }
        }
        return false;
    }

    /** Along its look, stopping short of the first thing in the way, then stepping back until it fits. */
    public static boolean look(LivingEntity mover, float radius) {
        Vec3 eye = mover.getEyePosition();
        Vec3 look = mover.getLookAngle();
        Vec3 end = mover.level().clip(new ClipContext(eye, eye.add(look.scale(radius)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mover))
                .getLocation().subtract(look.scale(0.6));
        for (int back = 0; back < Mth.ceil(radius); back++) {
            Vec3 at = end.subtract(look.scale(back));
            if (at.distanceToSqr(eye) < 1.0) {
                return false;
            }
            if (to(mover, at.x, Math.floor(at.y - mover.getEyeHeight() + 0.5), at.z)) {
                return true;
            }
        }
        return false;
    }

    /** Behind another creature, facing its back. */
    public static boolean behind(LivingEntity mover, LivingEntity mark) {
        Vec3 back = Vec3.directionFromRotation(0.0F, mark.getYRot()).scale(-(mark.getBbWidth() / 2.0F + mover.getBbWidth() / 2.0F + 0.8F));
        if (!to(mover, mark.getX() + back.x, Math.floor(mark.getY() + 0.5), mark.getZ() + back.z)) {
            return false;
        }
        if (!(mover instanceof Player)) {
            // a player's turn would need a teleport of its own; a mob simply faces its mark
            float yaw = (float) (Mth.atan2(mark.getZ() - mover.getZ(), mark.getX() - mover.getX()) * Mth.RAD_TO_DEG) - 90.0F;
            mover.setYRot(yaw);
            mover.setYHeadRot(yaw);
            mover.yBodyRot = yaw;
        }
        return true;
    }

    /** A following minion too far from its maker (in the same world) blinks back to beside them, as a tamed wolf does. */
    public static boolean toOwner(MinionEntity minion, float beyond) {
        Player maker = minion.maker();
        if (maker == null || maker.level() != minion.level() || maker.isSpectator() || minion.isPassenger() || minion.isLeashed()
                || !(minion.hasJob("companion") || minion.hasJob("bodyguard")) || minion.distanceToSqr(maker) <= beyond * beyond) {
            return false;
        }
        RandomSource random = minion.getRandom();
        for (int i = 0; i < 10; i++) {
            int dx = random.nextIntBetweenInclusive(-3, 3);
            int dz = random.nextIntBetweenInclusive(-3, 3);
            if (Math.abs(dx) < 2 && Math.abs(dz) < 2) {
                continue;
            }
            if (to(minion, maker.getBlockX() + dx + 0.5, maker.getBlockY() + random.nextIntBetweenInclusive(-1, 1), maker.getBlockZ() + dz + 0.5)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Try one spot, as an enderman does: others may call it off or move it, it drops to the ground there, and it must
     * fit and be dry. A wet tear where it left, a squelch where it lands.
     */
    public static boolean to(LivingEntity mover, double x, double y, double z) {
        EntityTeleportEvent.EnderEntity event = EventHooks.onEnderTeleport(mover, x, y, z);
        if (event.isCanceled()) {
            return false;
        }
        Vec3 from = mover.position();
        if (mover.isPassenger()) {
            mover.stopRiding();
        }
        if (!mover.randomTeleport(event.getTargetX(), event.getTargetY(), event.getTargetZ(), true)) {
            return false;
        }
        ServerLevel level = (ServerLevel) mover.level();
        level.gameEvent(GameEvent.TELEPORT, from, GameEvent.Context.of(mover));
        mover.resetFallDistance();
        MotionEffects.gore(level, mover, from.add(0.0, mover.getBbHeight() * 0.5, 0.0), 10);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.CHORUS_FRUIT_TELEPORT, mover.getSoundSource(), 1.0F, 0.6F);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.SLIME_BLOCK_BREAK, mover.getSoundSource(), 0.8F, 0.5F);
        level.playSound(null, mover.getX(), mover.getY(), mover.getZ(), SoundEvents.ENDERMAN_TELEPORT, mover.getSoundSource(), 0.8F, 0.7F);
        level.playSound(null, mover.getX(), mover.getY(), mover.getZ(), SoundEvents.SLIME_SQUISH, mover.getSoundSource(), 1.0F, 0.5F);
        return true;
    }
}
