package com.avicagan.bloodandbones.body;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.HumanoidArm;

/**
 * A part of a body that can be taken out and replaced: the limbs, the eyes and the organs. The head and
 * torso never are.
 */
public enum BodyPart implements StringRepresentable {
    LEFT_ARM("left_arm", Kind.ARM),
    RIGHT_ARM("right_arm", Kind.ARM),
    LEFT_LEG("left_leg", Kind.LEG),
    RIGHT_LEG("right_leg", Kind.LEG),
    LEFT_EYE("left_eye", Kind.EYE),
    RIGHT_EYE("right_eye", Kind.EYE),
    HEART("heart", Kind.HEART),
    LUNGS("lungs", Kind.LUNGS),
    STOMACH("stomach", Kind.STOMACH);

    public static final Codec<BodyPart> CODEC = StringRepresentable.fromEnum(BodyPart::values);

    /** What sort of part: an implant or a severed limb fits any part of its kind, either side. */
    public enum Kind {
        ARM, LEG, EYE, HEART, LUNGS, STOMACH
    }

    private final String name;
    private final Kind kind;

    BodyPart(String name, Kind kind) {
        this.name = name;
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public String translationKey() {
        return "bloodandbones.body." + name;
    }

    public static BodyPart arm(HumanoidArm side) {
        return side == HumanoidArm.LEFT ? LEFT_ARM : RIGHT_ARM;
    }
}
