package com.avicagan.bloodandbones.carcass.butchery;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * What a mob's carcass gives when taken apart: its hide (for the whole animal, skinned with the Flensing
 * Knife) and, per bone, what butchering that piece gives. Generated from the rig targets by datagen,
 * loaded from {@code data/<namespace>/butchery/<entity namespace>/<entity path>.json}.
 */
public record ButcheryTable(ResourceLocation entity, List<Yield> hide, Map<String, List<Yield>> parts) {
    public static final Codec<ButcheryTable> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("entity").forGetter(ButcheryTable::entity),
            Yield.CODEC.listOf().optionalFieldOf("hide", List.of()).forGetter(ButcheryTable::hide),
            Codec.unboundedMap(Codec.STRING, Yield.CODEC.listOf()).optionalFieldOf("parts", Map.of()).forGetter(ButcheryTable::parts)
    ).apply(i, ButcheryTable::new));

    public List<Yield> part(String bone) {
        return parts.getOrDefault(bone, List.of());
    }
}
