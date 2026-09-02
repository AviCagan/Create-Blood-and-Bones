package com.avicagan.bloodandbones.carcass.rig;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What a rig should be built from: the hand-written half of a rig, read by datagen, which fills in the
 * geometry from the vanilla model. Lives in {@code src/main/rig_targets/<mob>.json}.
 *
 * @param entity       mob
 * @param model        model layer id
 * @param layer        model layer name
 * @param texture      skin texture path, may contain placeholders
 * @param variantNames variant name to texture file name fix-ups
 * @param passes       extra coats
 * @param rotTime      ticks to rot
 * @param torso        root bone override, otherwise the biggest top-level part
 * @param hidden       parts (and everything under them) that neither collide nor draw: saddles, baby legs
 * @param merge        parts that do not become bones but still draw with the nearest bone above them
 * @param attach       parts that are not bones and draw along with the named bone: a beak on the head
 * @param parents      bone -> parent bone overrides
 * @param boxes        bone -> physics box overrides, part-local pixels
 * @param joints       bone -> joint overrides
 */
public record RigTarget(ResourceLocation entity, ResourceLocation model, String layer, String texture, Map<String, String> variantNames,
                        List<RenderPass> passes, int rotTime, Optional<String> torso, List<String> hidden, List<String> merge,
                        Map<String, String> attach, Map<String, String> parents, Map<String, Box> boxes, Map<String, JointSpec> joints) {
    public record Box(Vector3f min, Vector3f max) {
        public static final Codec<Box> CODEC = RecordCodecBuilder.create(i -> i.group(
                RigCodecs.VEC3.fieldOf("min").forGetter(Box::min),
                RigCodecs.VEC3.fieldOf("max").forGetter(Box::max)
        ).apply(i, Box::new));
    }

    public static final Codec<RigTarget> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("entity").forGetter(RigTarget::entity),
            ResourceLocation.CODEC.fieldOf("model").forGetter(RigTarget::model),
            Codec.STRING.optionalFieldOf("layer", "main").forGetter(RigTarget::layer),
            Codec.STRING.fieldOf("texture").forGetter(RigTarget::texture),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("variant_names", Map.of()).forGetter(RigTarget::variantNames),
            RenderPass.CODEC.listOf().optionalFieldOf("passes", List.of()).forGetter(RigTarget::passes),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("rot_time", Rig.DEFAULT_ROT_TIME).forGetter(RigTarget::rotTime),
            Codec.STRING.optionalFieldOf("torso").forGetter(RigTarget::torso),
            Codec.STRING.listOf().optionalFieldOf("hidden", List.of()).forGetter(RigTarget::hidden),
            Codec.STRING.listOf().optionalFieldOf("merge", List.of()).forGetter(RigTarget::merge),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("attach", Map.of()).forGetter(RigTarget::attach),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("parents", Map.of()).forGetter(RigTarget::parents),
            Codec.unboundedMap(Codec.STRING, Box.CODEC).optionalFieldOf("boxes", Map.of()).forGetter(RigTarget::boxes),
            Codec.unboundedMap(Codec.STRING, JointSpec.CODEC).optionalFieldOf("joints", Map.of()).forGetter(RigTarget::joints)
    ).apply(i, RigTarget::new));
}
