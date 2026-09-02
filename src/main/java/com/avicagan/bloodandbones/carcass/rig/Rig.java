package com.avicagan.bloodandbones.carcass.rig;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * The physics skeleton of one mob: which model parts become rigid bodies and how they connect.
 *
 * @param entity  entity type this rig is for
 * @param model   model layer id (the entity model registered under this id, layer {@code layer})
 * @param layer   model layer name, usually "main"
 * @param texture texture the carcass wears
 * @param weight  total mass in Sable units (a full solid block is about 1.0); drives shove, drag and floating
 * @param rotTime ticks for a carcass to go from fresh to rotten in a temperate place; cold stretches it
 * @param bones   bones, torso first
 */
public record Rig(ResourceLocation entity, ResourceLocation model, String layer, ResourceLocation texture, float weight, int rotTime,
                  List<Bone> bones) {
    /** One Minecraft day. */
    public static final int DEFAULT_ROT_TIME = 24000;

    public static final Codec<Rig> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("entity").forGetter(Rig::entity),
            ResourceLocation.CODEC.fieldOf("model").forGetter(Rig::model),
            Codec.STRING.optionalFieldOf("layer", "main").forGetter(Rig::layer),
            ResourceLocation.CODEC.fieldOf("texture").forGetter(Rig::texture),
            Codec.FLOAT.optionalFieldOf("weight", 1.0F).forGetter(Rig::weight),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("rot_time", DEFAULT_ROT_TIME).forGetter(Rig::rotTime),
            Bone.CODEC.listOf().fieldOf("bones").forGetter(Rig::bones)
    ).apply(i, Rig::new));

    public Optional<Bone> bone(String name) {
        for (Bone bone : bones) {
            if (bone.name().equals(name)) {
                return Optional.of(bone);
            }
        }
        return Optional.empty();
    }

    /** Fraction of walking speed lost while dragging this carcass: about 5% for a chicken, 55% for a ravager. */
    public float dragPenalty() {
        double penalty = 0.05 + 0.5 * Math.pow(Math.max(weight, 0.001) / 3.0, 0.6);
        return (float) Math.max(0.05, Math.min(0.55, penalty));
    }

    /** The bone with no parent. Every rig has exactly one. */
    public Bone root() {
        for (Bone bone : bones) {
            if (bone.parent().isEmpty()) {
                return bone;
            }
        }
        throw new IllegalStateException("Rig " + entity + " has no root bone");
    }
}
