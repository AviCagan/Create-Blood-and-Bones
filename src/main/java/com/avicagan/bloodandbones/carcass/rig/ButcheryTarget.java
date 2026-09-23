package com.avicagan.bloodandbones.carcass.rig;

import com.avicagan.bloodandbones.carcass.butchery.Yield;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Map;

/**
 * The hand-written half of a butchery table, inside a rig target. Datagen spreads it over the rig's bones
 * by their volume, so a big body gives more meat than a thin leg.
 *
 * @param meat          meat item, or "none"
 * @param meatPerBlock  meat per cubic block of animal
 * @param hide          hide item for skinning the whole animal, or "none"
 * @param hidePerWeight hides per unit of animal weight
 * @param bone          bone item, or "none"
 * @param bonePerBlock  bones per cubic block, with at least {@code boneMin} from any piece
 * @param boneMin       fewest bones any piece gives (as an expected count)
 * @param offalPerWeight offal from the torso per unit of weight
 * @param fatPerWeight  fat from the torso per unit of weight
 * @param hideExtras    more things skinning gives (a sheep's wool)
 * @param partExtras    more things butchering a named bone gives
 */
public record ButcheryTarget(String meat, float meatPerBlock, String hide, float hidePerWeight, String bone, float bonePerBlock, float boneMin,
                             float offalPerWeight, float fatPerWeight, List<Yield> hideExtras, Map<String, List<Yield>> partExtras) {
    public static final ButcheryTarget DEFAULT = new ButcheryTarget("bloodandbones:raw_meat", 8.0F, "bloodandbones:raw_hide", 3.0F,
            "minecraft:bone", 3.0F, 0.5F, 2.0F, 1.0F, List.of(), Map.of());

    public static final Codec<ButcheryTarget> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("meat", DEFAULT.meat()).forGetter(ButcheryTarget::meat),
            Codec.FLOAT.optionalFieldOf("meat_per_block", DEFAULT.meatPerBlock()).forGetter(ButcheryTarget::meatPerBlock),
            Codec.STRING.optionalFieldOf("hide", DEFAULT.hide()).forGetter(ButcheryTarget::hide),
            Codec.FLOAT.optionalFieldOf("hide_per_weight", DEFAULT.hidePerWeight()).forGetter(ButcheryTarget::hidePerWeight),
            Codec.STRING.optionalFieldOf("bone", DEFAULT.bone()).forGetter(ButcheryTarget::bone),
            Codec.FLOAT.optionalFieldOf("bone_per_block", DEFAULT.bonePerBlock()).forGetter(ButcheryTarget::bonePerBlock),
            Codec.FLOAT.optionalFieldOf("bone_min", DEFAULT.boneMin()).forGetter(ButcheryTarget::boneMin),
            Codec.FLOAT.optionalFieldOf("offal_per_weight", DEFAULT.offalPerWeight()).forGetter(ButcheryTarget::offalPerWeight),
            Codec.FLOAT.optionalFieldOf("fat_per_weight", DEFAULT.fatPerWeight()).forGetter(ButcheryTarget::fatPerWeight),
            Yield.CODEC.listOf().optionalFieldOf("hide_extras", List.of()).forGetter(ButcheryTarget::hideExtras),
            Codec.unboundedMap(Codec.STRING, Yield.CODEC.listOf()).optionalFieldOf("part_extras", Map.of()).forGetter(ButcheryTarget::partExtras)
    ).apply(i, ButcheryTarget::new));
}
