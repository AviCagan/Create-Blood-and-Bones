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
 * pull {strength} (docs/PARTS-AND-TRAITS.md section 5.5): yanks the creature toward whoever's effect it is, off its feet, as
 * a frog's tongue does, with a wet slap. Knockback resistance takes its share off. Like launch, the yank lands at the end
 * of the tick, after any knockback from the hit that set it off.
 */
public record PullAction(LevelBasedValue strength) implements EnchantmentEntityEffect {
    public static final MapCodec<PullAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            LevelBasedValue.CODEC.fieldOf("strength").forGetter(PullAction::strength)
    ).apply(i, PullAction::new));

    @Override
    public void apply(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item, Entity entity, Vec3 origin) {
        Entity owner = item.owner();
        if (owner == null || owner == entity) {
            return;
        }
        double resist = entity instanceof LivingEntity living ? 1.0 - living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) : 1.0;
        Vec3 toward = owner.position().subtract(entity.position());
        if (resist <= 0.0 || toward.lengthSqr() < 1.0E-4) {
            return;
        }
        // no harder than would carry it past its puller
        double pull = Math.min(strength.calculate(enchantmentLevel), toward.length() * 0.35) * resist;
        Vec3 dir = toward.normalize();
        RangedEffects.afterTick(() -> {
            if (entity.isAlive()) {
                entity.push(dir.x * pull, 0.2 + Math.max(0.0, dir.y) * pull * 0.5, dir.z * pull);
                entity.hurtMarked = true;
            }
        });
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.FROG_TONGUE, entity.getSoundSource(), 1.0F, 0.7F);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.SLIME_SQUISH_SMALL, entity.getSoundSource(), 0.6F, 0.6F);
    }

    @Override
    public MapCodec<PullAction> codec() {
        return CODEC;
    }
}
