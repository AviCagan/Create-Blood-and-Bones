package com.avicagan.bloodandbones.carcass.rig;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A body shape with no mob of its own: what an archetype's mobs are built from when they have no rig file
 * ({@code data/<ns>/generic_rig/<id>.json}, named by the archetype's {@code "generic_rig"}). Rule 2: a modded mob nobody
 * has heard of still becomes a carcass on day one, as a quadruped, a biped, a fish and so on, the size of its hitbox.
 * <p>
 * Each bone is a box measured against the mob's hitbox: across and along in hitbox widths, up in hitbox heights from its
 * feet, the middle of its feet at 0. The front is toward negative z and the mob's right toward negative x, as in the
 * game's own models. A bone that hangs off another joins it at its {@code pivot}; the torso comes first. Bones are named
 * as the game's models name their parts ("head", "right_front_leg", "tail"), so the part slots, the joint limits and the
 * butchery all read them as they read a vanilla mob's. {@code wears} lists the model part names the client looks for in
 * the mob's own model to draw in that bone's place, first found wins (its own name when not given). {@code joint} sets how
 * far the bone swings against its parent, as a rig file's joint does; with none, the naming rules pick one by its name
 * (JointRules), as they do for the rig targets.
 *
 * @param bones torso first
 */
public record GenericRig(List<GenericBone> bones) {
    /** Nothing is ever smaller than this, in model pixels, or it would be no body at all. */
    private static final float MIN_PIXELS = 1.0F;

    public record GenericBone(String name, Optional<String> parent, Vector3f from, Vector3f to, Optional<Vector3f> pivot, List<String> wears,
                              Optional<JointSpec> joint) {
    }

    public static GenericRig parse(ResourceLocation id, JsonObject json) {
        List<GenericBone> bones = new ArrayList<>();
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (JsonElement e : json.getAsJsonArray("bones")) {
            JsonObject o = e.getAsJsonObject();
            String name = o.get("name").getAsString();
            JsonArray box = o.getAsJsonArray("box");
            if (box == null || box.size() != 6) {
                throw new IllegalArgumentException(id + ": bone " + name + " needs a \"box\" of six numbers (from x, y, z, to x, y, z)");
            }
            Optional<String> parent = o.has("parent") ? Optional.of(o.get("parent").getAsString()) : Optional.empty();
            if (bones.isEmpty() == parent.isPresent()) {
                throw new IllegalArgumentException(id + ": the first bone is the torso and has no parent; every other names one (" + name + ")");
            }
            if (parent.isPresent() && !seen.containsKey(parent.get())) {
                throw new IllegalArgumentException(id + ": bone " + name + " hangs off " + parent.get() + ", which is not a bone above it");
            }
            if (seen.put(name, true) != null) {
                throw new IllegalArgumentException(id + ": two bones named " + name);
            }
            List<String> wears = new ArrayList<>();
            if (o.has("wears")) {
                o.getAsJsonArray("wears").forEach(w -> wears.add(w.getAsString()));
            } else {
                wears.add(name);
            }
            Optional<Vector3f> pivot = o.has("pivot") ? Optional.of(vec(o.getAsJsonArray("pivot"), 0)) : Optional.empty();
            Optional<JointSpec> joint = Optional.empty();
            if (o.has("joint")) {
                if (parent.isEmpty()) {
                    throw new IllegalArgumentException(id + ": the torso (" + name + ") hangs off nothing, so it has no joint");
                }
                joint = Optional.of(JointSpec.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, o.get("joint"))
                        .getOrThrow(message -> new IllegalArgumentException(id + ": bone " + name + "'s joint: " + message)));
            }
            bones.add(new GenericBone(name, parent, vec(box, 0), vec(box, 3), pivot, List.copyOf(wears), joint));
        }
        if (bones.isEmpty()) {
            throw new IllegalArgumentException(id + ": a generic rig needs at least a torso");
        }
        return new GenericRig(List.copyOf(bones));
    }

    private static Vector3f vec(JsonArray a, int from) {
        return new Vector3f(a.get(from).getAsFloat(), a.get(from + 1).getAsFloat(), a.get(from + 2).getAsFloat());
    }

    /**
     * This body at the size of a mob's hitbox, as a rig of the mob: model pixels, with the model's origin a block and a half
     * above the feet and its y downward, as the game draws models. It wears the mob's own texture (the path the game's mobs
     * use, which the client corrects from the mob's renderer), and its baby is the grown one shrunk about its feet, as the
     * game draws a baby it has no shape of its own for.
     */
    public Rig build(ResourceLocation entity, float width, float height) {
        List<Bone> out = new ArrayList<>();
        Map<String, float[]> boxes = new LinkedHashMap<>();
        float weight = 0.0F;
        for (GenericBone bone : bones) {
            Vector3f a = toModel(bone.from(), width, height);
            Vector3f b = toModel(bone.to(), width, height);
            Vector3f min = new Vector3f(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.min(a.z, b.z));
            Vector3f max = new Vector3f(Math.max(a.x, b.x), Math.max(a.y, b.y), Math.max(a.z, b.z));
            grow(min, max);
            @Nullable float[] parentBox = bone.parent().map(boxes::get).orElse(null);
            Vector3f pivot = bone.pivot().map(p -> toModel(p, width, height))
                    .orElseGet(() -> new Vector3f(min).add(max).mul(0.5F));
            boxes.put(bone.name(), new float[]{min.x, min.y, min.z, max.x, max.y, max.z});
            Optional<JointSpec> joint = bone.parent().isEmpty() ? Optional.empty()
                    : bone.joint().isPresent() ? bone.joint()
                    : Optional.of(JointRules.jointFor(bone.name(), parentBox != null && restsOnTop(pivot, min, max, parentBox), lyingFlat(min, max)));
            out.add(new Bone(bone.name(), String.join("|", bone.wears()), bone.parent(), pivot, new Quaternionf(),
                    new Vector3f(min).sub(pivot), new Vector3f(max).sub(pivot), joint));
            Vector3f size = new Vector3f(max).sub(min);
            weight += size.x * size.y * size.z / 4096.0F;
        }
        String texture = entity.getNamespace() + ":textures/entity/" + entity.getPath() + ".png";
        // shrunk about the feet: a point p is drawn at 0.5 * (p + 24), the model's origin being 24 pixels above them
        BabyShape baby = new BabyShape(List.of(), 1.0F, new Vector3f(), 0.5F, 24.0F, List.of());
        return new Rig(entity, entity, "main", texture, Map.of(), List.of(), 1.0F, weight, Optional.empty(), List.copyOf(out),
                Optional.of(baby), true);
    }

    /** A point measured against the hitbox, in model pixels. */
    private static Vector3f toModel(Vector3f p, float width, float height) {
        return new Vector3f(16.0F * p.x * width, 24.0F - 16.0F * p.y * height, 16.0F * p.z * width);
    }

    /** Every side at least a pixel, grown about its middle. */
    private static void grow(Vector3f min, Vector3f max) {
        for (int axis = 0; axis < 3; axis++) {
            float size = max.get(axis) - min.get(axis);
            if (size < MIN_PIXELS) {
                float middle = (min.get(axis) + max.get(axis)) / 2.0F;
                min.setComponent(axis, middle - MIN_PIXELS / 2.0F);
                max.setComponent(axis, middle + MIN_PIXELS / 2.0F);
            }
        }
    }

    /** A box much longer along the level than it is thick (an arthropod's leg held out), as the rig derivation judges it. */
    private static boolean lyingFlat(Vector3f min, Vector3f max) {
        Vector3f size = new Vector3f(max).sub(min);
        float longest = Math.max(size.x, Math.max(size.y, size.z));
        float next = size.x + size.y + size.z - longest - Math.min(size.x, Math.min(size.y, size.z));
        return size.y < longest && longest > 1.5F * next;
    }

    /** A bone that sits on top of its parent (a biped's head): joined at or above the parent's top, and rising from there. */
    private static boolean restsOnTop(Vector3f pivot, Vector3f min, Vector3f max, float[] parent) {
        // model y is downward: the parent's top is its smallest y
        return pivot.y <= parent[1] + 1.0F && (min.y + max.y) / 2.0F < pivot.y;
    }
}
