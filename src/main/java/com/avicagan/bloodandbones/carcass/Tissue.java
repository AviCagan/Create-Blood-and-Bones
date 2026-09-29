package com.avicagan.bloodandbones.carcass;

import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * What a carcass's bodies are made of, which sets how much a block of them weighs: flesh, bone (a skeleton) or plate (a
 * golem). A mob's group says which (the {@code "tissue"} field of its mob_group or mob_traits file, the last layer that
 * names one winning, as {@code scrap_material} does). A body is built of that tissue's block (BBBlocks#carcassPart), and
 * Sable weighs each state of it from data/bloodandbones/physics_block_properties/&lt;block&gt;.json, generated from
 * {@link #density}, which a datapack can override to retune.
 */
public enum Tissue implements StringRepresentable {
    /** Meat on bone, the reference: a solid block of it weighs 1.0, as a plain block does in Sable. */
    FLESH(1.0F),
    /** Bone. Denser than flesh, but a skeleton's boxes take in the hollows of its ribcage and skull, so not by bone's full 1.9. */
    BONE(1.5F),
    /** A golem's plate: iron over a hollow frame. */
    PLATE(2.0F);

    public static final Codec<Tissue> CODEC = StringRepresentable.fromEnum(Tissue::values);

    /** Weight of one full block of it, in Sable's units. */
    public final float density;

    Tissue(float density) {
        this.density = density;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Tissue byName(String name) {
        for (Tissue tissue : values()) {
            if (tissue.getSerializedName().equals(name)) {
                return tissue;
            }
        }
        throw new IllegalArgumentException("Unknown tissue " + name + " (flesh, bone or plate)");
    }

    /** What this mob's bodies are made of, from its groups; flesh when none says. */
    public static Tissue of(ResourceLocation entity) {
        return com.avicagan.bloodandbones.parts.PartsData.SERVER.resolve(entity, false).tissue();
    }
}
