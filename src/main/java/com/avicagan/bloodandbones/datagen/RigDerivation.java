package com.avicagan.bloodandbones.datagen;

import com.avicagan.bloodandbones.carcass.rig.Bone;
import com.avicagan.bloodandbones.carcass.rig.ExtraPart;
import com.avicagan.bloodandbones.carcass.rig.JointSpec;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.avicagan.bloodandbones.carcass.rig.RigTarget;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Turns a baked model tree into a rig. Rules: every part with cubes is a bone; a bone's parent is the
 * nearest enclosing part that is itself a bone; top-level bones hang off the biggest top-level bone,
 * the torso. The physics box is the bone's biggest cube. Joint limits come from the part name. The
 * target can hide parts, fold parts into a bone instead of making them bones, attach sibling parts to
 * a bone, re-parent bones, and override boxes and joints.
 */
public final class RigDerivation {
    /** Mass of one cubic block of animal, in Sable units where a plain solid block is 1.0. */
    public static final float FLESH_DENSITY = 1.0F;

    private RigDerivation() {
    }

    /** A part met on the walk, bone or not. */
    private record Seen(String path, Vector3f offset, Quaternionf rotation, boolean cubes, @Nullable ModelPart.Cube biggest) {
    }

    public static Rig derive(RigTarget target, ModelPart root) {
        List<Seen> seen = new ArrayList<>();
        walk(root, "", new Vector3f(), new Quaternionf(), target, seen);

        // which seen parts become bones
        Map<String, Seen> byPath = new LinkedHashMap<>();
        for (Seen s : seen) {
            byPath.put(s.path(), s);
        }
        List<String> bonePaths = new ArrayList<>();
        for (Seen s : seen) {
            if (s.cubes() && !isMerged(target, s.path()) && !target.attach().containsKey(s.path())) {
                bonePaths.add(s.path());
            }
        }
        if (bonePaths.isEmpty()) {
            throw new IllegalStateException("Model " + target.model() + " has no parts with cubes");
        }

        // the torso: named, or the biggest bone with no bone above it
        String torso = target.torso().orElse(null);
        if (torso == null) {
            float best = -1;
            for (String path : bonePaths) {
                if (enclosingBone(path, bonePaths) != null) {
                    continue;
                }
                Vector3f size = boxSize(target, byPath.get(path));
                float volume = size.x * size.y * size.z;
                if (volume > best) {
                    best = volume;
                    torso = path;
                }
            }
        } else if (!bonePaths.contains(torso)) {
            throw new IllegalStateException("Torso " + torso + " of " + target.entity() + " is not a bone; bones are " + bonePaths);
        }

        // parents: override, else enclosing bone, else torso
        Map<String, String> parents = new LinkedHashMap<>();
        for (String path : bonePaths) {
            if (path.equals(torso)) {
                continue;
            }
            String parent = target.parents().get(path);
            if (parent == null) {
                parent = enclosingBone(path, bonePaths);
            }
            if (parent == null) {
                parent = torso;
            }
            if (!bonePaths.contains(parent)) {
                throw new IllegalStateException("Bone " + path + " of " + target.entity() + " has unknown parent " + parent);
            }
            parents.put(path, parent);
        }
        for (String path : bonePaths) {
            // no cycles: walk up to the torso
            String cursor = path;
            int hops = 0;
            while (cursor != null && !cursor.equals(torso)) {
                cursor = parents.get(cursor);
                if (++hops > bonePaths.size()) {
                    throw new IllegalStateException("Parent cycle at bone " + path + " of " + target.entity());
                }
            }
        }

        List<Bone> ordered = new ArrayList<>();
        ordered.add(bone(target, byPath.get(torso), Optional.empty(), bonePaths, byPath));
        for (String path : bonePaths) {
            if (path.equals(torso)) {
                continue;
            }
            ordered.add(bone(target, byPath.get(path), Optional.of(parents.get(path)), bonePaths, byPath));
        }
        for (String attached : target.attach().keySet()) {
            if (!byPath.containsKey(attached)) {
                throw new IllegalStateException("Attached part " + attached + " of " + target.entity() + " does not exist");
            }
        }
        float weight = 0.0F;
        for (Bone bone : ordered) {
            Vector3f size = bone.boxSize();
            weight += (size.x / 16.0F) * (size.y / 16.0F) * (size.z / 16.0F) * FLESH_DENSITY;
        }
        return new Rig(target.entity(), target.model(), target.layer(), target.texture(), target.variantNames(), target.passes(),
                weight, target.rotTime(), ordered);
    }

    private static Bone bone(RigTarget target, Seen s, Optional<String> parent, List<String> bonePaths, Map<String, Seen> byPath) {
        Vector3f min;
        Vector3f max;
        RigTarget.Box override = target.boxes().get(s.path());
        if (override != null) {
            min = new Vector3f(override.min());
            max = new Vector3f(override.max());
        } else {
            min = new Vector3f(s.biggest().minX, s.biggest().minY, s.biggest().minZ);
            max = new Vector3f(s.biggest().maxX, s.biggest().maxY, s.biggest().maxZ);
        }
        // descendants that draw on their own (other bones) or not at all (hidden) are hidden while drawing this bone
        List<String> hide = new ArrayList<>();
        String prefix = s.path() + "/";
        for (String other : bonePaths) {
            if (other.startsWith(prefix)) {
                hide.add(other.substring(prefix.length()));
            }
        }
        for (String hidden : target.hidden()) {
            if (hidden.startsWith(prefix)) {
                hide.add(hidden.substring(prefix.length()));
            }
        }
        // sibling parts that ride along
        List<ExtraPart> extras = new ArrayList<>();
        Quaternionf inverse = new Quaternionf(s.rotation()).invert();
        target.attach().forEach((part, bone) -> {
            if (!bone.equals(s.path())) {
                return;
            }
            Seen extra = byPath.get(part);
            if (extra == null) {
                throw new IllegalStateException("Attached part " + part + " of " + target.entity() + " does not exist");
            }
            Vector3f offset = new Vector3f(extra.offset()).sub(s.offset());
            inverse.transform(offset);
            Quaternionf rotation = new Quaternionf(inverse).mul(extra.rotation());
            extras.add(new ExtraPart(part, offset, rotation));
        });
        Optional<JointSpec> joint = parent.isEmpty() ? Optional.empty()
                : Optional.of(target.joints().getOrDefault(s.path(), jointFor(s.path())));
        return new Bone(s.path(), s.path(), parent, s.offset(), s.rotation(), min, max, joint, hide, extras);
    }

    private static Vector3f boxSize(RigTarget target, Seen s) {
        RigTarget.Box override = target.boxes().get(s.path());
        if (override != null) {
            return new Vector3f(override.max()).sub(override.min());
        }
        ModelPart.Cube c = s.biggest();
        return new Vector3f(c.maxX - c.minX, c.maxY - c.minY, c.maxZ - c.minZ);
    }

    /** A merged part, or anything under one, draws with the bone above it instead of being a bone. */
    private static boolean isMerged(RigTarget target, String path) {
        for (String merged : target.merge()) {
            if (path.equals(merged) || path.startsWith(merged + "/")) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static String enclosingBone(String path, List<String> bonePaths) {
        String best = null;
        for (String other : bonePaths) {
            if (path.startsWith(other + "/") && (best == null || other.length() > best.length())) {
                best = other;
            }
        }
        return best;
    }

    private static void walk(ModelPart part, String path, Vector3f translation, Quaternionf rotation, RigTarget target, List<Seen> out) {
        for (Map.Entry<String, ModelPart> entry : part.children.entrySet()) {
            String name = entry.getKey();
            ModelPart child = entry.getValue();
            String childPath = path.isEmpty() ? name : path + "/" + name;
            if (target.hidden().contains(childPath)) {
                continue;
            }
            PartPose pose = child.getInitialPose();
            Quaternionf local = new Quaternionf().rotationZYX(pose.zRot, pose.yRot, pose.xRot);
            Vector3f childTranslation = rotation.transform(new Vector3f(pose.x, pose.y, pose.z)).add(translation);
            Quaternionf childRotation = new Quaternionf(rotation).mul(local);

            ModelPart.Cube biggest = null;
            float best = -1;
            for (ModelPart.Cube cube : child.cubes) {
                float volume = (cube.maxX - cube.minX) * (cube.maxY - cube.minY) * (cube.maxZ - cube.minZ);
                if (volume > best) {
                    best = volume;
                    biggest = cube;
                }
            }
            out.add(new Seen(childPath, childTranslation, childRotation, biggest != null, biggest));
            walk(child, childPath, childTranslation, childRotation, target, out);
        }
    }

    /** Joint limits by part name, for bones the target does not spell out. */
    public static JointSpec jointFor(String name) {
        // judge by the part's own name, not the path above it ("body/tail" is a tail)
        String lower = name.substring(name.lastIndexOf('/') + 1).toLowerCase();
        if (lower.contains("head") || lower.contains("neck")) {
            // a neck: modest nod and turn, no roll, and the head keeps colliding with the body so its corners
            // cannot sink into the torso when the carcass rolls over
            return new JointSpec(new Vector3f(-15, -30, -6), new Vector3f(25, 30, 6), 4.0F, 2.0F, true);
        }
        if (lower.contains("leg") || lower.contains("arm")) {
            // limbs swing fore and aft, barely sideways; damped so they settle instead of flailing, and with
            // almost no pull back toward standing, so a dead animal collapses instead of standing dead
            return new JointSpec(new Vector3f(-60, -10, -10), new Vector3f(60, 10, 10), 4.0F, 0.3F, false);
        }
        if (lower.contains("tail") || lower.contains("wing") || lower.contains("ear")) {
            return new JointSpec(new Vector3f(-45, -45, -45), new Vector3f(45, 45, 45), 2.0F, 1.0F, false);
        }
        if (lower.contains("body")) {
            // a second body segment: a stiff spine
            return new JointSpec(new Vector3f(-20, -15, -10), new Vector3f(20, 15, 10), 4.0F, 2.0F, false);
        }
        return JointSpec.DEFAULT;
    }

}
