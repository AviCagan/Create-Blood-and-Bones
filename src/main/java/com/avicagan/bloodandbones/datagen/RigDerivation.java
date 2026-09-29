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
    /**
     * Mass of one cubic block of animal, in Sable units where a plain solid block is 1.0: the rig's weight is its size as
     * flesh. What its bodies really weigh also depends on what they are made of (Tissue), which its group says.
     */
    public static final float FLESH_DENSITY = com.avicagan.bloodandbones.carcass.Tissue.FLESH.density;

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
            if (s.cubes() && !isMerged(target, s.path()) && !isAttached(target, s.path())) {
                bonePaths.add(s.path());
            }
        }
        if (bonePaths.isEmpty()) {
            throw new IllegalStateException("Model " + target.model() + " has no parts with cubes");
        }
        // every name the target uses must be real, or a typo silently changes the animal
        for (String path : target.merge()) {
            if (!byPath.containsKey(path)) {
                throw new IllegalStateException("Merged part " + path + " of " + target.entity() + " does not exist");
            }
            if (enclosingBone(path, bonePaths) == null) {
                throw new IllegalStateException("Merged part " + path + " of " + target.entity() + " has no bone above it to draw it");
            }
        }
        for (String path : target.hidden()) {
            String parentPath = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "";
            boolean known = byPath.containsKey(path) || (!parentPath.isEmpty() && byPath.containsKey(parentPath)) || parentPath.isEmpty();
            if (!known) {
                throw new IllegalStateException("Hidden part " + path + " of " + target.entity() + " does not exist");
            }
        }
        for (Map.Entry<String, String> attach : target.attach().entrySet()) {
            if (!byPath.containsKey(attach.getKey())) {
                throw new IllegalStateException("Attached part " + attach.getKey() + " of " + target.entity() + " does not exist");
            }
            if (!bonePaths.contains(attach.getValue())) {
                throw new IllegalStateException("Part " + attach.getKey() + " of " + target.entity() + " is attached to " + attach.getValue() + ", which is not a bone; bones are " + bonePaths);
            }
        }
        for (String key : target.parents().keySet()) {
            if (!bonePaths.contains(key)) {
                throw new IllegalStateException("parents names " + key + " of " + target.entity() + ", which is not a bone; bones are " + bonePaths);
            }
        }
        for (String key : target.boxes().keySet()) {
            if (!bonePaths.contains(key)) {
                throw new IllegalStateException("boxes names " + key + " of " + target.entity() + ", which is not a bone; bones are " + bonePaths);
            }
        }
        for (String key : target.joints().keySet()) {
            if (!bonePaths.contains(key)) {
                throw new IllegalStateException("joints names " + key + " of " + target.entity() + ", which is not a bone; bones are " + bonePaths);
            }
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
        float weight = 0.0F;
        for (Bone bone : ordered) {
            Vector3f size = bone.boxSize();
            weight += (size.x / 16.0F) * (size.y / 16.0F) * (size.z / 16.0F) * FLESH_DENSITY;
        }
        return new Rig(target.entity(), target.model(), target.layer(), target.texture(), target.variantNames(), target.passes(),
                target.scale(), weight, target.rotTime(), ordered, Optional.empty(), false);
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
        // rig pixels are render pixels: the renderer's model scale is baked in here and undone when drawing
        min.mul(target.scale());
        max.mul(target.scale());
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
            Vector3f offset = new Vector3f(extra.offset()).sub(s.offset()).mul(target.scale());
            inverse.transform(offset);
            Quaternionf rotation = new Quaternionf(inverse).mul(extra.rotation());
            extras.add(new ExtraPart(part, offset, rotation));
        });
        Optional<JointSpec> joint = parent.isEmpty() ? Optional.empty()
                : Optional.of(target.joints().getOrDefault(s.path(), jointFor(s.path(), restsOnTop(target, s, byPath.get(parent.get())))));
        return new Bone(s.path(), s.path(), parent, new Vector3f(s.offset()).mul(target.scale()), s.rotation(), min, max, joint, hide, extras, new Vector3f(1.0F));
    }

    private static Vector3f boxSize(RigTarget target, Seen s) {
        RigTarget.Box override = target.boxes().get(s.path());
        if (override != null) {
            return new Vector3f(override.max()).sub(override.min());
        }
        ModelPart.Cube c = s.biggest();
        return new Vector3f(c.maxX - c.minX, c.maxY - c.minY, c.maxZ - c.minZ);
    }

    /** An attached part, or anything under one, rides along with a bone instead of being one. */
    private static boolean isAttached(RigTarget target, String path) {
        for (String attached : target.attach().keySet()) {
            if (path.equals(attached) || path.startsWith(attached + "/")) {
                return true;
            }
        }
        return false;
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
            float best = 0; // a flat cube (a saddle line, a cape) is no body
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

    /**
     * Whether a part sits on top of the one it hangs from, as a biped's head sits on its shoulders: its pivot is at (or
     * above) the top of its parent's box, and its own box rises from it. Read from the model, as the rest of the rig is.
     */
    private static boolean restsOnTop(RigTarget target, Seen part, @Nullable Seen parent) {
        if (parent == null || parent.biggest() == null || part.biggest() == null) {
            return false;
        }
        // the parent's box top and the part's box middle, in the model's own space (+y is down)
        float top = Float.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            ModelPart.Cube c = parent.biggest();
            Vector3f corner = new Vector3f((i & 1) == 0 ? c.minX : c.maxX, (i & 2) == 0 ? c.minY : c.maxY, (i & 4) == 0 ? c.minZ : c.maxZ);
            top = Math.min(top, parent.rotation().transform(corner).add(parent.offset()).y);
        }
        ModelPart.Cube c = part.biggest();
        Vector3f middle = part.rotation().transform(new Vector3f((c.minX + c.maxX) / 2.0F, (c.minY + c.maxY) / 2.0F, (c.minZ + c.maxZ) / 2.0F)).add(part.offset());
        return part.offset().y <= top + 1.0F && middle.y < part.offset().y;
    }

    /** Joint limits by part name, for bones the target does not spell out (JointRules, shared with the generic bodies). */
    public static JointSpec jointFor(String name) {
        return com.avicagan.bloodandbones.carcass.rig.JointRules.jointFor(name);
    }

    /**
     * @param onTop whether the part sits on top of its parent ({@link #restsOnTop}); only a head or neck asks
     */
    public static JointSpec jointFor(String name, boolean onTop) {
        return com.avicagan.bloodandbones.carcass.rig.JointRules.jointFor(name, onTop);
    }

}
