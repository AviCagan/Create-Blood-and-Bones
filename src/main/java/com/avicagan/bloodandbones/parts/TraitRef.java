package com.avicagan.bloodandbones.parts;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/** A trait named in data, at a level: "ns:id" (level 1), or {"trait": "ns:id", "level": 2 or {"from": ...}}. */
public record TraitRef(ResourceLocation id, LevelSpec level) {
    private static final Codec<TraitRef> OBJECT = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("trait").forGetter(TraitRef::id),
            LevelSpec.CODEC.optionalFieldOf("level", LevelSpec.ONE).forGetter(TraitRef::level)
    ).apply(i, TraitRef::new));

    public static final Codec<TraitRef> CODEC = Codec.either(ResourceLocation.CODEC, OBJECT).xmap(
            e -> e.map(id -> new TraitRef(id, LevelSpec.ONE), r -> r),
            r -> r.level.equals(LevelSpec.ONE) ? Either.left(r.id) : Either.right(r));
}
