package com.avicagan.bloodandbones.carcass.butchery;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Map;

/**
 * How much of a butchery table one way of taking a carcass apart gets (the brief's "three processing paths that
 * must stay meaningfully distinct", and the yield gap between hand and machine). Loaded from
 * {@code data/<namespace>/butchery_path/<id>.json}; see {@link ButcheryPaths} for which path is which.
 *
 * @param scale     every count of the table is multiplied by this
 * @param kinds     and then by this for its kind (meat, bone, offal, fat, hide, other), 1 where not given
 * @param loss      the chance each whole item is spoiled and lost, rolled one by one (a hand's clumsy cuts)
 * @param lootTable whether grinding the body also rolls the mob's own loot table, as if it had died normally
 * @param scraps    whether each piece also gives armour scraps (docs/PARTS-AND-TRAITS.md section 7.1)
 */
public record ButcheryPath(float scale, Map<String, Float> kinds, float loss, boolean lootTable, boolean scraps) {
    public static final Codec<ButcheryPath> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.floatRange(0.0F, 16.0F).optionalFieldOf("scale", 1.0F).forGetter(ButcheryPath::scale),
            Codec.unboundedMap(Codec.STRING, Codec.floatRange(0.0F, 16.0F)).optionalFieldOf("kinds", Map.of()).forGetter(ButcheryPath::kinds),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("loss", 0.0F).forGetter(ButcheryPath::loss),
            Codec.BOOL.optionalFieldOf("loot_table", false).forGetter(ButcheryPath::lootTable),
            Codec.BOOL.optionalFieldOf("scraps", false).forGetter(ButcheryPath::scraps)
    ).apply(i, ButcheryPath::new));

    /** Everything the table says, nothing lost: what a path with no file gets. */
    public static final ButcheryPath FULL = new ButcheryPath(1.0F, Map.of(), 0.0F, false, false);

    /** The share of a yield of this kind it keeps, before the loss roll. */
    public float share(String kind) {
        return scale * kinds.getOrDefault(kind, 1.0F);
    }
}
