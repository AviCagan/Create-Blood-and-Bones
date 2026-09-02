package com.avicagan.bloodandbones.datagen;

import com.avicagan.bloodandbones.carcass.rig.Bone;
import com.avicagan.bloodandbones.carcass.rig.JointSpec;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Turns a baked model tree into a rig. Rules: every part with cubes is a bone; a bone's parent is the
 * nearest enclosing part that is itself a bone; top-level bones hang off the biggest top-level bone,
 * the torso. The physics box is the bone's biggest cube. Joint limits come from the part name.
 */
public final class RigDerivation {
    /** Mass of one cubic block of animal, in Sable units where a plain solid block is 1.0. */
    public static final float FLESH_DENSITY = 1.0F;

    private RigDerivation() {
    }

    public static Rig derive(ResourceLocation entity, ModelLayerLocation layer, ResourceLocation texture, ModelPart root) {
        List<Bone> bones = new ArrayList<>();
        walk(root, "", new Vector3f(), new Quaternionf(), null, bones);
        if (bones.isEmpty()) {
            throw new IllegalStateException("Model " + layer + " has no parts with cubes");
        }

        Bone torso = null;
        float best = -1;
        for (Bone bone : bones) {
            if (bone.parent().isEmpty()) {
                Vector3f size = bone.boxSize();
                float volume = size.x * size.y * size.z;
                if (volume > best) {
                    best = volume;
                    torso = bone;
                }
            }
        }

        List<Bone> ordered = new ArrayList<>();
        ordered.add(new Bone(torso.name(), torso.part(), Optional.empty(), torso.offset(), torso.rotation(),
                torso.boxMin(), torso.boxMax(), Optional.empty()));
        for (Bone bone : bones) {
            if (bone == torso) {
                continue;
            }
            Optional<String> parent = bone.parent().isPresent() ? bone.parent() : Optional.of(torso.name());
            ordered.add(new Bone(bone.name(), bone.part(), parent, bone.offset(), bone.rotation(),
                    bone.boxMin(), bone.boxMax(), Optional.of(jointFor(bone.name()))));
        }
        float weight = 0.0F;
        for (Bone bone : ordered) {
            Vector3f size = bone.boxSize();
            weight += (size.x / 16.0F) * (size.y / 16.0F) * (size.z / 16.0F) * FLESH_DENSITY;
        }
        return new Rig(entity, layer.getModel(), layer.getLayer(), texture, weight, ordered);
    }

    private static void walk(ModelPart part, String path, Vector3f translation, Quaternionf rotation,
                             @Nullable String parentBone, List<Bone> out) {
        for (Map.Entry<String, ModelPart> entry : part.children.entrySet()) {
            String name = entry.getKey();
            ModelPart child = entry.getValue();
            PartPose pose = child.getInitialPose();
            Quaternionf local = new Quaternionf().rotationZYX(pose.zRot, pose.yRot, pose.xRot);
            Vector3f childTranslation = rotation.transform(new Vector3f(pose.x, pose.y, pose.z)).add(translation);
            Quaternionf childRotation = new Quaternionf(rotation).mul(local);
            String childPath = path.isEmpty() ? name : path + "/" + name;

            String boneName = parentBone;
            if (!child.cubes.isEmpty()) {
                ModelPart.Cube biggest = null;
                float best = -1;
                for (ModelPart.Cube cube : child.cubes) {
                    float volume = (cube.maxX - cube.minX) * (cube.maxY - cube.minY) * (cube.maxZ - cube.minZ);
                    if (volume > best) {
                        best = volume;
                        biggest = cube;
                    }
                }
                out.add(new Bone(childPath, childPath, Optional.ofNullable(parentBone), childTranslation, childRotation,
                        new Vector3f(biggest.minX, biggest.minY, biggest.minZ),
                        new Vector3f(biggest.maxX, biggest.maxY, biggest.maxZ),
                        Optional.empty()));
                boneName = childPath;
            }
            walk(child, childPath, childTranslation, childRotation, boneName, out);
        }
    }

    private static JointSpec jointFor(String name) {
        String lower = name.toLowerCase();
        if (lower.contains("head") || lower.contains("neck")) {
            return new JointSpec(new Vector3f(-40, -60, -20), new Vector3f(45, 60, 20), 1.5F, 0.0F);
        }
        if (lower.contains("leg") || lower.contains("arm")) {
            return new JointSpec(new Vector3f(-70, -15, -15), new Vector3f(70, 15, 15), 1.5F, 0.0F);
        }
        if (lower.contains("tail") || lower.contains("wing") || lower.contains("ear")) {
            return new JointSpec(new Vector3f(-45, -45, -45), new Vector3f(45, 45, 45), 1.0F, 0.0F);
        }
        return JointSpec.DEFAULT;
    }
}
