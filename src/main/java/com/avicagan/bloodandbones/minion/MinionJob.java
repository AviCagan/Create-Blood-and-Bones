package com.avicagan.bloodandbones.minion;

import com.avicagan.bloodandbones.body.Body;
import com.avicagan.bloodandbones.body.BodyPart;
import com.avicagan.bloodandbones.body.ImplantItem;
import com.avicagan.bloodandbones.body.ImplantSpec;
import com.avicagan.bloodandbones.registry.BBItems;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * What a minion does, from what it was built with (checked again every second, so surgery changes it):
 * a weapon arm makes a fighter; two working arms and a working eye, a farmer; two working arms and no
 * eye, a courier that feels about for dropped things and carries them to a chest; anything less, a
 * companion that follows its maker.
 */
public enum MinionJob implements StringRepresentable {
    FIGHTER, FARMER, COURIER, COMPANION;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public String translationKey() {
        return "bloodandbones.minion.job." + getSerializedName();
    }

    /** A Hook Hand, Hydraulic Arm or Vent Arm that works. */
    public static boolean weapon(Body body, BodyPart arm, @Nullable LivingEntity wearer) {
        if (body.state(arm) != Body.State.IMPLANT || !body.works(arm, wearer) || !(body.implant(arm).getItem() instanceof ImplantItem implant)) {
            return false;
        }
        return implant == BBItems.HOOK_HAND.get() || implant == BBItems.HYDRAULIC_ARM.get() || implant.spec().ability() == ImplantSpec.Ability.VENT;
    }

    public static MinionJob of(Body body, @Nullable LivingEntity wearer) {
        if (weapon(body, BodyPart.LEFT_ARM, wearer) || weapon(body, BodyPart.RIGHT_ARM, wearer)) {
            return FIGHTER;
        }
        boolean arms = body.works(BodyPart.LEFT_ARM, wearer) && body.works(BodyPart.RIGHT_ARM, wearer);
        boolean sees = body.works(BodyPart.LEFT_EYE, wearer) || body.works(BodyPart.RIGHT_EYE, wearer);
        if (arms && sees) {
            return FARMER;
        }
        return arms ? COURIER : COMPANION;
    }
}
