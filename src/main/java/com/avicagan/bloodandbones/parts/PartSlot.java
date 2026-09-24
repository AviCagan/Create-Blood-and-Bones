package com.avicagan.bloodandbones.parts;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * What a bone of a mob is, as an ingredient (docs/PARTS-AND-TRAITS.md section 2): the torso and its
 * extensions, a neck or head, an arm, a leg, a tail, or decoration drawn with its parent.
 */
public enum PartSlot implements StringRepresentable {
    TORSO, TORSO_EXT, NECK, HEAD, ARM, LEG, TAIL, EXTRA;

    public static final Codec<PartSlot> CODEC = StringRepresentable.fromEnum(PartSlot::values);

    /** Which scraps it grinds into: head, torso, arm, leg or tail (decoration grinds with its parent, taken as torso). */
    public String scrapPart() {
        return switch (this) {
            case HEAD, NECK -> "head";
            case TORSO, TORSO_EXT, EXTRA -> "torso";
            case ARM -> "arm";
            case LEG -> "leg";
            case TAIL -> "tail";
        };
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static PartSlot byName(String name) {
        return valueOf(name.toUpperCase(Locale.ROOT));
    }
}
