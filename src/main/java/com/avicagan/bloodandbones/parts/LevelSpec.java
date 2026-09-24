package com.avicagan.bloodandbones.parts;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;

import java.util.Optional;

/**
 * A trait's level: a number, or worked out from the mob (its max health, attack damage, armour or speed, or
 * its rig's weight) divided and clamped, so numbers differ from mob to mob with no data per mob.
 */
public record LevelSpec(int fixed, Optional<String> from, float divide, int min, int max) {
    public static final LevelSpec ONE = new LevelSpec(1, Optional.empty(), 1.0F, 1, 1);

    private static final Codec<LevelSpec> DERIVED = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("from").forGetter(l -> l.from.orElse("")),
            Codec.FLOAT.optionalFieldOf("divide", 1.0F).forGetter(LevelSpec::divide),
            Codec.INT.optionalFieldOf("min", 1).forGetter(LevelSpec::min),
            Codec.INT.optionalFieldOf("max", 5).forGetter(LevelSpec::max)
    ).apply(i, (from, divide, min, max) -> new LevelSpec(min, Optional.of(from), divide, min, max)));

    public static final Codec<LevelSpec> CODEC = Codec.either(Codec.INT, DERIVED).xmap(
            e -> e.map(n -> new LevelSpec(n, Optional.empty(), 1.0F, n, n), l -> l),
            l -> l.from.isPresent() ? Either.right(l) : Either.left(l.fixed));

    /** The level for this mob. */
    @SuppressWarnings("unchecked")
    public int resolve(ResourceLocation entity) {
        if (from.isEmpty()) {
            return fixed;
        }
        double value = 0.0;
        if (from.get().equals("weight")) {
            value = com.avicagan.bloodandbones.carcass.rig.RigManager.forEntity(entity).map(r -> (double) r.weight()).orElse(1.0);
        } else {
            Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(entity);
            if (type.isPresent() && DefaultAttributes.hasSupplier(type.get())) {
                var supplier = DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) type.get());
                var attribute = switch (from.get()) {
                    case "max_health" -> Attributes.MAX_HEALTH;
                    case "attack_damage" -> Attributes.ATTACK_DAMAGE;
                    case "armor" -> Attributes.ARMOR;
                    case "movement_speed" -> Attributes.MOVEMENT_SPEED;
                    default -> null;
                };
                if (attribute != null && supplier.hasAttribute(attribute)) {
                    value = supplier.getBaseValue(attribute);
                }
            }
        }
        return Math.max(min, Math.min(max, (int) Math.round(value / Math.max(1.0E-4F, divide))));
    }
}
