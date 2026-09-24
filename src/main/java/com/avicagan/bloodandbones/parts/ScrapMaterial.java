package com.avicagan.bloodandbones.parts;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.List;
import java.util.Map;

/**
 * What armour made from a family's scraps is like (docs/PARTS-AND-TRAITS.md section 3.6): armour points per
 * piece, toughness, knockback resistance, durability (times vanilla's per-slot 11/16/15/13), enchantability,
 * how many scraps a block of flesh grinds into, and a small quirk on every piece.
 */
public record ScrapMaterial(String look, Map<String, Integer> armour, float toughness, float knockbackResistance, int durability,
                            int enchantability, float density, List<Quirk> quirk) {
    public record Quirk(Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) {
        public static final Codec<Quirk> CODEC = RecordCodecBuilder.create(i -> i.group(
                Attribute.CODEC.fieldOf("attribute").forGetter(Quirk::attribute),
                Codec.DOUBLE.fieldOf("amount").forGetter(Quirk::amount),
                AttributeModifier.Operation.CODEC.optionalFieldOf("operation", AttributeModifier.Operation.ADD_VALUE).forGetter(Quirk::operation)
        ).apply(i, Quirk::new));
    }

    public static final Codec<ScrapMaterial> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("look", "hide").forGetter(ScrapMaterial::look),
            Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("armour").forGetter(ScrapMaterial::armour),
            Codec.FLOAT.optionalFieldOf("toughness", 0.0F).forGetter(ScrapMaterial::toughness),
            Codec.FLOAT.optionalFieldOf("knockback_resistance", 0.0F).forGetter(ScrapMaterial::knockbackResistance),
            Codec.INT.optionalFieldOf("durability", 10).forGetter(ScrapMaterial::durability),
            Codec.INT.optionalFieldOf("enchantability", 10).forGetter(ScrapMaterial::enchantability),
            Codec.FLOAT.optionalFieldOf("density", 24.0F).forGetter(ScrapMaterial::density),
            Quirk.CODEC.listOf().optionalFieldOf("quirk", List.of()).forGetter(ScrapMaterial::quirk)
    ).apply(i, ScrapMaterial::new));

    /** Vanilla's durability per slot, times the material's figure, as vanilla armour materials do. */
    public static int slotDurability(String piece) {
        return switch (piece) {
            case "helmet" -> 11;
            case "chestplate" -> 16;
            case "leggings" -> 15;
            default -> 13;
        };
    }

    public int armourFor(String piece) {
        return armour.getOrDefault(piece, 0);
    }
}
