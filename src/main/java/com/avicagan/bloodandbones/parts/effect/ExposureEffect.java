package com.avicagan.bloodandbones.parts.effect;

import com.avicagan.bloodandbones.parts.TraitContext;
import com.avicagan.bloodandbones.parts.TraitEffect;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.LevelBasedValue;

/**
 * What its surroundings do to a body that cannot stand them, the drawbacks' harm (docs/PARTS-AND-TRAITS.md section 5.10:
 * sun_cursed, water_hurts, heat_hurts): {@code damage} of {@code damage_type} and {@code ignite} seconds alight, each
 * time a tick entry comes up. Where and when is the entry's condition (open sky by day, rain or water, a hot biome).
 * Nothing here protects: a helmet does not keep the sun off. The spec reaches these through the vanilla adapter's ignite
 * and damage_entity (the Ranged group's); this small type lets the drawbacks work without it.
 */
public record ExposureEffect(LevelBasedValue damage, ResourceKey<DamageType> damageType, float ignite) implements TraitEffect.Effect {
    public static final MapCodec<ExposureEffect> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            LevelBasedValue.CODEC.optionalFieldOf("damage", LevelBasedValue.constant(0.0F)).forGetter(ExposureEffect::damage),
            ResourceKey.codec(Registries.DAMAGE_TYPE).optionalFieldOf("damage_type", DamageTypes.GENERIC).forGetter(ExposureEffect::damageType),
            Codec.FLOAT.optionalFieldOf("ignite", 0.0F).forGetter(ExposureEffect::ignite)
    ).apply(i, ExposureEffect::new));

    @Override
    public MapCodec<? extends TraitEffect.Effect> codec() {
        return CODEC;
    }

    @Override
    public void run(TraitContext ctx) {
        LivingEntity host = ctx.host();
        if (!host.isAlive()) {
            return;
        }
        if (ignite > 0.0F) {
            host.igniteForSeconds(ignite);
        }
        float hurt = ctx.scaled(damage);
        if (hurt > 0.0F) {
            host.hurt(host.damageSources().source(damageType), hurt);
        }
    }
}
