package com.avicagan.bloodandbones.carcass.butchery;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * One thing butchering gives.
 *
 * @param item  item id; may hold a {trait} placeholder filled from the carcass (a sheep's "{wool}_wool")
 *              and is skipped when the carcass lacks that trait
 * @param count expected count; the fraction is a chance of one more (0.4 = 40% chance of one)
 * @param kind  meat, hide, bone, offal, fat or other: rot spoils meat, offal, fat and hide, never bone
 */
public record Yield(String item, float count, String kind) {
    public static final Codec<Yield> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("item").forGetter(Yield::item),
            Codec.FLOAT.optionalFieldOf("count", 1.0F).forGetter(Yield::count),
            Codec.STRING.optionalFieldOf("kind", "other").forGetter(Yield::kind)
    ).apply(i, Yield::new));

    public Yield scaled(float factor) {
        return new Yield(item, count * factor, kind);
    }
}
