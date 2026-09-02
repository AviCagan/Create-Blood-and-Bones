package com.avicagan.bloodandbones.carcass.rig;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A model part drawn along with a bone without being one: a chicken's beak rides on its head.
 *
 * @param part     model part path from the layer root
 * @param offset   the part's pivot in the bone's local frame, pixels
 * @param rotation the part's rest rotation relative to the bone
 */
public record ExtraPart(String part, Vector3f offset, Quaternionf rotation) {
    public static final Codec<ExtraPart> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("part").forGetter(ExtraPart::part),
            RigCodecs.VEC3.fieldOf("offset").forGetter(ExtraPart::offset),
            RigCodecs.QUAT.fieldOf("rotation").forGetter(ExtraPart::rotation)
    ).apply(i, ExtraPart::new));
}
