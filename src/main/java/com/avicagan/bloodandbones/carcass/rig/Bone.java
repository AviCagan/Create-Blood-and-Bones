package com.avicagan.bloodandbones.carcass.rig;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Optional;

/**
 * One rigid piece of a carcass, derived from one vanilla model part.
 *
 * @param name     unique name inside the rig (the model part path)
 * @param part     model part path from the layer root, segments separated by '/'
 * @param parent   bone this one hangs off, empty for the torso
 * @param offset   pivot of the part in model-root pixel space, at the rest pose
 * @param rotation rest rotation of the part relative to the model root
 * @param boxMin   physics box minimum corner in part-local pixels
 * @param boxMax   physics box maximum corner in part-local pixels
 * @param joint    swing limits of the joint to the parent
 */
public record Bone(String name, String part, Optional<String> parent, Vector3f offset, Quaternionf rotation,
                   Vector3f boxMin, Vector3f boxMax, Optional<JointSpec> joint) {
    public static final Codec<Bone> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(Bone::name),
            Codec.STRING.fieldOf("part").forGetter(Bone::part),
            Codec.STRING.optionalFieldOf("parent").forGetter(Bone::parent),
            RigCodecs.VEC3.fieldOf("offset").forGetter(Bone::offset),
            RigCodecs.QUAT.fieldOf("rotation").forGetter(Bone::rotation),
            RigCodecs.VEC3.fieldOf("box_min").forGetter(Bone::boxMin),
            RigCodecs.VEC3.fieldOf("box_max").forGetter(Bone::boxMax),
            JointSpec.CODEC.optionalFieldOf("joint").forGetter(Bone::joint)
    ).apply(i, Bone::new));

    public Vector3f boxSize() {
        return new Vector3f(boxMax).sub(boxMin);
    }

    public JointSpec jointOrDefault() {
        return joint.orElse(JointSpec.DEFAULT);
    }
}
