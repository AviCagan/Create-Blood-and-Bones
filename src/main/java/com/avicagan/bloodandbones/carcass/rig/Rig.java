package com.avicagan.bloodandbones.carcass.rig;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The physics skeleton of one mob: which model parts become rigid bodies and how they connect.
 *
 * @param entity       entity type this rig is for
 * @param model        model layer id (the entity model registered under this id, layer {@code layer})
 * @param layer        model layer name, usually "main"
 * @param texture      skin texture path; may hold placeholders like {@code {variant}} filled in at death
 * @param variantNames how a mob's variant name maps onto the texture file name where they differ
 * @param passes       extra coats drawn over the skin
 * @param scale        the renderer's model scale (a horse is drawn at 1.1); rig pixels are already multiplied by it
 * @param weight       its size as flesh, in Sable mass units (a full solid block is 1.0): how much animal there is, for
 *                     blood and yields; its bodies' real mass also depends on their tissue (bone and plate weigh more)
 * @param rotTime      ticks for a carcass to go from fresh to rotten in a temperate place (cold stretches it), when this
 *                     mob's differs from what its groups and weight class say (CarcassBody#rotTime)
 * @param bones        bones, torso first
 * @param baby         how its baby is drawn, if it has one that becomes a carcass (see {@link #asBaby()})
 * @param fitted       a generic body (GenericRig) rather than the mob's own model: each bone's {@code part} lists the model
 *                     part names it may wear ("tail|tail_fin"), and the client draws the first it finds in the mob's model,
 *                     stretched to fill the bone's box, or a box in the mob's skin if it finds none
 */
public record Rig(ResourceLocation entity, ResourceLocation model, String layer, String texture, Map<String, String> variantNames,
                  List<RenderPass> passes, float scale, float weight, Optional<Integer> rotTime, List<Bone> bones, Optional<BabyShape> baby,
                  boolean fitted) {
    /** One Minecraft day: what a carcass takes to rot when nothing says otherwise (the medium weight class's time). */
    public static final int DEFAULT_ROT_TIME = 24000;

    public static final Codec<Rig> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("entity").forGetter(Rig::entity),
            ResourceLocation.CODEC.fieldOf("model").forGetter(Rig::model),
            Codec.STRING.optionalFieldOf("layer", "main").forGetter(Rig::layer),
            Codec.STRING.fieldOf("texture").forGetter(Rig::texture),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("variant_names", Map.of()).forGetter(Rig::variantNames),
            RenderPass.CODEC.listOf().optionalFieldOf("passes", List.of()).forGetter(Rig::passes),
            Codec.FLOAT.optionalFieldOf("scale", 1.0F).forGetter(Rig::scale),
            Codec.FLOAT.optionalFieldOf("weight", 1.0F).forGetter(Rig::weight),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("rot_time").forGetter(Rig::rotTime),
            Bone.CODEC.listOf().fieldOf("bones").forGetter(Rig::bones),
            BabyShape.CODEC.optionalFieldOf("baby").forGetter(Rig::baby),
            Codec.BOOL.optionalFieldOf("fitted", false).forGetter(Rig::fitted)
    ).apply(i, Rig::new));

    public Rig withBaby(Optional<BabyShape> shape) {
        return new Rig(entity, model, layer, texture, variantNames, passes, scale, weight, rotTime, bones, shape, fitted);
    }

    /**
     * The baby's rig: head bones moved and sized as the game draws a baby's head, the rest as its body.
     * Rig pixels are model pixels times the renderer's scale, so the offset is scaled by that first. The
     * weight follows the new boxes. Only for a rig with a baby shape.
     */
    public Rig asBaby() {
        BabyShape shape = baby.orElseThrow(() -> new IllegalStateException("Rig " + entity + " has no baby shape"));
        List<Bone> out = new java.util.ArrayList<>();
        float mass = 0.0F;
        for (Bone bone : bones) {
            // along the model's axes for the pivot; along the part's own for its box, extras and drawing
            org.joml.Vector3f f = shape.scaleOf(bone.name());
            org.joml.Vector3f local = BabyShape.alongPart(f, bone.rotation());
            org.joml.Vector3f offset = shape.offsetOf(bone.name()).mul(scale).add(bone.offset()).mul(f);
            List<ExtraPart> extras = bone.extras().stream()
                    .map(e -> new ExtraPart(e.part(), new org.joml.Vector3f(e.offset()).mul(local), e.rotation())).toList();
            Bone small = new Bone(bone.name(), bone.part(), bone.parent(), offset, bone.rotation(),
                    new org.joml.Vector3f(bone.boxMin()).mul(local), new org.joml.Vector3f(bone.boxMax()).mul(local),
                    bone.joint(), bone.hide(), extras, new org.joml.Vector3f(bone.scale()).mul(local));
            org.joml.Vector3f size = small.boxSize();
            mass += (size.x / 16.0F) * (size.y / 16.0F) * (size.z / 16.0F);
            out.add(small);
        }
        return new Rig(entity, model, layer, texture, variantNames, passes, scale, mass, rotTime, out, Optional.empty(), fitted);
    }

    public Optional<Bone> bone(String name) {
        for (Bone bone : bones) {
            if (bone.name().equals(name)) {
                return Optional.of(bone);
            }
        }
        return Optional.empty();
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
