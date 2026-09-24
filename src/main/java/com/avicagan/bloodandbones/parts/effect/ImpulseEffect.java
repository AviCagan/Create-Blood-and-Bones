package com.avicagan.bloodandbones.parts.effect;

import com.avicagan.bloodandbones.parts.TraitContext;
import com.avicagan.bloodandbones.parts.TraitEffect;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A shove (docs/PARTS-AND-TRAITS.md section 5.4, impulse): leap, dash, fling, knockback. Set on the server with
 * {@code hurtMarked}, which sends the motion to a player as knockback is sent.
 *
 * @param target  "self" (the host lunges), "other" (whoever else is in it: the attacker, the victim, a minion's target)
 *                or "area" (everything within {@code radius} but the host and its own side)
 * @param forward along the way the host faces, level ground only (a minion firing at a target: toward it)
 * @param up      straight up
 * @param away    away from the host; for "self", everything within {@code radius} is thrown away this hard as it lunges
 * @param arc     "any", or only when the other is in "front" of the host or "behind" it (a tail swatting what hits from behind)
 * @param cushion whoever is thrown takes no fall damage until they next land
 */
public record ImpulseEffect(String target, LevelBasedValue forward, LevelBasedValue up, LevelBasedValue away, float radius, String arc,
                            boolean cushion) implements TraitEffect.Effect {
    private static final LevelBasedValue NONE = LevelBasedValue.constant(0.0F);

    public static final MapCodec<ImpulseEffect> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.optionalFieldOf("target", "self").forGetter(ImpulseEffect::target),
            LevelBasedValue.CODEC.optionalFieldOf("forward", NONE).forGetter(ImpulseEffect::forward),
            LevelBasedValue.CODEC.optionalFieldOf("up", NONE).forGetter(ImpulseEffect::up),
            LevelBasedValue.CODEC.optionalFieldOf("away", NONE).forGetter(ImpulseEffect::away),
            Codec.FLOAT.optionalFieldOf("radius", 0.0F).forGetter(ImpulseEffect::radius),
            Codec.STRING.optionalFieldOf("arc", "any").forGetter(ImpulseEffect::arc),
            Codec.BOOL.optionalFieldOf("cushion", false).forGetter(ImpulseEffect::cushion)
    ).apply(i, ImpulseEffect::new));

    @Override
    public MapCodec<? extends TraitEffect.Effect> codec() {
        return CODEC;
    }

    @Override
    public void run(TraitContext ctx) {
        LivingEntity host = ctx.host();
        LivingEntity other = ctx.other();
        if (!MotionEffects.inArc(host, other == null ? null : other.position(), arc)) {
            return;
        }
        Vec3 facing = facing(host, other, ctx);
        double f = ctx.scaled(forward);
        double u = ctx.scaled(up);
        double a = ctx.scaled(away);
        switch (target) {
            case "self" -> {
                Vec3 motion = host.getDeltaMovement();
                // a lunge sets where it goes; a flat one keeps whatever rise or fall it had
                host.setDeltaMovement(facing.x * f, u == 0.0 ? motion.y : Math.max(u, motion.y), facing.z * f);
                host.hurtMarked = true;
                host.hasImpulse = true;
                if (cushion) {
                    MotionFlags.cushion(host);
                }
                if (a != 0.0 && radius > 0.0F) {
                    for (LivingEntity near : MotionEffects.around(host, radius)) {
                        shove(host, near, Vec3.ZERO, 0.0, a, 0.35 * Math.abs(a));
                    }
                }
                ctx.level().playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.SLIME_JUMP, host.getSoundSource(), 0.8F,
                        0.5F + ctx.random().nextFloat() * 0.2F);
            }
            case "other" -> {
                if (other != null && other != host) {
                    shove(host, other, facing, f, a, u);
                    thud(ctx, other);
                }
            }
            case "area" -> {
                for (LivingEntity near : MotionEffects.around(host, radius)) {
                    shove(host, near, facing, f, a, u);
                }
                ctx.level().playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.SLIME_ATTACK, host.getSoundSource(), 1.0F, 0.5F);
            }
            default -> {
            }
        }
    }

    /** Throw another creature: along the host's facing, away from the host and up, less for what resists knockback. */
    private void shove(LivingEntity host, LivingEntity who, Vec3 facing, double forward, double away, double up) {
        double give = Math.max(0.0, 1.0 - who.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        if (give <= 0.0) {
            return;
        }
        Vec3 off = new Vec3(who.getX() - host.getX(), 0.0, who.getZ() - host.getZ());
        Vec3 out = off.lengthSqr() < 1.0E-4 ? facing : off.normalize();
        Vec3 push = facing.scale(forward).add(out.scale(away)).add(0.0, up, 0.0).scale(give);
        who.push(push.x, push.y, push.z);
        who.hurtMarked = true;
        if (cushion) {
            MotionFlags.cushion(who);
        }
    }

    private static void thud(TraitContext ctx, LivingEntity who) {
        ctx.level().playSound(null, who.getX(), who.getY(), who.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, who.getSoundSource(), 0.8F, 0.7F);
        ctx.level().playSound(null, who.getX(), who.getY(), who.getZ(), SoundEvents.SLIME_ATTACK, who.getSoundSource(), 0.6F, 0.6F);
    }

    /**
     * Which way "forward" is: toward a minion's target when it fires at one, otherwise the way the host faces, flat.
     */
    static Vec3 facing(LivingEntity host, @Nullable LivingEntity other, TraitContext ctx) {
        if (ctx.onMinion() && other != null) {
            Vec3 to = new Vec3(other.getX() - host.getX(), 0.0, other.getZ() - host.getZ());
            if (to.lengthSqr() > 1.0E-4) {
                return to.normalize();
            }
        }
        return Vec3.directionFromRotation(0.0F, host.getYRot());
    }
}
