package com.avicagan.bloodandbones.carcass.rig;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.joml.Vector3f;

/**
 * How far a bone may swing around its own X (pitch), Y (yaw) and Z (roll) axes relative to its rest pose,
 * in degrees, plus how much the joint resists motion.
 */
public record JointSpec(Vector3f minDegrees, Vector3f maxDegrees, float damping, float stiffness) {
    public static final Codec<JointSpec> CODEC = RecordCodecBuilder.create(i -> i.group(
            RigCodecs.VEC3.fieldOf("min_degrees").forGetter(JointSpec::minDegrees),
            RigCodecs.VEC3.fieldOf("max_degrees").forGetter(JointSpec::maxDegrees),
            Codec.FLOAT.optionalFieldOf("damping", 1.5F).forGetter(JointSpec::damping),
            Codec.FLOAT.optionalFieldOf("stiffness", 0.0F).forGetter(JointSpec::stiffness)
    ).apply(i, JointSpec::new));

    public static final JointSpec DEFAULT = new JointSpec(new Vector3f(-30, -30, -30), new Vector3f(30, 30, 30), 1.5F, 0.0F);
}
