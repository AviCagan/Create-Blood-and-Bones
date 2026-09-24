package com.avicagan.bloodandbones.parts.effect;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.item.enchantment.effects.EnchantmentEntityEffect;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * ink_cloud {radius, seconds} (docs/PARTS-AND-TRAITS.md section 5.5): a squid's puff of ink where it lands, blinding
 * everything within the radius for so long, except whoever's effect it is and those on their side (their team, a
 * minion's maker, a player's minions).
 */
public record InkCloudAction(LevelBasedValue radius, LevelBasedValue seconds) implements EnchantmentEntityEffect {
    public static final MapCodec<InkCloudAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            LevelBasedValue.CODEC.fieldOf("radius").forGetter(InkCloudAction::radius),
            LevelBasedValue.CODEC.fieldOf("seconds").forGetter(InkCloudAction::seconds)
    ).apply(i, InkCloudAction::new));

    @Override
    public void apply(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item, Entity entity, Vec3 origin) {
        double r = Math.max(0.5, radius.calculate(enchantmentLevel));
        int ticks = Math.round(seconds.calculate(enchantmentLevel) * 20.0F);
        Vec3 at = origin.add(0.0, entity.getBbHeight() * 0.5, 0.0);
        for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r),
                e -> e.isAlive() && e.distanceToSqr(at) <= r * r && !RangedAim.friendly(item.owner(), e))) {
            near.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ticks, 0), item.owner());
        }
        level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y, at.z, (int) (20 * r), r * 0.4, 0.5, r * 0.4, 0.05);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.SQUID_SQUIRT, SoundSource.NEUTRAL, 1.0F, 0.8F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_SQUISH, SoundSource.NEUTRAL, 0.7F, 0.5F);
    }

    @Override
    public MapCodec<InkCloudAction> codec() {
        return CODEC;
    }
}
