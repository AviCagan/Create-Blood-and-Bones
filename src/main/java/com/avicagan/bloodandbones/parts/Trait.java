package com.avicagan.bloodandbones.parts;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * A named, levelled trait (docs/PARTS-AND-TRAITS.md section 4.7), the reusable unit: "springy", "venomous",
 * "fall_guard". Its effects scale with the level through vanilla's level-based values.
 *
 * @param stacking "max": the same trait from several places counts once at its highest level; "sum": the
 *                 levels add up, to the maximum (a rabbit hide on every piece)
 */
public record Trait(String name, int maxLevel, String stacking, List<String> contexts, List<TraitEffect> effects) {
    public static final Codec<Trait> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(Trait::name),
            Codec.INT.optionalFieldOf("max_level", 1).forGetter(Trait::maxLevel),
            Codec.STRING.optionalFieldOf("stacking", "max").forGetter(Trait::stacking),
            Codec.STRING.listOf().optionalFieldOf("contexts", List.of("minion", "armour")).forGetter(Trait::contexts),
            TraitEffect.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(Trait::effects)
    ).apply(i, Trait::new));

    public boolean sums() {
        return "sum".equals(stacking);
    }
}
