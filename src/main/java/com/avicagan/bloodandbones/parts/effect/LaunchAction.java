package com.avicagan.bloodandbones.parts.effect;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.item.enchantment.effects.EnchantmentEntityEffect;
import net.minecraft.world.phys.Vec3;

/**
 * launch {up, away} (docs/PARTS-AND-TRAITS.md section 5.5): throws the creature up, and away from whoever's effect it is.
 * Knockback resistance takes its share off, as it does off any knockback. The throw lands at the end of the tick, after
 * the hit that set it off has knocked it back, so the two add up rather than the knockback flattening the throw.
 */
public record LaunchAction(LevelBasedValue up, LevelBasedValue away) implements EnchantmentEntityEffect {
    public static final MapCodec<LaunchAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            LevelBasedValue.CODEC.fieldOf("up").forGetter(LaunchAction::up),
            LevelBasedValue.CODEC.optionalFieldOf("away", LevelBasedValue.constant(0.0F)).forGetter(LaunchAction::away)
    ).apply(i, LaunchAction::new));

    @Override
    public void apply(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item, Entity entity, Vec3 origin) {
        double resist = entity instanceof LivingEntity living ? 1.0 - living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) : 1.0;
        if (resist <= 0.0) {
            return;
        }
        Entity owner = item.owner();
        Vec3 from = owner == null || owner == entity ? Vec3.ZERO : entity.position().subtract(owner.position()).multiply(1.0, 0.0, 1.0);
        Vec3 dir = from.lengthSqr() < 1.0E-6 ? Vec3.ZERO : from.normalize();
        double outward = away.calculate(enchantmentLevel) * resist;
        double upward = up.calculate(enchantmentLevel) * resist;
        RangedEffects.afterTick(() -> {
            if (entity.isAlive()) {
                entity.push(dir.x * outward, upward, dir.z * outward);
                entity.hurtMarked = true;
            }
        });
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.SLIME_JUMP, entity.getSoundSource(), 0.7F, 0.5F);
    }

    @Override
    public MapCodec<LaunchAction> codec() {
        return CODEC;
    }
}
