package com.avicagan.bloodandbones.carcass.rig;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.joml.Vector3f;

import java.util.List;

/**
 * How the game draws a mob's baby, so its carcass can look the same: vanilla draws the head parts at one
 * scale and offset and the rest of the body at another (AgeableListModel#renderToBuffer; a model point
 * {@code p} is drawn at {@code scale * (p + offset)}, in model pixels). Copied from each model's
 * constructor into its rig target. A mob its renderer simply shrinks (a villager) has both halves the same.
 *
 * @param head       bones drawn as the head, with everything under them
 * @param headScale  1.5 / babyHeadScale when the model scales the head, else 1
 * @param headOffset (0, babyYHeadOffset, babyZHeadOffset)
 * @param bodyScale  1 / babyBodyScale
 * @param bodyOffset bodyYOffset
 * @param parts      bones the baby draws with another part of the same model (a foal's own long legs)
 * @param extend     model pixels added to the far end of a bone's box, for such a part that is longer
 * @param groups     bones the game draws at their own scale per axis and offset, instead of the head's or
 *                   the body's (a baby llama: head, body and legs each squashed differently)
 */
public record BabyShape(List<String> head, float headScale, Vector3f headOffset, float bodyScale, float bodyOffset,
                        java.util.Map<String, String> parts, java.util.Map<String, Vector3f> extend, List<Group> groups) {
    /** Bones drawn at {@code scale * (p + offset)} per axis, in model pixels. */
    public record Group(List<String> bones, Vector3f scale, Vector3f offset) {
        public static final Codec<Group> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.listOf().fieldOf("bones").forGetter(Group::bones),
                RigCodecs.VEC3.fieldOf("scale").forGetter(Group::scale),
                RigCodecs.VEC3.optionalFieldOf("offset", new Vector3f()).forGetter(Group::offset)
        ).apply(i, Group::new));
    }

    public static final Codec<BabyShape> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.listOf().optionalFieldOf("head", List.of("head")).forGetter(BabyShape::head),
            Codec.FLOAT.optionalFieldOf("head_scale", 1.0F).forGetter(BabyShape::headScale),
            RigCodecs.VEC3.optionalFieldOf("head_offset", new Vector3f()).forGetter(BabyShape::headOffset),
            Codec.FLOAT.optionalFieldOf("body_scale", 0.5F).forGetter(BabyShape::bodyScale),
            Codec.FLOAT.optionalFieldOf("body_offset", 24.0F).forGetter(BabyShape::bodyOffset),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("parts", java.util.Map.of()).forGetter(BabyShape::parts),
            Codec.unboundedMap(Codec.STRING, RigCodecs.VEC3).optionalFieldOf("extend", java.util.Map.of()).forGetter(BabyShape::extend),
            Group.CODEC.listOf().optionalFieldOf("groups", List.of()).forGetter(BabyShape::groups)
    ).apply(i, BabyShape::new));

    /** How big the baby draws this bone along each model axis. */
    public Vector3f scaleOf(String bone) {
        for (Group group : groups) {
            if (group.bones().contains(bone)) {
                return new Vector3f(group.scale());
            }
        }
        return new Vector3f(isHead(bone) ? headScale : bodyScale);
    }

    /** Where the baby moves this bone to, in model pixels before its scale. */
    public Vector3f offsetOf(String bone) {
        for (Group group : groups) {
            if (group.bones().contains(bone)) {
                return new Vector3f(group.offset());
            }
        }
        return isHead(bone) ? new Vector3f(headOffset) : new Vector3f(0.0F, bodyOffset, 0.0F);
    }

    /**
     * A scale along the model's axes, as seen along a part's own axes (the part turned by {@code rotation}).
     * Exact for parts turned by quarter turns, as vanilla's are; close enough otherwise.
     */
    public static Vector3f alongPart(Vector3f modelScale, org.joml.Quaternionf rotation) {
        Vector3f out = new Vector3f();
        for (int axis = 0; axis < 3; axis++) {
            Vector3f d = rotation.transform(new Vector3f(axis == 0 ? 1 : 0, axis == 1 ? 1 : 0, axis == 2 ? 1 : 0));
            out.setComponent(axis, (float) Math.sqrt(d.x * d.x * modelScale.x * modelScale.x
                    + d.y * d.y * modelScale.y * modelScale.y + d.z * d.z * modelScale.z * modelScale.z));
        }
        return out;
    }

    /** Model pixels the baby adds to the far end of this bone's box (none for most). */
    public Vector3f extension(String bone) {
        Vector3f e = extend.get(bone);
        return e == null ? new Vector3f() : new Vector3f(e);
    }

    public boolean isHead(String bone) {
        for (String h : head) {
            if (bone.equals(h) || bone.startsWith(h + "/")) {
                return true;
            }
        }
        return false;
    }
}
