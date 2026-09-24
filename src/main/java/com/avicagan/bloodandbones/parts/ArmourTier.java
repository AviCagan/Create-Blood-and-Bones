package com.avicagan.bloodandbones.parts;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

import java.util.Map;

/**
 * A tier of carcass armour (docs/PARTS-AND-TRAITS.md section 7.5): the ingot or gem that fits it, its place in
 * the order (each needs the one before), and what it gives every piece on top of the material: armour per
 * piece, toughness, knockback resistance, a durability multiplier, and fire resistance. A tier's bonus takes the
 * place of the tier before it; they do not add up.
 */
public record ArmourTier(int order, Item item, Map<String, Integer> bonus, float toughness, float knockbackResistance,
                         float durabilityMult, boolean fireResistant) {
    public static final Codec<ArmourTier> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("order").forGetter(ArmourTier::order),
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(ArmourTier::item),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("bonus", Map.of()).forGetter(ArmourTier::bonus),
            Codec.FLOAT.optionalFieldOf("toughness", 0.0F).forGetter(ArmourTier::toughness),
            Codec.FLOAT.optionalFieldOf("knockback_resistance", 0.0F).forGetter(ArmourTier::knockbackResistance),
            Codec.FLOAT.optionalFieldOf("durability_mult", 1.0F).forGetter(ArmourTier::durabilityMult),
            Codec.BOOL.optionalFieldOf("fire_resistant", false).forGetter(ArmourTier::fireResistant)
    ).apply(i, ArmourTier::new));

    public int bonusFor(String piece) {
        return bonus.getOrDefault(piece, 0);
    }
}
