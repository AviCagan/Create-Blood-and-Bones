package com.avicagan.bloodandbones.parts.effect;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.item.enchantment.effects.EnchantmentEntityEffect;
import net.minecraft.world.phys.Vec3;

/** bleed {seconds, amplifier} (docs/PARTS-AND-TRAITS.md section 5.5): the creature bleeds ({@link BleedingMobEffect}) for so long. */
public record BleedAction(LevelBasedValue seconds, LevelBasedValue amplifier) implements EnchantmentEntityEffect {
    public static final MapCodec<BleedAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            LevelBasedValue.CODEC.fieldOf("seconds").forGetter(BleedAction::seconds),
            LevelBasedValue.CODEC.optionalFieldOf("amplifier", LevelBasedValue.constant(0.0F)).forGetter(BleedAction::amplifier)
    ).apply(i, BleedAction::new));

    @Override
    public void apply(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item, Entity entity, Vec3 origin) {
        int ticks = Math.round(seconds.calculate(enchantmentLevel) * 20.0F);
        if (entity instanceof LivingEntity living && ticks > 0) {
            living.addEffect(new MobEffectInstance(RangedContent.BLEEDING, ticks, Math.max(0, (int) amplifier.calculate(enchantmentLevel))), item.owner());
        }
    }

    @Override
    public MapCodec<BleedAction> codec() {
        return CODEC;
    }
}
