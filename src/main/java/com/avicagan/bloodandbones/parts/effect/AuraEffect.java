package com.avicagan.bloodandbones.parts.effect;

import com.avicagan.bloodandbones.parts.TraitContext;
import com.avicagan.bloodandbones.parts.TraitEffect;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * An aura (docs/PARTS-AND-TRAITS.md section 5.4, type 14): something done to everything the filter picks within the
 * radius, at most once a second. Kept up (a passive entry) it goes off every {@code interval} ticks (20 at least); from a
 * trigger (a tick, a hurt, the Organ Ability) it goes off then.
 * <ul>
 *     <li>{@code mob_effect}: gives them {@code effect} (others by default)</li>
 *     <li>{@code pull_items}: tugs loose items towards the host</li>
 *     <li>{@code bonemeal}: grows one crop, sapling or berry bush nearby, as bone meal does</li>
 *     <li>{@code calm}: mobs going for the host or its side give up, and leave it be for {@code duration} ticks
 *     (hostile ones by default)</li>
 *     <li>{@code push}: throws them back from the host, {@code strength} as a knockback (others by default)</li>
 *     <li>{@code rally}: sets them on whoever hurt the host (its allies by default)</li>
 * </ul>
 * An {@code effect} given with any other action is put on everything the aura touches too (a roar's slowness). A
 * {@code sound} and {@code particle} mark it going off.
 */
public record AuraEffect(String action, LevelBasedValue radius, int interval, Optional<SocialFilter> filter, Optional<Holder<MobEffect>> effect,
                         LevelBasedValue amplifier, int duration, LevelBasedValue strength, Optional<Holder<SoundEvent>> sound, float pitch,
                         Optional<ParticleOptions> particle) implements TraitEffect.Effect {
    public static final List<String> ACTIONS = List.of("mob_effect", "pull_items", "bonemeal", "calm", "push", "rally");
    public static final MapCodec<AuraEffect> CODEC = RecordCodecBuilder.<AuraEffect>mapCodec(i -> i.group(
            Codec.STRING.validate(a -> ACTIONS.contains(a) ? DataResult.success(a) : DataResult.error(() -> "Unknown aura action '" + a + "': one of " + ACTIONS))
                    .fieldOf("action").forGetter(AuraEffect::action),
            LevelBasedValue.CODEC.optionalFieldOf("radius", LevelBasedValue.constant(4.0F)).forGetter(AuraEffect::radius),
            Codec.INT.optionalFieldOf("interval", 20).forGetter(AuraEffect::interval),
            SocialFilter.CODEC.optionalFieldOf("filter").forGetter(AuraEffect::filter),
            MobEffect.CODEC.optionalFieldOf("effect").forGetter(AuraEffect::effect),
            LevelBasedValue.CODEC.optionalFieldOf("amplifier", LevelBasedValue.constant(0.0F)).forGetter(AuraEffect::amplifier),
            Codec.INT.optionalFieldOf("duration", 100).forGetter(AuraEffect::duration),
            LevelBasedValue.CODEC.optionalFieldOf("strength", LevelBasedValue.constant(1.0F)).forGetter(AuraEffect::strength),
            SoundEvent.CODEC.optionalFieldOf("sound").forGetter(AuraEffect::sound),
            Codec.FLOAT.optionalFieldOf("pitch", 1.0F).forGetter(AuraEffect::pitch),
            ParticleTypes.CODEC.optionalFieldOf("particle").forGetter(AuraEffect::particle)
    ).apply(i, AuraEffect::new)).validate(a -> "mob_effect".equals(a.action) && a.effect.isEmpty()
            ? DataResult.error(() -> "A mob_effect aura needs an effect") : DataResult.success(a));

    /** Whoever hurt the host this long ago can still be rallied against, from a kept-up rally. */
    private static final int RALLY_TICKS = 100;

    @Override
    public MapCodec<? extends TraitEffect.Effect> codec() {
        return CODEC;
    }

    /** Whom it works on: its filter, or by default its allies for a rally, hostile mobs for calm, and anyone else for the rest. */
    public SocialFilter who() {
        return filter.orElse(switch (action) {
            case "rally" -> SocialFilter.ALLIES;
            case "calm" -> SocialFilter.HOSTILE;
            default -> SocialFilter.OTHERS;
        });
    }

    /** Kept up: it goes off every interval ticks (at least 20). */
    @Override
    public void keepUp(TraitContext ctx) {
        if (ctx.host().tickCount % Math.max(20, interval) < 10) {
            pulse(ctx, false);
        }
    }

    @Override
    public void run(TraitContext ctx) {
        pulse(ctx, true);
    }

    /**
     * It goes off, if it has not in the last second: its action on everything it picks, its effect with it, and its
     * sound and particles (from a trigger always; kept up, only when it touched something).
     */
    private void pulse(TraitContext ctx, boolean triggered) {
        LivingEntity host = ctx.host();
        if (!SocialEffects.pulseReady(host, ctx.entry(), ctx.index())) {
            return;
        }
        ServerLevel level = ctx.level();
        float reach = Math.max(0.0F, radius.calculate(ctx.traitLevel()));
        int touched = 0;
        switch (action) {
            case "pull_items" -> touched = pullItems(host, reach);
            case "bonemeal" -> touched = bonemeal(host, reach, level) ? 1 : 0;
            default -> {
                LivingEntity attacker = "rally".equals(action) ? attacker(ctx) : null;
                if ("rally".equals(action) && attacker == null) {
                    return;
                }
                SocialFilter who = who();
                for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, host.getBoundingBox().inflate(reach),
                        e -> e != attacker && e.distanceToSqr(host) <= reach * reach && who.test(host, e))) {
                    if (touch(ctx, other, attacker)) {
                        touched++;
                        if (!"mob_effect".equals(action)) {
                            effect.ifPresent(e -> other.addEffect(new MobEffectInstance(e, duration, ctx.levelled(amplifier)), host));
                        }
                    }
                }
            }
        }
        if (touched > 0 || triggered) {
            mark(host, level, reach, touched > 0);
        }
    }

    /** Its action on one creature it picked; false if there was nothing to do to it. */
    private boolean touch(TraitContext ctx, LivingEntity other, @Nullable LivingEntity attacker) {
        LivingEntity host = ctx.host();
        switch (action) {
            case "mob_effect" -> {
                return effect.isPresent() && other.addEffect(new MobEffectInstance(effect.get(), duration, ctx.levelled(amplifier)), host);
            }
            case "calm" -> {
                if (!(other instanceof Mob mob) || mob.getTarget() == null || mob.getTarget() != host && !SocialFilter.allied(host, mob.getTarget())) {
                    return false;
                }
                SocialEffects.callOff(mob);
                SocialEffects.calm(mob, host, duration);
                return true;
            }
            case "push" -> {
                double push = ctx.scaled(strength);
                other.knockback(push, host.getX() - other.getX(), host.getZ() - other.getZ());
                other.hurtMarked = true;
                return push > 0.0;
            }
            case "rally" -> {
                if (attacker == null || !(other instanceof Mob mob) || !mob.canAttack(attacker) || SocialFilter.allied(attacker, mob)) {
                    return false;
                }
                mob.setTarget(attacker);
                if (mob.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)) {
                    mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, attacker);
                }
                return mob.getTarget() == attacker;
            }
            default -> {
                return false;
            }
        }
    }

    /** Whom a rally turns on: whoever set it off, or kept up, whoever hurt the host lately; never one of its own side. */
    @Nullable
    private static LivingEntity attacker(TraitContext ctx) {
        LivingEntity host = ctx.host();
        LivingEntity attacker = ctx.other();
        if (attacker == null && host.getLastHurtByMob() != null && host.tickCount - host.getLastHurtByMobTimestamp() < RALLY_TICKS) {
            attacker = host.getLastHurtByMob();
        }
        return attacker != null && attacker.isAlive() && attacker != host && !SocialFilter.allied(host, attacker) ? attacker : null;
    }

    /**
     * Loose items within reach hop towards the host, far enough to land about where it stands (a player then picks them
     * up); items still too fresh to pick up (just thrown) are left.
     */
    private static int pullItems(LivingEntity host, float reach) {
        Vec3 to = host.position().add(0.0, host.getBbHeight() * 0.4, 0.0);
        int pulled = 0;
        for (ItemEntity item : host.level().getEntitiesOfClass(ItemEntity.class, host.getBoundingBox().inflate(reach),
                i -> i.isAlive() && !i.hasPickUpDelay() && i.distanceToSqr(host) <= reach * reach)) {
            Vec3 d = to.subtract(item.position());
            double across = Math.sqrt(d.x * d.x + d.z * d.z);
            if (across < 0.75 && Math.abs(d.y) < 1.5) {
                continue;
            }
            // about 12 ticks in the air: fast enough across to cover the gap in that time
            double speed = across < 1.0E-3 ? 0.0 : Math.min(0.6, across * 0.095) / across;
            item.setDeltaMovement(d.x * speed, Math.min(0.5, 0.24 + Math.max(0.0, d.y) * 0.08), d.z * speed);
            item.hasImpulse = true;
            pulled++;
        }
        return pulled;
    }

    /** One crop, sapling or berry bush within reach grows, with bone meal's sparkle. */
    private static boolean bonemeal(LivingEntity host, float reach, ServerLevel level) {
        int r = Math.max(1, (int) Math.ceil(reach));
        BlockPos centre = host.blockPosition();
        for (int tries = 0; tries < 24; tries++) {
            BlockPos pos = centre.offset(host.getRandom().nextIntBetweenInclusive(-r, r), host.getRandom().nextIntBetweenInclusive(-2, 2),
                    host.getRandom().nextIntBetweenInclusive(-r, r));
            BlockState state = level.getBlockState(pos);
            if ((state.is(BlockTags.BEE_GROWABLES) || state.is(BlockTags.SAPLINGS)) && state.getBlock() instanceof BonemealableBlock grows
                    && grows.isValidBonemealTarget(level, pos, state)
                    && BoneMealItem.applyBonemeal(new ItemStack(Items.BONE_MEAL), level, pos, host instanceof Player player ? player : null)) {
                level.levelEvent(1505, pos, 15);
                return true;
            }
        }
        return false;
    }

    /** Its sound and particles: the data's own, or a wet slurp for items pulled and a crunch for bone meal. */
    private void mark(LivingEntity host, ServerLevel level, float reach, boolean touched) {
        float wobble = 0.9F + host.getRandom().nextFloat() * 0.2F;
        if (sound.isPresent()) {
            level.playSound(null, host.getX(), host.getY(), host.getZ(), sound.get(), host.getSoundSource(), 0.8F, pitch * wobble);
        } else if ("pull_items".equals(action) && touched) {
            level.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.SLIME_SQUISH_SMALL, host.getSoundSource(), 0.35F, 1.5F * wobble);
            level.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.HONEY_BLOCK_SLIDE, host.getSoundSource(), 0.25F, 1.2F * wobble);
        } else if ("bonemeal".equals(action) && touched) {
            level.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.BONE_MEAL_USE, host.getSoundSource(), 0.6F, 0.8F * wobble);
        }
        particle.ifPresent(p -> level.sendParticles(p, host.getX(), host.getY() + host.getBbHeight() * 0.5, host.getZ(), 8 + (int) (reach * 4),
                reach * 0.5, host.getBbHeight() * 0.4, reach * 0.5, 0.05));
    }
}
